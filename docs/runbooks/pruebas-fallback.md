# Runbook — probar el plan B del Modo 3 y el email del healthcheck

Se provoca el fallo de una pieza redirigiendo su dominio a `127.0.0.1` en
`/etc/hosts`: falla solo esa pieza, sin tocar el `.env` ni el código. Cada línea
lleva la marca `# prueba-fallback` para borrarla después.

Requisitos: la app en **Modo 3** y un segundo teléfono para llamar.

[VPS] Log en vivo durante todas las pruebas, en otra terminal:
```bash
sudo journalctl -u manolo_ari -f
```

[VPS] Limpieza: después de **cada** prueba (borra solo las líneas marcadas):
```bash
sudo sed -i '/# prueba-fallback$/d' /etc/hosts
```

---

## 0. Flujo normal

Llama y habla un par de turnos. ✅ Manolo contesta con la voz de `TTS_VOICE`. ✅ Ninguna línea `FALLBACK:`.

## 1. Fallo de TTS

[VPS] Bloquea Edge TTS:
```bash
echo "127.0.0.1 speech.platform.bing.com # prueba-fallback" | sudo tee -a /etc/hosts
```

Llama. ✅ Nada más descolgar, el mensaje fijo y cuelga (ya falla el saludo). ✅ `FALLBACK: TTS falló`.

## 2. Fallo de Groq

[VPS] Bloquea Groq:
```bash
echo "127.0.0.1 api.groq.com # prueba-fallback" | sudo tee -a /etc/hosts
```

Llama y habla. ✅ Saludo normal y luego "Ay, hijo, no te he oído bien. ¿Puedes repetirlo?". ✅ `FALLBACK: Groq falló`.

Con Groq y TTS bloqueados a la vez, una llamada real se comporta como la prueba 1,
porque falla el saludo. El orden Groq → TTS lo cubre `test_fallback.py`.

## 3. Fallo de Deepgram

[VPS] Bloquea Deepgram:
```bash
echo "127.0.0.1 api.deepgram.com # prueba-fallback" | sudo tee -a /etc/hosts
```

Llama. ✅ Saludo normal. Habla dos veces. ✅ Tras la segunda, el mensaje fijo y cuelga. ✅ `FALLBACK: Deepgram falló`.

El 18/10/2026 saltó en la primera frase: revisar en el log si `(1/2)` y `(2/2)` llegan
casi a la vez (roadmap, "Próxima ronda").

## 4. Email del healthcheck

[VPS] Provoca el fallo de TTS:
```bash
echo "127.0.0.1 speech.platform.bing.com # prueba-fallback" | sudo tee -a /etc/hosts
```

[VPS] Lanza el healthcheck:
```bash
sudo systemctl start manolo-healthcheck.service
```

✅ Llega "[Manolo] Falla: TTS", con la sugerencia `pip install -U edge-tts` si `TTS_PROVIDER` es `'edge'`.

[VPS] Lánzalo otra vez:
```bash
sudo systemctl start manolo-healthcheck.service
```

✅ **No** llega otro email: sigue roto, no ha cambiado nada.

[VPS] Limpia `/etc/hosts` (bloque de arriba) y lánzalo de nuevo:
```bash
sudo systemctl start manolo-healthcheck.service
```

✅ Llega "[Manolo] Recuperado: TTS".

## Al terminar

[VPS] No debe salir nada:
```bash
grep prueba-fallback /etc/hosts
```
