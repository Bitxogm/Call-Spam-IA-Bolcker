# CLAUDE.md — Call-Spam-IA-Blocker

> Archivo de contexto para Claude Code. Léelo completo antes de tocar cualquier archivo.
> Repositorio: https://github.com/Bitxogm/Call-Spam-IA-Bolcker (branch activo: `dev`)
>
> ⚠️ La fuente de verdad es siempre el código fuente. Si este fichero y el código
> discrepan, manda el código y este fichero se corrige.
>
> Historial detallado de sesiones cerradas y limpiezas pasadas en `HISTORIAL.md` — no se carga automáticamente, consúltalo si necesitas contexto de por qué algo se hizo así.

---

## 1. Descripción del proyecto

Bloqueador de llamadas spam para Android con dos modos de operación:

- **Modo 1 (Answer+Hangup):** la app contesta y cuelga localmente. El spammer no escucha nada.
- **Modo 2 (Backend Fixed):** la llamada se desvía a nivel de red GSM (USSD) al número Zadarma, que la reenvía al VPS. El servidor reproduce al spammer un WAV fijo ya presente en el VPS (`fixed_spam_message`). El teléfono del usuario no llega a sonar.

El único audio que oye el spammer lo genera el **servidor** (`vps_backend/manolo_ari.py`); en la app ya no hay TTS ni IA.

Llegar al Modo 2 funcional costó mucho trabajo. Este documento existe para que ese conocimiento no se pierda.

---

## 2. Arquitectura real (verificada en código)

### Modo 1 — Answer+Hangup (on-device)

```
Llamada entra al teléfono
  └── CallAccessibilityService detecta spam
        ├── TelecomManager.acceptRingingCall()
        ├── espera delay configurable (1-5s)
        └── TelecomManager.endCall()
```

⚠️ **El Accessibility Service es obligatorio, no es un residuo.** Se intentó eliminarlo moviendo la lógica a `CallStateReceiver` y en dispositivo real el resultado fue que **contesta pero no cuelga**. Revertido en `03782bc`. Detalle en la lección 2 (`.claude/rules/android-calls.md`).

### Modos 2 y 3 — desvío GSM + Asterisk

- **Activar (una vez, desde la UI):** RN → `CallForwardingModule.configureForMode("BACKEND_FIXED")` → `CallForwardingManager.enableForwarding()` → USSD `*67*919933065#` (ver nota USSD en la sección 3).
- **Por llamada:** el operador redirige a Zadarma y el teléfono **no suena** → Zadarma reenvía al VPS por SIP (configurado en su panel web, fuera del repo) → `extensions.conf` `[from-zadarma]`: `Answer → Stasis(manolo-ari) → Hangup` → `manolo_ari.py` lee `current_mode.json`: FIXED reproduce `fixed_spam_message` y cuelga; AI pasa la llamada a Manolo (sección 4).
- **Cambiar de modo:** `AnswerHangupSettingsScreen` → `BackendSyncService.ts` → `POST /set_mode` en `control_api.py`.
- **Desactivar:** `CallForwardingManager.disableForwarding()` → USSD `##67#`. Consultar estado: `*#67#`.

---

## 3. Modos de operación

|                                | Modo 1 (HANGUP_IMMEDIATELY)               | Modo 2 (BACKEND_FIXED)                    | Modo 3 (BACKEND_AI)              |
| ------------------------------ | ----------------------------------------- | ----------------------------------------- | -------------------------------- |
| USSD                           | desactiva (`##67#`)                       | activa (`*67*919933065#`)                 | activa (`*67*919933065#`)        |
| Teléfono suena                 | sí, app lo cuelga                         | no                                        | no                               |
| Spammer escucha                | nada                                      | WAV fijo (`fixed_spam_message`)           | Manolo (Groq `qwen/qwen3.8-27b`) |
| Requiere Accessibility Service | ✅ **imprescindible**                     | ❌                                        | ❌                               |
| Requiere VPS                   | ❌                                        | ✅                                        | ✅                               |
| Requiere Zadarma config        | ❌                                        | ✅                                        | ✅                               |
| Sincroniza con la app          | ❌ no lo necesita                         | ✅ vía HTTPS (sección 4)                  | ✅ vía HTTPS                     |
| Estado actual                  | ✅ verificado en dispositivo               | ✅ verificado en dispositivo               | ✅ verificado, 200-400 ms/turno  |

⚠️ **`CallForwardingManager` usa el código 67, con el número sin prefijo de país:** `*67*919933065#` / `##67#` / `*#67#`. El código GSM estándar de desvío incondicional es `*21*` y `*67*` es desvío en ocupado, pero `*67*` es el que funciona con el operador de Víctor — no cambiarlo sin probar en dispositivo.

---

## 4. Infraestructura del servidor

**Flujo de decisión en el VPS (producción):**

```
Asterisk [from-zadarma]
  └── Stasis(manolo-ari)
        └── manolo_ari.py (ARI 127.0.0.1:8088 + ExternalMedia UDP 127.0.0.1:7000)
              ├── Recibe RTP/mulaw por UDP, VAD por energía (barge-in: 800ms de
              │   gracia + 3 frames consecutivos antes de cortar la respuesta)
              ├── STT: Deepgram Nova-2 (batch) — Whisper por socket (/tmp/whisper.sock) como fallback
              ├── LLM: Groq `qwen/qwen3.8-27b`, con historial de conversación real
              └── TTS: Edge TTS `es-ES-AlvaroNeural` → mulaw 8kHz → RTP de vuelta
```

`manolo_agi.py` es el AGI legacy: sigue en disco como backup, pero el dialplan ya no lo referencia.

**Endpoints de `control_api.py` (todos con `X-API-Key`):** detalle en `.claude/rules/vps-backend.md`.

**Audio del mensaje fijo (Modo 2) — ruta crítica:** `/usr/share/asterisk/sounds/es` y `lang='es'` explícito; detalle en `.claude/rules/vps-backend.md`.

**Cómo se expone (en producción desde el 27 sept. 2026):**

```
App → https://manolo.bitxodev.com
        → Cloudflare (proxy naranja, rate limit)
              → 443 del VPS → Nginx Proxy Manager (contenedor, cert Let's Encrypt)
                    → http://172.18.0.1:5000 → Flask
```

- **Dónde escucha Flask:** `CONTROL_API_BIND`, por defecto `127.0.0.1`. En producción es el gateway del bridge Docker de NPM (`172.18.0.1`): NPM corre en contenedor y para él `127.0.0.1` es el propio contenedor
- **El puerto 5000 no se expone a Internet.** Todo entra por 443
- **Certificado: Let's Encrypt gestionado por NPM**, como el resto de las apps del VPS — no el Cloudflare Origin Certificate
- Hace falta una regla UFW explícita para que el contenedor alcance el gateway: la regla exacta está en `CLAUDE.local.md`; el porqué, en la lección "UFW y Docker" (caso A) de `~/claude-config/projects/bitxodev-vps/CLAUDE.md`
- `asterisk-control-api.service` necesita `EnvironmentFile=/root/ai_bridge/.env`, o Flask arranca sin token y en loopback

⚠️ Degradación silenciosa de `BackendSyncService.ts` y el lío `ContactService.ts`/`ContactsService.ts`: detalle en `.claude/rules/services.md`.

### Configuración externa (fuera del repo — crítica para Modo 2)

Esta configuración no vive en el código. Si se pierde, el Modo 2 deja de funcionar:

| Servicio             | Número/URL         | Configuración                                                                                                                                                                                                                    |
| -------------------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Zadarma**          | ver `CLAUDE.local.md` | Reenvío de entrantes → VPS por SIP. Número, SIP Login y External Server en el fichero local                                                                                                                                    |
| **VPS — SIP**        | ver `CLAUDE.local.md` | Puerto 5060/udp abierto solo a las 6 subnets de Zadarma                                                                                                                                                                       |
| **VPS — control API**| ver `CLAUDE.local.md` | `https://manolo.bitxodev.com` → Cloudflare → NPM → Flask en el bridge Docker. El 5000/tcp **sigue cerrado al exterior**: todo entra por 443. Los tres endpoints exigen `X-API-Key` (ver lección 4)          |
| **Deepgram**         | `api.deepgram.com` | $200 crédito gratuito. **STT principal desde sept. 2026** (Nova-2, batch) en `manolo_ari.py` — verificado en prueba real sobre audio del ExternalMedia (RTP/mulaw 8 kHz). Whisper queda como fallback si Deepgram falla           |
| **Groq**             | `api.groq.com`     | LLM de Manolo: `qwen/qwen3.8-27b`. Si la API falla, `manolo_ari.py` responde con un mensaje fijo de emergencia, no hay fallback a otro proveedor                                                                                  |

---

## 5. Variables de entorno

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

---

## 6. Comandos de desarrollo

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
```

---

## 7. Forma de trabajar con Claude Code

### Reglas (no negociables)

1. **Una tarea a la vez.** No empezar la siguiente hasta que la anterior esté verificada en el dispositivo.

2. **Leer antes de tocar.** Antes de modificar cualquier archivo, leerlo completo. Los `.java` especialmente — la lógica no es obvia por el nombre.

3. **`npx tsc --noEmit` tras cada cambio TypeScript.** Error de tipo = parar y corregir antes de continuar.

4. **No sustituir `CallAccessibilityService`; no borrar `.java` sin verificar referencias; no tocar `/android/` sin avisar.** Detalle en `.claude/rules/android-calls.md`.

5. **Todo cambio en la capa de llamadas se verifica en dispositivo real.** `npx tsc --noEmit` y que compile no dicen nada sobre si la llamada se cuelga. La prueba es una llamada de verdad desde el Note 20.
   Dispositivos: Pixel 8 (GrapheneOS) — principal, donde se instala y prueba la app; Samsung Galaxy Note 20 — secundario, simula al spammer.

6. **Conventional Commits** cuando Víctor pida commitear:
   `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `test:`

---

## 8. Valores hardcodeados — localizaciones exactas

Los de la app Android están en `.claude/rules/android-calls.md`.

Los del VPS están en `.claude/rules/vps-backend.md`.

---

## 9. Deuda técnica real (verificada en código)

### Crítica

- [ ] **🔴 Rotar `CONTROL_API_TOKEN`.** El token en uso se expuso en un chat. Mientras no se rote, quien lo haya visto puede cambiar el modo de operación y leer `GET /call_log` — los números de quien ha llamado. Procedimiento: generar uno nuevo (`python3 -c "import secrets; print(secrets.token_urlsafe(32))"`), ponerlo en el `.env` del VPS, `systemctl restart asterisk-control-api`, ponerlo en el `.env` de la app, rebuild e instalar. **Y borrar los APK viejos de `~/Downloads`: llevan el token antiguo incrustado.**

- [ ] **🔴 Panel de NPM (puerto 81) accesible desde Internet.** Afecta a todo el VPS, no solo a este proyecto: detalle y arreglo en `~/claude-config/projects/bitxodev-vps/CLAUDE.md`.

- [ ] **Una sola llamada concurrente en `manolo_ari.py`.** Puerto UDP fijo (7000) + estado global. Con dos llamadas simultáneas la segunda pisa a la primera. Es el bloqueante real para cualquier uso más allá del propio Víctor.

- [ ] **Estado del Modo Radical leído de dos sitios distintos.** `WhitelistScreen` lo lee y escribe con `ContactsService.ts`; `DashboardScreen` usa `ContactService.ts`. La fuente autoritativa para la capa nativa es SharedPreferences, y el Dashboard puede mostrar un estado que no es el que aplica el servicio. Unificar en un único servicio.

### Importante

- [ ] **Residuos IVR on-device:** `IVRAudioPlayer.java` (referenciado en `CallAccessibilityService`) e `IVRMessageHelper.java` (referenciado en `CallStateReceiver`). Ya no pueden reproducir nada porque el MP3 no se genera, pero siguen compilando. Borrarlos exige limpiar esas 3 referencias primero.

- [ ] **6 permisos residuales en `AndroidManifest.xml`:** `BIND_INCALL_SERVICE`, `BIND_TELECOM_CONNECTION_SERVICE`, `CONTROL_INCALL_EXPERIENCE`, `MODIFY_AUDIO_SETTINGS`, `MODIFY_PHONE_STATE`, `MODIFY_AUDIO_ROUTING`. Los services que los usaban (`SpamCallService`, `DialerActivity`, `InCallActivity`) ya están fuera del manifest — los permisos siguen asustando al usuario sin aportar nada.

- [ ] **Sin tests** en ninguna capa.

- [ ] **Latencia por turno sin instrumentar.** El paso de AGI a ARI se hizo para bajarla, pero nunca se midió con timestamps reales. Sin cifras no se puede afirmar que el streaming mejoró nada.

- [ ] **`scripts/check-secrets.sh` no es fiable como gate.** Marca como "posible secreto" el propio texto de este CLAUDE.md que describe el problema, además de referencias a `process.env.EXPO_PUBLIC_*` y nombres de variable. Y falla con `/dev/tty: No such device or address` en entornos no interactivos (CI, agentes), cayendo a modo advertencia sin bloquear.

- [ ] **"Borrar Todo" en `CallHistoryScreen` solo limpia el historial local.** Las llamadas del servidor (`call_log.json`) reaparecen al siguiente refresco, porque no hay endpoint para borrarlas.

### Menor

- [ ] Typo en nombre del repo: `Bolcker` → `Blocker`

---

## 10. Roadmap

En orden de prioridad; el detalle de cada punto está en la sección 9.

1. **🔴 Rotar `CONTROL_API_TOKEN` y cerrar el panel de NPM** (este último en `bitxodev-vps`). Nada de lo demás importa hasta que eso esté resuelto.
2. **Token fuera del APK (`EncryptedSharedPreferences`).** Campo en Ajustes donde se pega el token una vez, guardado cifrado vía módulo nativo: deja de estar en el APK y en el repo, y rotarlo no exige recompilar. Coste: un `TextInput`, dos métodos en Java y leerlo desde `BackendSyncService`.
3. **Asignación dinámica de puertos UDP en `manolo_ari.py`.**
4. **Medir latencia por turno** (audio → STT → LLM → TTS → primer RTP de vuelta).
5. **Unificar `ContactService.ts` y `ContactsService.ts`.**
6. **Limpiar residuos IVR** y después los 6 permisos del manifest.
7. **Arreglar o retirar `scripts/check-secrets.sh`.**

Completados: ver `HISTORIAL.md`.

---

## 11. Infraestructura VPS — datos reales

⚠️ **Los datos reales están en `CLAUDE.local.md`, que no se versiona.** El repositorio es
público: IP del VPS, puerto SSH, login SIP de Zadarma, `pjsip.conf`, las subnets de UFW y
las rutas de despliegue se quedan fuera de git a propósito.

**Copia de seguridad de `CLAUDE.local.md` en el repo privado `claude-config`, ruta
`projects/call-spam-ia-blocker/`. Si falta en local, recuperarlo de ahí.**

---

## 12. Lecciones aprendidas — trampas ya pisadas

Lecciones 1 y 3 (Asterisk y ARI) en `.claude/rules/vps-backend.md`; lección 2 (Accessibility Service) en `.claude/rules/android-calls.md`; la lección "UFW y Docker" (antes lección 5) en `~/claude-config/projects/bitxodev-vps/CLAUDE.md`, porque afecta a todo el VPS.

### Lección 4 — Un servicio arriba no es un servicio alcanzable (y abrir el puerto no es la solución)

**Síntoma:** `control_api.py` sano — `systemctl status` OK, `curl` local OK — pero la app no cambia de modo ni sincroniza. Sin error visible en pantalla: `BackendSyncService` degrada en silencio a propósito, que es lo correcto para la UX pero esconde el fallo.

**Lo que NO hay que hacer: `ufw allow 5000/tcp`.** Antes de abrir un puerto, mirar qué quedaría expuesto. La solución fue HTTPS por NPM con `X-API-Key` (sección 4), con el 5000 cerrado.

**Y hay un segundo bloqueante, independiente del firewall:** la exención de tráfico en claro vive en `android/app/src/debug/AndroidManifest.xml`, solo en el source set de debug. Con `targetSdkVersion 34`, un build de release bloquea cualquier `http://`. Aunque el puerto estuviera abierto, la app en release no habría podido hablar con el servidor.

**Regla de diagnóstico:** "el servicio está arriba" no significa "el servicio es alcanzable". Comprobarlo desde **fuera** del host, no con un `curl` local:

```bash
curl -H "X-API-Key: $TOKEN" https://tu-subdominio.tu-dominio.com/get_mode
```

---

## 13. Ramas del repositorio

`dev` es la rama por defecto en GitHub. `master` está abandonada desde agosto 2025 y contenida en `dev` — se conserva solo como archivo histórico.

### ⚠️ `claude/modo1-optimized-01KAWsdwPsM88H55dEWN8Xqf` — no borrar

**No está mergeada:** 17 commits que nunca llegaron a `dev`, con trabajo aprovechable (verificado que no existe en `dev`). Rescatar con cherry-pick, commit a commit, en una rama nueva desde `dev`. **Nunca `git merge`**: toca `CallStateReceiver`, la UI de Answer+Hangup y borra ficheros vivos.

| Feature rescatable                                          | Commits                  |
| ----------------------------------------------------------- | ------------------------ |
| Lookup del número en webs de spam con Chrome Custom Tabs    | `f095802`, `804edd5`     |
| Detección local de spam para España (regulación 2025)       | `f697767`                |
| Score y categoría de spam en historial y notificaciones     | `2bef944`, `b91e23f`     |
| Almacenamiento dual al añadir a blacklist desde el historial | `40491d1`                |

**No traer:** `86d0c32` (dice que la accesibilidad no es necesaria — falso, ver lección 2 en `.claude/rules/android-calls.md`); `79ece8a` y `f9a5d9a` (quitan Modo 2/3, que hoy son producción); `aec0dd2` (borra `LogsService.ts`, que en `dev` sigue en uso).
