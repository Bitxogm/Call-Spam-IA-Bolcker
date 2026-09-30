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
