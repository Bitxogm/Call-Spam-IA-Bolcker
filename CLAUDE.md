# CLAUDE.md — Call-Spam-IA-Blocker

> Archivo de contexto para Claude Code. Léelo completo antes de tocar cualquier archivo.
> Repositorio: https://github.com/Bitxogm/Call-Spam-IA-Bolcker (branch activo: `dev`)
>
> ⚠️ IGNORA toda la documentación en `/docs/`, `README_MODO1_OPTIMIZED.md` y
> `README_MODO2_BRANCH.md` — están desactualizados y contradicen el código real.
> La fuente de verdad es siempre el código fuente.

---

## 1. Contexto del desarrollador

- **Nombre:** Víctor (GitHub: Bitxogm)
- **Nivel:** Junior Full-Stack Developer — recién graduado KeepCoding bootcamp 2025-2026
- **Stack principal:** Next.js, React, TypeScript, Node.js/Express, Python, PostgreSQL, MongoDB, Docker, Prisma, TailwindCSS
- **Intereses paralelos:** ciberseguridad y ethical hacking
- **Infraestructura:** VPS Hetzner Ubuntu 24.04 (IP `157.180.35.161`, SSH puerto 2222)
- **Estilo de trabajo:** directo, una tarea concreta a la vez con resultado verificable
- **Dispositivos de desarrollo/testing:** Pixel 8 (GrapheneOS) — dispositivo principal; Samsung Galaxy Note 20 — dispositivo secundario/simulador de spammer

---

## 2. Descripción del proyecto

Bloqueador de llamadas spam para Android con dos modos de operación:

- **Modo 1 (Answer+Hangup):** la app contesta y cuelga localmente. El spammer no escucha nada.
- **Modo 2 (Backend Fixed):** la llamada se desvía a nivel de red GSM (USSD) al número Zadarma, que la reenvía al VPS. El servidor reproduce un mensaje de voz al spammer usando TTS del Android nativo. El teléfono del usuario no llega a sonar.

Llegar al Modo 2 funcional costó mucho trabajo. Este documento existe para que ese conocimiento no se pierda.

---

## 3. Sistemas de audio — hay dos, independientes entre sí

Es crítico no confundirlos:

| Sistema            | Dónde vive                                                | Tecnología                                                           | Para qué                                                                                          |
| ------------------ | --------------------------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| **IVR on-device**  | `IVRGeneratorModule.java` + `IVRMessageHelper.java`       | `android.speech.tts.TextToSpeech` (TTS nativo Android, motor Google) | Genera `ivr_corporate.mp3` en el dispositivo para reproducción local                              |
| **AI Test Screen** | `src/services/ElevenLabsService.ts` + `AITestsScreen.tsx` | ElevenLabs primero, fallback a `expo-speech`                         | Solo para la pantalla de pruebas, no interviene en llamadas reales                                |

**Estado actual:** La generación de audio funcional para el mensaje al spammer es el TTS nativo de Android (`IVRGeneratorModule.java`).

---

## 4. Arquitectura real (verificada en código)

### Modo 1 — Answer+Hangup (on-device)

```
Llamada entra al teléfono
  └── CallAccessibilityService detecta spam
        ├── TelecomManager.acceptRingingCall()
        ├── espera delay configurable (1-5s)
        └── TelecomManager.endCall()
```

### Modo 2 — Backend Fixed (desvío GSM + Asterisk)

```
┌──────────────────────────────────────────────────────────────┐
│  ACTIVACIÓN (una sola vez desde la UI)                       │
│                                                              │
│  Usuario activa Modo 2 en la app                             │
│    └── RN → CallForwardingModule.configureForMode            │
│              ("BACKEND_FIXED")                               │
│          └── CallForwardingManager.enableForwarding()        │
│              └── ejecuta USSD en el operador:                │
│                  *21*34919933065#                            │
│                  (desvío incondicional → Zadarma)            │
└──────────────────────────────────────────────────────────────┘
                           ↓
                  (por cada llamada spam)
                           ↓
┌──────────────────────────────────────────────────────────────┐
│  FLUJO POR LLAMADA                                           │
│                                                              │
│  1. Llega llamada spam al número del usuario                 │
│     └── El operador GSM la redirige a +34919933065           │
│         El teléfono del usuario NO suena                     │
│                                                              │
│  2. Zadarma recibe la llamada                                │
│     └── Configurado en panel web Zadarma (fuera del repo)   │
│         para reenviar al VPS vía SIP/trunk                   │
│                                                              │
│  3. VPS — Asterisk recibe la llamada                         │
│     └── extensions.conf [from-zadarma]:                     │
│         Answer → Wait(1) → AGI(decision_agi.py) → Hangup    │
│                                                              │
│  4. decision_agi.py lee current_mode.json                    │
│     ├── FIXED → Playback fixed_spam_message + Hangup        │
│     └── AI    → AGI(victor_agi.py) [Modo 3]                 │
│                                                              │
│  5. El modo lo controla control_api.py (Flask, puerto 5000) │
│     └── BackendSyncService.ts → POST /set_mode              │
│         (llamado desde AnswerHangupSettingsScreen al         │
│          cambiar de Modo 2 a Modo 3)                         │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  DESACTIVAR Modo 2                                           │
│                                                              │
│  CallForwardingManager.disableForwarding()                   │
│    └── USSD: ##21#  (cancela desvío en el operador)          │
│  Consultar estado: *#21#                                     │
└──────────────────────────────────────────────────────────────┘
```

### ⚠️ Problema de coordinación conocido (on-device)

En Modo 2, `CallAccessibilityService` también reacciona al estado OFFHOOK de forma independiente al servidor. Busca `ivr_corporate.mp3` y lo reproduce si existe. El servidor Asterisk ya gestiona el audio — el teléfono no debería hacer nada adicional.

---

## 5. Modos de operación

|                         | Modo 1 (HANGUP_IMMEDIATELY) | Modo 2 (BACKEND_FIXED)      | Modo 3 (BACKEND_AI)         |
| ----------------------- | --------------------------- | --------------------------- | --------------------------- |
| USSD                    | desactiva (`##21#`)         | activa (`*21*34919933065#`) | activa (`*21*34919933065#`) |
| Teléfono suena          | sí, app lo cuelga           | no                          | no                          |
| Spammer escucha         | nada                        | mensaje "Roberto"           | Manolo (Gemini 2.5-flash)   |
| Requiere VPS            | ❌                          | ✅                          | ✅                          |
| Requiere Zadarma config | ❌                          | ✅                          | ✅                          |
| Estado actual           | ✅ funcional                | ✅ funcional                | ✅ funcional                |

---

## 6. Infraestructura del servidor

### Stack real del VPS (verificado en código)

El VPS gestiona las llamadas con **Asterisk**.

| Componente            | Archivo                                    | Puerto/Ruta                    | Estado              |
| --------------------- | ------------------------------------------ | ------------------------------ | ------------------- |
| **Asterisk dialplan** | `vps_backend/extensions.conf`              | contexto `[from-zadarma]`      | ✅ funcional        |
| **AGI unificado**     | `vps_backend/manolo_agi.py`                | `/usr/share/asterisk/agi-bin/` | ✅ funcional        |
| **API de control**    | `vps_backend/control_api.py`               | puerto 5000                    | ✅ funcional        |
| **Systemd service**   | `vps_backend/asterisk-control-api.service` | —                              | instalado           |

**Flujo de decisión en el VPS:**

```
Asterisk [from-zadarma]
  └── AGI(manolo_agi.py) lee /root/ai_bridge/current_mode.json
        ├── FIXED → Playback fixed_spam_message → Hangup
        └── AI    → Manolo conversa (Gemini 2.5-flash), todo en el mismo script
```

**Limpieza agosto 2026 — scripts VPS obsoletos, sustituidos por `manolo_agi.py`:**

- `agi-bin/decision_agi.py` — **ELIMINADO**
- `victor_agi.py` — **ELIMINADO**
- `deploy.sh` — **ELIMINADO**, usar `deploy_modo3.sh` (despliega `manolo_agi.py` + `extensions.conf`)

`manolo_agi.py` es ahora el AGI único y definitivo: decisión FIXED/AI + conversación, en un solo script.

**control_api.py endpoints (Flask, puerto 5000):**

- `POST /set_mode` — body `{"mode": "FIXED"|"AI"}` → escribe `current_mode.json`
- `GET /get_mode` — devuelve modo actual
- `POST /set_message` — body `{"message": "..."}` → genera WAV con gTTS + ffmpeg (8kHz mono pcm_s16le) como `fixed_spam_message.wav`

### Servicios auxiliares en src/services/

- **`GeminiServices.ts`** — llama a `generativelanguage.googleapis.com` con `EXPO_PUBLIC_GEMINI_API_KEY`. Generación de texto, no TTS.
- **`ElevenLabsService.ts`** — TTS premium. Intenta ElevenLabs, fallback a `expo-speech`. Solo usado en `AITestsScreen.tsx`, no en el flujo de llamadas reales.

### Configuración externa (fuera del repo — crítica para Modo 2)

Esta configuración no vive en el código. Si se pierde, el Modo 2 deja de funcionar:

| Servicio             | Número/URL         | Configuración                                                                                                                                                                      |
| -------------------- | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Zadarma**          | `+34919933065`     | Reenvío de entrantes → VPS (documentar URL exacta aquí cuando se confirme)                                                                                                         |
| **VPS**              | `157.180.35.161`   | Puerto 5000 (`control_api.py`, gestionado por systemd `asterisk-control-api.service`)                                                                                              |
| **Gemini free tier** | Google AI Studio   | 20 req/día con `gemini-2.5-flash`. Cuando se agota, Modo 3 falla silenciosamente (fallback hardcodeado). Solución: activar billing.                                                |
| **Deepgram**         | `api.deepgram.com` | $200 crédito gratuito. **NO usar para STT en este proyecto** — devuelve transcript vacío para audio SIP 8kHz (baja calidad). Whisper local es más tolerante para este caso de uso. |

---

## 7. Estructura de archivos

```
Call-Spam-IA-Blocker/
├── android/app/src/main/
│   ├── AndroidManifest.xml                 ← tiene residuos (ver sección 11)
│   └── java/com/anonymous/SpamBlockerApp/
│       │
│       │── ACTIVOS
│       ├── CallAccessibilityService.java   ← CORE: detección de spam
│       ├── CallInterceptorService.java     ← foreground service
│       ├── AnswerHangupHelper.java         ← lógica Answer+Hangup + normalización ES (L223)
│       ├── AnswerHangupModule.java         ← bridge RN↔Java
│       ├── CallForwardingManager.java      ← gestión USSD, Zadarma hardcodeado (L34)
│       ├── CallForwardingModule.java       ← bridge RN↔Java para desvío
│       ├── BlacklistModule.java
│       ├── CallHistoryHelper.java
│       ├── CallHistoryModule.java
│       ├── CallInterceptorModule.java
│       ├── CallInterceptorPackage.java
│       ├── CallAnswerReceiver.java
│       ├── CallStateReceiver.java
│       ├── CallScreeningServiceImpl.java
│       ├── ContactsModule.java
│       ├── ContactsHelper.java
│       ├── SharedPreferencesHelper.java
│       ├── LogsModule.java
│       ├── LogsHelper.java
│       └── SpamNotificationManager.java
│       │
│       │── RESIDUOS IVR ON-DEVICE — ⚠️ no borrar sin limpiar referencias
│       ├── IVRAudioPlayer.java             ← ⚠️ referenciado en CallAccessibilityService L44-45, 513, 516
│       ├── IVRGeneratorModule.java         ← genera ivr_corporate.mp3 con Android TTS
│       ├── IVRMessageHelper.java           ← TTS nativo a STREAM_VOICE_CALL
│       └── SpeechRecognitionModule.java
│
├── src/
│   ├── screens/
│   │   ├── DashboardScreen.tsx
│   │   ├── SpamNumbersScreen.tsx
│   │   ├── AITestsScreen.tsx               ← usa ElevenLabsService
│   │   ├── WhitelistScreen.tsx
│   │   ├── LogsScreen.tsx
│   │   ├── CallHistoryScreen.tsx
│   │   └── AnswerHangupSettingsScreen.tsx
│   └── services/
│       ├── GeminiServices.ts               ← Gemini text generation
│       ├── ElevenLabsService.ts            ← TTS ElevenLabs + fallback expo-speech
│       ├── BlacklistService.ts
│       ├── ContactsService.ts
│       └── AnswerHangupService.ts
│
├── vps_backend/                            ← infraestructura del servidor
│   ├── extensions.conf                     ← dialplan Asterisk [from-zadarma] ✅
│   ├── manolo_agi.py                       ← AGI único y definitivo (decisión + conversación) ✅
│   ├── control_api.py                      ← Flask API puerto 5000 ✅
│   ├── asterisk-control-api.service        ← systemd unit para control_api
│   ├── deploy_modo3.sh                     ← script de deploy vigente (manolo_agi.py + extensions.conf)
│   ├── install_service.sh                  ← registra systemd service
│   └── README_VPS.md
│                                           ← ELIMINADOS (limpieza agosto 2026): agi-bin/decision_agi.py, victor_agi.py, deploy.sh
│
├── App.tsx
├── index.ts
├── package.json
├── tsconfig.json
├── app.json
├── build-fresh.sh
├── rebuild-clean.sh
└── copy-apk.sh
```

---

## 8. Variables de entorno

```env
# IA — activo
EXPO_PUBLIC_GEMINI_API_KEY=...
EXPO_PUBLIC_GEMINI_MODEL=gemini-1.5-flash

# ElevenLabs — activo (con fallback a expo-speech)
EXPO_PUBLIC_ELEVENLABS_API_KEY=...

# VPS
EXPO_PUBLIC_VPS_IP=157.180.35.161
EXPO_PUBLIC_VPS_PORT=5000
```

**Estado del `.env`:** verificado que no está commiteado (`git log --all` no lo muestra). Solo aparece `ios/.xcode.env` que es irrelevante.

⚠️ El prefijo `EXPO_PUBLIC_*` embebe todos estos valores en el APK compilado.

---

## 9. Comandos de desarrollo

```bash
# Instalar dependencias
npm install

# Build debug Android
npm run android
./build-fresh.sh          # alternativa
./rebuild-clean.sh        # build limpio si hay problemas de caché

# Instalar APK en dispositivo
adb install android/app/build/outputs/apk/debug/app-debug.apk
./copy-apk.sh

# Verificar tipos TypeScript — ejecutar tras CADA cambio TS
npx tsc --noEmit

# Logs del dispositivo en tiempo real
adb logcat | grep -E "SpamBlocker|CallAccessibility|AnswerHangup|CallForwarding"
adb logcat *:E

# Verificar estado del desvío USSD en el operador
# Marcar desde el teclado del teléfono: *#21#

# Recargar dialplan Asterisk sin reiniciar el servicio
asterisk -rx "dialplan reload"
```

---

## 10. Forma de trabajar con Claude Code

### Reglas (no negociables)

1. **Una tarea a la vez.** No empezar la siguiente hasta que la anterior esté verificada en el dispositivo.

2. **Leer antes de tocar.** Antes de modificar cualquier archivo, leerlo completo. Los `.java` especialmente — la lógica no es obvia por el nombre.

3. **`npx tsc --noEmit` tras cada cambio TypeScript.** Error de tipo = parar y corregir antes de continuar.

4. **No hacer commit sin instrucción explícita de Víctor.** Claude Code edita archivos. El `git add` + `git commit` + `git push` los ejecuta Víctor cuando él lo decide.

5. **No tocar `/android/` sin avisar primero.** Cualquier cambio en Java requiere rebuild completo (~2-5 min). Confirmar antes de proceder.

6. **Nunca borrar un `.java` sin verificar referencias en todo el proyecto.** Ejemplo crítico: `IVRAudioPlayer` está referenciado en `CallAccessibilityService.java` líneas 44-45, 513 y 516. Borrarlo sin limpiar esas referencias rompe el build.

7. **No confundir los dos sistemas de audio.** Android TTS on-device y ElevenLabs/expo-speech de la pantalla de pruebas son independientes. Un cambio en uno no afecta al otro.

8. **Ignorar los READMEs de planificación.** El código manda.

9. **Conventional Commits** cuando Víctor pida commitear:
   `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `test:`

### Flujo de una tarea típica

```
1. Víctor describe la tarea
2. Claude lee los archivos afectados sin asumir su contenido
3. Claude propone: qué archivos toca, qué cambia, en qué orden
4. Víctor aprueba o ajusta
5. Claude implementa
6. npx tsc --noEmit (si hay cambios TS)
7. Víctor hace build y prueba en dispositivo
8. Si OK → Víctor hace commit cuando quiera
```

---

## 11. Valores hardcodeados — localizaciones exactas

| Valor                               | Archivo                       | Línea | Notas                          |
| ----------------------------------- | ----------------------------- | ----- | ------------------------------ |
| `"34919933065"`                     | CallForwardingManager.java    | L34   | Número Zadarma                 |
| `*21*34919933065#`                  | CallForwardingManager.java    | L37   | USSD activar desvío            |
| `##21#`                             | CallForwardingManager.java    | L38   | USSD desactivar                |
| `*#21#`                             | CallForwardingManager.java    | L39   | USSD consultar estado          |
| `"/ivr_corporate.mp3"`              | CallAccessibilityService.java | L501  | Ruta MP3 on-device             |
| últimos 9 dígitos                   | AnswerHangupHelper.java       | L223  | Normalización España           |

---

## 12. Deuda técnica real (verificada en código)

### Crítica

- [ ] **Lógica on-device y servidor sin coordinación en Modo 2.** Cuando el operador desvía la llamada, `CallAccessibilityService` también reacciona al estado OFFHOOK de forma independiente. El MP3 que busca (`ivr_corporate.mp3`) puede o no existir según si `IVRGeneratorModule` lo generó previamente. Hay que decidir qué capa gestiona el audio.

- [ ] **Referencias a `IVRAudioPlayer` activas en `CallAccessibilityService`** (L44-45, 513, 516). Prerequisito para cualquier limpieza o refactor del servicio principal.

- [ ] **Permisos residuales en `AndroidManifest.xml`:** `BIND_INCALL_SERVICE`, `BIND_TELECOM_CONNECTION_SERVICE`, `CONTROL_INCALL_EXPERIENCE`, `MODIFY_AUDIO_SETTINGS`, `MODIFY_PHONE_STATE`, `MODIFY_AUDIO_ROUTING`. Services residuales: `SpamCallService`, `DialerActivity` (priority=1000), `InCallActivity`.

### Importante

- [ ] **12 archivos Java residuales** compilados y declarando permisos innecesarios al usuario.

- [ ] **Configuración de Zadarma no documentada en el repo.** Si se pierde, Modo 2 deja de funcionar sin rastro de por qué. Completar la tabla de sección 6 con la URL exacta de reenvío.

- [ ] **Sin tests** en ninguna capa.

- [x] **Modo 3 ✅ Funcional — conversación real con Manolo**
  - LLM: Groq + llama-3.3-70b-versatile (gratis, ~0.2s)
  - TTS: Edge TTS voz es-ES-AlvaroNeural (gratis, ~1s)
  - STT: Whisper small via socket permanente (gratis, ~0.5s)
  - Latencia por turno: ~8-14s (RECORD timeout el mayor problema)

- [ ] **RECORD FILE devuelve timeout en vez de silence:** El canal SIP tiene comfort noise que impide la detección de silencio. Posible solución: reducir `maxdur` o usar VAD externo.

- [ ] **Quota Gemini agotada (resetea diariamente):** `gemini-2.5-flash` tiene límite de 20 req/día en free tier. Solución: activar billing en Google AI Studio o esperar reset diario. El Modo 3 no funciona cuando se agota la quota — Manolo responde siempre con el fallback `"Ay hijo no te he oído bien"`.

### Menor

- [ ] Typo en nombre del repo: `Bolcker` → `Blocker`
- [ ] `test.mp3` en la raíz
- [ ] READMEs de planificación obsoletos en raíz

---

## 13. Roadmap

En orden de prioridad lógica:

1. **Resolver coordinación Modo 2:** decidir si la lógica IVR on-device se elimina o coordina con el servidor. La opción limpia es que el servidor gestione el audio y el teléfono solo registre el evento.

2. **Documentar configuración Zadarma** en sección 6 de este fichero.

3. **Limpiar residuos:** empezar por las referencias a `IVRAudioPlayer` en `CallAccessibilityService`, luego los 12 archivos Java y el Manifest.

4. ~~**Modo 3 — Agente IA conversacional**~~ ✅ **Completado.** `manolo_agi.py` unifica la lógica de decisión y la conversación de Manolo en un único script AGI. Funcional en producción.

---

## 14. Infraestructura VPS — detalle técnico

### Configuración Zadarma → Asterisk

**Panel Zadarma:**

```
Número: +34 919 93 30 65
SIP Login: #719926
External Server: 34919933065@157.180.35.161:5060
```

⚠️ Puerto crítico: debe ser `5060` (SIP), no `5000` (Flask).

**pjsip.conf en el VPS (`/etc/asterisk/pjsip.conf`):**

```ini
[zadarma-endpoint]
type=endpoint
context=from-zadarma
disallow=all
allow=ulaw,alaw
aors=zadarma-aor
allow_subscribe=yes

[zadarma-aor]
type=aor
contact=sip:sips.zadarma.com

[zadarma-identify]
type=identify
endpoint=zadarma-endpoint
match=185.45.152.0/24
match=185.45.154.0/24
match=185.45.155.0/24
match=195.122.19.0/27
match=31.31.222.192/27
match=15.235.128.64/28
```

**Firewall UFW — las 6 subnets de Zadarma (todas necesarias):**

```bash
ufw allow from 185.45.152.0/24 to any port 5060 proto udp
ufw allow from 185.45.154.0/24 to any port 5060 proto udp
ufw allow from 185.45.155.0/24 to any port 5060 proto udp
ufw allow from 195.122.19.0/27 to any port 5060 proto udp
ufw allow from 31.31.222.192/27 to any port 5060 proto udp
ufw allow from 15.235.128.64/28 to any port 5060 proto udp
```

Con solo 3 subnets las llamadas llegan intermitentemente. Deben estar las 6.

---

### Bugs de configuración resueltos (VPS)

Estos bugs costaron horas — documentados para no repetirlos:

| #   | Síntoma                          | Causa                                                                          | Solución                                                       |
| --- | -------------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------- |
| 1   | Llamadas no llegan al VPS        | External Server con puerto `5000` en panel Zadarma                             | Cambiar a `34919933065@157.180.35.161:5060`                    |
| 2   | Llamadas llegan solo a veces     | Solo 3 subnets de Zadarma en UFW                                               | Añadir las 6 subnets completas                                 |
| 3   | AGI no encontrado por Asterisk   | Script en `/root/ai_bridge/`, Asterisk busca en `/usr/share/asterisk/agi-bin/` | Copiar con `chmod +x` al directorio correcto                   |
| 4   | Audio no encontrado por Asterisk | Audio en `/root/ai_bridge/`, Asterisk busca en `/var/lib/asterisk/sounds/`     | Copiar como `fixed_spam_message.wav` al directorio correcto    |
| 5   | Sub-AGI falla (Modo 3)           | `decision_agi.py` no leía el header AGI de Asterisk antes de enviar comandos   | Unificar en `manolo_agi.py` con `agi_read_headers()` al inicio |

---

### Costes mensuales

```
Zadarma número virtual Madrid: €1.70/mes
Hetzner VPS (2GB RAM, 40GB):   €4.51/mes
Llamadas entrantes Zadarma:    €0.00 (gratis)
─────────────────────────────────────────
TOTAL con VPS (Modos 2+3):     €6.21/mes
Solo Modo 1 (sin VPS):         €0.00/mes
```

---

### Comandos de mantenimiento VPS

```bash
# Logs Asterisk en tiempo real
tail -f /var/log/asterisk/full

# Estado de la conexión SIP con Zadarma
asterisk -rx "pjsip show endpoints"

# Canales activos (llamadas en curso)
asterisk -rx "core show channels"

# Cambiar modo desde terminal (sin la app)
curl -X POST http://157.180.35.161:5000/set_mode \
     -H "Content-Type: application/json" \
     -d '{"mode":"FIXED"}'

# Consultar modo actual
curl http://157.180.35.161:5000/get_mode

# Estado del servicio control_api
systemctl status asterisk-control-api

# Reiniciar servicios
systemctl restart asterisk
systemctl restart asterisk-control-api
```

---

Limpieza agosto 2026: eliminados residuos de Twilio, DefaultDialer, IVR local y AGI scripts obsoletos. Stack del servidor: manolo_agi.py único AGI.
