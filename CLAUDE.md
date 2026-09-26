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
- **Infraestructura:** VPS Hetzner Ubuntu 24.04 — IP, puerto SSH y rutas en `CLAUDE.local.md`
- **Estilo de trabajo:** directo, una tarea concreta a la vez con resultado verificable
- **Dispositivos de desarrollo/testing:** Pixel 8 (GrapheneOS) — dispositivo principal; Samsung Galaxy Note 20 — dispositivo secundario/simulador de spammer

---

## 2. Descripción del proyecto

Bloqueador de llamadas spam para Android con dos modos de operación:

- **Modo 1 (Answer+Hangup):** la app contesta y cuelga localmente. El spammer no escucha nada.
- **Modo 2 (Backend Fixed):** la llamada se desvía a nivel de red GSM (USSD) al número Zadarma, que la reenvía al VPS. El servidor reproduce un mensaje de voz al spammer usando TTS del Android nativo. El teléfono del usuario no llega a sonar.

Llegar al Modo 2 funcional costó mucho trabajo. Este documento existe para que ese conocimiento no se pierda.

---

## 3. Sistemas de audio — solo queda uno vivo

Tras la limpieza de septiembre 2026 el único audio que oye el spammer lo genera el **servidor**. En la app ya no hay TTS ni IA.

| Sistema                                  | Dónde vive                                                 | Tecnología                                                                                     | Estado                |
| ---------------------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | --------------------- |
| **Audio del servidor** (Modo 2 y Modo 3) | `vps_backend/control_api.py` y `vps_backend/manolo_ari.py` | Modo 2: gTTS + ffmpeg → WAV 8 kHz. Modo 3: Edge TTS `es-ES-AlvaroNeural` → mulaw 8 kHz → RTP  | ✅ en producción      |
| **IVR on-device**                        | `IVRAudioPlayer.java`, `IVRMessageHelper.java`             | `android.speech.tts.TextToSpeech` + `MediaPlayer` sobre `STREAM_VOICE_CALL`                    | ⚠️ residuo, ver abajo |

**Eliminados (sept. 2026):** `IVRGeneratorModule.java`, `SpeechRecognitionModule.java`, `AITestsScreen.tsx`, `ElevenLabsService.ts`, `GeminiServices.ts`.

Como `IVRGeneratorModule` ya no existe, **nadie genera `ivr_corporate.mp3`**: la rama de `CallAccessibilityService` que lo busca (L501) nunca lo encuentra y no hace nada. Los dos ficheros IVR siguen en disco solo porque tienen referencias vivas que romperían el build:

- `IVRAudioPlayer` ← `CallAccessibilityService.java` L44, L513
- `IVRMessageHelper` ← `CallStateReceiver.java` L213 (`handleIdle`, detiene un IVR que ya no arranca nadie)

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

⚠️ **El Accessibility Service es obligatorio, no es un residuo.** Se intentó eliminarlo moviendo la lógica a `CallStateReceiver` y en dispositivo real el resultado fue que **contesta pero no cuelga**. Revertido en `03782bc`. Detalle en la lección 2 (sección 15).

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
│                  *67*919933065#                              │
│                  (desvío → Zadarma; ver nota USSD sección 5) │
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
│         Answer → Stasis(manolo-ari) → Hangup                │
│                                                              │
│  4. manolo_ari.py (ARI + ExternalMedia) lee current_mode.json│
│     ├── FIXED → Playback fixed_spam_message + Hangup        │
│     └── AI    → Manolo conversa [Modo 3, ver sección 6]     │
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
│    └── USSD: ##67#  (cancela desvío en el operador)          │
│  Consultar estado: *#67#                                     │
└──────────────────────────────────────────────────────────────┘
```

### Problema de coordinación (on-device) — resuelto de facto

En Modo 2, `CallAccessibilityService` sigue reaccionando al estado OFFHOOK de forma independiente del servidor y busca `ivr_corporate.mp3`. Desde que se eliminó `IVRGeneratorModule` ese fichero **no se genera nunca**, así que la rama muere en el `if (audioFile.exists())` y el teléfono no reproduce nada. El audio queda en exclusiva del servidor, que es lo que se buscaba.

Sigue siendo deuda cosmética — el código muerto conviene borrarlo — pero ya no puede pisar a Asterisk.

---

## 5. Modos de operación

|                                | Modo 1 (HANGUP_IMMEDIATELY)               | Modo 2 (BACKEND_FIXED)                    | Modo 3 (BACKEND_AI)              |
| ------------------------------ | ----------------------------------------- | ----------------------------------------- | -------------------------------- |
| USSD                           | desactiva (`##67#`)                       | activa (`*67*919933065#`)                 | activa (`*67*919933065#`)        |
| Teléfono suena                 | sí, app lo cuelga                         | no                                        | no                               |
| Spammer escucha                | nada                                      | mensaje fijo (gTTS)                       | Manolo (Groq `qwen/qwen3.8-27b`) |
| Requiere Accessibility Service | ✅ **imprescindible**                     | ❌                                        | ❌                               |
| Requiere VPS                   | ❌                                        | ✅                                        | ✅                               |
| Requiere Zadarma config        | ❌                                        | ✅                                        | ✅                               |
| Sincroniza con la app          | ❌ no lo necesita                         | ⛔ no (5000 cerrado, ver lección 4)       | ⛔ no (5000 cerrado)             |
| Estado actual                  | ✅ verificado en dispositivo (sept. 2026) | ✅ verificado en dispositivo (sept. 2026) | ✅ funcional (ARI)               |

⚠️ **Los códigos USSD reales no son `*21*`.** Desde `b41448d` (marzo 2026) `CallForwardingManager` usa el código **67** y el número **sin prefijo de país**: `*67*919933065#` / `##67#` / `*#67#`. La documentación anterior de este fichero decía `*21*34919933065#`, que no es lo que ejecuta la app. El código GSM estándar de desvío incondicional es `*21*` y `*67*` es desvío en ocupado, pero `*67*` es el que funciona con el operador de Víctor — no cambiarlo sin probar en dispositivo.

---

## 6. Infraestructura del servidor

### Stack real del VPS (verificado en código)

El VPS gestiona las llamadas con **Asterisk**.

| Componente            | Archivo                                    | Puerto/Ruta                    | Estado              |
| --------------------- | ------------------------------------------ | ------------------------------ | ------------------- |
| **Asterisk dialplan** | `vps_backend/extensions.conf`              | contextos `[from-zadarma]` y `[manolo-ari-test]` | ✅ funcional        |
| **Motor Modo 3 (ARI)**| `vps_backend/manolo_ari.py`                | ARI (`127.0.0.1:8088`) + ExternalMedia UDP `127.0.0.1:7000` | ✅ en producción (sept. 2026) |
| **API de control**    | `vps_backend/control_api.py`               | puerto 5000                    | ✅ funcional        |
| **Whisper (fallback STT)** | `vps_backend/whisper_server.py`       | socket Unix `/tmp/whisper.sock` | ✅ activo, fallback |
| **Systemd — control API** | `vps_backend/asterisk-control-api.service` | —                          | instalado           |
| **Systemd — Whisper** | `vps_backend/whisper_server.service`       | —                              | instalado           |
| **Systemd — Manolo ARI** | `vps_backend/manolo_ari.service`        | —                              | instalado y activo  |
| **AGI legacy (backup, sin usar)** | `vps_backend/manolo_agi.py`        | —                               | en disco, no referenciado desde el dialplan |

**Migración septiembre 2026:** `[from-zadarma]` dejó de usar `AGI(manolo_agi.py)` y ahora entrega el canal a `Stasis(manolo-ari)`, atendido por `manolo_ari.py` vía ARI + ExternalMedia (streaming bidireccional UDP/RTP, sin el modelo graba→procesa→reproduce del AGI). `manolo_agi.py` queda en disco como backup, ya no forma parte del flujo de llamadas reales.

**Flujo de decisión en el VPS (producción):**

```
Asterisk [from-zadarma]
  └── Stasis(manolo-ari)
        └── manolo_ari.py (ARI + ExternalMedia)
              ├── Recibe RTP/mulaw por UDP, VAD por energía (barge-in: 800ms de
              │   gracia + 3 frames consecutivos antes de cortar la respuesta)
              ├── STT: Deepgram Nova-2 (batch) — Whisper por socket como fallback
              ├── LLM: Groq `qwen/qwen3.8-27b`, con historial de conversación real
              └── TTS: Edge TTS `es-ES-AlvaroNeural` → mulaw 8kHz → RTP de vuelta
```

**Limitación conocida de `manolo_ari.py`:** puerto UDP fijo (7000) + estado global → una sola llamada concurrente. Pendiente de asignación dinámica de puertos para producción a escala.

**Limpieza agosto 2026 — scripts VPS obsoletos, sustituidos por `manolo_agi.py` en su momento:**

- `agi-bin/decision_agi.py` — **ELIMINADO**
- `victor_agi.py` — **ELIMINADO**
- `deploy.sh` — **ELIMINADO**, usar `deploy_modo3.sh` (despliega `manolo_agi.py` + `extensions.conf`, hoy solo relevante para el backup AGI)

**control_api.py endpoints (Flask):**

⚠️ **Los tres exigen la cabecera `X-API-Key`**, comparada con `CONTROL_API_TOKEN` del `.env` del VPS. Sin token configurado en el servidor, todo devuelve 401 — falla cerrado a propósito.

- `POST /set_mode` — body `{"mode": "FIXED"|"AI"}` → escribe `current_mode.json`
- `GET /get_mode` — devuelve el modo actual
- `GET /call_log` — últimas 50 llamadas atendidas por el servidor, ordenadas por `timestamp_inicio` desc. Las escribe `manolo_ari.py` en `<BRIDGE_DIR>/call_log.json`; las consume `CallHistoryScreen` vía `BackendSyncService.getCallLog()`

**Eliminado (sept. 2026): `POST /set_message`.** Generaba `custom_fixed_message.wav` con gTTS para la UI de mensajes personalizados, que ya no existe. El Modo 2 reproduce siempre `fixed_spam_message`.

**Dónde escucha Flask:** `CONTROL_API_BIND`, por defecto `127.0.0.1`. En producción es el gateway del bridge Docker al que está conectado Nginx Proxy Manager — NPM corre en contenedor y para él `127.0.0.1` es el propio contenedor. El puerto 5000 **no** se expone a Internet: el tráfico entra por 443 vía Cloudflare → NPM.

**Audio del mensaje fijo (Modo 2) — ruta crítica:**

Asterisk resuelve `sound:` contra su **Data directory**, que en este VPS es `/usr/share/asterisk`, **no** `/var/lib/asterisk` (ver lección 1, sección 15):

- `manolo_ari.py` L64: `SOUNDS_DIR = '/usr/share/asterisk/sounds/es'`
- `manolo_ari.py` L65 reproduce siempre `FIXED_SOUND = 'fixed_spam_message'`. El fallback a `custom_fixed_message` se eliminó con la UI de mensajes personalizados: si queda un WAV viejo en el servidor, se ignora
- La llamada a ARI pasa `lang='es'` explícito (`ARIClient.play`, L144). Sin él Asterisk resuelve contra el idioma del canal, que llega como `en` desde Zadarma, y busca en `sounds/en/` — donde el fichero no está

### Servicios en src/services/

Ninguno habla con una IA: toda la inteligencia vive en el VPS.

- **`BackendSyncService.ts`** — cliente HTTP de `control_api.py`: `syncMode()`, `syncMessage()`, `getCallLog()`. Timeout de 5 s y degradación silenciosa (devuelve `[]`, no lanza) para que la app siga usable sin VPS
- **`AnswerHangupService.ts`** — bridge al módulo nativo: modo, delay, permisos, estado del Accessibility Service
- **`CallForwardingService.ts`** — bridge al módulo USSD
- **`BlacklistService.ts`**, **`CallHistoryService.ts`**, **`LogsService.ts`**, **`DataBaseService.ts`** — persistencia local (SQLite / SharedPreferences)
- ⚠️ **`ContactService.ts`** y **`ContactsService.ts`** — dos ficheros distintos, con nombres casi idénticos, **ambos en uso**: `DashboardScreen` importa `contactsService` de `ContactService.ts`, `WhitelistScreen` importa `ContactsService` de `ContactsService.ts`. De ahí el bug del Modo Radical (ver sección 12)

### Configuración externa (fuera del repo — crítica para Modo 2)

Esta configuración no vive en el código. Si se pierde, el Modo 2 deja de funcionar:

| Servicio             | Número/URL         | Configuración                                                                                                                                                                                                                    |
| -------------------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Zadarma**          | ver `CLAUDE.local.md` | Reenvío de entrantes → VPS por SIP. Número, SIP Login y External Server en el fichero local                                                                                                                                    |
| **VPS — SIP**        | ver `CLAUDE.local.md` | Puerto 5060/udp abierto solo a las 6 subnets de Zadarma                                                                                                                                                                       |
| **VPS — control API**| ver `CLAUDE.local.md` | Puerto **5000/tcp CERRADO a propósito**. No abrirlo hasta que `control_api.py` autentique por token: `GET /call_log` expone números de terceros. Mientras tanto la app no sincroniza (ver lección 4, sección 15)               |
| **Deepgram**         | `api.deepgram.com` | $200 crédito gratuito. **STT principal desde sept. 2026** (Nova-2, batch) en `manolo_ari.py` — verificado en prueba real sobre audio del ExternalMedia (RTP/mulaw 8 kHz). Whisper queda como fallback si Deepgram falla           |
| **Groq**             | `api.groq.com`     | LLM de Manolo: `qwen/qwen3.8-27b`. Si la API falla, `manolo_ari.py` responde con un mensaje fijo de emergencia, no hay fallback a otro proveedor                                                                                  |
| **Gemini**           | Google AI Studio   | Ya **no se usa**. Era el fallback de `manolo_agi.py` (20 req/día en free tier). `GeminiServices.ts` eliminado de la app                                                                                                           |

---

## 7. Estructura de archivos

```
Call-Spam-IA-Blocker/
├── android/app/src/main/
│   ├── AndroidManifest.xml                 ← limpio de services residuales; quedan 6 permisos de más (sección 12)
│   └── java/com/anonymous/SpamBlockerApp/
│       │
│       │── ACTIVOS
│       ├── CallAccessibilityService.java   ← CORE Modo 1: detecta spam, contesta y cuelga. NO se puede eliminar
│       ├── CallScreeningServiceImpl.java   ← primer filtro + notificación de bloqueo (Modo 1)
│       ├── CallInterceptorService.java     ← foreground service
│       ├── AnswerHangupHelper.java         ← lógica Answer+Hangup + normalización ES (L222-225)
│       ├── AnswerHangupModule.java         ← bridge RN↔Java
│       ├── CallForwardingManager.java      ← gestión USSD, Zadarma hardcodeado (L34-39)
│       ├── CallForwardingModule.java       ← bridge RN↔Java para desvío
│       ├── CallStateReceiver.java          ← receiver de PHONE_STATE. Detecta, pero NO consigue colgar (lección 2)
│       ├── SpamNotificationManager.java    ← notificación local al bloquear en Modo 1
│       ├── BlacklistModule.java
│       ├── CallHistoryHelper.java
│       ├── CallHistoryModule.java
│       ├── CallInterceptorModule.java
│       ├── CallInterceptorPackage.java
│       ├── CallAnswerReceiver.java
│       ├── ContactsModule.java
│       ├── ContactsHelper.java
│       ├── SharedPreferencesHelper.java
│       ├── LogsModule.java
│       ├── LogsHelper.java
│       ├── MainActivity.kt
│       └── MainApplication.kt
│       │
│       │── RESIDUOS IVR — ⚠️ no borrar sin limpiar las referencias (sección 3)
│       ├── IVRAudioPlayer.java             ← referenciado en CallAccessibilityService L44, L513
│       └── IVRMessageHelper.java           ← referenciado en CallStateReceiver L213
│                                           ← ELIMINADOS (sept. 2026): IVRGeneratorModule.java, SpeechRecognitionModule.java
│
├── src/
│   ├── screens/
│   │   ├── DashboardScreen.tsx
│   │   ├── SpamNumbersScreen.tsx
│   │   ├── WhitelistScreen.tsx
│   │   ├── LogsScreen.tsx
│   │   ├── CallHistoryScreen.tsx           ← historial local (Modo 1) + servidor (Modos 2/3) fusionados
│   │   └── AnswerHangupSettingsScreen.tsx  ← selector de modo; sincroniza mensaje fijo al pasar a Modo 2
│   │                                       ← ELIMINADA (sept. 2026): AITestsScreen.tsx
│   └── services/
│       ├── BackendSyncService.ts           ← cliente de control_api.py (modo, mensaje, call_log)
│       ├── AnswerHangupService.ts
│       ├── CallForwardingService.ts
│       ├── BlacklistService.ts
│       ├── CallHistoryService.ts
│       ├── LogsService.ts
│       ├── DataBaseService.ts
│       ├── ContactService.ts               ← ⚠️ usado por DashboardScreen
│       └── ContactsService.ts              ← ⚠️ usado por WhitelistScreen (ver sección 12)
│                                           ← ELIMINADOS (sept. 2026): GeminiServices.ts, ElevenLabsService.ts
│
├── vps_backend/                            ← infraestructura del servidor
│   ├── extensions.conf                     ← dialplan Asterisk [from-zadarma] + [manolo-ari-test] ✅
│   ├── manolo_ari.py                       ← motor Modos 2 y 3 en producción: ARI + ExternalMedia ✅
│   ├── manolo_ari.service                  ← systemd unit para manolo_ari.py
│   ├── manolo_agi.py                       ← AGI legacy, backup en disco, ya no usado (sept. 2026)
│   ├── control_api.py                      ← Flask API puerto 5000 ✅
│   ├── asterisk-control-api.service        ← systemd unit para control_api
│   ├── whisper_server.py                   ← servicio persistente Whisper (fallback STT) ✅
│   ├── whisper_server.service              ← systemd unit para whisper_server
│   ├── deploy_modo3.sh                     ← script de deploy del AGI legacy (histórico)
│   ├── install_service.sh                  ← registra systemd service
│   └── README_VPS.md
│                                           ← ELIMINADOS (agosto 2026): agi-bin/decision_agi.py, victor_agi.py, deploy.sh
│
├── App.tsx
├── index.ts
├── package.json
├── tsconfig.json
├── app.json
├── build-fresh.sh                          ← build limpio (borra .cxx) + copia el APK
└── copy-apk.sh                             ← copia el APK a ~/Downloads
                                            ← ELIMINADO (sept. 2026): rebuild-clean.sh (redundante con build-fresh.sh)
```

---

## 8. Variables de entorno

Solo quedan dos. Las claves de IA (Groq, Deepgram) viven en el `.env` **del VPS**, nunca en la app.

```env
# URL pública de control_api.py (Cloudflare → NPM → Flask). https obligatorio:
# en release Android bloquea el tráfico en claro.
EXPO_PUBLIC_VPS_URL=https://tu-subdominio.tu-dominio.com

# Debe coincidir con CONTROL_API_TOKEN del .env del VPS
EXPO_PUBLIC_CONTROL_API_TOKEN=
```

En el `.env` **del VPS** hacen falta además `CONTROL_API_TOKEN` y `CONTROL_API_BIND`.

⚠️ **El token acaba dentro del APK.** `EXPO_PUBLIC_*` se incrusta en el bundle en tiempo de compilación, así que cualquiera con el APK puede extraerlo — es el mismo mecanismo por el que las claves de Gemini y ElevenLabs acabaron en el historial de git. Aceptable mientras la app no se distribuya: protege contra escaneo de Internet, no contra quien tenga el APK. La solución definitiva está en el punto 2 del roadmap.

**Eliminadas (sept. 2026):** `EXPO_PUBLIC_GEMINI_API_KEY`, `EXPO_PUBLIC_GEMINI_MODEL`, `EXPO_PUBLIC_ELEVENLABS_API_KEY` — sus consumidores (`GeminiServices.ts`, `ElevenLabsService.ts`) ya no existen. `.env.example` está actualizado.

**Estado del `.env`:** verificado que no está commiteado (`git log --all` no lo muestra). Solo aparece `ios/.xcode.env`, que es irrelevante.

⚠️ El prefijo `EXPO_PUBLIC_*` embebe estos valores en el APK compilado.

---

## 9. Comandos de desarrollo

```bash
# Instalar dependencias
npm install

# Build debug Android — usar SIEMPRE build-fresh.sh
# Borra los .cxx de android/app y de node_modules/*/android antes de compilar
# (las cachés CMake obsoletas rompen el build con fbjni::fbjni) y copia el APK
./build-fresh.sh

# Copiar el APK a ~/Downloads por separado
./copy-apk.sh

# Instalar APK en dispositivo
adb install android/app/build/outputs/apk/debug/app-debug.apk

# Verificar tipos TypeScript — ejecutar tras CADA cambio TS
npx tsc --noEmit

# Logs del dispositivo en tiempo real
adb logcat | grep -E "SpamBlocker|CallAccessibility|AnswerHangup|CallForwarding|CallStateReceiver|CallScreening"
adb logcat *:E

# adb inalámbrico (el Pixel 8 se usa sin cable)
adb pair <ip>:<puerto-de-emparejamiento>    # el código lo muestra el teléfono
adb connect <ip>:<puerto-de-depuración>      # puerto distinto al de emparejamiento

# Verificar estado del desvío USSD en el operador
# Marcar desde el teclado del teléfono: *#67#

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

6. **Nunca borrar un `.java` sin verificar referencias en todo el proyecto.** Casos vivos: `IVRAudioPlayer` en `CallAccessibilityService.java` (L44, L513) e `IVRMessageHelper` en `CallStateReceiver.java` (L213). Borrarlos sin limpiar esas referencias rompe el build.

7. **No tocar `CallAccessibilityService`.** Es la única capa que consigue contestar y colgar en Modo 1. Ya se intentó sustituirlo por `CallStateReceiver` y falló en dispositivo real — lección 2, sección 15.

8. **Todo cambio en la capa de llamadas se verifica en dispositivo real.** `npx tsc --noEmit` y que compile no dicen nada sobre si la llamada se cuelga. La prueba es una llamada de verdad desde el Note 20.

9. **Ignorar los READMEs de planificación.** El código manda.

10. **Conventional Commits** cuando Víctor pida commitear:
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

### App Android

| Valor                  | Archivo                       | Línea     | Notas                                        |
| ---------------------- | ----------------------------- | --------- | -------------------------------------------- |
| `"919933065"`          | CallForwardingManager.java    | L34       | Número Zadarma, **sin prefijo de país**      |
| `*67*919933065#`       | CallForwardingManager.java    | L37       | USSD activar desvío (**67**, no 21)          |
| `##67#`                | CallForwardingManager.java    | L38       | USSD desactivar                              |
| `*#67#`                | CallForwardingManager.java    | L39       | USSD consultar estado                        |
| `"/ivr_corporate.mp3"` | CallAccessibilityService.java | L501      | Ruta MP3 on-device — ya nadie genera el fichero |
| últimos 9 dígitos      | AnswerHangupHelper.java       | L222-225  | Normalización España                         |

### VPS

| Valor                                | Archivo        | Línea | Notas                                       |
| ------------------------------------ | -------------- | ----- | ------------------------------------------- |
| `<BRIDGE_DIR>/current_mode.json`     | manolo_ari.py  | L63   | Estado del modo, lo escribe `control_api.py` |
| `/usr/share/asterisk/sounds/es`      | manolo_ari.py  | L64   | Data directory de Asterisk                  |
| `fixed_spam_message`                 | manolo_ari.py  | L65   | Único mensaje del Modo 2                    |
| `20` (s)                             | manolo_ari.py  | L67   | Timeout esperando `PlaybackFinished`        |
| `1500`                               | manolo_ari.py  | L55   | Umbral RMS del VAD (barge-in)               |
| `7000`                               | manolo_ari.py  | —     | Puerto UDP de ExternalMedia, fijo           |

---

## 12. Deuda técnica real (verificada en código)

### Crítica

- [ ] **Una sola llamada concurrente en `manolo_ari.py`.** Puerto UDP fijo (7000) + estado global. Con dos llamadas simultáneas la segunda pisa a la primera. Es el bloqueante real para cualquier uso más allá del propio Víctor.

- [ ] **Estado del Modo Radical leído de dos sitios distintos.** `WhitelistScreen` lo lee y escribe con `ContactsService.ts`; `DashboardScreen` usa `ContactService.ts`. La fuente autoritativa para la capa nativa es SharedPreferences, y el Dashboard puede mostrar un estado que no es el que aplica el servicio. Unificar en un único servicio.

### Importante

- [ ] **Autenticación de `control_api.py`: implementada en el repo, sin desplegar.** El código ya exige `X-API-Key` en los tres endpoints, pero falta la infraestructura: registro DNS en Cloudflare, Proxy Host en NPM, `CONTROL_API_TOKEN` y `CONTROL_API_BIND` en el `.env` del VPS, y el token en el `.env` de la app. Hasta que eso esté, la app sigue sin sincronizar. Punto 1 del roadmap.

- [ ] **Residuos IVR on-device:** `IVRAudioPlayer.java` (referenciado en `CallAccessibilityService` L44, L513) e `IVRMessageHelper.java` (referenciado en `CallStateReceiver` L213). Ya no pueden reproducir nada porque el MP3 no se genera, pero siguen compilando. Borrarlos exige limpiar esas 3 referencias primero.

- [ ] **6 permisos residuales en `AndroidManifest.xml`:** `BIND_INCALL_SERVICE`, `BIND_TELECOM_CONNECTION_SERVICE`, `CONTROL_INCALL_EXPERIENCE`, `MODIFY_AUDIO_SETTINGS`, `MODIFY_PHONE_STATE`, `MODIFY_AUDIO_ROUTING`. Los services que los usaban (`SpamCallService`, `DialerActivity`, `InCallActivity`) ya están fuera del manifest — los permisos siguen asustando al usuario sin aportar nada.

- [ ] **Sin tests** en ninguna capa.

- [ ] **Latencia por turno sin instrumentar.** El paso de AGI a ARI se hizo para bajarla, pero nunca se midió con timestamps reales. Sin cifras no se puede afirmar que el streaming mejoró nada.

- [ ] **`scripts/check-secrets.sh` no es fiable como gate.** Marca como "posible secreto" el propio texto de este CLAUDE.md que describe el problema, además de referencias a `process.env.EXPO_PUBLIC_*` y nombres de variable. Y falla con `/dev/tty: No such device or address` en entornos no interactivos (CI, agentes), cayendo a modo advertencia sin bloquear.

- [ ] **"Borrar Todo" en `CallHistoryScreen` solo limpia el historial local.** Las llamadas del servidor (`call_log.json`) reaparecen al siguiente refresco, porque no hay endpoint para borrarlas.

### Resueltos en esta sesión (sept. 2026)

- [x] **Modo 2 reproducía a Manolo en lugar del mensaje fijo.** `manolo_ari.py` no leía `current_mode.json`: la rama FIXED nunca se había portado del AGI. Añadidos `get_current_mode()`, `elegir_sonido_fijo()` y `run_fixed()`.
- [x] **El mensaje fijo no se encontraba** — ruta y idioma equivocados. Ver lección 1.
- [x] **Modo FIXED no colgaba hasta el timeout** — interbloqueo del bucle de eventos ARI. Ver lección 3.
- [x] **Referencias a `IVRAudioPlayer` documentadas y acotadas** — dejan de ser un riesgo ciego para refactorizar.
- [x] **Modo 3 funcional con ARI.** Groq `qwen/qwen3.8-27b` con historial real (antes no se enviaba a la API), Edge TTS `es-ES-AlvaroNeural`, Deepgram Nova-2 con Whisper de fallback, barge-in con 800 ms de gracia + 3 frames consecutivos.
- [x] **Builds rotos por cachés CMake obsoletas.** `build-fresh.sh` borra los `.cxx` de `android/app` y de `node_modules/*/android/`.

### Menor

- [ ] Typo en nombre del repo: `Bolcker` → `Blocker`

---

## 13. Roadmap

En orden de prioridad lógica:

1. **Exponer `control_api.py` por HTTPS y desplegar la autenticación.** El código del repo ya está hecho (`X-API-Key` en los tres endpoints, `CONTROL_API_BIND`). Falta la infraestructura, y es **prerequisito para que la app sincronice**:

   - Registro `A` en Cloudflare para el subdominio, con **proxy naranja activado** (así el origen no queda expuesto por DNS)
   - Proxy Host en la UI de **Nginx Proxy Manager** — no un `.conf`: NPM corre en Docker. Scheme `http`, Forward a la IP del gateway del bridge Docker de NPM, puerto 5000, Force SSL on, certificado **Cloudflare Origin** (no Let's Encrypt), SSL/TLS de Cloudflare en **Full (strict)**
   - `CONTROL_API_TOKEN` y `CONTROL_API_BIND` en el `.env` del VPS. El bind es el gateway del bridge, **no** `127.0.0.1`: para un contenedor, loopback es el propio contenedor
   - Rate limiting en **Cloudflare**, no en Nginx: detrás del proxy, `$binary_remote_addr` son IPs de Cloudflare. Además `limit_req_zone` no cabe en la pestaña Advanced de NPM, que inyecta en el bloque `server` y no en `http`
   - El token en el `.env` de la app, y rebuild

   **`ufw allow 5000/tcp` no se ejecuta en ningún momento.** El tráfico entra por 443.

2. **Token fuera del APK (`EncryptedSharedPreferences`).** Hoy `EXPO_PUBLIC_CONTROL_API_TOKEN` se incrusta en el bundle y es extraíble de cualquier APK. La alternativa: un campo en Ajustes donde se pega el token una vez, guardado cifrado en el dispositivo vía módulo nativo. El token deja de estar en el APK y en el repo, y rotarlo no exige recompilar. Coste estimado: un `TextInput`, dos métodos en Java y leerlo desde `BackendSyncService`.

3. **Asignación dinámica de puertos UDP en `manolo_ari.py`.** Hoy puerto fijo 7000 + estado global = una sola llamada concurrente. Es el techo real del sistema.

4. **Medir latencia por turno con timestamps reales.** Instrumentar recepción de audio → STT → LLM → TTS → primer paquete RTP de vuelta. Sin esto no se sabe si el cambio a streaming sirvió.

5. **Unificar `ContactService.ts` y `ContactsService.ts`** y dejar una sola fuente para el Modo Radical.

6. **Limpiar residuos:** quitar las 3 referencias a `IVRAudioPlayer`/`IVRMessageHelper`, borrar los dos ficheros, y después los 6 permisos del manifest.

7. **Arreglar `scripts/check-secrets.sh`** o retirarlo: un gate que siempre avisa y nunca bloquea no es un gate.

8. ~~**Modo 3 — Agente IA conversacional**~~ ✅ **Completado.** Primero con `manolo_agi.py`, y desde sept. 2026 con `manolo_ari.py` (ARI + ExternalMedia, streaming).

9. ~~**Resolver coordinación Modo 2**~~ ✅ **Resuelto de facto** al eliminar `IVRGeneratorModule`: el teléfono ya no puede reproducir audio en Modo 2 (sección 4).

10. ~~**Documentar configuración Zadarma**~~ ✅ **Hecho**, en `CLAUDE.local.md` (sección 14).

---

## 14. Infraestructura VPS — detalle técnico

⚠️ **Los datos reales están en `CLAUDE.local.md`, que no se versiona.** El repositorio es
público: IP del VPS, puerto SSH, login SIP de Zadarma, `pjsip.conf`, las subnets de UFW y
las rutas de despliegue se quedan fuera de git a propósito.

`CLAUDE.local.md` contiene:

- Configuración Zadarma → Asterisk (número, SIP Login, External Server)
- `pjsip.conf` completo: endpoint, aor e identify con las 6 subnets
- Reglas UFW: las 6 subnets en 5060/udp, y por qué 5000/tcp sigue cerrado
- Los bugs de configuración resueltos, con rutas reales
- Comandos de mantenimiento: logs, estado de servicios, cambio de modo
- Rutas de despliegue en el VPS

Si no lo tienes en tu copia, pídeselo a Víctor: sin esos datos no se puede tocar el VPS, y
no se reconstruyen leyendo el código.

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

## 15. Lecciones aprendidas — trampas ya pisadas

Cuatro fallos que costaron una sesión entera cada uno. Si algo de Modo 1 o Modo 2 se rompe, empezar por aquí.

### Lección 1 — Asterisk busca los sonidos en `/usr/share/asterisk`, no en `/var/lib/asterisk`

**Síntoma:** `sound:fixed_spam_message` daba "File does not exist" aunque el WAV estaba en `/var/lib/asterisk/sounds/es/`.

**Causa:** el *Data directory* de esta instalación es `/usr/share/asterisk` — el mismo árbol donde vive `agi-bin/`. Todo lo que Asterisk resuelve por nombre lógico (`sound:`) se busca ahí.

**Y un segundo nivel:** con la ruta ya correcta seguía fallando, porque `sound:` se resuelve **por idioma del canal** y el canal de Zadarma llega con `language=en` → Asterisk buscaba en `sounds/en/`. Hay que pasar `lang='es'` explícito en `POST /channels/{id}/play`.

**Regla:** al añadir audio, `ls` en `/usr/share/asterisk/sounds/es/` y pasar `lang` siempre. Nunca fiarse del idioma del canal.

### Lección 2 — El Accessibility Service no es eliminable

**Síntoma:** tras quitar `CallAccessibilityService` y mover la lógica a `CallStateReceiver`, en dispositivo real la app **detectaba el spam pero no contestaba ni colgaba**.

**Causa:** `CallStateReceiver` está declarado en el manifest, y Android crea **una instancia nueva por cada broadcast**. Los campos de instancia (`lastIncomingNumber`, `wasRingingBeforeOffhook`) no sobreviven de RINGING a OFFHOOK, y `EXTRA_INCOMING_NUMBER` solo viaja en RINGING. Al llegar OFFHOOK el receiver no sabe a qué número pertenece la llamada, así que no cuelga. `CallScreeningService`, por su parte, **no puede contestar** una llamada por diseño.

**Regla:** Modo 1 depende del Accessibility Service. Si se vuelve a intentar quitarlo, el estado entre broadcasts tiene que persistirse (SharedPreferences), y aun así hay que **probarlo en dispositivo real** antes de dar nada por bueno. Revertido en `03782bc`.

### Lección 3 — No hacer `await` de un handler largo dentro del bucle de eventos ARI

**Síntoma:** en Modo 2 el mensaje fijo se oía completo, pero la llamada no colgaba hasta agotar el timeout de 20 s.

**Causa:** `run_ari_events()` despacha con `await handler(event)` **dentro** de `async for raw in ws`. `run_fixed()` esperaba el evento `PlaybackFinished`… que no podía leerse, porque el bucle que lo lee estaba suspendido dentro del propio handler. Interbloqueo clásico: el handler espera un evento que solo llegará cuando el handler termine.

**Solución:** lanzar `run_fixed()` con `asyncio.create_task()` y guardar una referencia fuerte contra el GC.

**Matiz importante:** **no** convertir todo el dispatcher a `create_task`. El `StasisStart` del canal `UnicastRTP/` lee `call_state.bridge_id` que escribe el `StasisStart` del canal entrante; hoy funciona porque los handlers corren en orden. Hacerlos todos concurrentes provocaría una carrera justo en el camino de Modo 3, que sí funciona.

### Lección 4 — Un servicio arriba no es un servicio alcanzable (y abrir el puerto no es la solución)

**Síntoma:** `control_api.py` sano — `systemctl status` OK, `curl` local OK — pero la app no cambia de modo ni sincroniza. Sin error visible en pantalla.

**Causa:** el puerto 5000/tcp no está abierto en UFW, así que las peticiones del móvil se descartan antes de llegar a Flask. Y `BackendSyncService` degrada en silencio a propósito (timeout de 5 s, devuelve `[]` en vez de lanzar), que es lo correcto para la UX pero esconde el fallo.

**Lo que NO hay que hacer: `ufw allow 5000/tcp`.** Ningún endpoint de `control_api.py` valida nada — ni token, ni IP de origen, ni cabecera. Abrir el puerto expone a Internet:

- `POST /set_mode` — cualquiera cambia el modo de operación
- `POST /set_message` — cualquiera hace que el servidor genere audio arbitrario
- `GET /call_log` — **los números de quien ha llamado**, datos personales de terceros

**Estado actual (sept. 2026): el puerto sigue cerrado, deliberadamente.** Consecuencias que hay que conocer antes de diagnosticar nada:

- El modo se cambia desde el propio VPS con `curl` a `127.0.0.1:5000`, no desde la app
- `CallHistoryScreen` solo muestra el historial local del Modo 1; `getCallLog()` devuelve `[]` por timeout
- **Los Modos 2 y 3 funcionan igual de bien:** la llamada entra por SIP en el 5060, que sí está abierto. El 5000 solo afecta al control desde la app

**Solución real:** autenticar `control_api.py` con `X-API-Key` y exponerlo por HTTPS — **sin abrir el 5000 nunca**. El tráfico entra por 443 vía Cloudflare → Nginx Proxy Manager → Flask en el bridge Docker. El código del repo ya lo exige; falta la infraestructura. Punto 1 del roadmap.

**Y hay un segundo bloqueante, independiente del firewall:** la exención de tráfico en claro vive en `android/app/src/debug/AndroidManifest.xml`, solo en el source set de debug. Con `targetSdkVersion 34`, un build de release bloquea cualquier `http://`. Aunque el puerto estuviera abierto, la app en release no habría podido hablar con el servidor. Por eso la solución es HTTPS y no "abrir el puerto".

**Regla de diagnóstico que sí se generaliza:** "el servicio está arriba" no significa "el servicio es alcanzable". Comprobarlo desde **fuera** del host, no con un `curl` local:

```bash
curl -H "X-API-Key: $TOKEN" https://tu-subdominio.tu-dominio.com/get_mode
```

Si responde en local pero no desde fuera, es la red — y antes de abrir nada, mirar qué quedaría expuesto.

---

## 16. Ramas del repositorio

`dev` es la rama por defecto en GitHub (verificado con `git ls-remote --symref origin HEAD`, sept. 2026). `master` está abandonada desde agosto 2025 y contenida en `dev` — se conserva solo como archivo histórico.

Limpieza de ramas (sept. 2026): borradas `refactor/ui-cleanup-sept-2026`, `refactor/cleanup-agosto-2026`, `claude/fix-call-permissions-…` y `claude/ivr-bcp-method-…`, todas mergeadas en `dev` y sin commits propios.

### ⚠️ `claude/modo1-optimized-01KAWsdwPsM88H55dEWN8Xqf` — no borrar

Última actividad: 31 diciembre 2025 (`aec0dd2`). **No está mergeada:** tiene 17 commits que nunca llegaron a `dev`, y `dev` le lleva 96. Se conserva porque hay trabajo aprovechable dentro.

**Features rescatables vía cherry-pick** (verificado que no existen en `dev`: 0 ficheros con `CustomTabs`, 0 con `spamScore`):

| Feature                                                     | Commits                  |
| ----------------------------------------------------------- | ------------------------ |
| Lookup del número en webs de spam con Chrome Custom Tabs    | `f095802`, `804edd5`     |
| Detección local de spam para España (regulación 2025)       | `f697767`                |
| Score y categoría de spam en historial y notificaciones     | `2bef944`, `b91e23f`     |
| Almacenamiento dual al añadir a blacklist desde el historial | `40491d1`                |

**Commits que NO hay que traer — son incorrectos a día de hoy:**

- `86d0c32` "docs: Update README for v0.1 - Clarify Accessibility NOT required" — **falso**. La accesibilidad sí es imprescindible en Modo 1, ver lección 2 (sección 15).
- `79ece8a` "refactor: Remove Modo 2/3 (IVR/AI) from CallStateReceiver" y `f9a5d9a` "Remove Modo 2/3 (IVR/AI) from Answer+Hangup UI" — la rama es de diciembre 2025, anterior a que Modo 2 y Modo 3 existieran de verdad. Arrancarían lo que hoy es producción.
- `aec0dd2` "chore: Remove unused LogsService.ts" — en `dev` ese servicio sigue en uso.

**Cómo rescatar:** rama nueva desde `dev` y cherry-pick commit a commit de la tabla de arriba. **Nunca `git merge`** de esta rama: toca `CallStateReceiver`, la UI de Answer+Hangup y borra ficheros vivos.

---

Limpieza agosto 2026: eliminados residuos de Twilio, DefaultDialer, IVR local y AGI scripts obsoletos. Stack del servidor en ese momento: manolo_agi.py único AGI.

Migración septiembre 2026: `[from-zadarma]` pasa de `AGI(manolo_agi.py)` a `Stasis(manolo-ari)`. Motor Modo 3 en producción: `manolo_ari.py` (ARI + ExternalMedia) + Deepgram Nova-2 + Groq `qwen/qwen3.8-27b` + Edge TTS. `manolo_agi.py` y Whisper quedan como backup/fallback, no eliminados.

Sesión 26 septiembre 2026: `manolo_ari.py` asume también el Modo 2 (`run_fixed()` leyendo `current_mode.json`). Limpieza de UI en `refactor/ui-cleanup-sept-2026`: fuera `AITestsScreen`, `ElevenLabsService`, `GeminiServices`, `IVRGeneratorModule`, `SpeechRecognitionModule`; historial del servidor fusionado en `CallHistoryScreen`. Modos 1 y 2 verificados en dispositivo real. Documentadas las 4 lecciones de la sección 15.
