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
import re
import json
import time
import wave
import random
import struct
import asyncio
import calendar
import audioop
from datetime import date, datetime, timezone

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

CALL_LOG_PATH = '/root/ai_bridge/call_log.json'
CALL_LOG_MAX = 50               # rota las más viejas

STATE_FILE = '/root/ai_bridge/current_mode.json'
SOUNDS_DIR = '/usr/share/asterisk/sounds/es'   # Data directory de Asterisk, igual que agi-bin/
FIXED_SOUND = 'fixed_spam_message'            # único mensaje del Modo 2
FIXED_PLAYBACK_TIMEOUT = 20     # segundos máximo esperando PlaybackFinished

# cartesia = voz principal de Cartesia con Edge (TTS_VOICE) de reserva; edge = solo Edge
TTS_PROVIDER = (os.getenv('TTS_PROVIDER') or 'edge').strip().lower()
# `or` en vez de default de getenv: una línea vacía en el .env (TTS_VOICE=) también cae al defecto
TTS_VOICE = os.getenv('TTS_VOICE') or 'es-ES-AlvaroNeural'
TTS_RATE = os.getenv('TTS_RATE') or '-8%'
TTS_PITCH = os.getenv('TTS_PITCH') or '-4Hz'
GROQ_MODEL = 'qwen/qwen3.8-27b'

TTS_TIMEOUT = 10                # segundos máximo para Edge TTS + ffmpeg
MAX_ERRORES_STT = 2             # errores reales seguidos de Deepgram antes del plan B
FRASE_EMERGENCIA = 'Ay, hijo, no te he oído bien. ¿Puedes repetirlo?'

DEEPGRAM_KEY = os.getenv('DEEPGRAM_API_KEY')
GROQ_KEY = os.getenv('GROQ_API_KEY')

groq_client = AsyncGroq(api_key=GROQ_KEY)


def ari_log(msg):
    ts = datetime.now().strftime('%H:%M:%S.%f')[:-3]
    sys.stderr.write(f'[{ts}] MANOLO_ARI: {msg}\n')
    sys.stderr.flush()


# Las funciones de STT/LLM/TTS lanzan estas excepciones en vez de tragarse el
# error: así la llamada puede caer al plan B y healthcheck.py puede usar las
# mismas funciones para saber si cada pieza funciona.
class STTError(Exception):
    pass


class LLMError(Exception):
    pass


class TTSError(Exception):
    pass


class SinCreditos(TTSError):
    pass


class CartesiaSinCreditos(SinCreditos):
    pass


class ElevenLabsSinCreditos(SinCreditos):
    pass


# ── Cartesia ───────────────────────────────────────────────────
CARTESIA_URL = 'https://api.cartesia.ai'
CARTESIA_VERSION = '2026-08-14'     # único valor que admite la API hoy
CARTESIA_MODEL = 'sonic-3.6'
CARTESIA_API_KEY = os.getenv('CARTESIA_API_KEY')
CARTESIA_VOICE_ID = os.getenv('CARTESIA_VOICE_ID')
CARTESIA_STATE_PATH = '/root/ai_bridge/cartesia_state.json'
# Corto a propósito: si Cartesia tarda, el spammer espera en silencio y aún queda
# Edge por detrás. Los 10 s de TTS_TIMEOUT son para el último escalón.
CARTESIA_TIMEOUT = 3
CARTESIA_SPEED_MIN, CARTESIA_SPEED_MAX = 0.6, 1.5   # generation_config.speed, 1 = normal


def _leer_velocidad(valor):
    if not valor:
        return 1.0
    try:
        velocidad = float(valor)
    except ValueError:
        velocidad = None
    if velocidad is None or not CARTESIA_SPEED_MIN <= velocidad <= CARTESIA_SPEED_MAX:
        ari_log(f'CARTESIA_SPEED={valor!r} fuera de {CARTESIA_SPEED_MIN}-{CARTESIA_SPEED_MAX}, uso 1.0')
        return 1.0
    return velocidad


CARTESIA_SPEED = _leer_velocidad(os.getenv('CARTESIA_SPEED'))


def _leer_dia_renovacion(valor, variable='CARTESIA_DIA_RENOVACION'):
    if not valor:
        return 1
    try:
        dia = int(valor)
    except ValueError:
        dia = None
    if dia is None or not 1 <= dia <= 31:
        ari_log(f'{variable}={valor!r} no es un día del mes (1-31), uso 1')
        return 1
    return dia


# Día del mes en que Cartesia renueva los créditos del plan
CARTESIA_DIA_RENOVACION = _leer_dia_renovacion(os.getenv('CARTESIA_DIA_RENOVACION'))

# ── ElevenLabs ────────────────────────────────────────────────
ELEVENLABS_URL = 'https://api.elevenlabs.io'
ELEVENLABS_MODEL = 'eleven_flash_v2_5'      # el más rápido y barato, con español
ELEVENLABS_API_KEY = os.getenv('ELEVENLABS_API_KEY')
ELEVENLABS_VOICE_ID = os.getenv('ELEVENLABS_VOICE_ID')
ELEVENLABS_STATE_PATH = '/root/ai_bridge/elevenlabs_state.json'
ELEVENLABS_TIMEOUT = 3          # mismo criterio que CARTESIA_TIMEOUT: Edge sigue detrás
ELEVENLABS_DIA_RENOVACION = _leer_dia_renovacion(os.getenv('ELEVENLABS_DIA_RENOVACION'),
                                                 'ELEVENLABS_DIA_RENOVACION')

# ── Personajes ──────────────────────────────────────────────────
PERSONAS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'personas')

PERSONAJES = {
    'manolo': {
        'nombre': 'Manolo',
        'prompt': 'manolo.txt',
        'voz': 'cartesia',
        'voz_edge': None,                   # reserva: TTS_VOICE (Roi)
        'saludo': '¿Dígame? Ay, espere un momento que no encuentro las gafas... ¿Quién es?',
        'despedida': '¿Hola? ¿Sigues ahí? Bueno, que Dios te bendiga hijo. Adiós.',
    },
    'paquita': {
        'nombre': 'Paquita',
        'prompt': 'paquita.txt',
        'voz': 'elevenlabs',
        # Reserva femenina: Roi no le pega. Configurable, por si Edge cambia de voces
        'voz_edge': os.getenv('PAQUITA_VOZ_RESERVA') or 'es-ES-ElviraNeural',
        'saludo': '¿Sí? ¿Dígame? Espere, hijo, que estaba tendiendo la ropa... ¿Quién es?',
        'despedida': '¿Hola? ¿Sigue usted ahí? Ea, pues se habrá cortado. Adiós, mi alma.',
    },
}


def _leer_prompts():
    """Manolo es obligatorio: sin él no hay Modo 3. Un personaje sin fichero se desactiva."""
    prompts = {}
    for clave, personaje in PERSONAJES.items():
        ruta = os.path.join(PERSONAS_DIR, personaje['prompt'])
        try:
            with open(ruta, encoding='utf-8') as f:
                prompts[clave] = f.read().strip()
        except FileNotFoundError:
            if clave == 'manolo':
                raise FileNotFoundError(f'Falta {ruta}: es el personaje por defecto')
            ari_log(f'Falta {ruta}: {personaje["nombre"]} desactivado')
    return prompts


PROMPTS = _leer_prompts()
PROMPT_MANOLO = PROMPTS['manolo']


def _leer_personaje(valor):
    valor = (valor or 'aleatorio').strip().lower()
    if valor != 'aleatorio' and valor not in PERSONAJES:
        ari_log(f'PERSONAJE={valor!r} no válido (aleatorio|{"|".join(PERSONAJES)}), uso aleatorio')
        return 'aleatorio'
    return valor


PERSONAJE = _leer_personaje(os.getenv('PERSONAJE'))


def voz_edge(personaje):
    """Voz de Edge del personaje: su reserva si cae la premium, o la única con TTS_PROVIDER=edge."""
    return PERSONAJES.get(personaje, {}).get('voz_edge') or TTS_VOICE


def personajes_activos():
    """Los que pueden atender una llamada con la configuración actual."""
    if PERSONAJE == 'aleatorio':
        return [c for c in PERSONAJES if c in PROMPTS]
    return [PERSONAJE] if PERSONAJE in PROMPTS else ['manolo']


def elegir_personaje():
    return random.choice(personajes_activos())


def voces_en_uso():
    """Voces premium que pueden sonar: el healthcheck solo comprueba esas."""
    if TTS_PROVIDER == 'edge':
        return set()
    return {PERSONAJES[c]['voz'] for c in personajes_activos()} & set(VOCES_PREMIUM)


# edge = todo con TTS_VOICE; cualquier otro valor válido = la voz premium de cada personaje
if TTS_PROVIDER not in ('cartesia', 'edge'):
    ari_log(f'TTS_PROVIDER={TTS_PROVIDER!r} no válido, uso edge')
    TTS_PROVIDER = 'edge'


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

    async def play(self, channel_id, sound_name, lang='es'):
        # 'lang' es obligatorio: sin él, Asterisk resuelve sound: contra el
        # idioma del canal (que llega como 'en' desde Zadarma) y busca en
        # sounds/en/, donde el fichero no existe.
        r = await self.http.post(f'/channels/{channel_id}/play',
                                 params={'media': f'sound:{sound_name}', 'lang': lang})
        r.raise_for_status()
        return r.json()

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
        self.errores_stt_consecutivos = 0
        self.plan_b = False              # True = ya se está reproduciendo el mensaje fijo
        self.saltos = set()              # escalones de TTS ya bajados en esta llamada
        self.personaje = 'manolo'        # lo elige on_stasis_start en el Modo 3

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


# ── Registro de llamadas (call_log.json) ────────────────────────
def load_call_log():
    try:
        with open(CALL_LOG_PATH) as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except (FileNotFoundError, json.JSONDecodeError):
        return []


def save_call_log(registros):
    """Escritura atómica: control_api.py lee este fichero en paralelo."""
    registros = registros[-CALL_LOG_MAX:]
    tmp_path = CALL_LOG_PATH + '.tmp'
    try:
        with open(tmp_path, 'w') as f:
            json.dump(registros, f, ensure_ascii=False)
        os.replace(tmp_path, CALL_LOG_PATH)
    except Exception as e:
        ari_log(f'call_log write error: {e}')


def get_current_mode():
    """FIXED = mensaje fijo y colgar. AI = conversación con Manolo."""
    try:
        with open(STATE_FILE) as f:
            return json.load(f).get('mode', 'AI')
    except Exception as e:
        ari_log(f'No se pudo leer {STATE_FILE} ({e}), asumiendo AI')
        return 'AI'



def extraer_numero(channel):
    """Número del llamante. Campo pendiente de confirmar con un evento real."""
    for clave in ('caller', 'connected'):
        datos = channel.get(clave) or {}
        numero = (datos.get('number') or '').strip()
        if numero:
            return numero
    return 'desconocido'


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
    """Devuelve la transcripción ('' si no hay voz). Lanza STTError si Deepgram falla."""
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
        raise STTError(f'{type(e).__name__}: {e}') from e


# ── Groq ──────────────────────────────────────────────────────
# Margen holgado: el prompt pide 1-2 frases, pero el modelo se pasa a veces.
MAX_TOKENS_RESPUESTA = 200
_FIN_DE_FRASE = '.!?…'


def recortar_ultima_frase(texto):
    """Si la respuesta quedó cortada por max_tokens, la recorta en el último
    signo de fin de frase. Si no hay ninguno, la devuelve tal cual."""
    texto = texto.strip()
    if not texto or texto[-1] in _FIN_DE_FRASE:
        return texto
    corte = max(texto.rfind(c) for c in _FIN_DE_FRASE)
    if corte == -1:
        return texto
    return texto[:corte + 1].strip()


def _leer_max_chars(valor):
    if not valor:
        return 180
    try:
        maximo = int(valor)
    except ValueError:
        maximo = None
    if maximo is None or maximo < 20:
        ari_log(f'MAX_CHARS_RESPUESTA={valor!r} no válido (entero de 20 o más), uso 180')
        return 180
    return maximo


# Límite duro: el prompt pide 1-2 frases, pero alguna respuesta llegó a 400 caracteres
MAX_CHARS_RESPUESTA = _leer_max_chars(os.getenv('MAX_CHARS_RESPUESTA'))


def limitar_longitud(texto, maximo=None):
    """Corta en el último . ? ! antes del límite, nunca a mitad de frase. Sin ninguno,
    en la última coma o espacio, con "…". El resultado nunca pasa de `maximo`."""
    maximo = maximo or MAX_CHARS_RESPUESTA
    if len(texto) <= maximo:
        return texto
    trozo = texto[:maximo]
    corte = max(trozo.rfind(c) for c in '.?!')
    if corte > 0:
        return trozo[:corte + 1].strip()
    trozo = trozo[:maximo - 1]          # sitio para el "…"
    corte = max(trozo.rfind(','), trozo.rfind(' '))
    if corte > 0:
        trozo = trozo[:corte]
    return trozo.rstrip(' ,;:') + '…'


async def generate_manolo_reply(chat_history, text, personaje='manolo'):
    """Respuesta del personaje. Lanza LLMError si Groq falla o devuelve vacío."""
    messages = [{'role': 'system', 'content': PROMPTS.get(personaje, PROMPT_MANOLO)}]
    for i, mensaje in enumerate(chat_history):
        role = 'user' if i % 2 == 0 else 'assistant'
        messages.append({'role': role, 'content': mensaje})
    messages.append({'role': 'user', 'content': text})

    try:
        response = await groq_client.chat.completions.create(
            model=GROQ_MODEL,
            messages=messages,
            max_tokens=MAX_TOKENS_RESPUESTA,
            temperature=0.8,
        )
        choice = response.choices[0]
        respuesta = (choice.message.content or '').strip()
        if getattr(choice, 'finish_reason', None) == 'length':
            respuesta = recortar_ultima_frase(respuesta)
    except Exception as e:
        raise LLMError(f'{type(e).__name__}: {e}') from e
    if not respuesta:
        raise LLMError('respuesta vacía')
    limitada = limitar_longitud(respuesta)
    if limitada != respuesta:
        ari_log(f'Respuesta recortada: {len(respuesta)} → {len(limitada)} caracteres')
    return limitada


async def get_manolo_response(chat_history, text, personaje='manolo'):
    try:
        respuesta = await generate_manolo_reply(chat_history, text, personaje)
    except LLMError as e:
        ari_log(f'FALLBACK: Groq falló ({e}) → frase de emergencia')
        return FRASE_EMERGENCIA
    chat_history.append(text)
    chat_history.append(respuesta)
    ari_log(f'{PERSONAJES[personaje]["nombre"]} (Groq): {respuesta}')
    return respuesta


# ── TTS: Cartesia -> Edge (TTS_VOICE) -> mensaje fijo ─────────────
def cartesia_headers(api_key=None):
    return {
        'Authorization': f'Bearer {api_key or CARTESIA_API_KEY}',
        'Cartesia-Version': CARTESIA_VERSION,
    }


def es_sin_creditos(status, cuerpo):
    """La API no documenta el código de 'sin créditos': 402, o un 4xx que lo diga."""
    if status == 402:
        return True
    return 400 <= status < 500 and bool(
        re.search(r'credit|insufficient|quota|balance|payment', cuerpo or '', re.I))


async def _synthesize_cartesia(texto):
    if not (CARTESIA_API_KEY and CARTESIA_VOICE_ID):
        raise TTSError('Cartesia: faltan CARTESIA_API_KEY o CARTESIA_VOICE_ID')
    try:
        async with httpx.AsyncClient(timeout=CARTESIA_TIMEOUT) as client:
            r = await client.post(
                f'{CARTESIA_URL}/tts/bytes',
                headers=cartesia_headers(),
                json={
                    'model_id': CARTESIA_MODEL,
                    'transcript': texto,
                    'voice': {'id': CARTESIA_VOICE_ID},
                    'language': 'es',
                    # µ-law 8 kHz en crudo: lo que va al RTP, sin pasar por ffmpeg
                    'output_format': {'container': 'raw', 'encoding': 'pcm_mulaw',
                                      'sample_rate': SAMPLE_RATE},
                    'generation_config': {'speed': CARTESIA_SPEED},
                },
            )
    except Exception as e:
        raise TTSError(f'Cartesia: {type(e).__name__}: {e}') from e
    if r.status_code != 200:
        cuerpo = ' '.join((r.text or '').split())[:300]
        error = f'Cartesia {r.status_code}: {cuerpo}'
        if es_sin_creditos(r.status_code, cuerpo):
            raise CartesiaSinCreditos(error)
        raise TTSError(error)
    if not r.content:
        raise TTSError('Cartesia: audio vacío')
    return r.content


async def synthesize_cartesia(texto):
    """Texto -> mulaw 8 kHz con Cartesia. CartesiaSinCreditos si no quedan créditos."""
    try:
        return await asyncio.wait_for(_synthesize_cartesia(texto), timeout=CARTESIA_TIMEOUT)
    except asyncio.TimeoutError as e:
        raise TTSError(f'Cartesia: timeout de {CARTESIA_TIMEOUT}s') from e


async def cartesia_verificar_voz():
    """Comprueba clave y voz con una lectura (GET /voices/{id}), sin gastar créditos."""
    if not (CARTESIA_API_KEY and CARTESIA_VOICE_ID):
        raise TTSError('Cartesia: faltan CARTESIA_API_KEY o CARTESIA_VOICE_ID')
    async with httpx.AsyncClient(timeout=TTS_TIMEOUT) as client:
        r = await client.get(f'{CARTESIA_URL}/voices/{CARTESIA_VOICE_ID}',
                             headers=cartesia_headers())
    if r.status_code != 200:
        raise TTSError(f'Cartesia {r.status_code}: {" ".join((r.text or "").split())[:300]}')
    return r.json()


def _dia_en_mes(anio, mes, dia):
    # Un 31 en noviembre es el 30: el último día que tenga el mes
    return date(anio, mes, min(dia, calendar.monthrange(anio, mes)[1]))


def proxima_renovacion(hoy, dia):
    """La próxima vez que llega el día de renovación, estrictamente después de hoy."""
    este_mes = _dia_en_mes(hoy.year, hoy.month, dia)
    if este_mes > hoy:
        return este_mes
    anio, mes = (hoy.year + 1, 1) if hoy.month == 12 else (hoy.year, hoy.month + 1)
    return _dia_en_mes(anio, mes, dia)


def _agotado_hasta(ruta, hoy=None):
    """Fecha hasta la que un proveedor se salta por falta de créditos, o None."""
    hoy = hoy or date.today()
    try:
        with open(ruta) as f:
            hasta = date.fromisoformat(json.load(f)['agotado_hasta'])
    except (FileNotFoundError, KeyError, ValueError, TypeError, json.JSONDecodeError):
        return None
    return hasta if hoy < hasta else None


def _marcar_agotado(ruta, dia_renovacion, hoy=None):
    """Sin créditos: Edge (TTS_VOICE) directamente hasta que el proveedor renueve."""
    hoy = hoy or date.today()
    hasta = proxima_renovacion(hoy, dia_renovacion)
    tmp_path = ruta + '.tmp'
    try:
        with open(tmp_path, 'w') as f:
            json.dump({'agotado_hasta': hasta.isoformat(), 'desde': hoy.isoformat()}, f)
        os.replace(tmp_path, ruta)
    except Exception as e:
        ari_log(f'{os.path.basename(ruta)} write error: {e}')
    return hasta


def cartesia_agotado_hasta(hoy=None):
    return _agotado_hasta(CARTESIA_STATE_PATH, hoy)


def marcar_cartesia_agotado(hoy=None):
    return _marcar_agotado(CARTESIA_STATE_PATH, CARTESIA_DIA_RENOVACION, hoy)


def elevenlabs_agotado_hasta(hoy=None):
    return _agotado_hasta(ELEVENLABS_STATE_PATH, hoy)


def marcar_elevenlabs_agotado(hoy=None):
    return _marcar_agotado(ELEVENLABS_STATE_PATH, ELEVENLABS_DIA_RENOVACION, hoy)


def elevenlabs_headers():
    return {'xi-api-key': ELEVENLABS_API_KEY or ''}


async def _synthesize_elevenlabs(texto):
    if not (ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID):
        raise TTSError('ElevenLabs: faltan ELEVENLABS_API_KEY o ELEVENLABS_VOICE_ID')
    try:
        async with httpx.AsyncClient(timeout=ELEVENLABS_TIMEOUT) as client:
            r = await client.post(
                f'{ELEVENLABS_URL}/v1/text-to-speech/{ELEVENLABS_VOICE_ID}',
                # µ-law 8 kHz: lo que va al RTP, sin pasar por ffmpeg
                params={'output_format': 'ulaw_8000'},
                headers=elevenlabs_headers(),
                json={'text': texto, 'model_id': ELEVENLABS_MODEL, 'language_code': 'es'},
            )
    except Exception as e:
        raise TTSError(f'ElevenLabs: {type(e).__name__}: {e}') from e
    if r.status_code != 200:
        cuerpo = ' '.join((r.text or '').split())[:300]
        error = f'ElevenLabs {r.status_code}: {cuerpo}'
        if es_sin_creditos(r.status_code, cuerpo):
            raise ElevenLabsSinCreditos(error)
        raise TTSError(error)
    if not r.content:
        raise TTSError('ElevenLabs: audio vacío')
    return r.content


async def synthesize_elevenlabs(texto):
    """Texto -> mulaw 8 kHz con ElevenLabs. ElevenLabsSinCreditos si no quedan caracteres."""
    try:
        return await asyncio.wait_for(_synthesize_elevenlabs(texto), timeout=ELEVENLABS_TIMEOUT)
    except asyncio.TimeoutError as e:
        raise TTSError(f'ElevenLabs: timeout de {ELEVENLABS_TIMEOUT}s') from e


async def _elevenlabs_get(ruta):
    if not ELEVENLABS_API_KEY:
        raise TTSError('ElevenLabs: falta ELEVENLABS_API_KEY')
    async with httpx.AsyncClient(timeout=TTS_TIMEOUT) as client:
        r = await client.get(f'{ELEVENLABS_URL}{ruta}', headers=elevenlabs_headers())
    if r.status_code != 200:
        raise TTSError(f'ElevenLabs {r.status_code}: {" ".join((r.text or "").split())[:300]}')
    return r.json()


async def elevenlabs_verificar_voz():
    """Clave y voz con una lectura (GET /v1/voices/{id}), sin sintetizar."""
    if not ELEVENLABS_VOICE_ID:
        raise TTSError('ElevenLabs: falta ELEVENLABS_VOICE_ID')
    return await _elevenlabs_get(f'/v1/voices/{ELEVENLABS_VOICE_ID}')


async def elevenlabs_suscripcion():
    """Caracteres usados y límite del periodo (GET /v1/user/subscription)."""
    return await _elevenlabs_get('/v1/user/subscription')


# Voces premium por clave del personaje: (nombre en log y plan_b, síntesis, agotado, marcar).
# Por nombre y no por referencia: así se resuelven en cada llamada (y los tests pueden
# sustituirlas).
VOCES_PREMIUM = {
    'cartesia': ('Cartesia', 'synthesize_cartesia', 'cartesia_agotado_hasta', 'marcar_cartesia_agotado'),
    'elevenlabs': ('ElevenLabs', 'synthesize_elevenlabs', 'elevenlabs_agotado_hasta', 'marcar_elevenlabs_agotado'),
}


def _voz_premium(voz):
    nombre, *funciones = VOCES_PREMIUM[voz]
    return (nombre, *(globals()[f] for f in funciones))


async def _synthesize_edge(texto, tmp_mp3, voz):
    try:
        communicate = edge_tts.Communicate(
            text=texto,
            voice=voz,
            rate=TTS_RATE,
            pitch=TTS_PITCH
        )
        await communicate.save(tmp_mp3)
    except Exception as e:
        raise TTSError(f'Edge TTS: {type(e).__name__}: {e}') from e

    try:
        proc = await asyncio.create_subprocess_exec(
            'ffmpeg', '-y', '-i', tmp_mp3,
            '-ar', str(SAMPLE_RATE), '-ac', '1', '-f', 'mulaw', 'pipe:1',
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        mulaw_bytes, stderr = await proc.communicate()
    except Exception as e:
        raise TTSError(f'ffmpeg: {type(e).__name__}: {e}') from e
    if proc.returncode != 0 or not mulaw_bytes:
        ultima_linea = (stderr or b'').decode(errors='replace').strip().splitlines()[-1:]
        raise TTSError(f'ffmpeg: código {proc.returncode}, {len(mulaw_bytes)} bytes '
                       f'{ultima_linea[0] if ultima_linea else ""}'.strip())
    return mulaw_bytes


async def synthesize_edge(texto, voz=None):
    """Texto -> mulaw 8 kHz con Edge (`voz`, por defecto TTS_VOICE). Lanza TTSError si Edge
    o ffmpeg fallan o tardan."""
    tmp_mp3 = f'/tmp/manolo_ari_{int(time.time()*1000)}.mp3'
    try:
        return await asyncio.wait_for(_synthesize_edge(texto, tmp_mp3, voz or TTS_VOICE),
                                      timeout=TTS_TIMEOUT)
    except asyncio.TimeoutError as e:
        raise TTSError(f'timeout de {TTS_TIMEOUT}s') from e
    finally:
        if os.path.exists(tmp_mp3):
            os.remove(tmp_mp3)


async def synthesize_mulaw(texto, al_saltar=None, probar_premium=True, voz='cartesia',
                           voz_reserva=None):
    """Texto -> mulaw 8 kHz por la escalera voz premium (Cartesia o ElevenLabs) -> Edge.

    `voz` es la del personaje. `al_saltar(pieza, error)` se llama cada vez que se baja
    un escalón. Con `probar_premium=False` va directo a Edge (la premium ya falló en
    esta llamada). Si falla también Edge, TTSError: el que llama decide (mensaje fijo).
    """
    if TTS_PROVIDER != 'edge' and probar_premium and voz in VOCES_PREMIUM:
        nombre, sintetizar, agotado_hasta, marcar_agotado = _voz_premium(voz)
        hasta = agotado_hasta()
        if hasta:
            _saltar(al_saltar, nombre, f'sin créditos hasta {hasta.isoformat()}')
        else:
            try:
                return await _cronometrar(nombre, sintetizar(texto))
            except SinCreditos as e:
                hasta = marcar_agotado()
                _saltar(al_saltar, nombre, f'{e} — sin créditos, Edge hasta {hasta.isoformat()}')
            except TTSError as e:
                _saltar(al_saltar, nombre, e)
    return await _cronometrar('Edge', synthesize_edge(texto, voz_reserva))


async def _cronometrar(proveedor, sintesis):
    """Log de latencia: hasta tener el audio listo (las dos síntesis devuelven el audio entero)."""
    inicio = time.monotonic()
    try:
        audio = await sintesis
    except Exception:
        ari_log(f'TTS {proveedor}: falló tras {(time.monotonic() - inicio) * 1000:.0f} ms')
        raise
    ari_log(f'TTS {proveedor}: {(time.monotonic() - inicio) * 1000:.0f} ms ({len(audio)} bytes)')
    return audio


def _saltar(al_saltar, pieza, error):
    if al_saltar:
        al_saltar(pieza, error)


def registrar_salto(state, pieza, error):
    """Un salto de escalón por pieza y llamada: al log y al call log."""
    if pieza in state.saltos:
        return
    state.saltos.add(pieza)
    ari_log(f'FALLBACK: {pieza} falló ({error}) → Edge ({voz_edge(state.personaje)})')
    marcar_plan_b(state.channel_id, pieza)


async def play_response(state, texto):
    state.tts_start_time = time.time()
    state.barge_in_frames = 0
    try:
        try:
            # Si la voz premium ya falló en esta llamada, el resto va directo a Edge:
            # reintentarlo costaría hasta 3 s de silencio en cada respuesta
            voz = PERSONAJES[state.personaje]['voz']
            premium = VOCES_PREMIUM.get(voz, (None,))[0]
            mulaw_bytes = await synthesize_mulaw(
                texto,
                al_saltar=lambda pieza, error: registrar_salto(state, pieza, error),
                probar_premium=premium not in state.saltos,
                voz=voz,
                voz_reserva=voz_edge(state.personaje))
        except TTSError as e:
            activar_plan_b(state, 'TTS', e)
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

    try:
        texto = await deepgram_stt(pcm_bytes)
    except STTError as e:
        # Un error real no es silencio: lleva su propio contador
        state.errores_stt_consecutivos += 1
        ari_log(f'STT Deepgram falló ({state.errores_stt_consecutivos}/{MAX_ERRORES_STT}): {e}')
        if state.errores_stt_consecutivos >= MAX_ERRORES_STT:
            activar_plan_b(state, 'Deepgram', e)
        return
    state.errores_stt_consecutivos = 0

    if state.plan_b:
        return

    if not texto:
        state.silencio_consecutivo += 1
        if state.silencio_consecutivo >= MAX_SILENCIO:
            await despedida_y_cierre(state)
        return

    state.silencio_consecutivo = 0
    respuesta = await get_manolo_response(state.chat_history, texto, state.personaje)
    if state.plan_b:
        return

    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, respuesta))


async def despedida_y_cierre(state):
    despedida = PERSONAJES[state.personaje]['despedida']
    if state.tts_task and not state.tts_task.done():
        state.tts_task.cancel()
    state.tts_task = asyncio.create_task(play_response(state, despedida))
    await asyncio.sleep(4)
    if state.plan_b:
        return  # run_fixed ya cuelga al terminar el mensaje fijo
    await ari.hangup(state.channel_id)


# ── Protocolo UDP (recibe y envía RTP) ──────────────────────────
class RTPProtocol(asyncio.DatagramProtocol):
    def connection_made(self, transport):
        self.transport = transport
        if call_state:
            call_state.transport = transport

    def datagram_received(self, data, addr):
        global call_state
        if call_state is None or call_state.plan_b:
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


# ── Modo FIXED: reproducir mensaje y colgar ─────────────────────
playbacks_pendientes: dict[str, asyncio.Event] = {}
# Referencia fuerte a la tarea: sin ella el GC puede llevarse una tarea
# fire-and-forget a medio ejecutar. Una sola basta: el diseño ya asume
# una única llamada concurrente (puerto UDP fijo).
fixed_task: asyncio.Task | None = None


async def run_fixed(channel_id):
    ari_log(f'Modo FIXED: reproduciendo {FIXED_SOUND}')

    try:
        playback = await ari.play(channel_id, FIXED_SOUND)
        ari_log(f'Playback creado: {json.dumps(playback, ensure_ascii=False)}')
        playback_id = playback['id']
        terminado = asyncio.Event()
        playbacks_pendientes[playback_id] = terminado
        try:
            await asyncio.wait_for(terminado.wait(), timeout=FIXED_PLAYBACK_TIMEOUT)
            ari_log('Reproducción terminada')
        except asyncio.TimeoutError:
            # Red de seguridad: sin esto la llamada se quedaría abierta
            ari_log(f'PlaybackFinished no llegó en {FIXED_PLAYBACK_TIMEOUT}s, colgando igualmente')
        finally:
            playbacks_pendientes.pop(playback_id, None)
    except Exception as e:
        ari_log(f'Error reproduciendo mensaje fijo: {e}')

    await ari.hangup(channel_id)


def marcar_plan_b(channel_id, pieza):
    """Apunta en el call log cada pieza que falló en la llamada."""
    log = load_call_log()
    for entry in log:
        if entry.get('id') == channel_id:
            # Acumula los saltos de la llamada: "Cartesia", "Cartesia → TTS"...
            previo = entry.get('plan_b')
            entry['plan_b'] = f'{previo} → {pieza}' if previo else pieza
            break
    save_call_log(log)


def activar_plan_b(state, pieza, error):
    """Modo 3 sin una de sus piezas: mensaje fijo del Modo 2 y colgar, en vez de quedarse mudo."""
    global fixed_task
    if state.plan_b:
        return
    state.plan_b = True
    ari_log(f'FALLBACK: {pieza} falló ({error}) → mensaje fijo y colgar')
    marcar_plan_b(state.channel_id, pieza)
    if (state.tts_task and not state.tts_task.done()
            and state.tts_task is not asyncio.current_task()):
        state.tts_task.cancel()
    # Tarea aparte por el mismo motivo que en on_stasis_start (lección 3)
    fixed_task = asyncio.create_task(run_fixed(state.channel_id))


async def on_playback_finished(event):
    playback_id = event.get('playback', {}).get('id')
    terminado = playbacks_pendientes.get(playback_id)
    if terminado:
        terminado.set()


# ── Eventos ARI ──────────────────────────────────────────────────
async def on_stasis_start(event):
    global call_state, fixed_task
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

    # Log temporal de diagnóstico: con esto confirmamos qué campo trae el número
    ari_log(f'StasisStart channel: {json.dumps(channel, ensure_ascii=False)}')

    numero = extraer_numero(channel)
    ari_log(f'Número del llamante: {numero}')

    # Se lee una sola vez y antes del registro: el modo que queda en el log es
    # el mismo que decide el camino de esta llamada.
    modo = get_current_mode()
    personaje = elegir_personaje() if modo == 'AI' else None
    if personaje:
        call_state.personaje = personaje
        ari_log(f'Personaje: {PERSONAJES[personaje]["nombre"]}')

    log = load_call_log()
    log.append({
        'id': channel_id,
        'numero': numero,
        # UTC con offset explícito: el VPS corre en UTC y el cliente está en
        # otra zona. Sin el offset, la app no puede saber qué hora es esta.
        'timestamp_inicio': datetime.now(timezone.utc).isoformat(),
        'timestamp_fin': None,
        'duracion_segundos': 0,
        'estado': 'activa',
        'modo': modo,
        'plan_b': None,           # pieza que falló si la llamada cayó al mensaje fijo
        'personaje': personaje,   # None en el Modo 2
    })
    save_call_log(log)

    await ari.answer(channel_id)

    # Modo FIXED: mensaje fijo y colgar. No hace falta bridge, externalMedia
    # ni Deepgram: el pipeline de conversación no interviene aquí.
    if modo == 'FIXED':
        # En tarea aparte, NO con await: run_fixed espera PlaybackFinished, y
        # run_ari_events despacha los handlers con await dentro del bucle que
        # lee el WebSocket. Si bloqueamos aquí, ese evento no se lee nunca y
        # la llamada se queda hasta el timeout.
        fixed_task = asyncio.create_task(run_fixed(channel_id))
        return

    call_state.bridge_id = await ari.create_bridge()
    await ari.add_channel_to_bridge(call_state.bridge_id, channel_id)

    em_id = await ari.create_external_media(f'{EXTERNAL_MEDIA_HOST}:{EXTERNAL_MEDIA_PORT}')
    ari_log(f'externalMedia creado: {em_id} -> {EXTERNAL_MEDIA_HOST}:{EXTERNAL_MEDIA_PORT}')
    # Su propio StasisStart (rama de arriba) lo añadirá al bridge cuando llegue

    saludo = PERSONAJES[call_state.personaje]['saludo']
    call_state.tts_task = asyncio.create_task(play_response(call_state, saludo))


async def on_stasis_end(event):
    global call_state
    channel_id = event['channel']['id']
    if call_state and channel_id == call_state.channel_id:
        ari_log(f'Llamada terminada: {channel_id}')

        log = load_call_log()
        for entry in log:
            if entry.get('id') == channel_id and entry.get('estado') == 'activa':
                fin = datetime.now(timezone.utc)
                try:
                    inicio = datetime.fromisoformat(entry['timestamp_inicio'])
                    # Los registros anteriores a este cambio son naive: eran UTC
                    # sin decirlo. Sin esto, restar aware - naive es TypeError.
                    if inicio.tzinfo is None:
                        inicio = inicio.replace(tzinfo=timezone.utc)
                    entry['duracion_segundos'] = int((fin - inicio).total_seconds())
                except Exception:
                    entry['duracion_segundos'] = 0
                entry['timestamp_fin'] = fin.isoformat()
                entry['estado'] = 'finalizada'
                break
        save_call_log(log)

        if call_state.tts_task and not call_state.tts_task.done():
            call_state.tts_task.cancel()
        if call_state.bridge_id:
            await ari.destroy_bridge(call_state.bridge_id)
        call_state = None


EVENT_HANDLERS = {
    'StasisStart': on_stasis_start,
    'StasisEnd': on_stasis_end,
    'PlaybackFinished': on_playback_finished,
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
