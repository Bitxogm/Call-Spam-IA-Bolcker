# Call Spam IA Blocker

Bloqueador de llamadas spam para Android con tres modos de operación, incluyendo un agente IA conversacional que entretiene al spammer.

**Repo:** https://github.com/Bitxogm/Call-Spam-IA-Bolcker &nbsp;|&nbsp; **Branch activo:** `dev`

---

## Modos de operación

| Modo                          | Descripción                                                                                                                                                    | Estado       |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| **Modo 1 — Answer+Hangup**    | La app detecta spam, contesta y cuelga en menos de 1 segundo. El spammer no escucha nada. El teléfono sí suena brevemente.                                     | ✅ Funcional |
| **Modo 2 — Mensaje fijo**     | Desvío GSM al VPS vía USSD. El teléfono no suena. El spammer escucha un mensaje de voz personalizado reproducido por Asterisk.                                 | ✅ Funcional |
| **Modo 3 — Agente IA Manolo** | Igual que Modo 2, pero en vez de un mensaje fijo contesta Manolo usando LLM Groq + TTS Edge en el VPS, manteniendo al spammer ocupado el mayor tiempo posible. | ✅ Funcional |

---

## Arquitectura

```
MODO 1
  Llamada entrante
    └── CallAccessibilityService detecta spam
          └── TelecomManager.acceptRingingCall() → endCall()

MODO 2
  Llamada entrante
    └── Operador GSM aplica desvío USSD (*21*34919933065#)
          └── Zadarma (+34 919 93 30 65)
                └── SIP INVITE → VPS Asterisk (157.180.35.161:5060)
                      └── extensions.conf [from-zadarma] → Stasis(manolo-ari)
                            └── manolo_ari.py lee current_mode.json
                                  └── FIXED → Playback fixed_spam_message → Hangup

MODO 3 (migrado a ARI en septiembre 2026)
  Llamada entrante (mismo camino Zadarma → Asterisk que Modo 2)
    └── extensions.conf [from-zadarma] → Stasis(manolo-ari)
          └── manolo_ari.py (ARI + ExternalMedia, streaming UDP/RTP)
                bucle por turno:
                  ├── Recibe RTP/mulaw del spammer, VAD por energía
                  │     (barge-in: 800ms de gracia + 3 frames antes de cortar)
                  ├── STT: Deepgram Nova-2 (batch) — Whisper por socket si falla
                  ├── LLM: Groq qwen/qwen3.8-27b, con historial real de la conversación
                  └── TTS: Edge TTS (es-ES-AlvaroNeural) → mulaw 8kHz → RTP de vuelta
```

La app controla el modo activo (FIXED/AI) vía `BackendSyncService.ts` → `POST http://157.180.35.161:5000/set_mode`. `manolo_ari.py` lee ese mismo `current_mode.json`.

### Estado por capa (importante)

- **Producción Modo 3 (VPS/Asterisk):** `manolo_ari.py` vía ARI + ExternalMedia — Deepgram + Groq + Edge TTS. `manolo_agi.py` (AGI) queda en disco como backup, ya no está en el dialplan.
- **Pantalla de pruebas IA (app móvil):** sigue en migración y todavía usa `GeminiServices.ts` + `ElevenLabService.ts` en varias rutas.
- **Conclusión:** el stack real de llamadas en producción y el stack del laboratorio de UI no son idénticos hoy.

---

## Stack tecnológico

| Capa                           | Tecnología                                                                  |
| ------------------------------ | --------------------------------------------------------------------------- |
| App Android                    | React Native (Expo) + Java                                                  |
| Detección de spam              | `CallAccessibilityService.java` — AccessibilityService + PhoneStateListener |
| Desvío de llamadas             | USSD vía `TelecomManager` → Zadarma SIP trunk                               |
| PBX en VPS                     | Asterisk + pjsip + ARI (REST/WebSocket)                                     |
| Motor Modo 3 (producción)      | Python — `manolo_ari.py` (ARI + ExternalMedia, streaming UDP/RTP)           |
| AGI legacy (backup, sin usar)  | Python — `manolo_agi.py`                                                    |
| IA conversacional (producción) | Groq `qwen/qwen3.8-27b`, con historial de conversación real                 |
| TTS en VPS (producción)        | Edge TTS voz `es-ES-AlvaroNeural` → mulaw 8kHz                              |
| STT en VPS (producción)        | Deepgram Nova-2 (batch) — Whisper por socket Unix como fallback             |
| API de control                 | Flask — `control_api.py` (puerto 5000)                                      |
| Infraestructura                | VPS Hetzner Ubuntu 24.04 — `157.180.35.161`                                 |

---

## Requisitos

### Todos los modos

- Android 9.0+ (API 28)
- Permisos: `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `READ_CONTACTS`, `READ_CALL_LOG`, `POST_NOTIFICATIONS`
- App configurada como **Call Screening Service predeterminado** en ajustes del sistema

### Modos 2 y 3 (además)

- Número Zadarma activo con reenvío configurado al VPS vía SIP
- VPS con Asterisk (ARI activo en `ari.conf`/`http.conf`), Python 3.12 y venv con dependencias instaladas
- Fichero `/root/ai_bridge/.env` con `GROQ_API_KEY`, `DEEPGRAM_API_KEY` y `ARI_PASSWORD`

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
EXPO_PUBLIC_GROQ_API_KEY=tu_api_key
EXPO_PUBLIC_VPS_IP=157.180.35.161
EXPO_PUBLIC_VPS_PORT=5000
```

> Nota: en producción Modo 3 del VPS se usa `GROQ_API_KEY` (server-side). Las variables `EXPO_PUBLIC_*` pertenecen a la app móvil y pueden no reflejar al 100% el pipeline AGI de Asterisk.

Compilar e instalar:

```bash
./build-fresh.sh
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### VPS — Modos 2 y 3

Ejecutar desde la raíz del repo en el VPS:

```bash
bash vps_backend/deploy.sh           # instala Asterisk, Python, dependencias
bash vps_backend/install_service.sh  # registra control_api + whisper_server como servicios systemd

# Motor Modo 3 (ARI) — copiar manolo_ari.py y manolo_ari.service al VPS,
# luego:
sudo systemctl daemon-reload
sudo systemctl enable --now manolo_ari.service
```

Servicios esperados tras instalación:

```bash
systemctl status asterisk-control-api
systemctl status whisper_server
systemctl status manolo_ari
```

> `manolo_agi.py` + `deploy_modo3.sh` quedan como ruta de backup (AGI legacy) — no se ejecutan en el flujo de llamadas reales desde que `[from-zadarma]` apunta a `Stasis(manolo-ari)`.

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
│   ├── manolo_ari.py                   ← motor Modo 3 en producción: ARI + ExternalMedia
│   ├── manolo_ari.service              ← systemd unit para manolo_ari.py
│   ├── manolo_agi.py                   ← AGI legacy (backup, ya no usado desde sept. 2026)
│   ├── whisper_server.py               ← servicio persistente Whisper (fallback STT)
│   ├── whisper_server.service          ← unit systemd del servicio Whisper
│   ├── control_api.py                  ← Flask API de control (puerto 5000)
│   ├── extensions.conf                 ← dialplan Asterisk [from-zadarma] → Stasis(manolo-ari)
│   ├── deploy.sh                       ← instala dependencias en VPS
│   ├── deploy_modo3.sh                 ← despliega manolo_agi.py (ruta legacy)
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
