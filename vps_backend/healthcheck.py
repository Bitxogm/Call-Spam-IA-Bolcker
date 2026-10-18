#!/root/ai_bridge/venv/bin/python3
"""
healthcheck.py — comprueba las piezas del Modo 3 y avisa por email si algo cae.

Lo lanza manolo-healthcheck.timer. TTS, Groq y Deepgram se prueban con las
mismas funciones que usa manolo_ari.py: si cambia el proveedor de voz o el
modelo, el healthcheck comprueba el nuevo sin tocar este fichero.

Solo manda email cuando cambia el estado: uno al fallar y otro al recuperarse.
"""
import os
import sys
import ssl
import json
import wave
import asyncio
import smtplib
from datetime import datetime, timezone
from email.message import EmailMessage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import manolo_ari  # noqa: E402  (importarlo no arranca el servidor ARI)

STATE_PATH = '/root/ai_bridge/healthcheck_state.json'
SAMPLE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'healthcheck_sample.wav')
CHECK_TIMEOUT = 30              # segundos máximo por comprobación
SERVICES = ('asterisk', 'manolo_ari', 'asterisk-control-api')

SMTP_HOST = 'smtp.gmail.com'
SMTP_PORT = 587
SMTP_TIMEOUT = 30

EDGE_FIX = 'sudo /root/ai_bridge/venv/bin/pip install -U edge-tts'


def log(msg):
    sys.stderr.write(f'HEALTHCHECK: {msg}\n')
    sys.stderr.flush()


# ── Comprobaciones ───────────────────────────────────────────────
async def check_tts():
    await manolo_ari.synthesize_mulaw('Hola, soy Manolo.')


async def check_groq():
    await manolo_ari.generate_manolo_reply([], 'Hola, ¿quién es?')


async def check_deepgram():
    with wave.open(SAMPLE_PATH, 'rb') as wf:
        if (wf.getnchannels(), wf.getsampwidth(), wf.getframerate()) != (1, 2, manolo_ari.SAMPLE_RATE):
            raise RuntimeError(f'{SAMPLE_PATH} no es mono PCM16 a {manolo_ari.SAMPLE_RATE} Hz')
        pcm = wf.readframes(wf.getnframes())
    texto = await manolo_ari.deepgram_stt(pcm)
    # La muestra tiene voz: una transcripción vacía aquí es un fallo, no silencio
    if not texto:
        raise RuntimeError('transcripción vacía de una muestra con voz')


def check_service(nombre):
    async def check():
        proc = await asyncio.create_subprocess_exec(
            'systemctl', 'is-active', nombre,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )
        salida, _ = await proc.communicate()
        estado = salida.decode(errors='replace').strip()
        if estado != 'active':
            raise RuntimeError(f'estado "{estado}"')
    return check


CHECKS = {
    'TTS': check_tts,
    'Groq': check_groq,
    'Deepgram': check_deepgram,
    **{f'servicio {s}': check_service(s) for s in SERVICES},
}


async def run_checks(checks=None, timeout=None):
    """{nombre: None si va bien, o el error resumido}."""
    checks = checks or CHECKS
    timeout = timeout or CHECK_TIMEOUT
    resultados = {}
    for nombre, check in checks.items():
        try:
            await asyncio.wait_for(check(), timeout=timeout)
            resultados[nombre] = None
        except asyncio.TimeoutError:
            resultados[nombre] = f'sin respuesta en {timeout}s'
        except Exception as e:
            resultados[nombre] = resumir(e)
        log(f'{nombre}: {resultados[nombre] or "OK"}')
    return resultados


def resumir(error, max_len=300):
    texto = ' '.join(str(error).split()) or type(error).__name__
    return texto if len(texto) <= max_len else texto[:max_len] + '…'


# ── Estado anterior ──────────────────────────────────────────────
def load_state():
    try:
        with open(STATE_PATH) as f:
            fallos = json.load(f).get('fallos', {})
            return fallos if isinstance(fallos, dict) else {}
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_state(fallos):
    tmp_path = STATE_PATH + '.tmp'
    with open(tmp_path, 'w') as f:
        json.dump({'fallos': fallos, 'timestamp': datetime.now(timezone.utc).isoformat()},
                  f, ensure_ascii=False, indent=2)
    os.replace(tmp_path, STATE_PATH)


# ── Emails ───────────────────────────────────────────────────────
def build_emails(antes, ahora):
    """Lista de (asunto, cuerpo). Vacía si no ha cambiado qué piezas fallan."""
    nuevos = [n for n in ahora if n not in antes]
    recuperados = [n for n in antes if n not in ahora]
    emails = []

    if nuevos:
        lineas = [f'Ha dejado de funcionar: {", ".join(nuevos)}.', '']
        lineas += [f'- {n}: {ahora[n]}' for n in ahora]
        if 'TTS' in nuevos and manolo_ari.TTS_PROVIDER == 'edge':
            lineas += ['', 'Edge TTS suele romperse cuando Microsoft cambia el protocolo. '
                           'Prueba a actualizarlo y reiniciar manolo_ari:', f'  {EDGE_FIX}']
        lineas += ['', 'Mientras tanto, el Modo 3 cae al mensaje fijo si la pieza caída '
                       'es TTS o Deepgram.']
        emails.append((f'[Manolo] Falla: {", ".join(nuevos)}', '\n'.join(lineas)))

    if recuperados:
        lineas = [f'Vuelve a funcionar: {", ".join(recuperados)}.']
        if ahora:
            lineas += ['', 'Sigue fallando:'] + [f'- {n}: {ahora[n]}' for n in ahora]
        emails.append((f'[Manolo] Recuperado: {", ".join(recuperados)}', '\n'.join(lineas)))

    return emails


def send_email(asunto, cuerpo):
    remitente = os.getenv('ALERT_EMAIL_FROM')
    destino = os.getenv('ALERT_EMAIL_TO')
    password = os.getenv('ALERT_EMAIL_PASSWORD')
    if not (remitente and destino and password):
        raise RuntimeError('faltan ALERT_EMAIL_FROM, ALERT_EMAIL_TO o ALERT_EMAIL_PASSWORD en el .env')

    msg = EmailMessage()
    msg['Subject'] = asunto
    msg['From'] = remitente
    msg['To'] = destino
    msg.set_content(cuerpo)

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=SMTP_TIMEOUT) as smtp:
        smtp.starttls(context=ssl.create_default_context())
        smtp.login(remitente, password)
        smtp.send_message(msg)


# ── Main ─────────────────────────────────────────────────────────
async def main():
    resultados = await run_checks()
    ahora = {n: e for n, e in resultados.items() if e is not None}
    antes = load_state()

    for asunto, cuerpo in build_emails(antes, ahora):
        try:
            send_email(asunto, cuerpo)
            log(f'email enviado: {asunto}')
        except Exception as e:
            # Sin guardar el estado: el próximo run vuelve a ver el cambio y reintenta
            log(f'no se pudo enviar el email "{asunto}": {resumir(e)}')
            return 2

    save_state(ahora)
    return 1 if ahora else 0


if __name__ == '__main__':
    sys.exit(asyncio.run(main()))
