# CLAUDE.md — Call-Spam-IA-Blocker

> Léelo completo antes de tocar cualquier archivo. Repositorio: https://github.com/Bitxogm/Call-Spam-IA-Bolcker (branch activo: `dev`).
> La fuente de verdad es el código: si este fichero discrepa, manda el código y esto se corrige.
> Historia, bugs resueltos y por qué se hizo cada cosa: `HISTORIAL.md` (no se carga solo).

## 1. Qué es

Bloqueador de llamadas spam para Android con tres modos. El único audio que oye el spammer lo genera el **servidor** (`vps_backend/manolo_ari.py`); en la app no hay TTS ni IA.

|                                | Modo 1 (HANGUP_IMMEDIATELY) | Modo 2 (BACKEND_FIXED)           | Modo 3 (BACKEND_AI)              |
| ------------------------------ | --------------------------- | -------------------------------- | -------------------------------- |
| USSD                           | desactiva (`##67#`)         | activa (`*67*919933065#`)        | activa (`*67*919933065#`)        |
| Teléfono suena                 | sí, la app contesta y cuelga | no                              | no                               |
| Spammer escucha                | nada                        | WAV fijo (`fixed_spam_message`)  | Manolo (Groq `qwen/qwen3.8-27b`) |
| Requiere Accessibility Service | ✅ **imprescindible**        | ❌                               | ❌                               |
| Requiere VPS y Zadarma         | ❌                          | ✅                               | ✅                               |
| Sincroniza con la app          | ❌ no lo necesita            | ✅ vía HTTPS (sección 3)         | ✅ vía HTTPS                     |
| Estado                         | ✅ verificado en dispositivo | ✅ verificado en dispositivo     | ✅ verificado, 200-400 ms/turno  |

## 2. Arquitectura

**Modo 1 (on-device):** `CallAccessibilityService` detecta spam → `TelecomManager.acceptRingingCall()` → espera 1-5 s → `endCall()`.
⚠️ **El Accessibility Service es obligatorio, no un residuo.** Sin él contesta pero no cuelga (revertido en `03782bc`). Lección 2 en `.claude/rules/android-calls.md`.

**Modos 2 y 3 (desvío GSM + Asterisk):**

- **Activar, una vez desde la UI:** `CallForwardingService.configureForMode()` pide `CALL_PHONE` si falta → `CallForwardingModule` → `CallForwardingManager.enableForwarding()` → USSD vía `TelecomManager.placeCall()`.
- **Por llamada:** el operador desvía a Zadarma y el teléfono no suena → Zadarma reenvía al VPS por SIP (panel web, fuera del repo) → `extensions.conf` `[from-zadarma]`: `Answer → Stasis(manolo-ari) → Hangup` → `manolo_ari.py` lee `current_mode.json`: FIXED reproduce `fixed_spam_message` y cuelga; AI pasa a Manolo.
- **Cambiar de modo:** `AnswerHangupSettingsScreen` → `BackendSyncService.ts` → `POST /set_mode` en `control_api.py`.
- **Desactivar:** USSD `##67#`. Consultar estado: `*#67#`.

⚠️ **USSD con código 67 y el número sin prefijo de país.** El desvío incondicional estándar es `*21*` (`*67*` es en ocupado), pero `*67*` es el que funciona con el operador de Víctor: no cambiarlo sin probar en dispositivo.

## 3. Servidor (VPS)

```
Asterisk [from-zadarma]
  └── Stasis(manolo-ari)
        └── manolo_ari.py (ARI 127.0.0.1:8088 + ExternalMedia UDP 127.0.0.1:7000)
              ├── RTP/mulaw por UDP, VAD por energía (barge-in: 800 ms de gracia + 3 frames)
              ├── STT: Deepgram Nova-2 (batch)
              ├── LLM: Groq `qwen/qwen3.8-27b`, con historial de conversación real
              └── TTS: Edge TTS (voz `TTS_VOICE` del .env) → mulaw 8 kHz → RTP de vuelta
```

- **Plan B del Modo 3** (si cae TTS, Groq o Deepgram) y **healthcheck** cada 6 h con aviso por email: `.claude/rules/vps-backend.md`.
- **API de control:** `control_api.py` en `https://manolo.bitxodev.com` (Cloudflare → NPM → Flask en el bridge Docker). El 5000 **no** se abre a Internet. Endpoints, exposición y lección 4: `.claude/rules/vps-backend.md`.
- **Mensaje fijo:** `/usr/share/asterisk/sounds/es` y `lang='es'` explícito: `.claude/rules/vps-backend.md`.
- **Legacy en disco, sin uso:** `manolo_agi.py` (el dialplan ya no lo referencia) y `whisper_server.py` (parado y deshabilitado; `manolo_ari.py` no lo menciona). Se conservan por si se recablean.
- ⚠️ Degradación silenciosa de `BackendSyncService.ts` y el lío `ContactService.ts`/`ContactsService.ts`: `.claude/rules/services.md`.

**Configuración externa (fuera del repo; si se pierde, los Modos 2/3 dejan de funcionar):**

| Servicio | Dónde | Qué |
| --- | --- | --- |
| **Zadarma** | `CLAUDE.local.md` | Reenvío de entrantes → VPS por SIP. Número, SIP Login y External Server |
| **VPS — SIP** | `CLAUDE.local.md` | 5060/udp abierto solo a las 6 subnets de Zadarma |
| **VPS — API** | `CLAUDE.local.md` | Proxy Host de NPM, regla UFW del bridge, `CONTROL_API_BIND` |
| **Deepgram** | `api.deepgram.com` | Único STT, verificado sobre el audio real del ExternalMedia (RTP/mulaw 8 kHz). $200 de crédito gratuito. Sin proveedor alternativo: 2 errores seguidos → plan B |
| **Edge TTS** | `speech.platform.bing.com` | Voz de Manolo, sin clave. Se rompe cuando Microsoft cambia el protocolo: `pip install -U edge-tts` en el venv. Plan B: mensaje fijo |
| **Groq** | `api.groq.com` | LLM de Manolo. Si falla, frase de emergencia. Sin proveedor alternativo |

**Datos reales** (IP, puerto y usuario SSH, login SIP, `pjsip.conf`, subnets UFW, rutas de despliegue): `CLAUDE.local.md`, que **no se versiona** porque el repo es público. Backup en el repo privado `claude-config`, ruta `projects/call-spam-ia-blocker/`.

## 4. Variables de entorno (sin valores)

**App** (`.env` local; las claves de IA nunca van aquí):

| Variable | Para qué |
| --- | --- |
| `EXPO_PUBLIC_VPS_URL` | URL pública de `control_api.py`. `https` obligatorio: el release de Android bloquea el tráfico en claro |
| `EXPO_PUBLIC_CONTROL_API_TOKEN` | Igual que `CONTROL_API_TOKEN` del VPS |

⚠️ **El token acaba dentro del APK:** `EXPO_PUBLIC_*` se incrusta en el bundle al compilar, y cualquiera con el APK puede sacarlo (así acabaron las claves de Gemini y ElevenLabs en el historial de git). Aceptable mientras la app no se distribuya. Solución: punto 2 del roadmap. Rotación: `docs/runbooks/rotar-token.md`.

**VPS** (`<BRIDGE_DIR>/.env`, `chmod 600`):

| Variable | Para qué | Si falta |
| --- | --- | --- |
| `ARI_USER` (defecto `manolo`), `ARI_PASSWORD` | `manolo_ari.py` | no atiende ninguna llamada de los Modos 2 y 3 |
| `GROQ_API_KEY`, `DEEPGRAM_API_KEY` | `manolo_ari.py` | Modo 3 en plan B (frase de emergencia / mensaje fijo) |
| `CONTROL_API_TOKEN`, `CONTROL_API_BIND` | `control_api.py` | todo 401 / Flask en loopback |
| `TTS_VOICE`, `TTS_RATE`, `TTS_PITCH` | voz de Manolo (Edge TTS) | `es-ES-AlvaroNeural`, `-8%`, `-4Hz` |
| `ALERT_EMAIL_FROM`, `ALERT_EMAIL_TO` | remitente y destino del healthcheck | no hay email (no guarda el estado y reintenta) |
| `ALERT_EMAIL_PASSWORD` | contraseña de aplicación de Google, sin espacios (`docs/runbooks/healthcheck-email.md`) | ídem |

Un `TTS_VOICE` mal escrito hace caer **todas** las llamadas del Modo 3 al mensaje fijo, y el email sugiere actualizar `edge-tts` aunque el fallo sea el nombre: revisar la variable primero.

## 5. Comandos

```bash
npm install

# Build debug: SIEMPRE build-fresh.sh (borra las cachés CMake .cxx que rompen fbjni)
./build-fresh.sh

# Copiar el APK a ~/Downloads con fecha
./copy-apk.sh <descripcion>

# Instalar por adb (sin adb: pasar el APK al teléfono y abrirlo)
adb install android/app/build/outputs/apk/debug/app-debug.apk

# Tipos: tras CADA cambio TS
npx tsc --noEmit

# Tests de la app (Vitest) y del backend (unittest; simula las dependencias del VPS)
npm test
python3 -m unittest discover -s vps_backend/tests -v

# Logs del dispositivo
adb logcat | grep -E "SpamBlocker|CallAccessibility|AnswerHangup|CallForwarding|CallStateReceiver|CallScreening"
adb logcat *:E

# adb inalámbrico
adb pair <ip>:<puerto-de-emparejamiento>    # el código lo muestra el teléfono
adb connect <ip>:<puerto-de-depuración>      # distinto del de emparejamiento
```

Estado del desvío en el operador: marcar `*#67#` en el teclado del teléfono.

**Runbooks:** desplegar en el VPS → `docs/runbooks/despliegue-vps.md` · probar el plan B y el email → `docs/runbooks/pruebas-fallback.md` · contraseña de Gmail → `docs/runbooks/healthcheck-email.md` · rotar el token → `docs/runbooks/rotar-token.md`.

## 6. Reglas de trabajo (no negociables)

1. **Una tarea a la vez.** No empezar la siguiente hasta que la anterior esté verificada en el dispositivo.
2. **Leer antes de tocar.** Cada archivo completo antes de modificarlo; los `.java` especialmente, la lógica no es obvia por el nombre.
3. **`npx tsc --noEmit` tras cada cambio TypeScript.** Error de tipo = parar y corregir.
4. **No sustituir `CallAccessibilityService`; no borrar `.java` sin verificar referencias; no tocar `/android/` sin avisar.** Detalle en `.claude/rules/android-calls.md`.
5. **Todo cambio en la capa de llamadas se verifica en dispositivo real.** Que compile no dice nada sobre si la llamada se cuelga: la prueba es una llamada de verdad.
   Dispositivos: **Samsung Galaxy Note 20** — principal, donde se instala y prueba la app; **Pixel 8** (GrapheneOS) — secundario, simula al spammer.
6. **Instalar el APK encima, sin desinstalar.** Desinstalar borra los permisos concedidos.
7. **Conventional Commits** cuando Víctor pida commitear: `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `test:`.

**Lo que no se toca:**

- `CallAccessibilityService` (regla 4) y el USSD `*67*` (sección 2).
- El puerto 5000: nunca `ufw allow 5000/tcp`. "El servicio está arriba" no significa "es alcanzable": comprobarlo desde fuera del host (lección 4, `.claude/rules/vps-backend.md`).
- La rama `claude/modo1-optimized-01KAWsdwPsM88H55dEWN8Xqf`: **no borrar y nunca `git merge`** (solo cherry-pick en una rama nueva). Qué se puede rescatar y qué no: `docs/ramas-rescate.md`.
- `master`: abandonada desde agosto 2025 y contenida en `dev`; se conserva como archivo. `dev` es la rama por defecto.

**Valores hardcodeados:** los de la app en `.claude/rules/android-calls.md`; los del VPS en `.claude/rules/vps-backend.md`.

**Lecciones:** 1, 3 y 4 en `.claude/rules/vps-backend.md`; 2 en `.claude/rules/android-calls.md`; "UFW y Docker" en `~/claude-config/projects/bitxodev-vps/CLAUDE.md` (afecta a todo el VPS).

## 7. Deuda técnica (verificada en código)

### Crítica

- [ ] **🔴 Panel de NPM (puerto 81) accesible desde Internet.** Afecta a todo el VPS: detalle y arreglo en `~/claude-config/projects/bitxodev-vps/CLAUDE.md`.
- [ ] **Una sola llamada concurrente en `manolo_ari.py`.** Puerto UDP fijo (7000) + estado global: con dos llamadas simultáneas la segunda pisa a la primera. Es el bloqueante real para cualquier uso más allá del propio Víctor.
- [ ] **Estado del Modo Radical leído de dos sitios.** `WhitelistScreen` usa `ContactsService.ts`; `DashboardScreen`, `ContactService.ts`. La fuente autoritativa para la capa nativa es SharedPreferences, y el Dashboard puede mostrar un estado que no es el que aplica el servicio. Unificar.

### Importante

- [ ] **Residuos IVR on-device:** `IVRAudioPlayer.java` (referenciado en `CallAccessibilityService`) e `IVRMessageHelper.java` (en `CallStateReceiver`). Ya no reproducen nada porque el MP3 no se genera, pero compilan. Borrarlos exige limpiar esas 3 referencias primero.
- [ ] **6 permisos residuales en `AndroidManifest.xml`:** `BIND_INCALL_SERVICE`, `BIND_TELECOM_CONNECTION_SERVICE`, `CONTROL_INCALL_EXPERIENCE`, `MODIFY_AUDIO_SETTINGS`, `MODIFY_PHONE_STATE`, `MODIFY_AUDIO_ROUTING`. Los services que los usaban (`SpamCallService`, `DialerActivity`, `InCallActivity`) ya no están en el manifest: los permisos asustan al usuario sin aportar nada.
- [ ] **Cobertura de tests mínima.** Vitest cubre `src/utils/historial.ts`, `BackendSyncService` y el permiso `CALL_PHONE` de `CallForwardingService` (28 casos). `unittest` cubre el plan B de `manolo_ari.py`, el healthcheck y la voz (38 casos). Sin cobertura: la capa de llamadas (Java), el resto de `manolo_ari.py` y `control_api.py`. JUnit/Robolectric sobre Expo es caro y no habría cazado ninguno de los fallos reales (lección 2, el deadlock ARI, el Modo 2 con Manolo), que salen en dispositivo: la regla 5 sigue siendo el gate.
- [ ] **Latencia por turno sin instrumentar.** El paso de AGI a ARI se hizo para bajarla, pero nunca se midió con timestamps reales.
- [ ] **`scripts/check-secrets.sh` no es fiable como gate.** Marca como "posible secreto" el texto de este fichero que describe el problema, referencias a `process.env.EXPO_PUBLIC_*` y nombres de variable. Y falla con `/dev/tty: No such device or address` en entornos no interactivos, cayendo a modo advertencia sin bloquear.

### Menor

- [ ] Typo en el nombre del repo: `Bolcker` → `Blocker`.

## 8. Roadmap

En orden de prioridad; el detalle está en la sección 7.

1. **🔴 Cerrar el panel de NPM** (detalle en `bitxodev-vps`). Nada de lo demás importa hasta que eso esté resuelto.
2. **Token fuera del APK (`EncryptedSharedPreferences`).** Campo en Ajustes donde se pega el token una vez, guardado cifrado vía módulo nativo: deja de estar en el APK y en el repo, y rotarlo no exige recompilar. Coste: un `TextInput`, dos métodos en Java y leerlo desde `BackendSyncService`.
3. **Asignación dinámica de puertos UDP en `manolo_ari.py`.**
4. **Medir latencia por turno** (audio → STT → LLM → TTS → primer RTP de vuelta).
5. **Unificar `ContactService.ts` y `ContactsService.ts`.**
6. **Limpiar residuos IVR** y después los 6 permisos del manifest.
7. **Arreglar o retirar `scripts/check-secrets.sh`.**

### Próxima ronda — Manolo (lo visto en las pruebas del 18/10/2026)

- [ ] **El plan B de Deepgram saltó en la primera frase, no en la segunda.** Hipótesis por verificar en el log: una pausa de más de `SILENCE_MS_TO_CLOSE_TURN` (600 ms) dentro de la frase la parte en dos turnos, y cada uno cuenta un error. Mirar si `STT Deepgram falló (1/2)` y `(2/2)` llegan con milisegundos de diferencia.
- [ ] **`MAX_TURNOS = 8` corta conversaciones que van bien.** Subirlo o limitar por tiempo (unos 5 minutos de llamada).
- [ ] **Respuestas de 1 o 2 frases.** El prompt pide "2-3 frases" y `max_tokens=100` corta alguna a medias. Bajar a 1-2 en el prompt y no dejar frases cortadas (más margen de tokens o recortar en el último punto).
- [ ] **No repetir expresiones dentro de la llamada.** "Fibra de los chinos" salió 3 veces: el ejemplo del prompt se usa como muletilla. Prohibir repetir expresiones ya dichas en el historial.
- [ ] **Coherencia del personaje.** Está sordo del oído derecho: que lo mantenga toda la llamada.

Ideas valoradas y descartadas por riesgo: `docs/ideas-descartadas.md`. Completados: `HISTORIAL.md`.
