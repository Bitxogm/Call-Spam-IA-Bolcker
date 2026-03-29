# 🤖 Plan para Modo 3: IA Conversacional

**Fecha de planificación:** 28 Enero 2026
**Estado actual:** Modo 2 (IVR fijo) funcionando ✅
**Objetivo:** Conversación en tiempo real con IA para frustrar spammers

---

## 🎯 Concepto de Modo 3

### ¿Qué hace?
Cuando un spammer llama:
1. Asterisk responde la llamada
2. **Escucha** lo que dice el spammer (Speech-to-Text)
3. Envía el texto a **Claude/GPT** con prompt especial
4. La IA genera una respuesta confusa/dilatoria
5. **Convierte** la respuesta a audio (Text-to-Speech)
6. **Reproduce** el audio al spammer
7. **Repite** el ciclo hasta que el spammer se frustre y cuelgue

### Objetivo:
- Mantener al spammer ocupado 2-5 minutos
- Hacerle perder tiempo (no a ti)
- Conversación creíble pero frustrante
- Divertido de revisar después

---

## 🏗️ Arquitectura Técnica

```
Llamada spam → Zadarma → Asterisk VPS
    ↓
decision_agi.py (detecta Modo 3)
    ↓
victor_agi.py (nuevo script)
    ↓
┌─────────────────────────────────┐
│  LOOP CONVERSACIONAL            │
│                                 │
│  1. Escuchar (Asterisk Record)  │
│  2. STT (Whisper/Google)        │
│  3. IA (Claude/GPT)             │
│  4. TTS (ElevenLabs/Google)     │
│  5. Playback audio              │
│  6. Volver a 1                  │
└─────────────────────────────────┘
    ↓
Hangup después de N turnos
```

---

## 🔧 Componentes Necesarios

### 1. Speech-to-Text (STT)

**Opciones:**

#### Opción A: Whisper API (OpenAI) - RECOMENDADO
- **Precio:** $0.006 / minuto
- **Calidad:** Excelente
- **Latencia:** ~2-3 segundos
- **Idiomas:** Excelente español
```python
import openai

def transcribe_audio(audio_file_path):
    with open(audio_file_path, 'rb') as audio:
        transcript = openai.Audio.transcribe(
            model="whisper-1",
            file=audio,
            language="es"
        )
    return transcript['text']
```

#### Opción B: Google Cloud Speech-to-Text
- **Precio:** $0.006 / 15 segundos
- **Calidad:** Muy buena
- **Latencia:** Streaming (tiempo real)
- Requiere cuenta GCP

#### Opción C: Vosk (Offline, GRATIS)
- **Precio:** GRATIS
- **Calidad:** Buena
- **Latencia:** Instantánea
- **Problema:** Modelos grandes, menos preciso

**Recomendación:** **Whisper API** por balance precio/calidad

---

### 2. IA Conversacional

**Opciones:**

#### Opción A: Claude API (Anthropic) - RECOMENDADO PARA TI
- **Precio:** $3 / 1M tokens de entrada, $15 / 1M tokens de salida
- **Modelo:** Claude 3.5 Sonnet
- **Por llamada:** ~$0.001-0.005
- **Ventajas:**
  - Excelente en español
  - Muy creativo
  - Mejor para conversaciones naturales

```python
import anthropic

client = anthropic.Anthropic(api_key="tu-api-key")

def get_ai_response(conversation_history):
    message = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=150,
        system=SPAM_FRUSTRATOR_PROMPT,
        messages=conversation_history
    )
    return message.content[0].text
```

#### Opción B: OpenAI GPT-4
- **Precio:** Similar a Claude
- **Ventajas:** Muy popular, buena documentación

**Recomendación:** **Claude 3.5 Sonnet** (tu ya tienes cuenta)

---

### 3. Text-to-Speech (TTS)

**Opciones:**

#### Opción A: ElevenLabs - MEJOR CALIDAD
- **Precio:** $0.30 / 1000 caracteres (~$0.01-0.03 por llamada)
- **Calidad:** ★★★★★ (voces ultra-realistas)
- **Latencia:** 1-2 segundos
- **Voces español:** Excelentes
```python
import requests

def text_to_speech_elevenlabs(text, voice_id="ErXwobaYiN019PkySvjV"):
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"

    response = requests.post(
        url,
        headers={
            "xi-api-key": ELEVENLABS_API_KEY,
            "Content-Type": "application/json"
        },
        json={
            "text": text,
            "model_id": "eleven_multilingual_v2",
            "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75
            }
        }
    )

    return response.content  # Audio MP3
```

#### Opción B: Google Cloud TTS
- **Precio:** $4 / 1M caracteres (~$0.004 por llamada)
- **Calidad:** ★★★★☆ (buena, no ultra-realista)
- **Voces español:** Buenas

#### Opción C: Festival/eSpeak (Offline, GRATIS)
- **Precio:** GRATIS
- **Calidad:** ★★☆☆☆ (robótica)
- **Problema:** Suena falso, spammer sospecha

**Recomendación:** **ElevenLabs** (vale la pena por calidad)

---

## 📝 Prompt de IA para Frustrar Spammers

```python
SPAM_FRUSTRATOR_PROMPT = """Eres Víctor, un señor mayor de 75 años algo despistado pero educado.

Tu objetivo es MANTENER AL SPAMMER EN LA LÍNEA el mayor tiempo posible siendo amable pero confuso:

REGLAS IMPORTANTES:
1. Habla ESPAÑOL de España (tú, vosotros)
2. Sé educado pero despistado
3. HAZ PREGUNTAS que obliguen al spammer a repetir
4. Di que no oyes bien o no entiendes
5. Interrumpe con anécdotas irrelevantes ("esto me recuerda a cuando...")
6. Muestra INTERÉS pero pide que expliquen TODO de nuevo
7. Di que vas a buscar tu tarjeta/papeles pero tardas
8. NUNCA digas que no te interesa (mantén esperanza)
9. NUNCA cuelgues tú primero
10. Máximo 2-3 frases por respuesta (natural)

EJEMPLOS BUENOS:
- "Perdona hijo, ¿puedes hablar más alto? Tengo el audífono malo"
- "Eso me interesa, pero ¿podrías explicarme qué es eso de 'fibra'?"
- "Espera, espera, que voy a buscar un boli para apuntarlo"
- "Ah, como mi sobrino que trabaja en... ¿cómo se llama?"

EJEMPLOS MALOS (NO HAGAS):
- "No me interesa, adiós" ❌
- "Sé que eres spam" ❌
- Respuestas muy largas ❌
- Sonar como robot ❌

Mantén el contexto de la conversación. Si el spammer te pregunta algo, responde pero de forma confusa o pidiendo aclaración."""
```

---

## 🛠️ Implementación: victor_agi.py

### Estructura del script:

```python
#!/usr/bin/env python3
"""
victor_agi.py - IA Conversacional para Modo 3
Mantiene conversación con spammers usando Claude + TTS
"""

import sys
import os
import json
import requests
import anthropic
from pathlib import Path

# Configuración
CLAUDE_API_KEY = os.getenv('ANTHROPIC_API_KEY')
ELEVENLABS_API_KEY = os.getenv('ELEVENLABS_API_KEY')
OPENAI_API_KEY = os.getenv('OPENAI_API_KEY')

AUDIO_DIR = '/var/lib/asterisk/sounds/temp/'
MAX_TURNS = 10  # Máximo 10 intercambios (5 min aprox)

def agi_log(message):
    """Log a Asterisk"""
    sys.stderr.write(f"VICTOR_IA: {message}\n")
    sys.stderr.flush()

def read_agi_vars():
    """Lee variables AGI de Asterisk"""
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
    """
    Graba audio del caller usando Asterisk Record

    Args:
        filename: Nombre del archivo (sin extensión)
        timeout: Tiempo máximo de grabación
        silence: Segundos de silencio para terminar
    """
    agi_log(f"Grabando audio del caller: {filename}")

    # Record(filename,format,silence_seconds,max_duration)
    print(f"EXEC Record {filename},wav,{silence},{timeout}")
    sys.stdout.flush()

    result = sys.stdin.readline().strip()
    agi_log(f"Resultado grabación: {result}")

    return f"{filename}.wav"

def transcribe_with_whisper(audio_path):
    """Transcribe audio usando Whisper API"""
    agi_log(f"Transcribiendo: {audio_path}")

    try:
        with open(audio_path, 'rb') as audio_file:
            response = requests.post(
                "https://api.openai.com/v1/audio/transcriptions",
                headers={"Authorization": f"Bearer {OPENAI_API_KEY}"},
                files={"file": audio_file},
                data={
                    "model": "whisper-1",
                    "language": "es"
                }
            )

        transcript = response.json()['text']
        agi_log(f"Transcrito: {transcript}")
        return transcript

    except Exception as e:
        agi_log(f"ERROR transcripción: {e}")
        return ""

def get_claude_response(conversation_history):
    """Obtiene respuesta de Claude"""
    agi_log("Consultando Claude...")

    try:
        client = anthropic.Anthropic(api_key=CLAUDE_API_KEY)

        message = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=150,
            temperature=0.8,
            system=SPAM_FRUSTRATOR_PROMPT,
            messages=conversation_history
        )

        response = message.content[0].text
        agi_log(f"Claude responde: {response}")
        return response

    except Exception as e:
        agi_log(f"ERROR Claude: {e}")
        return "Perdona, no te he entendido bien. ¿Puedes repetir?"

def text_to_speech_elevenlabs(text, output_path):
    """Convierte texto a audio usando ElevenLabs"""
    agi_log(f"Generando TTS: {text}")

    try:
        url = "https://api.elevenlabs.io/v1/text-to-speech/ErXwobaYiN019PkySvjV"

        response = requests.post(
            url,
            headers={
                "xi-api-key": ELEVENLABS_API_KEY,
                "Content-Type": "application/json"
            },
            json={
                "text": text,
                "model_id": "eleven_multilingual_v2",
                "voice_settings": {
                    "stability": 0.5,
                    "similarity_boost": 0.75
                }
            }
        )

        # Guardar audio
        with open(output_path, 'wb') as f:
            f.write(response.content)

        agi_log(f"TTS guardado: {output_path}")
        return True

    except Exception as e:
        agi_log(f"ERROR TTS: {e}")
        return False

def play_audio(audio_path):
    """Reproduce audio usando Asterisk Playback"""
    # Asterisk espera path sin extensión
    path_no_ext = audio_path.replace('.wav', '')

    agi_log(f"Reproduciendo: {path_no_ext}")
    print(f"EXEC Playback {path_no_ext}")
    sys.stdout.flush()

    result = sys.stdin.readline().strip()
    agi_log(f"Resultado playback: {result}")

def main():
    """Loop principal de conversación"""
    agi_log("=== INICIO MODO 3: IA CONVERSACIONAL ===")

    # Leer variables AGI
    agi_vars = read_agi_vars()
    caller_id = agi_vars.get('agi_callerid', 'unknown')

    agi_log(f"Caller: {caller_id}")

    # Crear directorio temporal
    os.makedirs(AUDIO_DIR, exist_ok=True)

    # Historial de conversación para Claude
    conversation_history = []

    # Saludo inicial
    greeting = "¿Dígame? ¿Quién llama?"
    greeting_audio = f"{AUDIO_DIR}greeting.wav"

    if text_to_speech_elevenlabs(greeting, greeting_audio):
        play_audio(greeting_audio)
        conversation_history.append({
            "role": "assistant",
            "content": greeting
        })

    # Loop conversacional
    for turn in range(MAX_TURNS):
        agi_log(f"--- TURNO {turn + 1}/{MAX_TURNS} ---")

        # 1. GRABAR lo que dice el spammer
        audio_filename = f"{AUDIO_DIR}caller_turn_{turn}"
        record_caller_audio(audio_filename, timeout=15, silence=2)

        # 2. TRANSCRIBIR (STT)
        transcript = transcribe_with_whisper(f"{audio_filename}.wav")

        if not transcript or len(transcript) < 3:
            agi_log("Transcripción vacía o muy corta, terminando")
            break

        # Añadir a historial
        conversation_history.append({
            "role": "user",
            "content": transcript
        })

        # 3. OBTENER RESPUESTA DE IA
        ai_response = get_claude_response(conversation_history)

        conversation_history.append({
            "role": "assistant",
            "content": ai_response
        })

        # 4. CONVERTIR A AUDIO (TTS)
        response_audio = f"{AUDIO_DIR}victor_turn_{turn}.wav"

        if not text_to_speech_elevenlabs(ai_response, response_audio):
            agi_log("ERROR generando TTS, usando fallback")
            break

        # 5. REPRODUCIR al spammer
        play_audio(response_audio)

        # Limpiar archivos temporales viejos
        try:
            os.remove(f"{audio_filename}.wav")
            os.remove(response_audio)
        except:
            pass

    agi_log("=== FIN CONVERSACIÓN ===")
    agi_log(f"Total turnos: {len(conversation_history) // 2}")

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

## 📦 Dependencias Python

```bash
# En el VPS
pip install anthropic openai requests
```

---

## 🔑 Configuración de API Keys

```bash
# Editar .env en /root/ai_bridge/
nano /root/ai_bridge/.env
```

```env
# API Keys para Modo 3
ANTHROPIC_API_KEY=sk-ant-api03-xxxxx
OPENAI_API_KEY=sk-xxxxx  # Para Whisper STT
ELEVENLABS_API_KEY=xxxxx  # Para TTS
```

---

## 💰 Costes Estimados por Llamada

```
Whisper STT (5 min):      $0.03
Claude 3.5 (10 turnos):   $0.005
ElevenLabs TTS (500 car): $0.015
--------------------------------
TOTAL por llamada:        ~$0.05 (5 céntimos)
```

Si recibes 10 llamadas spam/día:
- **Coste diario:** $0.50
- **Coste mensual:** ~$15

**Rentable** si tu tiempo vale más que eso.

---

## 🧪 Plan de Testing

### Fase 1: Componentes individuales
1. Probar Whisper STT con audio de prueba
2. Probar Claude con conversación mock
3. Probar ElevenLabs TTS con texto de prueba
4. Verificar que Asterisk puede grabar/reproducir

### Fase 2: Integración
1. Crear `victor_agi.py` básico (1 turno)
2. Probar con llamada real
3. Añadir loop conversacional
4. Ajustar prompts según resultados

### Fase 3: Optimización
1. Reducir latencia
2. Mejorar detección de fin de conversación
3. Guardar conversaciones interesantes
4. Estadísticas de tiempo perdido por spammers

---

## 🚀 Pasos de Implementación (Cuando lo intentes)

### Día 1: Setup (1 hora)
- [ ] Crear cuentas API (Whisper, ElevenLabs)
- [ ] Instalar dependencias Python
- [ ] Configurar API keys en .env
- [ ] Probar cada API individualmente

### Día 2: Script básico (2 horas)
- [ ] Crear `victor_agi.py` versión simple (1 turno)
- [ ] Probar grabación/transcripción/TTS
- [ ] Llamada de prueba end-to-end
- [ ] Ajustar según errores

### Día 3: Loop conversacional (1 hora)
- [ ] Añadir loop de múltiples turnos
- [ ] Implementar detección de silencio/cuelgue
- [ ] Optimizar prompts de IA
- [ ] Testing con llamadas reales

### Día 4: Pulido (1 hora)
- [ ] Guardar logs de conversaciones
- [ ] Estadísticas de duración
- [ ] Cleanup de archivos temporales
- [ ] Documentación

---

## 📊 Métricas de Éxito

**Modo 3 será exitoso si:**
- ✅ Mantiene al spammer >2 minutos
- ✅ Conversación suena natural (no robótica)
- ✅ Latencia <5 segundos por turno
- ✅ Coste <$0.10 por llamada
- ✅ No crashes (maneja errores)
- ✅ Es divertido revisar las conversaciones después 😄

---

## 🎯 Próximos Pasos (Cuando quieras continuarlo)

1. **Crear cuentas API** (Whisper + ElevenLabs)
2. **Probar APIs** individualmente
3. **Implementar `victor_agi.py`** versión básica
4. **Test con llamada real**
5. **Iterar y mejorar**

---

**Estado actual:** Plan completo documentado ✅
**Listo para implementar:** Cuando tengas energía y ganas 🚀
**Complejidad:** Alta pero factible
**Tiempo estimado:** 4-6 horas totales
**Diversión esperada:** ⭐⭐⭐⭐⭐

---

*Creado: 28 Enero 2026*
*Modo 2 funcionando, Modo 3 planificado para el futuro*
