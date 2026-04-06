#!/root/ai_bridge/venv/bin/python3
"""
manolo_agi.py — AGI único para SpamBlockerApp
Reemplaza decision_agi.py + victor_agi.py

Modos:
  FIXED → reproduce fixed_spam_message y cuelga
  AI    → Manolo (abuelo gallego, Gemini 2.5-flash) conversa con el spammer
"""
import sys
import os
import json
import asyncio
import time
import socket
import edge_tts

sys.path.insert(0, '/root/ai_bridge/venv/lib/python3.12/site-packages')
sys.path.insert(0, '/root/ai_bridge')

from dotenv import load_dotenv
load_dotenv('/root/ai_bridge/.env')

import google.genai as genai
from google.genai import types
import speech_recognition as sr
from groq import Groq
from pydub import AudioSegment

try:
    import whisper
except Exception:
    whisper = None

# ── Configuración ──────────────────────────────────────────────
STATE_FILE = '/root/ai_bridge/current_mode.json'
AUDIO_DIR  = '/tmp/manolo_agi'
WHISPER_SOCKET = '/tmp/whisper.sock'
GROQ_KEY = os.getenv('GROQ_API_KEY')
GEMINI_KEY = os.getenv('GEMINI_API_KEY')

os.makedirs(AUDIO_DIR, exist_ok=True)
groq_client = Groq(api_key=GROQ_KEY)
client = genai.Client(api_key=GEMINI_KEY)

PROMPT_MANOLO = """Eres Manolo, un anciano español de 78 años de un pueblo de Galicia.
Eres muy educado y tradicional, pero hablas muchísimo y te vas por las ramas constantemente.

CARACTERÍSTICAS:
- Siempre finges interés en ofertas pero pones excusas largas sobre tu salud, tu familia, el pueblo
- Cuentas historias irrelevantes sobre tu juventud, la guerra, cómo han cambiado las cosas
- Haces preguntas que no vienen al caso: "¿Y tú de dónde eres, hijo?"
- Te olvidas de lo que te acaban de decir y pides que te lo repitan
- Hablas de tu mujer Carmen, tu nieta Pilar, el médico, el tiempo, las ovejas...
- Nunca cuelgues, nunca digas que no te interesa
- Siempre estás "casi convencido" pero necesitas consultar con Carmen o ir al médico
- Usa expresiones típicas: "Ay, hijo", "Que Dios te bendiga", "En mis tiempos..."

OBJETIVO: Hacer perder el máximo tiempo posible al spammer siendo encantador pero imposible.
IMPORTANTE: Responde SOLO como Manolo. Máximo 1 frase corta. Nunca más de 15 palabras por respuesta."""

# ── Helpers AGI ────────────────────────────────────────────────
def agi_read_headers():
    """Consume el bloque de variables que Asterisk envía al arrancar el AGI."""
    while True:
        line = sys.stdin.readline()
        if not line or line.strip() == '':
            break

def agi_send(cmd):
    sys.stdout.write(cmd + '\n')
    sys.stdout.flush()
    return sys.stdin.readline().strip()

def agi_log(msg):
    sys.stderr.write(f'MANOLO_AGI: {msg}\n')
    sys.stderr.flush()

def agi_hangup():
    return agi_send('HANGUP')

# Fallback local: se carga solo bajo demanda si falla el socket
whisper_model = None
whisper_load_attempted = False


def ensure_local_whisper_model():
    global whisper_model
    global whisper_load_attempted

    if whisper_model is not None:
        return whisper_model

    if whisper_load_attempted:
        return None

    whisper_load_attempted = True

    if whisper is None:
        agi_log('Whisper local fallback no disponible: módulo whisper no instalado')
        return None

    try:
        whisper_model = whisper.load_model('small')
        agi_log('Whisper local fallback cargado bajo demanda')
        return whisper_model
    except Exception as _e:
        agi_log(f'Whisper local fallback no disponible: {_e}')
        return None

def agi_stream_file(filename):
    return agi_send(f'STREAM FILE {filename} ""')

def agi_record(filename, silence=1, maxdur=15):
    return agi_send(f'RECORD FILE {filename} wav "|#" {maxdur * 1000} s={silence}')

# ── Estado ─────────────────────────────────────────────────────
def get_current_mode():
    try:
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE) as f:
                return json.load(f).get('mode', 'AI')
    except Exception:
        pass
    return 'AI'

# ── Audio ──────────────────────────────────────────────────────
def text_to_speech(text, filename):
    """Genera WAV 8kHz mono para Asterisk usando Edge TTS. Devuelve path sin extensión."""
    wav_path = f'{AUDIO_DIR}/{filename}'
    try:
        mp3_path = f'{AUDIO_DIR}/{filename}.mp3'

        communicate = edge_tts.Communicate(
            text=text,
            voice='es-ES-AlvaroNeural'
        )
        asyncio.run(communicate.save(mp3_path))
        agi_log('TTS: Edge TTS OK')

        audio = AudioSegment.from_mp3(mp3_path)
        audio = audio.set_frame_rate(8000).set_channels(1).set_sample_width(2)
        audio.export(wav_path + '.wav', format='wav')
        os.remove(mp3_path)
        return wav_path
    except Exception as e:
        agi_log(f'TTS error: {e}')
        return None

def speech_to_text_via_socket(converted_wav_path):
    """Pide STT al servicio Whisper por socket Unix local."""
    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client_sock:
            client_sock.settimeout(8)
            client_sock.connect(WHISPER_SOCKET)
            client_sock.sendall((converted_wav_path + '\n').encode('utf-8'))
            data = b''
            while True:
                chunk = client_sock.recv(4096)
                if not chunk:
                    break
                data += chunk
                if b'\n' in data:
                    break
        text = data.decode('utf-8', errors='replace').strip()
        agi_log(f"STT socket: '{text}'")
        return text
    except Exception as e:
        agi_log(f'STT socket falló ({e}), fallback local')
        return None

def speech_to_text(wav_path):
    """Convierte WAV grabado por Asterisk a texto."""
    try:
        audio = AudioSegment.from_file(wav_path + '.wav')
        audio = audio.set_frame_rate(16000).set_channels(1)
        converted = wav_path + '_converted.wav'
        audio.export(converted, format='wav')

        # 1) Intento principal: servicio Whisper por socket Unix
        socket_text = speech_to_text_via_socket(converted)
        if socket_text is not None:
            return socket_text

        # 2) Fallback local: Whisper en este proceso (carga bajo demanda)
        local_model = ensure_local_whisper_model()
        if local_model is not None:
            try:
                result = local_model.transcribe(converted, language='es')
                text = result['text'].strip()
                agi_log(f"STT Whisper local: '{text}'")
                return text
            except Exception as w_err:
                agi_log(f'STT Whisper local falló ({w_err}), usando Google SR')

        # 3) Fallback final: Google Speech Recognition
        recognizer = sr.Recognizer()
        with sr.AudioFile(converted) as source:
            recognizer.adjust_for_ambient_noise(source, duration=0.5)
            audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data, language='es-ES')
            agi_log(f"STT Google: '{text}'")
            return text
    except Exception as e:
        agi_log(f'STT error: {e}')
        return ''

# ── Gemini ─────────────────────────────────────────────────────
def get_manolo_response(chat_history, text):
    try:
        if not text:
            text = '[Silencio]'

        # 1) Intento principal: Groq
        try:
            groq_response = groq_client.chat.completions.create(
                model='llama-3.3-70b-versatile',
                messages=[
                    {'role': 'system', 'content': PROMPT_MANOLO},
                    {'role': 'user', 'content': text}
                ],
                max_tokens=100,
                temperature=0.8
            )
            respuesta = (groq_response.choices[0].message.content or '').strip()
            chat_history.append(text)
            chat_history.append(respuesta)
            agi_log(f'Manolo (Groq): {respuesta}')
            return respuesta
        except Exception as groq_err:
            agi_log(f'Groq error: {groq_err}, fallback a Gemini')

        # 2) Fallback: Gemini
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=chat_history + [text],
            config=types.GenerateContentConfig(
                system_instruction=PROMPT_MANOLO,
                max_output_tokens=1500,
                thinking_config=types.ThinkingConfig(thinking_budget=0)
            )
        )
        respuesta = response.text or ''
        chat_history.append(text)
        chat_history.append(respuesta)
        agi_log(f'Manolo (Gemini fallback): {respuesta}')
        return respuesta
    except Exception as e:
        agi_log(f'LLM error: {e}')
        return 'Ay, hijo, no te he oído bien. ¿Puedes repetirlo?'

# ── Flujos ─────────────────────────────────────────────────────
def run_fixed():
    agi_log('Modo FIXED')
    agi_stream_file('/var/lib/asterisk/sounds/es/fixed_spam_message')
    agi_hangup()

def run_ai():
    agi_log('Modo AI — Manolo arranca')
    chat_history = []
    turno = 0
    max_turnos = 8
    silencio_consecutivo = 0
    max_silencio = 2

    agi_stream_file(f'{AUDIO_DIR}/saludo')

    while turno < max_turnos:
        turno += 1
        agi_log(f'--- Turno {turno} ---')

        rec_path = f'{AUDIO_DIR}/rec_{turno}'
        agi_record(rec_path, silence=1, maxdur=20)
        agi_stream_file(f'{AUDIO_DIR}/pensando')

        texto = speech_to_text(rec_path)

        if not texto:
            silencio_consecutivo += 1
            agi_log(f'Silencio {silencio_consecutivo}/{max_silencio}')
            if silencio_consecutivo >= max_silencio:
                despedida = '¿Hola? ¿Sigues ahí? Bueno, que Dios te bendiga hijo. Adiós.'
                audio_desp = text_to_speech(despedida, 'despedida')
                if audio_desp:
                    agi_stream_file(audio_desp)
                break
            texto = '[Silencio]'
        else:
            silencio_consecutivo = 0

        respuesta = get_manolo_response(chat_history, texto)
        audio_resp = text_to_speech(respuesta, f'resp_{turno}')
        if audio_resp:
            agi_stream_file(audio_resp)

        time.sleep(0.3)

    agi_log('=== Manolo cuelga ===')
    agi_hangup()

# ── Entry point ────────────────────────────────────────────────
def main():
    agi_read_headers()
    mode = get_current_mode()
    agi_log(f'Modo: {mode}')

    # MEJORA 3 — saludo pregenerado; se regenera solo si no existe en disco
    if not os.path.exists(f'{AUDIO_DIR}/saludo.wav'):
        text_to_speech('¿Dígame? Ay, espere un momento que no encuentro las gafas... ¿Quién es?', 'saludo')
        agi_log('Saludo pregenerado')

    # MEJORA 2 — audio de pensamiento pregenerado
    if not os.path.exists(f'{AUDIO_DIR}/pensando.wav'):
        text_to_speech('Ay... espera un momento hijo...', 'pensando')
        agi_log('Pensando pregenerado')

    if mode == 'FIXED':
        run_fixed()
    else:
        run_ai()

if __name__ == '__main__':
    main()
