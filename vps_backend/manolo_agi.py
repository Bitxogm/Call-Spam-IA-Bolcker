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
import time
import requests

sys.path.insert(0, '/root/ai_bridge/venv/lib/python3.12/site-packages')
sys.path.insert(0, '/root/ai_bridge')

from dotenv import load_dotenv
load_dotenv('/root/ai_bridge/.env')

import google.genai as genai
from google.genai import types
from gtts import gTTS
import speech_recognition as sr
import whisper
from pydub import AudioSegment

# ── Configuración ──────────────────────────────────────────────
STATE_FILE = '/root/ai_bridge/current_mode.json'
AUDIO_DIR  = '/tmp/manolo_agi'
GEMINI_KEY = os.getenv('GEMINI_API_KEY')
ELEVENLABS_KEY      = os.getenv('ELEVENLABS_API_KEY')
ELEVENLABS_VOICE_ID = 'onwK4e9ZLuTAKqWW03F9'
ELEVENLABS_URL      = f'https://api.elevenlabs.io/v1/text-to-speech/{ELEVENLABS_VOICE_ID}'

os.makedirs(AUDIO_DIR, exist_ok=True)
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
IMPORTANTE: Responde SOLO como Manolo. Máximo 2-3 frases cortas y naturales. Nada de listas."""

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

# Cargar Whisper una sola vez (después de agi_log)
try:
    whisper_model = whisper.load_model('small')
    agi_log('Whisper small cargado')
except Exception as _e:
    whisper_model = None
    agi_log(f'Whisper no disponible: {_e}')

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
    """Genera WAV 8kHz mono para Asterisk. Devuelve path sin extensión."""
    wav_path = f'{AUDIO_DIR}/{filename}'
    try:
        mp3_path = f'{AUDIO_DIR}/{filename}.mp3'

        # Intentar ElevenLabs primero
        el_ok = False
        if ELEVENLABS_KEY:
            try:
                resp = requests.post(
                    ELEVENLABS_URL,
                    headers={
                        'xi-api-key': ELEVENLABS_KEY,
                        'Content-Type': 'application/json',
                        'Accept': 'audio/mpeg',
                    },
                    json={
                        'text': text,
                        'model_id': 'eleven_multilingual_v2',
                        'voice_settings': {
                            'stability': 0.3,
                            'similarity_boost': 0.8,
                        },
                    },
                    timeout=15,
                )
                resp.raise_for_status()
                with open(mp3_path, 'wb') as f:
                    f.write(resp.content)
                el_ok = True
                agi_log('TTS: ElevenLabs OK')
            except Exception as el_err:
                agi_log(f'TTS: ElevenLabs falló ({el_err}), usando gTTS')

        if not el_ok:
            tts = gTTS(text=text, lang='es', tld='es')
            tts.save(mp3_path)

        audio = AudioSegment.from_mp3(mp3_path)
        audio = audio.set_frame_rate(8000).set_channels(1).set_sample_width(2)
        audio.export(wav_path + '.wav', format='wav')
        os.remove(mp3_path)
        return wav_path
    except Exception as e:
        agi_log(f'TTS error: {e}')
        return None

def speech_to_text(wav_path):
    """Convierte WAV grabado por Asterisk a texto."""
    try:
        audio = AudioSegment.from_file(wav_path + '.wav')
        audio = audio.set_frame_rate(16000).set_channels(1)
        converted = wav_path + '_converted.wav'
        audio.export(converted, format='wav')

        if whisper_model is not None:
            try:
                result = whisper_model.transcribe(converted, language='es')
                text = result['text'].strip()
                agi_log(f"STT Whisper: '{text}'")
                return text
            except Exception as w_err:
                agi_log(f'STT: Whisper falló ({w_err}), usando Google SR')

        # Fallback: Google Speech Recognition
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
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=chat_history + [text],
            config=types.GenerateContentConfig(
                system_instruction=PROMPT_MANOLO,
                max_output_tokens=1500,
                thinking_config=types.ThinkingConfig(thinking_budget=0)
            )
        )
        chat_history.append(text)
        chat_history.append(response.text)
        agi_log(f'Manolo: {response.text}')
        return response.text
    except Exception as e:
        agi_log(f'Gemini error: {e}')
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
        text_to_speech('Mmm... un momento hijo, que no oigo bien...', 'pensando')
        agi_log('Pensando pregenerado')

    if mode == 'FIXED':
        run_fixed()
    else:
        run_ai()

if __name__ == '__main__':
    main()
