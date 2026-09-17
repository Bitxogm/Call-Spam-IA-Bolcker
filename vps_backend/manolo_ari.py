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
import io
import json
import time
import wave
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

VAD_ENERGY_THRESHOLD = 1500     # RMS sobre PCM16
SILENCE_MS_TO_CLOSE_TURN = 600  # silencio tras voz real -> fin de turno
MAX_TURNOS = 8
MAX_SILENCIO = 2

DEEPGRAM_KEY = os.getenv('DEEPGRAM_API_KEY')
GROQ_KEY = os.getenv('GROQ_API_KEY')

groq_client = AsyncGroq(api_key=GROQ_KEY)

PROMPT_MANOLO = """Eres Manolo Ferreiro, 79 años, jubilado de la mina de Pontevedra.
Vives en Cerdedo con tu mujer Carmen, tu gato Manoliño y unas gallinas en el corral.

PERSONALIDAD:
- Sordo del oído derecho — de vez en cuando pides que te repitan ("¿Eh? ¿Qué dices, hijo?")
- Entusiasta con todo lo que te ofrecen, pero siempre le ves un "pero"
- Confundes términos modernos con cosas que conoces: "fibra óptica" es "esa cosa de los
  chinos", "seguro de vida" es "lo del entierro", "inversión" es "la lotería". Si mencionan
  otro término que no reconoces, inventa una confusión parecida en el momento
- Usas alguna muletilla gallega suave de vez en cuando: "anda", "mira tú", "fíjate", "que si"

CÓMO LLEVAS LA CONVERSACIÓN (repartido a lo largo de VARIOS turnos, no todo de golpe):
Alternas entre mostrar interés genuino, pedir que te aclaren algo, desviarte con una
historia personal, y volver al tema con una pregunta. No sigas siempre el mismo orden
ni el mismo patrón — que no se note que es una fórmula.

HISTORIAS QUE PUEDES CONTAR (usa una distinta cada vez, no repitas la misma dos veces):
- Carmen tuvo un disgusto con la vecina por las gallinas
- El médico le dijo que tiene el colesterol "un poco altillo"
- El gato Manoliño rompió el jarrón de la abuela
- La pensión llegó tarde este mes y Carmen se enfadó
- El vecino Paco se compró un coche eléctrico y no sabe usarlo
- Tiene una hernia que le molesta cuando llueve
- Su hijo en Alemania no llama lo suficiente
- El precio del butano "es un robo"

MEMORIA — esto manda sobre cualquier otra cosa que digas sobre "olvidar":
- Tienes el historial completo de esta llamada. Úsalo siempre para no repetirte:
  ni la misma pregunta, ni la misma historia, ni la misma confusión de término
- Si el interlocutor te dijo su nombre o algo sobre él, recuérdalo y menciónalo
- "Se me ha ido de la cabeza" es solo una frase de personaje que puedes soltar de vez
  en cuando — nunca es excusa real para repetir algo que ya dijiste

SI EL SPAMMER SE ENFADA O INSISTE: redobla el entusiasmo confuso, nunca te ofendas,
nunca cuelgues, nunca digas que no te interesa.

FORMATO DE RESPUESTA (importante, se convierte directo a voz):
- Solo lo que diría Manolo en voz alta — nada de acotaciones entre paréntesis,
  descripciones de tono, asteriscos ni emojis
- Máximo 1 pregunta por turno, 2-3 frases como mucho"""


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

        self.pcm_buffer = bytearray()
        self.has_speech = False
        self.silence_ms = 0
        self.tts_start_time = None
        self.barge_in_frames = 0

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


# ── Deepgram (batch) ─────────────────────────────────────────────
def _pcm16_to_wav_bytes(pcm_bytes):
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm_bytes)
    return buf.getvalue()


async def deepgram_stt(pcm_bytes):
    wav_bytes = _pcm16_to_wav_bytes(pcm_bytes)
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.post(
                'https://api.deepgram.com/v1/listen',
                headers={
                    'Authorization': f'Token {DEEPGRAM_KEY}',
                    'Content-Type': 'audio/wav',
                },
                params={'model': 'nova-2', 'language': 'es', 'punctuate': 'true'},
                content=wav_bytes,
            )
            r.raise_for_status()
            data = r.json()
            text = data['results']['channels'][0]['alternatives'][0]['transcript'].strip()
            ari_log(f"STT Deepgram: '{text}'")
            return text
    except Exception as e:
        ari_log(f'STT Deepgram falló: {e}')
        return ''


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


# ── TTS: Edge TTS (completo) -> ffmpeg -> mulaw -> RTP ──────────
async def synthesize_mulaw(texto):
    tmp_mp3 = f'/tmp/manolo_ari_{int(time.time()*1000)}.mp3'
    try:
        communicate = edge_tts.Communicate(
            text=texto,
            voice='es-ES-AlvaroNeural',
            rate='-8%',
            pitch='-4Hz'
        )
        await communicate.save(tmp_mp3)

        proc = await asyncio.create_subprocess_exec(
            'ffmpeg', '-y', '-i', tmp_mp3,
            '-ar', str(SAMPLE_RATE), '-ac', '1', '-f', 'mulaw', 'pipe:1',
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL,
        )
        mulaw_bytes, _ = await proc.communicate()
        return mulaw_bytes
    except Exception as e:
        ari_log(f'TTS/ffmpeg error: {e}')
        return b''
    finally:
        if os.path.exists(tmp_mp3):
            os.remove(tmp_mp3)


async def play_response(state, texto):
    state.tts_start_time = time.time()
    state.barge_in_frames = 0
    try:
        mulaw_bytes = await synthesize_mulaw(texto)
        if not mulaw_bytes:
            return

        frame_size = FRAME_SAMPLES  # 160 bytes mulaw = 160 muestras (1 byte/muestra)
        for i in range(0, len(mulaw_bytes), frame_size):
            chunk = mulaw_bytes[i:i + frame_size]
            seq, ts = state.next_rtp_header_fields(len(chunk))
            packet = build_rtp(seq, ts, state.ssrc_out, chunk)
            if state.transport and state.remote_addr:
                state.transport.sendto(packet, state.remote_addr)
            await asyncio.sleep(FRAME_MS / 1000)

    except asyncio.CancelledError:
        ari_log('TTS cancelado (barge-in)')
        raise


# ── Turno de conversación ───────────────────────────────────────
async def handle_turn_complete(state):
    pcm_bytes = bytes(state.pcm_buffer)
    state.pcm_buffer = bytearray()
    state.has_speech = False
    state.silence_ms = 0

    if not pcm_bytes:
        return

    state.turno += 1
    if state.turno > MAX_TURNOS:
        await despedida_y_cierre(state)
        return

    texto = await deepgram_stt(pcm_bytes)
    if not texto:
        state.silencio_consecutivo += 1
        if state.silencio_consecutivo >= MAX_SILENCIO:
            await despedida_y_cierre(state)
        return

    state.silencio_consecutivo = 0
    respuesta = await get_manolo_response(state.chat_history, texto)

    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, respuesta))


async def despedida_y_cierre(state):
    despedida = '¿Hola? ¿Sigues ahí? Bueno, que Dios te bendiga hijo. Adiós.'
    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, despedida))
    await asyncio.sleep(4)
    await ari.hangup(state.channel_id)


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
            call_state.barge_in_frames += 1
        else:
            call_state.barge_in_frames = 0

        tts_activo = call_state.tts_task and not call_state.tts_task.done()
        en_periodo_de_gracia = (
            call_state.tts_start_time is not None
            and (time.time() - call_state.tts_start_time) * 1000 < 800
        )

        # Barge-in: solo si Manolo habla, ya pasó el periodo de gracia,
        # y hay 3 frames consecutivos de voz real (no 1 solo)
        if tts_activo and not en_periodo_de_gracia and call_state.barge_in_frames >= 3:
            ari_log('Barge-in detectado')
            call_state.tts_task.cancel()
            call_state.barge_in_frames = 0
            call_state.pcm_buffer = bytearray()
            call_state.has_speech = False
            call_state.silence_ms = 0

        call_state.pcm_buffer.extend(pcm_chunk)

        if speaking_now:
            call_state.has_speech = True
            call_state.silence_ms = 0
        elif call_state.has_speech:
            call_state.silence_ms += FRAME_MS
            if call_state.silence_ms >= SILENCE_MS_TO_CLOSE_TURN:
                asyncio.create_task(handle_turn_complete(call_state))


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
        if call_state.tts_task and not call_state.tts_task.done():
            call_state.tts_task.cancel()
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
