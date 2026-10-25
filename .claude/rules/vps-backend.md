---
paths: ["vps_backend/**"]
---

# Backend VPS (Asterisk + ARI + Flask) — reglas y trampas

## Endpoints de control_api.py (Flask)

⚠️ **Los tres exigen la cabecera `X-API-Key`**, comparada con `CONTROL_API_TOKEN` del `.env` del VPS. Sin token configurado en el servidor, todo devuelve 401 — falla cerrado a propósito.

- `POST /set_mode` — body `{"mode": "FIXED"|"AI"}` → escribe `current_mode.json`
- `GET /get_mode` — devuelve el modo actual
- `GET /call_log` — últimas 50 llamadas atendidas por el servidor, ordenadas por `timestamp_inicio` desc. Las escribe `manolo_ari.py` en `<BRIDGE_DIR>/call_log.json`; las consume `CallHistoryScreen` vía `BackendSyncService.getCallLog()`

## Cómo se expone `control_api.py`

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

## Audio del mensaje fijo (Modo 2) — ruta crítica

Asterisk resuelve `sound:` contra su **Data directory**, que en este VPS es `/usr/share/asterisk`, **no** `/var/lib/asterisk` (ver lección 1 más abajo):

- `manolo_ari.py`: `SOUNDS_DIR = '/usr/share/asterisk/sounds/es'`
- `manolo_ari.py` reproduce siempre `FIXED_SOUND = 'fixed_spam_message'`. El fallback a `custom_fixed_message` se eliminó con la UI de mensajes personalizados: si queda un WAV viejo en el servidor, se ignora
- La llamada a ARI pasa `lang='es'` explícito (`ARIClient.play`). Sin él Asterisk resuelve contra el idioma del canal, que llega como `en` desde Zadarma, y busca en `sounds/en/` — donde el fichero no está

## Plan B del Modo 3 y healthcheck

**Plan B del Modo 3** (`activar_plan_b`, una vez por llamada, log `FALLBACK: <pieza> falló`):

| Falla | Qué oye el spammer |
| --- | --- |
| Voz premium del personaje (Cartesia o ElevenLabs: error, > 3 s o sin créditos), con `TTS_PROVIDER=cartesia` | El personaje sigue con su voz de Edge (Manolo: `TTS_VOICE`; Paquita: `PAQUITA_VOZ_RESERVA`). Un solo intento por llamada: el resto va directo a Edge |
| Edge (TTS, ffmpeg o > 10 s) | `fixed_spam_message` y cuelga (`run_fixed`) |
| Groq (error o respuesta vacía) | la frase de emergencia; si el TTS también falla, el mensaje fijo |
| Deepgram, 2 errores reales seguidos | `fixed_spam_message` y cuelga. Una transcripción vacía es silencio, no error |

Las funciones de cada pieza (`deepgram_stt`, `generate_manolo_reply`, `synthesize_mulaw`) lanzan `STTError`/`LLMError`/`TTSError`: no tragarse el error ahí, o se rompen el plan B y el healthcheck.

Cada salto queda en el call log: `plan_b` acumula las piezas (`"Cartesia"`, `"Cartesia → TTS"`), y la app lo muestra como "voz de reserva" o "mensaje fijo". **Sin créditos** (402, o un 4xx que lo diga; la API no lo documenta) se escribe `<BRIDGE_DIR>/cartesia_state.json` y Cartesia se salta hasta `CARTESIA_DIA_RENOVACION`. Cada síntesis deja su latencia en el log (`TTS Cartesia: N ms`).

**Healthcheck** (`healthcheck.py` + `manolo-healthcheck.timer`, a las 00/06/12/18 h): prueba Edge, Groq y Deepgram llamando a esas mismas funciones, con la muestra `healthcheck_sample.wav`, y comprueba que `asterisk`, `manolo_ari` y `asterisk-control-api` están `active`. **Nunca sintetiza con Cartesia** (gastaría créditos): comprueba la clave y la voz con `GET /voices/{id}` y si está marcada sin créditos. El aviso de saldo (< 20 %) solo existe con `CARTESIA_ADMIN_API_KEY` y `CARTESIA_CREDITOS_MES`. Cada comprobación tiene 30 s como máximo. Manda email por Gmail SMTP **solo cuando cambia el conjunto de piezas caídas** (uno al fallar, otro al recuperarse). El estado se guarda en `<BRIDGE_DIR>/healthcheck_state.json`.

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

## Lección 4 — Un servicio arriba no es un servicio alcanzable (y abrir el puerto no es la solución)

**Síntoma:** `control_api.py` sano — `systemctl status` OK, `curl` local OK — pero la app no cambia de modo ni sincroniza. Sin error visible en pantalla: `BackendSyncService` degrada en silencio a propósito, que es lo correcto para la UX pero esconde el fallo.

**Lo que NO hay que hacer: `ufw allow 5000/tcp`.** Antes de abrir un puerto, mirar qué quedaría expuesto. La solución fue HTTPS por NPM con `X-API-Key` (sección de exposición, arriba), con el 5000 cerrado.

**Y hay un segundo bloqueante, independiente del firewall:** la exención de tráfico en claro vive en `android/app/src/debug/AndroidManifest.xml`, solo en el source set de debug. Con `targetSdkVersion 34`, un build de release bloquea cualquier `http://`. Aunque el puerto estuviera abierto, la app en release no habría podido hablar con el servidor.

**Regla de diagnóstico:** "el servicio está arriba" no significa "el servicio es alcanzable". Comprobarlo desde **fuera** del host, no con un `curl` local:

```bash
curl -H "X-API-Key: $TOKEN" https://tu-subdominio.tu-dominio.com/get_mode
```

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
| `CARTESIA_TIMEOUT = 3` (s)       | manolo_ari.py | Cartesia; pasado, Edge                       |
| `CARTESIA_MODEL = 'sonic-3.6'`   | manolo_ari.py | Con `Cartesia-Version: 2026-08-14`           |
| `MAX_ERRORES_STT = 2`            | manolo_ari.py | Errores de Deepgram seguidos antes del plan B |
| `<BRIDGE_DIR>/cartesia_state.json` | manolo_ari.py | `agotado_hasta`: Cartesia sin créditos hasta esa fecha |
| `GROQ_MODEL`                     | manolo_ari.py | `qwen/qwen3.8-27b`                           |
| `CHECK_TIMEOUT = 30` (s)         | healthcheck.py | Máximo por comprobación                     |
| `<BRIDGE_DIR>/healthcheck_state.json` | healthcheck.py | Último estado; decide si se manda email |
