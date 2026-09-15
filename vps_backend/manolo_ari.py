#!/root/ai_bridge/venv/bin/python3
"""
manolo_ari.py — Servidor ARI + ExternalMedia (RTP/UDP) para SpamBlockerApp
Sustituye el modelo AGI (graba->procesa->reproduce) por streaming bidireccional.
Solo se activa desde [manolo-ari-test]. [from-zadarma] sigue con AGI intacto.

LIMITACIÓN CONOCIDA: puerto UDP fijo (7000) + estado global -> una sola
llamada concurrente. Para producción hace falta asignación dinámica de puertos.
"""
import sys
import os
import json
import time
import random
import struct
import asyncio
import audioop
from datetime import datetime

import httpx
import websockets
import edge_tts

sys.path.insert(0, '/root/ai_bridge/venv/lib/python3.12/site-packages')
sys.path.insert(0, '/root/ai_bridge')

from dotenv import load_dotenv
load_dotenv('/root/ai_bridge/.env')

from groq import AsyncGroq

# ── Configuración ──────────────────────────────────────────────
ARI_HOST = '127.0.0.1'
ARI_PORT = 8088
ARI_USER = os.getenv('ARI_USER', 'manolo')
ARI_PASSWORD = os.getenv('ARI_PASSWORD')
ARI_APP = 'manolo-ari'

ARI_REST_BASE = f'http://{ARI_HOST}:{ARI_PORT}/ari'
ARI_WS_URL = (
    f'ws://{ARI_HOST}:{ARI_PORT}/ari/events'
    f'?api_key={ARI_USER}:{ARI_PASSWORD}&app={ARI_APP}'
)

EXTERNAL_MEDIA_HOST = '127.0.0.1'
EXTERNAL_MEDIA_PORT = 7000

SAMPLE_RATE = 8000
FRAME_MS = 20
FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS // 1000   # 160 muestras/frame
RTP_PAYLOAD_TYPE_ULAW = 0                        # PT=0 = PCMU (mulaw) según RFC 3551

VAD_ENERGY_THRESHOLD = 500      # RMS sobre PCM16 (solo para barge-in)
SILENCE_TIMEOUT = 15            # segundos sin voz real detectada -> cuenta como silencio
MAX_TURNOS = 8
MAX_SILENCIO = 2

DEEPGRAM_KEY = os.getenv('DEEPGRAM_API_KEY')
GROQ_KEY = os.getenv('GROQ_API_KEY')

groq_client = AsyncGroq(api_key=GROQ_KEY)

PROMPT_MANOLO = """Eres Manolo, un anciano gallego de 78 años,
jubilado, que vive en un pueblo pequeño con su mujer Carmen.

PERSONALIDAD:
- Amable, curioso, un poco sordo y despistado
- Te vas por las ramas con historias del pueblo
- Mencionas a Carmen, al médico, las ovejas, el tiempo
- Usas expresiones: "Ay hijo", "Que Dios te bendiga",
  "En mis tiempos...", "¡Carmen!"

REGLAS ESTRICTAS:
- NUNCA repitas una pregunta que ya hiciste antes
- Recuerda TODO lo que te han dicho en la conversación
- Solo UNA pregunta por turno
- Varía los temas: pueblo, familia, salud, tiempo, noticias
- Si ya sabes de dónde es, NO vuelvas a preguntar
- Haz referencias a lo que te dijeron antes
- Respuestas cortas: máximo 2 frases

OBJETIVO: entretener al máximo al spammer haciéndole
perder tiempo con conversación natural y absurda."""


def ari_log(msg):
    ts = datetime.now().strftime('%H:%M:%S.%f')[:-3]
    sys.stderr.write(f'[{ts}] MANOLO_ARI: {msg}\n')
    sys.stderr.flush()


# ── Cliente REST ARI ────────────────────────────────────────────
class ARIClient:
    def __init__(self):
        self.http = httpx.AsyncClient(
            auth=(ARI_USER, ARI_PASSWORD),
            base_url=ARI_REST_BASE,
            timeout=10.0,
        )

    async def answer(self, channel_id):
        r = await self.http.post(f'/channels/{channel_id}/answer')
        r.raise_for_status()

    async def create_bridge(self):
        r = await self.http.post('/bridges', params={'type': 'mixing'})
        r.raise_for_status()
        return r.json()['id']

    async def add_channel_to_bridge(self, bridge_id, channel_id):
        r = await self.http.post(f'/bridges/{bridge_id}/addChannel', params={'channel': channel_id})
        r.raise_for_status()

    async def create_external_media(self, host_port):
        r = await self.http.post('/channels/externalMedia', params={
            'app': ARI_APP,
            'external_host': host_port,
            'format': 'ulaw',
            'encapsulation': 'rtp',
            'transport': 'udp',
        })
        r.raise_for_status()
        return r.json()['id']

    async def destroy_bridge(self, bridge_id):
        try:
            r = await self.http.delete(f'/bridges/{bridge_id}')
            r.raise_for_status()
        except Exception as e:
            ari_log(f'destroy_bridge: {e}')

    async def hangup(self, channel_id):
        try:
            await self.http.delete(f'/channels/{channel_id}')
        except Exception as e:
            ari_log(f'hangup: {e}')

    async def close(self):
        await self.http.aclose()


ari = ARIClient()


# ── RTP: parseo y construcción ──────────────────────────────────
def parse_rtp(packet):
    """Devuelve (seq, timestamp, ssrc, payload) de un paquete RTP de 12 bytes de cabecera."""
    header = packet[:12]
    _v_p_x_cc, _m_pt, seq, timestamp, ssrc = struct.unpack('!BBHII', header)
    payload = packet[12:]
    return seq, timestamp, ssrc, payload


def build_rtp(seq, timestamp, ssrc, payload):
    header = struct.pack(
        '!BBHII',
        0x80,                       # V=2, P=0, X=0, CC=0
        RTP_PAYLOAD_TYPE_ULAW,      # M=0, PT=0 (PCMU)
        seq & 0xFFFF,
        timestamp & 0xFFFFFFFF,
        ssrc,
    )
    return header + payload


# ── Estado de la llamada (global — una sola llamada concurrente) ─
class CallState:
    def __init__(self, channel_id):
        self.channel_id = channel_id
        self.bridge_id = None
        self.external_media_channel_id = None
        self.remote_addr = None          # (ip, puerto) desde donde llega el RTP de Asterisk
        self.deepgram_ws = None
        self.last_speech_ts = time.time()
        self.closed = False

        self.chat_history = []
        self.turno = 0
        self.silencio_consecutivo = 0

        self.tts_task = None
        self.transport = None            # DatagramTransport para enviar RTP de vuelta

        self.ssrc_out = random.randint(0, 0xFFFFFFFF)
        self.seq_out = random.randint(0, 0xFFFF)
        self.timestamp_out = random.randint(0, 0xFFFFFFFF)

    def next_rtp_header_fields(self, n_samples):
        seq, ts = self.seq_out, self.timestamp_out
        self.seq_out = (self.seq_out + 1) & 0xFFFF
        self.timestamp_out = (self.timestamp_out + n_samples) & 0xFFFFFFFF
        return seq, ts


call_state: CallState | None = None
udp_transport: asyncio.DatagramTransport | None = None


# ── VAD por energía (RMS sobre PCM16) ───────────────────────────
def is_speech(pcm16_chunk):
    try:
        return audioop.rms(pcm16_chunk, 2) > VAD_ENERGY_THRESHOLD
    except Exception:
        return False


# ── Deepgram (streaming) ───────────────────────────────────────
DEEPGRAM_STREAM_URL = (
    'wss://api.deepgram.com/v1/listen'
    '?model=nova-2&language=es&punctuate=true'
    '&interim_results=true&endpointing=500'
    '&encoding=linear16&sample_rate=8000&channels=1'
)


async def deepgram_connect():
    headers = {'Authorization': f'Token {DEEPGRAM_KEY}'}
    ws = await websockets.connect(DEEPGRAM_STREAM_URL, additional_headers=headers)
    ari_log('Deepgram WS conectado')
    return ws


async def deepgram_receiver(state):
    try:
        async for raw in state.deepgram_ws:
            data = json.loads(raw)
            ari_log(f'Deepgram respuesta: {data}')

            if data.get('type') != 'Results' or not data.get('is_final'):
                continue
            alt = data.get('channel', {}).get('alternatives', [{}])[0]
            texto = alt.get('transcript', '').strip()
            await on_deepgram_transcript(state, texto)
    except websockets.exceptions.ConnectionClosed:
        ari_log(f'Deepgram WS cerrado ({state.channel_id})')
    except Exception as e:
        ari_log(f'Deepgram receiver error: {e}')


# ── Groq ──────────────────────────────────────────────────────
async def get_manolo_response(chat_history, text):
    try:
        messages = [{'role': 'system', 'content': PROMPT_MANOLO}]
        for i, mensaje in enumerate(chat_history):
            role = 'user' if i % 2 == 0 else 'assistant'
            messages.append({'role': role, 'content': mensaje})
        messages.append({'role': 'user', 'content': text})

        response = await groq_client.chat.completions.create(
            model='qwen/qwen3.8-27b',
            messages=messages,
            max_tokens=100,
            temperature=0.8,
        )
        respuesta = (response.choices[0].message.content or '').strip()
        chat_history.append(text)
        chat_history.append(respuesta)
        ari_log(f'Manolo (Groq): {respuesta}')
        return respuesta
    except Exception as e:
        ari_log(f'Groq error: {e}')
        return 'Ay, hijo, no te he oído bien. ¿Puedes repetirlo?'


# ── TTS: Edge TTS (streaming) -> ffmpeg -> mulaw -> RTP ─────────
async def synthesize_mulaw_stream(texto):
    """Generador async: produce chunks mulaw según Edge TTS los va entregando."""
    proc = await asyncio.create_subprocess_exec(
        'ffmpeg', '-y', '-f', 'mp3', '-i', 'pipe:0',
        '-ar', str(SAMPLE_RATE), '-ac', '1', '-f', 'mulaw', 'pipe:1',
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )

    async def feed_mp3():
        try:
            communicate = edge_tts.Communicate(text=texto, voice='es-ES-AlvaroNeural')
            async for chunk in communicate.stream():
                if chunk['type'] == 'audio':
                    proc.stdin.write(chunk['data'])
                    await proc.stdin.drain()
        except Exception as e:
            ari_log(f'Edge TTS stream error: {e}')
        finally:
            proc.stdin.close()

    feeder_task = asyncio.create_task(feed_mp3())
    try:
        while True:
            data = await proc.stdout.read(FRAME_SAMPLES)
            if not data:
                break
            yield data
    finally:
        await feeder_task
        if proc.returncode is None:
            proc.kill()
            await proc.wait()


async def play_response(state, texto):
    try:
        async for chunk in synthesize_mulaw_stream(texto):
            seq, ts = state.next_rtp_header_fields(len(chunk))
            packet = build_rtp(seq, ts, state.ssrc_out, chunk)
            if state.transport and state.remote_addr:
                state.transport.sendto(packet, state.remote_addr)
            await asyncio.sleep(FRAME_MS / 1000)

    except asyncio.CancelledError:
        ari_log('TTS cancelado (barge-in)')
        raise


# ── Turno de conversación ───────────────────────────────────────
async def silence_watchdog(state):
    while not state.closed:
        await asyncio.sleep(2)
        if state.closed:
            return
        if time.time() - state.last_speech_ts > SILENCE_TIMEOUT:
            state.silencio_consecutivo += 1
            state.last_speech_ts = time.time()
            ari_log(f'Silencio {state.silencio_consecutivo}/{MAX_SILENCIO} ({state.channel_id})')
            if state.silencio_consecutivo >= MAX_SILENCIO:
                await despedida_y_cierre(state)
                return


async def on_deepgram_transcript(state, texto):
    if not texto:
        return

    state.silencio_consecutivo = 0
    state.turno += 1
    if state.turno > MAX_TURNOS:
        await despedida_y_cierre(state)
        return

    respuesta = await get_manolo_response(state.chat_history, texto)

    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, respuesta))


async def despedida_y_cierre(state):
    state.closed = True
    despedida = '¿Hola? ¿Sigues ahí? Bueno, que Dios te bendiga hijo. Adiós.'
    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, despedida))
    await asyncio.sleep(4)
    await ari.hangup(state.channel_id)


async def send_to_deepgram(state, chunk):
    try:
        await state.deepgram_ws.send(chunk)
        ari_log(f'Deepgram chunk enviado: {len(chunk)} bytes')
    except Exception as e:
        ari_log(f'Deepgram send error: {e}')


# ── Protocolo UDP (recibe y envía RTP) ──────────────────────────
class RTPProtocol(asyncio.DatagramProtocol):
    def connection_made(self, transport):
        self.transport = transport
        if call_state:
            call_state.transport = transport

    def datagram_received(self, data, addr):
        global call_state
        if call_state is None:
            return

        if call_state.remote_addr is None:
            call_state.remote_addr = addr
            ari_log(f'RTP remoto detectado: {addr}')

        try:
            _seq, _ts, _ssrc, mulaw_payload = parse_rtp(data)
        except Exception as e:
            ari_log(f'RTP parse error: {e}')
            return

        pcm_chunk = audioop.ulaw2lin(mulaw_payload, 2)
        speaking_now = is_speech(pcm_chunk)

        if speaking_now:
            call_state.last_speech_ts = time.time()

        # Barge-in: si Manolo está hablando y llega voz real, cortar
        if call_state.tts_task and not call_state.tts_task.done() and speaking_now:
            ari_log('Barge-in detectado')
            call_state.tts_task.cancel()

        if call_state.deepgram_ws is not None:
            asyncio.create_task(send_to_deepgram(call_state, pcm_chunk))


# ── Eventos ARI ──────────────────────────────────────────────────
async def on_stasis_start(event):
    global call_state
    channel = event['channel']
    channel_id = channel['id']
    channel_name = channel.get('name', '')

    if channel_name.startswith('UnicastRTP/'):
        # Es el canal de externalMedia entrando en Stasis, no la llamada real
        if call_state and call_state.bridge_id:
            await ari.add_channel_to_bridge(call_state.bridge_id, channel_id)
            call_state.external_media_channel_id = channel_id
            ari_log(f'externalMedia añadido al bridge: {channel_id}')
        return

    ari_log(f'Llamada entrante: {channel_id}')
    call_state = CallState(channel_id)
    call_state.transport = udp_transport

    await ari.answer(channel_id)
    call_state.bridge_id = await ari.create_bridge()
    await ari.add_channel_to_bridge(call_state.bridge_id, channel_id)

    call_state.deepgram_ws = await deepgram_connect()
    asyncio.create_task(deepgram_receiver(call_state))
    asyncio.create_task(silence_watchdog(call_state))

    em_id = await ari.create_external_media(f'{EXTERNAL_MEDIA_HOST}:{EXTERNAL_MEDIA_PORT}')
    ari_log(f'externalMedia creado: {em_id} -> {EXTERNAL_MEDIA_HOST}:{EXTERNAL_MEDIA_PORT}')
    # Su propio StasisStart (rama de arriba) lo añadirá al bridge cuando llegue

    saludo = '¿Dígame? Ay, espere un momento que no encuentro las gafas... ¿Quién es?'
    call_state.tts_task = asyncio.create_task(play_response(call_state, saludo))


async def on_stasis_end(event):
    global call_state
    channel_id = event['channel']['id']
    if call_state and channel_id == call_state.channel_id:
        ari_log(f'Llamada terminada: {channel_id}')
        call_state.closed = True
        if call_state.tts_task and not call_state.tts_task.done():
            call_state.tts_task.cancel()
        if call_state.deepgram_ws:
            await call_state.deepgram_ws.close()
        if call_state.bridge_id:
            await ari.destroy_bridge(call_state.bridge_id)
        call_state = None


EVENT_HANDLERS = {
    'StasisStart': on_stasis_start,
    'StasisEnd': on_stasis_end,
}


# ── Main ──────────────────────────────────────────────────────
async def run_udp_server():
    global udp_transport
    loop = asyncio.get_running_loop()
    transport, _protocol = await loop.create_datagram_endpoint(
        RTPProtocol,
        local_addr=(EXTERNAL_MEDIA_HOST, EXTERNAL_MEDIA_PORT),
    )
    udp_transport = transport
    ari_log(f'Servidor UDP/RTP escuchando en {EXTERNAL_MEDIA_HOST}:{EXTERNAL_MEDIA_PORT}')
    return transport


async def run_ari_events():
    ari_log(f'Conectando a ARI: {ARI_WS_URL.split("?")[0]}')
    async with websockets.connect(ARI_WS_URL) as ws:
        ari_log('Conectado al WebSocket de eventos ARI')
        async for raw in ws:
            event = json.loads(raw)
            handler = EVENT_HANDLERS.get(event.get('type'))
            if handler:
                try:
                    await handler(event)
                except Exception as e:
                    ari_log(f'Error manejando {event.get("type")}: {e}')


async def main():
    udp_transport = await run_udp_server()
    try:
        await run_ari_events()
    finally:
        udp_transport.close()
        await ari.close()


if __name__ == '__main__':
    asyncio.run(main())
