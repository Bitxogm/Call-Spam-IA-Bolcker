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
from datetime import date, datetime, timezone
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

# Cartesia no tiene endpoint de saldo: se calcula con el consumo del mes
# (GET /usage/credits, que exige clave de administrador) y los créditos del plan
CARTESIA_ADMIN_API_KEY = os.getenv('CARTESIA_ADMIN_API_KEY')
CARTESIA_CREDITOS_MES = os.getenv('CARTESIA_CREDITOS_MES')
CARTESIA_AVISO_SALDO = 0.20
AVISO_SALDO = CARTESIA_AVISO_SALDO   # el mismo umbral para ElevenLabs


def log(msg):
    sys.stderr.write(f'HEALTHCHECK: {msg}\n')
    sys.stderr.flush()


# ── Comprobaciones ───────────────────────────────────────────────
async def check_tts():
    # Edge es gratis y es el escalón de reserva con cualquier TTS_PROVIDER.
    # Cartesia no se sintetiza aquí: gastaría créditos cada 6 horas.
    await manolo_ari.synthesize_edge('Hola, soy Manolo.')


def check_tts_voz(voz):
    """La reserva de un personaje con voz de Edge propia: si el nombre está mal, falla aquí."""
    async def check():
        await manolo_ari.synthesize_edge('Hola, ¿quién es?', voz)
    return check


async def check_cartesia():
    await manolo_ari.cartesia_verificar_voz()


async def cartesia_creditos_usados_mes(hoy=None):
    hoy = hoy or date.today()
    async with manolo_ari.httpx.AsyncClient(timeout=CHECK_TIMEOUT) as client:
        r = await client.get(
            f'{manolo_ari.CARTESIA_URL}/usage/credits',
            headers=manolo_ari.cartesia_headers(CARTESIA_ADMIN_API_KEY),
            params={'start_ts': f'{hoy:%Y-%m}-01T00:00:00Z'},
        )
    if r.status_code != 200:
        raise RuntimeError(f'Cartesia /usage/credits {r.status_code}: {" ".join((r.text or "").split())[:200]}')
    return sum(b.get('credits', 0) for b in r.json().get('data', []))


def check_cartesia_saldo(estado):
    async def check():
        total = int(CARTESIA_CREDITOS_MES)
        usados = await cartesia_creditos_usados_mes()
        restante = 1 - usados / total
        estado['restante'] = restante
        if restante < CARTESIA_AVISO_SALDO:
            raise RuntimeError(f'quedan {max(restante, 0):.0%} '
                               f'({max(total - usados, 0)} de {total} créditos este mes)')
    return check


def check_cartesia_creditos(estado):
    async def check():
        hasta = manolo_ari.cartesia_agotado_hasta()
        if hasta:
            raise RuntimeError(f'sin créditos: Manolo usa Edge ({manolo_ari.TTS_VOICE}) '
                               f'hasta el {hasta.isoformat()}')
        if estado.get('restante') is not None and estado['restante'] <= 0:
            raise RuntimeError('sin créditos según el consumo del mes')
    return check


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


def check_elevenlabs_cuenta(estado):
    """Clave, voz y caracteres del periodo, todo con lecturas: no sintetiza nada."""
    async def check():
        await manolo_ari.elevenlabs_verificar_voz()
        sub = await manolo_ari.elevenlabs_suscripcion()
        limite = sub.get('character_limit') or 0
        usados = sub.get('character_count') or 0
        if limite > 0:
            estado['restante'] = 1 - usados / limite
            estado['detalle'] = f'{max(limite - usados, 0)} de {limite} caracteres'
    return check


def check_elevenlabs_saldo(estado):
    async def check():
        restante = estado.get('restante')
        if restante is not None and restante < AVISO_SALDO:
            raise RuntimeError(f'quedan {max(restante, 0):.0%} ({estado["detalle"]})')
    return check


def check_elevenlabs_creditos(estado):
    async def check():
        hasta = manolo_ari.elevenlabs_agotado_hasta()
        if hasta:
            raise RuntimeError(f'sin caracteres: Paquita usa Edge ({manolo_ari.voz_edge("paquita")}) '
                               f'hasta el {hasta.isoformat()}')
        if estado.get('restante') is not None and estado['restante'] <= 0:
            raise RuntimeError('sin caracteres según la suscripción')
    return check


def construir_checks():
    checks = {
        'TTS': check_tts,
        **({'TTS Paquita': check_tts_voz(manolo_ari.voz_edge('paquita'))}
           if 'paquita' in manolo_ari.personajes_activos() else {}),
        'Groq': check_groq,
        'Deepgram': check_deepgram,
    }
    voces = manolo_ari.voces_en_uso()
    if 'cartesia' in voces:
        estado = {}     # el saldo lo calcula una comprobación y lo usa la siguiente
        checks['Cartesia'] = check_cartesia
        if CARTESIA_ADMIN_API_KEY and CARTESIA_CREDITOS_MES:
            checks['Cartesia: saldo bajo'] = check_cartesia_saldo(estado)
        else:
            log('sin CARTESIA_ADMIN_API_KEY o CARTESIA_CREDITOS_MES: no se comprueba el saldo')
        checks['Cartesia: sin créditos'] = check_cartesia_creditos(estado)
    if 'elevenlabs' in voces:
        estado_el = {}  # ElevenLabs sí dice lo que queda: /v1/user/subscription
        checks['ElevenLabs'] = check_elevenlabs_cuenta(estado_el)
        checks['ElevenLabs: saldo bajo'] = check_elevenlabs_saldo(estado_el)
        checks['ElevenLabs: sin créditos'] = check_elevenlabs_creditos(estado_el)
    checks.update({f'servicio {s}': check_service(s) for s in SERVICES})
    return checks


async def run_checks(checks=None, timeout=None):
    """{nombre: None si va bien, o el error resumido}."""
    checks = checks or construir_checks()
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
        # Edge está en la escalera con cualquier TTS_PROVIDER: o es la voz o es la reserva
        if 'TTS' in nuevos:
            lineas += ['', 'Edge TTS suele romperse cuando Microsoft cambia el protocolo. '
                           'Prueba a actualizarlo y reiniciar manolo_ari:', f'  {EDGE_FIX}']
        lineas += ['', 'Mientras tanto: si cae Cartesia o ElevenLabs, el personaje sigue con '
                       'la voz de Edge; si cae Edge (TTS) o Deepgram, el Modo 3 pasa al '
                       'mensaje fijo.']
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
