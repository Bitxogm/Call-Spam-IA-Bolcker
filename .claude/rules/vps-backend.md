---
paths: ["vps_backend/**"]
---

# Backend VPS (Asterisk + ARI + Flask) — reglas y trampas

## Endpoints de control_api.py (Flask)

⚠️ **Los tres exigen la cabecera `X-API-Key`**, comparada con `CONTROL_API_TOKEN` del `.env` del VPS. Sin token configurado en el servidor, todo devuelve 401 — falla cerrado a propósito.

- `POST /set_mode` — body `{"mode": "FIXED"|"AI"}` → escribe `current_mode.json`
- `GET /get_mode` — devuelve el modo actual
- `GET /call_log` — últimas 50 llamadas atendidas por el servidor, ordenadas por `timestamp_inicio` desc. Las escribe `manolo_ari.py` en `<BRIDGE_DIR>/call_log.json`; las consume `CallHistoryScreen` vía `BackendSyncService.getCallLog()`

## Audio del mensaje fijo (Modo 2) — ruta crítica

Asterisk resuelve `sound:` contra su **Data directory**, que en este VPS es `/usr/share/asterisk`, **no** `/var/lib/asterisk` (ver lección 1 más abajo):

- `manolo_ari.py`: `SOUNDS_DIR = '/usr/share/asterisk/sounds/es'`
- `manolo_ari.py` reproduce siempre `FIXED_SOUND = 'fixed_spam_message'`. El fallback a `custom_fixed_message` se eliminó con la UI de mensajes personalizados: si queda un WAV viejo en el servidor, se ignora
- La llamada a ARI pasa `lang='es'` explícito (`ARIClient.play`). Sin él Asterisk resuelve contra el idioma del canal, que llega como `en` desde Zadarma, y busca en `sounds/en/` — donde el fichero no está

## Plan B del Modo 3 y healthcheck

**Plan B del Modo 3** (`activar_plan_b`, una vez por llamada, log `FALLBACK: <pieza> falló`):

| Falla | Qué oye el spammer |
| --- | --- |
| TTS (Edge, ffmpeg o > 10 s) | `fixed_spam_message` y cuelga (`run_fixed`) |
| Groq (error o respuesta vacía) | la frase de emergencia; si el TTS también falla, el mensaje fijo |
| Deepgram, 2 errores reales seguidos | `fixed_spam_message` y cuelga. Una transcripción vacía es silencio, no error |

Las funciones de cada pieza (`deepgram_stt`, `generate_manolo_reply`, `synthesize_mulaw`) lanzan `STTError`/`LLMError`/`TTSError`: no tragarse el error ahí, o se rompen el plan B y el healthcheck.

**Healthcheck** (`healthcheck.py` + `manolo-healthcheck.timer`, a las 00/06/12/18 h): prueba TTS, Groq y Deepgram llamando a esas mismas funciones, con la muestra `healthcheck_sample.wav`, y comprueba que `asterisk`, `manolo_ari` y `asterisk-control-api` están `active`. Cada comprobación tiene 30 s como máximo. Manda email por Gmail SMTP **solo cuando cambia el conjunto de piezas caídas** (uno al fallar, otro al recuperarse). El estado se guarda en `<BRIDGE_DIR>/healthcheck_state.json`. Si cambias de proveedor de voz, actualiza `TTS_PROVIDER`: el email solo sugiere `pip install -U edge-tts` cuando vale `'edge'`.

## Lección 1 — Asterisk busca los sonidos en `/usr/share/asterisk`, no en `/var/lib/asterisk`

**Síntoma:** `sound:fixed_spam_message` daba "File does not exist" aunque el WAV estaba en `/var/lib/asterisk/sounds/es/`.

**Causa:** el *Data directory* de esta instalación es `/usr/share/asterisk` — el mismo árbol donde vive `agi-bin/`. Todo lo que Asterisk resuelve por nombre lógico (`sound:`) se busca ahí.

**Y un segundo nivel:** con la ruta ya correcta seguía fallando, porque `sound:` se resuelve **por idioma del canal** y el canal de Zadarma llega con `language=en` → Asterisk buscaba en `sounds/en/`. Hay que pasar `lang='es'` explícito en `POST /channels/{id}/play`.

**Regla:** al añadir audio, `ls` en `/usr/share/asterisk/sounds/es/` y pasar `lang` siempre. Nunca fiarse del idioma del canal.

## Lección 3 — No hacer `await` de un handler largo dentro del bucle de eventos ARI

**Síntoma:** en Modo 2 el mensaje fijo se oía completo, pero la llamada no colgaba hasta agotar el timeout de 20 s.

**Causa:** `run_ari_events()` despacha con `await handler(event)` **dentro** de `async for raw in ws`. `run_fixed()` esperaba el evento `PlaybackFinished`… que no podía leerse, porque el bucle que lo lee estaba suspendido dentro del propio handler. Interbloqueo clásico: el handler espera un evento que solo llegará cuando el handler termine.

**Solución:** lanzar `run_fixed()` con `asyncio.create_task()` y guardar una referencia fuerte contra el GC.

**Matiz importante:** **no** convertir todo el dispatcher a `create_task`. El `StasisStart` del canal `UnicastRTP/` lee `call_state.bridge_id` que escribe el `StasisStart` del canal entrante; hoy funciona porque los handlers corren en orden. Hacerlos todos concurrentes provocaría una carrera justo en el camino de Modo 3, que sí funciona.

## Valores hardcodeados

| Valor                            | Archivo       | Notas                                        |
| -------------------------------- | ------------- | -------------------------------------------- |
| `<BRIDGE_DIR>/current_mode.json` | manolo_ari.py | Estado del modo, lo escribe `control_api.py` |
| `/usr/share/asterisk/sounds/es`  | manolo_ari.py | Data directory de Asterisk                   |
| `fixed_spam_message`             | manolo_ari.py | Único mensaje del Modo 2                     |
| `20` (s)                         | manolo_ari.py | Timeout esperando `PlaybackFinished`         |
| `1500`                           | manolo_ari.py | Umbral RMS del VAD (barge-in)                |
| `7000`                           | manolo_ari.py | Puerto UDP de ExternalMedia, fijo            |
| `TTS_TIMEOUT = 10` (s)           | manolo_ari.py | Edge TTS + ffmpeg; pasado, plan B            |
| `MAX_ERRORES_STT = 2`            | manolo_ari.py | Errores de Deepgram seguidos antes del plan B |
| `TTS_PROVIDER = 'edge'`          | manolo_ari.py | Decide si el email sugiere `pip install -U edge-tts` |
| `GROQ_MODEL`                     | manolo_ari.py | `qwen/qwen3.8-27b`                           |
| `CHECK_TIMEOUT = 30` (s)         | healthcheck.py | Máximo por comprobación                     |
| `<BRIDGE_DIR>/healthcheck_state.json` | healthcheck.py | Último estado; decide si se manda email |
