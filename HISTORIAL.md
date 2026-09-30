# HISTORIAL — Call-Spam-IA-Blocker

> Sesiones cerradas, limpiezas hechas y puntos del roadmap completados.
> Se separó de `CLAUDE.md` para que ese fichero solo lleve lo que sigue abierto.
>
> Nada de aquí es contexto necesario para trabajar. Se consulta cuando hace falta
> entender **por qué** algo se hizo de una manera concreta.

---

## Cronología

**Limpieza agosto 2026:** eliminados residuos de Twilio, DefaultDialer, IVR local y AGI
scripts obsoletos. Stack del servidor en ese momento: `manolo_agi.py` único AGI.

**Migración septiembre 2026:** `[from-zadarma]` pasa de `AGI(manolo_agi.py)` a
`Stasis(manolo-ari)`. Motor Modo 3 en producción: `manolo_ari.py` (ARI + ExternalMedia) +
Deepgram Nova-2 + Groq `qwen/qwen3.8-27b` + Edge TTS. `manolo_agi.py` y Whisper quedan
como backup/fallback, no eliminados. El cambio es a streaming bidireccional UDP/RTP: se
abandona el modelo graba→procesa→reproduce del AGI.

Scripts VPS eliminados en la limpieza de agosto 2026, sustituidos en su momento por
`manolo_agi.py`: `agi-bin/decision_agi.py`, `victor_agi.py` y `deploy.sh` (este último
sustituido por `deploy_modo3.sh`, hoy solo relevante para el backup AGI).

**Sesión 26 septiembre 2026:** `manolo_ari.py` asume también el Modo 2 (`run_fixed()`
leyendo `current_mode.json`). Limpieza de UI en `refactor/ui-cleanup-sept-2026`: fuera
`AITestsScreen`, `ElevenLabsService`, `GeminiServices`, `IVRGeneratorModule`,
`SpeechRecognitionModule`; historial del servidor fusionado en `CallHistoryScreen`.
Eliminado también `rebuild-clean.sh` (sept. 2026), redundante con `build-fresh.sh`.
Modos 1 y 2 verificados en dispositivo real. Documentadas las 4 lecciones de la
sección 15 de `CLAUDE.md`.

**Sesión 27 septiembre 2026:** desplegada la autenticación de `control_api.py` por HTTPS.
La app controla los tres modos desde el teléfono. Añadida la lección 5 (UFW y Docker).

---

## Resueltos en la sesión de septiembre 2026

- [x] **La app ya controla los tres modos desde el teléfono.** `control_api.py` con `X-API-Key` expuesto por HTTPS (Cloudflare → NPM → Flask en el bridge Docker), sin abrir el 5000. Verificado en dispositivo el 27 sept. 2026: cambio de modo, historial del servidor y Manolo conversando en 200-400 ms por turno.
- [x] **`asterisk-control-api.service` no cargaba el `.env`.** Le faltaba `EnvironmentFile`, así que Flask arrancaba sin `CONTROL_API_TOKEN` (todo 401) y en `127.0.0.1` (NPM no lo alcanzaba). Corregido en el repo.
- [x] **Modo 2 reproducía a Manolo en lugar del mensaje fijo.** `manolo_ari.py` no leía `current_mode.json`: la rama FIXED nunca se había portado del AGI. Añadidos `get_current_mode()`, `elegir_sonido_fijo()` y `run_fixed()`.
- [x] **El mensaje fijo no se encontraba** — ruta y idioma equivocados. Ver lección 1 en `.claude/rules/vps-backend.md`.
- [x] **Modo FIXED no colgaba hasta el timeout** — interbloqueo del bucle de eventos ARI. Ver lección 3 en `.claude/rules/vps-backend.md`.
- [x] **Referencias a `IVRAudioPlayer` documentadas y acotadas** — dejan de ser un riesgo ciego para refactorizar.
- [x] **Modo 3 funcional con ARI.** Groq `qwen/qwen3.8-27b` con historial real (antes no se enviaba a la API), Edge TTS `es-ES-AlvaroNeural`, Deepgram Nova-2 con Whisper de fallback, barge-in con 800 ms de gracia + 3 frames consecutivos.
- [x] **Builds rotos por cachés CMake obsoletas.** `build-fresh.sh` borra los `.cxx` de `android/app` y de `node_modules/*/android/`.

---

## Roadmap completado

- ~~**Autenticar `control_api.py` y exponerlo por HTTPS**~~ ✅ **27 sept. 2026.** `X-API-Key` en los tres endpoints, `https://manolo.bitxodev.com` vía Cloudflare → NPM (Let's Encrypt) → Flask en `172.18.0.1:5000`. El 5000 nunca se abrió. Detalles en la sección 6 y la lección 5 de `CLAUDE.md`.

- ~~**Modo 3 — Agente IA conversacional**~~ ✅ **15 sept. 2026.** Primero con `manolo_agi.py`, y desde esa fecha con `manolo_ari.py` (ARI + ExternalMedia, streaming).

- ~~**Resolver coordinación Modo 2**~~ ✅ **26 sept. 2026** (commit `1a6dd0b`). Resuelto de facto al eliminar `IVRGeneratorModule`: el teléfono ya no puede reproducir audio en Modo 2.

- ~~**Documentar configuración Zadarma**~~ ✅ **26 sept. 2026** (commit `2fee4f5`). Está en `CLAUDE.local.md`, fuera de git porque el repo es público.

---

## Ramas borradas

Limpieza de ramas (sept. 2026): borradas `refactor/ui-cleanup-sept-2026`,
`refactor/cleanup-agosto-2026`, `claude/fix-call-permissions-…` y
`claude/ivr-bcp-method-…`, todas mergeadas en `dev` y sin commits propios.

`claude/modo1-optimized-…` **no** se borró y sigue documentada en la sección 13 de
`CLAUDE.md`: tiene 17 commits sin mergear con features rescatables.

---

## Retirado del CLAUDE.md en la limpieza del 30 sept. 2026

- **`POST /set_message` eliminado (sept. 2026).** Generaba `custom_fixed_message.wav` con gTTS para la UI de mensajes personalizados, que ya no existe. El Modo 2 reproduce siempre `fixed_spam_message`.
- **Gemini ya no se usa.** Era el fallback de `manolo_agi.py` (20 req/día en free tier). `GeminiServices.ts` eliminado de la app.
- **Variables eliminadas (sept. 2026):** `EXPO_PUBLIC_GEMINI_API_KEY`, `EXPO_PUBLIC_GEMINI_MODEL`, `EXPO_PUBLIC_ELEVENLABS_API_KEY` — sus consumidores (`GeminiServices.ts`, `ElevenLabsService.ts`) ya no existen. `.env.example` actualizado.
- **`.env` verificado como no commiteado** (`git log --all` no lo muestra). Solo aparece `ios/.xcode.env`, irrelevante.
- **Lección 4, estado previo al 27 sept. 2026:** el 5000/tcp no estaba abierto en UFW y ningún endpoint de `control_api.py` validaba nada (ni token, ni IP, ni cabecera). Abrirlo habría expuesto `POST /set_mode`, `POST /set_message` y `GET /call_log` (números de terceros). Resuelto sin abrir el puerto: `X-API-Key` + HTTPS por Cloudflare → NPM → Flask en el gateway del bridge Docker. Para que funcionara hicieron falta las dos piezas de la lección "UFW y Docker", hoy en `~/claude-config/projects/bitxodev-vps/CLAUDE.md`.
- **Rama `claude/modo1-optimized-…`:** última actividad 31 dic. 2025 (`aec0dd2`); `dev` le lleva 96 commits. Features rescatables verificadas como ausentes en `dev` (0 ficheros con `CustomTabs`, 0 con `spamScore`). `79ece8a`/`f9a5d9a` son de antes de que Modo 2 y 3 existieran de verdad. `dev` confirmada como rama por defecto con `git ls-remote --symref origin HEAD` (sept. 2026).
