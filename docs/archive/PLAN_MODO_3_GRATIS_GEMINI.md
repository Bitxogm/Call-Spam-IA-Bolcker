# 🤖 Plan Modo 3 - Versión 100% GRATUITA con Gemini

**Fecha:** 28 Enero 2026
**Stack:** Gemini + Vosk + Google TTS Android
**Coste:** $0.00 por llamada ✅

---

## 🎯 Stack Tecnológico (Todo Gratis)

### 1. IA Conversacional: **Google Gemini 2.5 Flash**

**Por qué Gemini:**
- ✅ **GRATIS**: 15 requests/minuto, 1500/día
- ✅ Excelente en español
- ✅ Rápido (< 2 segundos)
- ✅ Muy creativo para conversaciones

**Límites:**
- 60 requests/minuto (suficiente para 6 llamadas simultáneas)
- 1500 requests/día (suficiente para 150 llamadas de 10 turnos)

**API Key:**
```bash
# Obtener gratis en:
https://makersuite.google.com/app/apikey

# No requiere tarjeta de crédito
```

---

### 2. Speech-to-Text: **Vosk (Offline)**

**Por qué Vosk:**
- ✅ **COMPLETAMENTE GRATIS**
- ✅ Funciona offline (privacidad)
- ✅ Modelo español disponible
- ✅ Latencia baja

**Instalación:**
```bash
pip install vosk

# Descargar modelo español (130 MB)
wget https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip
unzip vosk-model-es-0.42.zip -d /root/vosk-models/
```

**Calidad:** ★★★☆☆ (70-80% precisión)
- Suficiente para entender spammers
- No perfecto pero funcional

---

### 3. Text-to-Speech: **Google TTS Android**

**Por qué Google TTS:**
- ✅ **GRATIS** (viene con Android)
- ✅ Calidad decente (★★★★☆)
- ✅ Voz española natural
- ✅ Ya disponible en el VPS via `gtts-cli`

**Instalación:**
```bash
pip install gTTS
```

**Calidad:** Mejor que eSpeak, casi tan buena como ElevenLabs para español.

---

## 🛠️ Implementación: victor_agi_gratis.py

```python
#!/usr/bin/env python3
"""
victor_agi_gratis.py - IA Conversacional GRATIS con Gemini
Stack: Gemini + Vosk + Google TTS
Coste: $0.00 por llamada
"""

import sys
import os
import json
import wave
import google.generativeai as genai
from vosk import Model, KaldiRecognizer
from gtts import gTTS
from pathlib import Path

# Configuración
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY')
VOSK_MODEL_PATH = '/root/vosk-models/vosk-model-es-0.42'
AUDIO_DIR = '/var/lib/asterisk/sounds/temp/'
MAX_TURNS = 10

# Configurar Gemini
genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel('gemini-2.5-flash-latest-exp')

# Prompt para frustrar spammers
SPAM_FRUSTRATOR_PROMPT = """Eres Víctor, un señor mayor de 75 años algo despistado pero educado.

Tu objetivo es MANTENER AL SPAMMER EN LA LÍNEA el mayor tiempo posible:

REGLAS:
1. Habla español de España (tú, vosotros)
2. Sé educado pero confuso
3. HAZ PREGUNTAS que obliguen a repetir
4. Di que no oyes bien
5. Interrumpe con anécdotas irrelevantes
6. Muestra INTERÉS pero pide explicaciones
7. NUNCA digas que no te interesa
8. Máximo 2-3 frases cortas

EJEMPLOS:
- "¿Eh? Habla más alto que no te oigo bien"
- "Eso me interesa, pero explícame qué es 'la fibra'"
- "Espera que voy a buscar un boli"

Mantén el contexto. Sé natural."""

def agi_log(message):
    """Log a Asterisk"""
    sys.stderr.write(f"VICTOR_IA: {message}\n")
    sys.stderr.flush()

def read_agi_vars():
    """Lee variables AGI"""
    vars = {}
    while True:
        line = sys.stdin.readline().strip()
        if not line:
            break
        parts = line.split(':', 1)
        if len(parts) == 2:
            vars[parts[0].strip()] = parts[1].strip()
    return vars

def record_caller_audio(filename, timeout=10, silence=3):
    """Graba audio del caller"""
    agi_log(f"Grabando: {filename}")

    print(f"EXEC Record {filename},wav,{silence},{timeout}")
    sys.stdout.flush()

    result = sys.stdin.readline().strip()
    agi_log(f"Grabación OK: {result}")

    return f"{filename}.wav"

def transcribe_with_vosk(audio_path):
    """Transcribe usando Vosk (offline, gratis)"""
    agi_log(f"Transcribiendo con Vosk: {audio_path}")

    try:
        # Cargar modelo Vosk
        if not os.path.exists(VOSK_MODEL_PATH):
            agi_log(f"ERROR: Modelo Vosk no encontrado en {VOSK_MODEL_PATH}")
            return ""

        model_vosk = Model(VOSK_MODEL_PATH)

        # Abrir audio WAV
        wf = wave.open(audio_path, "rb")

        # Verificar formato
        if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getframerate() not in [8000, 16000]:
            agi_log("ERROR: Audio debe ser mono, 16-bit, 8000 o 16000 Hz")
            return ""

        # Reconocer
        rec = KaldiRecognizer(model_vosk, wf.getframerate())
        rec.SetWords(True)

        transcript = ""
        while True:
            data = wf.readframes(4000)
            if len(data) == 0:
                break
            if rec.AcceptWaveform(data):
                result = json.loads(rec.Result())
                transcript += result.get('text', '')

        # Resultado final
        final_result = json.loads(rec.FinalResult())
        transcript += final_result.get('text', '')

        wf.close()

        agi_log(f"Transcripción: {transcript}")
        return transcript.strip()

    except Exception as e:
        agi_log(f"ERROR Vosk: {e}")
        return ""

def get_gemini_response(conversation_history):
    """Obtiene respuesta de Gemini (GRATIS)"""
    agi_log("Consultando Gemini...")

    try:
        # Construir prompt con historial
        chat = model.start_chat(history=[])

        # Contexto inicial
        full_prompt = SPAM_FRUSTRATOR_PROMPT + "\n\n"

        # Añadir historial
        for msg in conversation_history:
            role = "Spammer" if msg['role'] == 'user' else "Víctor"
            full_prompt += f"{role}: {msg['content']}\n"

        full_prompt += "\nVíctor:"

        # Generar respuesta
        response = model.generate_content(full_prompt)
        ai_response = response.text.strip()

        agi_log(f"Gemini: {ai_response}")
        return ai_response

    except Exception as e:
        agi_log(f"ERROR Gemini: {e}")
        return "Perdona, no te he entendido. ¿Puedes repetir?"

def text_to_speech_gtts(text, output_path):
    """Convierte texto a audio usando Google TTS (GRATIS)"""
    agi_log(f"Generando TTS: {text}")

    try:
        # Generar audio con gTTS
        tts = gTTS(text=text, lang='es', slow=False)

        # Guardar como MP3 temporal
        temp_mp3 = output_path.replace('.wav', '_temp.mp3')
        tts.save(temp_mp3)

        # Convertir MP3 a WAV (formato Asterisk)
        os.system(f"ffmpeg -i {temp_mp3} -ar 8000 -ac 1 -y {output_path} 2>/dev/null")

        # Limpiar temporal
        os.remove(temp_mp3)

        agi_log(f"TTS guardado: {output_path}")
        return True

    except Exception as e:
        agi_log(f"ERROR gTTS: {e}")
        return False

def play_audio(audio_path):
    """Reproduce audio"""
    path_no_ext = audio_path.replace('.wav', '')

    agi_log(f"Reproduciendo: {path_no_ext}")
    print(f"EXEC Playback {path_no_ext}")
    sys.stdout.flush()

    result = sys.stdin.readline().strip()
    agi_log(f"Playback OK")

def main():
    """Loop conversacional GRATIS"""
    agi_log("=== MODO 3 GRATIS: Gemini + Vosk + gTTS ===")

    # Leer AGI vars
    agi_vars = read_agi_vars()
    caller_id = agi_vars.get('agi_callerid', 'unknown')

    agi_log(f"Caller: {caller_id}")

    # Crear directorio temporal
    os.makedirs(AUDIO_DIR, exist_ok=True)

    # Historial de conversación
    conversation_history = []

    # Saludo inicial
    greeting = "¿Dígame? ¿Quién llama?"
    greeting_audio = f"{AUDIO_DIR}greeting.wav"

    if text_to_speech_gtts(greeting, greeting_audio):
        play_audio(greeting_audio)
        conversation_history.append({
            "role": "assistant",
            "content": greeting
        })

    # Loop conversacional
    for turn in range(MAX_TURNS):
        agi_log(f"--- TURNO {turn + 1}/{MAX_TURNS} ---")

        # 1. GRABAR spammer
        audio_filename = f"{AUDIO_DIR}caller_turn_{turn}"
        record_caller_audio(audio_filename, timeout=15, silence=2)

        # 2. TRANSCRIBIR con Vosk (offline, gratis)
        transcript = transcribe_with_vosk(f"{audio_filename}.wav")

        if not transcript or len(transcript) < 3:
            agi_log("Transcripción vacía, terminando")
            break

        conversation_history.append({
            "role": "user",
            "content": transcript
        })

        # 3. GENERAR RESPUESTA con Gemini (gratis)
        ai_response = get_gemini_response(conversation_history)

        conversation_history.append({
            "role": "assistant",
            "content": ai_response
        })

        # 4. TTS con Google (gratis)
        response_audio = f"{AUDIO_DIR}victor_turn_{turn}.wav"

        if not text_to_speech_gtts(ai_response, response_audio):
            agi_log("ERROR TTS, terminando")
            break

        # 5. REPRODUCIR
        play_audio(response_audio)

        # Limpiar archivos temporales
        try:
            os.remove(f"{audio_filename}.wav")
            os.remove(response_audio)
        except:
            pass

    agi_log("=== FIN CONVERSACIÓN ===")
    agi_log(f"Total turnos: {len(conversation_history) // 2}")
    agi_log(f"Coste total: $0.00 😎")

    # Despedida
    print("EXEC Playback /var/lib/asterisk/sounds/es/goodbye")
    sys.stdout.flush()
    sys.stdin.readline()

    # Hangup
    print("EXEC Hangup")
    sys.stdout.flush()

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        agi_log(f"ERROR FATAL: {e}")
        print("EXEC Hangup")
        sys.stdout.flush()
```

---

## 📦 Instalación en el VPS

```bash
# 1. Instalar dependencias Python
pip install google-generativeai vosk gTTS

# 2. Descargar modelo Vosk español
cd /root
mkdir -p vosk-models
cd vosk-models
wget https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip
unzip vosk-model-es-0.42.zip

# 3. Instalar ffmpeg (para convertir audio)
apt-get install -y ffmpeg

# 4. Obtener API key de Gemini GRATIS
# https://makersuite.google.com/app/apikey

# 5. Configurar .env
nano /root/ai_bridge/.env
```

**Añadir al .env:**
```env
GEMINI_API_KEY=AIzaSy...tu-api-key-aqui
```

---

## 🧪 Test Rápido de Componentes

### Test 1: Gemini API
```python
import google.generativeai as genai

genai.configure(api_key='TU_API_KEY')
model = genai.GenerativeModel('gemini-2.5-flash-latest-exp')

response = model.generate_content("Hola, ¿cómo estás?")
print(response.text)
```

### Test 2: Vosk STT
```python
from vosk import Model
import os

model_path = '/root/vosk-models/vosk-model-es-0.42'
if os.path.exists(model_path):
    model = Model(model_path)
    print("✅ Modelo Vosk cargado")
else:
    print("❌ Modelo no encontrado")
```

### Test 3: Google TTS
```python
from gtts import gTTS
import os

text = "Hola, soy Víctor. ¿Puedes repetir?"
tts = gTTS(text=text, lang='es')
tts.save('/tmp/test.mp3')

os.system('ffmpeg -i /tmp/test.mp3 -ar 8000 -ac 1 /tmp/test.wav')
print("✅ Audio generado: /tmp/test.wav")
```

---

## 💰 Análisis de Costes (TODO GRATIS)

```
Gemini API:           $0.00 (15 req/min gratis)
Vosk STT:             $0.00 (offline, sin límites)
Google TTS:           $0.00 (sin límites)
Servidor VPS:         €4.51/mes (ya lo tienes)
Zadarma número:       €1.70/mes (ya lo tienes)
-------------------------------------------
COSTE ADICIONAL:      $0.00 🎉
```

**Por 10 llamadas/día:**
- Coste: $0.00
- Ahorro vs versión premium: ~$15/mes

---

## 📊 Comparación: Gratis vs Premium

| Componente | Versión Gratis | Versión Premium | Diferencia |
|------------|----------------|-----------------|------------|
| **IA** | Gemini Flash (gratis) | Claude 3.5 | Gemini casi igual de bueno |
| **STT** | Vosk (70-80%) | Whisper (95%) | Vosk suficiente para spam |
| **TTS** | gTTS (★★★★☆) | ElevenLabs (★★★★★) | gTTS suena natural |
| **Coste** | $0.00 | $0.05/llamada | Ahorro: $15/mes |
| **Latencia** | ~5 seg/turno | ~3 seg/turno | Diferencia mínima |

**Recomendación:** Empieza con versión gratis, si funciona bien no necesitas premium.

---

## 🚀 Roadmap de Implementación

### Día 1: Setup (1 hora)
- [ ] Crear cuenta Google AI (gratis, sin tarjeta)
- [ ] Obtener API key de Gemini
- [ ] Instalar Vosk y descargar modelo español
- [ ] Instalar gTTS y ffmpeg
- [ ] Test individual de cada componente

### Día 2: Script básico (2 horas)
- [ ] Crear `victor_agi_gratis.py`
- [ ] Implementar loop de 1 turno
- [ ] Test con llamada real
- [ ] Ajustar prompts según resultado

### Día 3: Loop completo (1 hora)
- [ ] Añadir loop de múltiples turnos
- [ ] Detección de silencio/cuelgue
- [ ] Optimización de latencia
- [ ] Testing con llamadas reales

### Día 4: Pulido (1 hora)
- [ ] Guardar conversaciones divertidas
- [ ] Estadísticas de duración
- [ ] Cleanup automático
- [ ] Documentación

---

## 🎯 Límites del Tier Gratuito

### Gemini API (Google):
- ✅ 15 requests/minuto
- ✅ 1500 requests/día
- ✅ Sin vencimiento
- ✅ Sin tarjeta de crédito requerida

**Suficiente para:**
- 6 llamadas simultáneas
- 150 llamadas de 10 turnos por día
- Uso ilimitado en el tiempo

### Vosk STT:
- ✅ Completamente offline
- ✅ Sin límites de uso
- ✅ Privacidad total

### Google TTS (gTTS):
- ✅ Sin límites oficiales
- ⚠️ Rate limit no documentado (probablemente generoso)
- ✅ Funciona sin API key

---

## 🔧 Modificar decision_agi.py

```python
#!/usr/bin/env python3
import sys
import json
import os

STATE_FILE = '/root/ai_bridge/current_mode.json'

def get_current_mode():
    try:
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, 'r') as f:
                return json.load(f).get('mode', 'FIXED')
    except Exception:
        pass
    return 'FIXED'

def agi_log(message):
    sys.stderr.write(f"VICTOR_DECISION: {message}\n")
    sys.stderr.flush()

def main():
    agi_vars = {}
    while True:
        line = sys.stdin.readline().strip()
        if not line:
            break
        parts = line.split(':', 1)
        if len(parts) == 2:
            agi_vars[parts[0].strip()] = parts[1].strip()

    mode = get_current_mode()
    agi_log(f"Modo: {mode}")

    if mode == 'FIXED':
        # Modo 2: Mensaje fijo
        print("EXEC Playback /var/lib/asterisk/sounds/es/fixed_spam_message")
        sys.stdout.flush()
        sys.stdin.readline()
    elif mode == 'AI':
        # Modo 3: IA conversacional GRATIS
        agi_log("Iniciando IA conversacional con Gemini...")
        print("EXEC AGI /usr/share/asterisk/agi-bin/victor_agi_gratis.py")
        sys.stdout.flush()
        sys.stdin.readline()

    print("EXEC Hangup")
    sys.stdout.flush()

if __name__ == "__main__":
    main()
```

---

## ✅ Checklist de Implementación

- [ ] Cuenta Google AI creada
- [ ] API key de Gemini obtenida
- [ ] Modelo Vosk descargado (130 MB)
- [ ] Dependencias Python instaladas
- [ ] ffmpeg instalado
- [ ] `victor_agi_gratis.py` creado y con permisos +x
- [ ] `decision_agi.py` modificado
- [ ] Test de Gemini exitoso
- [ ] Test de Vosk exitoso
- [ ] Test de gTTS exitoso
- [ ] Llamada de prueba end-to-end

---

## 📝 Notas Importantes

### Calidad vs Coste:
- **Gemini** es casi tan bueno como Claude para conversación
- **Vosk** tiene 70-80% precisión (vs 95% de Whisper)
  - Suficiente para entender spammers
  - Puede tener errores ocasionales
- **gTTS** suena natural (mejor que eSpeak)
  - No tan realista como ElevenLabs
  - Pero totalmente creíble para spammers

### Privacidad:
- **Vosk:** 100% offline (máxima privacidad)
- **Gemini:** Datos enviados a Google (pero gratis)
- **gTTS:** Usa servidores Google (pero gratis)

### Rendimiento:
- Latencia total: ~5 segundos por turno
- Suficientemente rápido para conversación natural
- Spammers no notarán la diferencia

---

## 🎉 Ventajas de la Versión Gratis

1. ✅ **$0 de coste** (vs $15/mes premium)
2. ✅ Gemini es excelente (casi = Claude)
3. ✅ Sin límite de tiempo (gratis para siempre)
4. ✅ Sin tarjeta de crédito requerida
5. ✅ Vosk funciona offline (privacidad)
6. ✅ Suficientemente bueno para frustrar spammers

---

**Estado:** Plan completo versión GRATIS ✅
**Próximo paso:** Obtener API key de Gemini e instalar Vosk
**Tiempo estimado:** 4-6 horas implementación total
**Coste mensual:** $0.00 🎊

---

*Creado: 28 Enero 2026*
*Stack: Gemini + Vosk + gTTS (TODO GRATIS)*
