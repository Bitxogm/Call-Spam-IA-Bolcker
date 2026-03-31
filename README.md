# Call Spam IA Blocker

Bloqueador de llamadas spam para Android con tres modos de operación, incluyendo un agente IA conversacional que entretiene al spammer.

**Repo:** https://github.com/Bitxogm/Call-Spam-IA-Bolcker &nbsp;|&nbsp; **Branch activo:** `dev`

---

## Modos de operación

| Modo | Descripción | Estado |
|------|-------------|--------|
| **Modo 1 — Answer+Hangup** | La app detecta spam, contesta y cuelga en menos de 1 segundo. El spammer no escucha nada. El teléfono sí suena brevemente. | ✅ Funcional |
| **Modo 2 — Mensaje fijo** | Desvío GSM al VPS vía USSD. El teléfono no suena. El spammer escucha un mensaje de voz personalizado reproducido por Asterisk. | ✅ Funcional |
| **Modo 3 — Agente IA Manolo** | Igual que Modo 2, pero en vez de un mensaje fijo contesta Manolo: un abuelo gallego de 78 años (Gemini 2.5-flash) que mantiene al spammer ocupado el mayor tiempo posible. | ✅ Funcional |

---

## Arquitectura

```
MODO 1
  Llamada entrante
    └── CallAccessibilityService detecta spam
          └── TelecomManager.acceptRingingCall() → endCall()

MODOS 2 y 3
  Llamada entrante
    └── Operador GSM aplica desvío USSD (*21*34919933065#)
          └── Zadarma (+34 919 93 30 65)
                └── SIP INVITE → VPS Asterisk (157.180.35.161:5060)
                      └── extensions.conf [from-zadarma]
                            └── manolo_agi.py
                                  ├── Lee /root/ai_bridge/current_mode.json
                                  ├── FIXED → Playback fixed_spam_message → Hangup
                                  └── AI    → Manolo
                                              bucle: graba spammer
                                                     → Google STT
                                                     → Gemini 2.5-flash
                                                     → gTTS → WAV 8kHz
                                                     → reproduce en llamada
```

La app controla el modo activo vía `BackendSyncService.ts` → `POST http://157.180.35.161:5000/set_mode`.

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| App Android | React Native (Expo) + Java |
| Detección de spam | `CallAccessibilityService.java` — AccessibilityService + PhoneStateListener |
| Desvío de llamadas | USSD vía `TelecomManager` → Zadarma SIP trunk |
| PBX en VPS | Asterisk + pjsip |
| AGI unificado | Python — `manolo_agi.py` |
| IA conversacional | Google Gemini 2.5-flash (`thinking_budget=0`) |
| TTS en VPS | gTTS español (es-ES) → WAV 8kHz mono pcm_s16le |
| STT en VPS | SpeechRecognition + Google Speech API |
| API de control | Flask — `control_api.py` (puerto 5000) |
| Infraestructura | VPS Hetzner Ubuntu 24.04 — `157.180.35.161` |

---

## Requisitos

### Todos los modos

- Android 9.0+ (API 28)
- Permisos: `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `READ_CONTACTS`, `READ_CALL_LOG`, `POST_NOTIFICATIONS`
- App configurada como **Call Screening Service predeterminado** en ajustes del sistema

### Modos 2 y 3 (además)

- Número Zadarma activo con reenvío configurado al VPS vía SIP
- VPS con Asterisk, Python 3.12 y venv con dependencias instaladas
- Fichero `/root/ai_bridge/.env` con `GEMINI_API_KEY`

---

## Instalación

### App

```bash
git clone https://github.com/Bitxogm/Call-Spam-IA-Bolcker.git
cd Call-Spam-IA-Bolcker
npm install
```

Crear `.env` en la raíz del proyecto:

```env
EXPO_PUBLIC_GEMINI_API_KEY=tu_api_key
EXPO_PUBLIC_GEMINI_MODEL=gemini-2.5-flash
EXPO_PUBLIC_ELEVENLABS_API_KEY=tu_api_key
EXPO_PUBLIC_VPS_IP=157.180.35.161
EXPO_PUBLIC_VPS_PORT=5000
```

Compilar e instalar:

```bash
./build-fresh.sh
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### VPS — Modos 2 y 3

Ejecutar desde la raíz del repo en el VPS:

```bash
bash vps_backend/deploy.sh           # instala Asterisk, Python, dependencias
bash vps_backend/install_service.sh  # registra control_api como servicio systemd
bash vps_backend/deploy_modo3.sh     # copia manolo_agi.py y extensions.conf
```

Para la configuración completa de pjsip y las subnets de Zadarma, ver sección 14 de [CLAUDE.md](CLAUDE.md).

---

## Costes

```
Zadarma número virtual Madrid:  €1.70/mes
Hetzner VPS (2GB RAM, 40GB):    €4.51/mes
Llamadas entrantes Zadarma:     €0.00 (gratis)
──────────────────────────────────────────────
Total con VPS (Modos 2 y 3):    €6.21/mes
Solo Modo 1 (sin VPS):          €0.00/mes
```

---

## Estructura del proyecto

```
Call-Spam-IA-Bolcker/
├── android/app/src/main/java/…/SpamBlockerApp/
│   ├── CallAccessibilityService.java   ← núcleo: detección de spam + Modo 1
│   ├── CallForwardingManager.java      ← activación/desactivación USSD (Modos 2/3)
│   ├── AnswerHangupHelper.java         ← lógica Answer+Hangup
│   └── … (bridges React Native ↔ Java)
│
├── src/
│   ├── screens/                        ← UI React Native (7 pantallas)
│   └── services/
│       ├── BackendSyncService.ts       ← sincroniza modo y mensaje con VPS
│       └── …
│
├── vps_backend/
│   ├── manolo_agi.py                   ← AGI unificado: decisión + agente Manolo IA
│   ├── control_api.py                  ← Flask API de control (puerto 5000)
│   ├── extensions.conf                 ← dialplan Asterisk [from-zadarma]
│   ├── deploy.sh                       ← instala dependencias en VPS
│   ├── deploy_modo3.sh                 ← despliega manolo_agi.py en Asterisk
│   ├── install_service.sh              ← registra systemd service
│   └── asterisk-control-api.service   ← systemd unit para control_api
│
├── webhook-server.js                   ← stub TwiML puerto 3000 (Twilio no activo)
├── CLAUDE.md                           ← contexto técnico completo del proyecto
└── App.tsx
```

---

## Documentación técnica

El fichero [CLAUDE.md](CLAUDE.md) contiene la documentación técnica completa:
arquitectura detallada, deuda técnica conocida, valores hardcodeados, comandos de desarrollo y mantenimiento, configuración VPS y bugs históricos resueltos.

---

## Autor

**[@Bitxogm](https://github.com/Bitxogm)** — con asistencia técnica de Claude (Anthropic)

Issues y contribuciones bienvenidas en [GitHub Issues](https://github.com/Bitxogm/Call-Spam-IA-Bolcker/issues).

---

## Licencia

MIT — Copyright (c) 2026 Bitxogm
