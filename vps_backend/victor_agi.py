#!/usr/bin/env python3
"""
victor_agi.py — Agente IA Manolo para Asterisk AGI
Personalidad: Manolo, abuelo gallego de 78 años
"""
import sys
import os
import time
import subprocess
import tempfile

# Añadir el path del venv y ai_bridge
sys.path.insert(0, '/root/ai_bridge/venv/lib/python3.12/site-packages')
sys.path.insert(0, '/root/ai_bridge')

from dotenv import load_dotenv
load_dotenv('/root/ai_bridge/.env')

import google.genai as genai
from google.genai import types
from gtts import gTTS
import speech_recognition as sr
from pydub import AudioSegment

# ── Configuración ──────────────────────────────────────────────
GEMINI_KEY = os.getenv("GEMINI_API_KEY")
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
def agi_send(cmd):
    sys.stdout.write(cmd + '\n')
    sys.stdout.flush()
    return sys.stdin.readline().strip()

def agi_log(msg):
    sys.stderr.write(f"MANOLO_AGI: {msg}\n")
    sys.stderr.flush()

def agi_answer():
    return agi_send("ANSWER")

def agi_hangup():
    return agi_send("HANGUP")

def agi_stream_file(filename):
    """Reproduce un audio en Asterisk (sin extensión)"""
    return agi_send(f"STREAM FILE {filename} \"\"")

def agi_record(filename, silence=3, maxdur=15):
    """Graba audio del caller. Devuelve path del fichero."""
    return agi_send(
        f"RECORD FILE {filename} wav \"|#\" {maxdur*1000} s={silence}"
    )

# ── Audio ──────────────────────────────────────────────────────
AUDIO_DIR = '/tmp/manolo_agi'
os.makedirs(AUDIO_DIR, exist_ok=True)

def text_to_speech(text, filename):
    """Convierte texto a WAV 8kHz mono para Asterisk"""
    try:
        mp3_path = f"{AUDIO_DIR}/{filename}.mp3"
        wav_path = f"{AUDIO_DIR}/{filename}"

        tts = gTTS(text=text, lang='es', tld='es')
        tts.save(mp3_path)

        audio = AudioSegment.from_mp3(mp3_path)
        audio = audio.set_frame_rate(8000).set_channels(1).set_sample_width(2)
        audio.export(wav_path + '.wav', format='wav')

        os.remove(mp3_path)
        return wav_path  # sin .wav — Asterisk lo añade solo
    except Exception as e:
        agi_log(f"TTS error: {e}")
        return None

def speech_to_text(wav_path):
    """Convierte WAV grabado por Asterisk a texto"""
    recognizer = sr.Recognizer()
    try:
        # Asterisk graba en formato especial, convertir primero
        audio = AudioSegment.from_file(wav_path + '.wav')
        audio = audio.set_frame_rate(16000).set_channels(1)
        converted = wav_path + '_converted.wav'
        audio.export(converted, format='wav')

        with sr.AudioFile(converted) as source:
            recognizer.adjust_for_ambient_noise(source, duration=0.5)
            audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data, language='es-ES')
            agi_log(f"STT: '{text}'")
            return text
    except sr.UnknownValueError:
        agi_log("STT: silencio o no entendido")
        return ""
    except Exception as e:
        agi_log(f"STT error: {e}")
        return ""

# ── Gemini ─────────────────────────────────────────────────────
def get_manolo_response(chat_history, text):
    try:
        if not text:
            text = "[Silencio]"
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
        agi_log(f"Manolo: {response.text}")
        return response.text
    except Exception as e:
        agi_log(f"Gemini error: {e}")
        return "Ay, hijo, no te he oído bien. ¿Puedes repetirlo?"

# ── Flujo principal ────────────────────────────────────────────
def main():
    agi_log("=== Manolo AGI arrancado ===")

    chat_history = []
    turno = 0
    max_turnos = 8  # máximo 8 intercambios (~5 minutos)
    silencio_consecutivo = 0
    max_silencio = 2  # colgar tras 2 silencios seguidos

    # Saludo inicial de Manolo
    saludo = "¿Dígame? Ay, espere un momento que no encuentro las gafas... ¿Quién es?"
    audio_saludo = text_to_speech(saludo, "saludo")
    if audio_saludo:
        agi_stream_file(audio_saludo)

    while turno < max_turnos:
        turno += 1
        agi_log(f"--- Turno {turno} ---")

        # Grabar al spammer
        rec_path = f"{AUDIO_DIR}/rec_{turno}"
        agi_record(rec_path, silence=3, maxdur=20)

        # STT
        texto = speech_to_text(rec_path)

        if not texto:
            silencio_consecutivo += 1
            agi_log(f"Silencio {silencio_consecutivo}/{max_silencio}")
            if silencio_consecutivo >= max_silencio:
                despedida = "¿Hola? ¿Sigues ahí? Bueno, que Dios te bendiga hijo. Adiós."
                audio_desp = text_to_speech(despedida, "despedida")
                if audio_desp:
                    agi_stream_file(audio_desp)
                break
            # Manolo llena el silencio
            texto = "[Silencio]"
        else:
            silencio_consecutivo = 0

        # Gemini → respuesta de Manolo
        respuesta = get_manolo_response(chat_history, texto)

        # TTS y reproducir
        audio_resp = text_to_speech(respuesta, f"resp_{turno}")
        if audio_resp:
            agi_stream_file(audio_resp)

        time.sleep(0.3)

    agi_log("=== Manolo cuelga ===")
    agi_hangup()

if __name__ == '__main__':
    main()
