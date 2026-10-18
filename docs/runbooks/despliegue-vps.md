# Runbook — desplegar `vps_backend/` en el VPS

Genérico: los valores reales (`<VPS_HOST>`, `<SSH_PORT>`, `<SSH_USER>`) están en
`CLAUDE.local.md`, que no se versiona. `<BRIDGE_DIR>` es `/root/ai_bridge`.

El SSH entra con un usuario sin privilegios: se sube a `/tmp` y se coloca con
`sudo install`, que deja dueño `root` y permisos 644 en un paso (un `mv` dejaría
los ficheros a nombre del usuario).

Notación: **[local]** = máquina de desarrollo, desde la raíz del repo · **[VPS]** = sesión SSH.
Ajusta la lista de ficheros a los que cambien en cada despliegue.

---

## 1. Copia de seguridad

[VPS] Carpeta con la fecha del día:
```bash
sudo mkdir -p /root/backup-AAAAMMDD
```

[VPS] Todo lo que se va a sustituir (ejemplo: motor y `.env`):
```bash
sudo cp -a /root/ai_bridge/manolo_ari.py /root/ai_bridge/.env /root/backup-AAAAMMDD/
```

[VPS] Y `/etc/hosts` si vas a hacer las pruebas de fallback:
```bash
sudo cp -a /etc/hosts /root/backup-AAAAMMDD/
```

## 2. Subir a `/tmp`

[local] Código y ficheros de datos:
```bash
scp -P <SSH_PORT> vps_backend/manolo_ari.py vps_backend/healthcheck.py vps_backend/healthcheck_sample.wav <SSH_USER>@<VPS_HOST>:/tmp/
```

[local] Unidades de systemd:
```bash
scp -P <SSH_PORT> vps_backend/manolo-healthcheck.service vps_backend/manolo-healthcheck.timer <SSH_USER>@<VPS_HOST>:/tmp/
```

## 3. Colocar

[VPS] Un fichero por línea, con dueño root:
```bash
sudo install -o root -g root -m 644 /tmp/manolo_ari.py /root/ai_bridge/manolo_ari.py
```
```bash
sudo install -o root -g root -m 644 /tmp/healthcheck.py /root/ai_bridge/healthcheck.py
```
```bash
sudo install -o root -g root -m 644 /tmp/healthcheck_sample.wav /root/ai_bridge/healthcheck_sample.wav
```
```bash
sudo install -o root -g root -m 644 /tmp/manolo-healthcheck.service /etc/systemd/system/
```
```bash
sudo install -o root -g root -m 644 /tmp/manolo-healthcheck.timer /etc/systemd/system/
```

[VPS] Limpia `/tmp`:
```bash
rm /tmp/manolo_ari.py /tmp/healthcheck.py /tmp/healthcheck_sample.wav /tmp/manolo-healthcheck.*
```

## 4. Comprobar que compila

[VPS] Con el Python del venv, que es el de systemd. Debe decir `OK`:
```bash
sudo /root/ai_bridge/venv/bin/python3 -m py_compile /root/ai_bridge/manolo_ari.py /root/ai_bridge/healthcheck.py && echo OK
```

## 5. `.env`

[VPS] Añade o cambia variables (lista y significado en la sección 5 de `CLAUDE.md`):
```bash
sudo nano /root/ai_bridge/.env
```

[VPS] Solo root lo lee:
```bash
sudo chmod 600 /root/ai_bridge/.env
```

## 6. Timer del healthcheck

[VPS] Lee las unidades nuevas o cambiadas:
```bash
sudo systemctl daemon-reload
```

[VPS] Actívalo ahora y en cada arranque:
```bash
sudo systemctl enable --now manolo-healthcheck.timer
```

[VPS] Próxima ejecución:
```bash
systemctl list-timers manolo-healthcheck.timer
```

## 7. Reiniciar Manolo

[VPS] Debe decir `0 active calls`; reiniciar corta la llamada en curso:
```bash
sudo asterisk -rx "core show channels"
```

[VPS] Carga el código y el `.env` nuevos:
```bash
sudo systemctl restart manolo_ari
```

[VPS] Deben salir "Servidor UDP/RTP escuchando" y "Conectado al WebSocket de eventos ARI":
```bash
sudo journalctl -u manolo_ari -n 20 --no-pager
```

## 8. Healthcheck a mano

[VPS] Una ejecución:
```bash
sudo systemctl start manolo-healthcheck.service
```

[VPS] Esperado: una línea `OK` por comprobación y ningún email si todo va bien:
```bash
sudo journalctl -u manolo-healthcheck -n 15 --no-pager
```

Si falla `TTS`, revisa primero `TTS_VOICE` en el `.env`.

## 9. App (si cambia)

Instala el APK **encima** de la app, sin desinstalar: desinstalar borra los permisos
concedidos (así se perdió `CALL_PHONE` el 12/10/2026). Si da "conflicto con un paquete
existente", desinstala y vuelve a conceder Teléfono, Registro de llamadas, Contactos y
el servicio de Accesibilidad.

Después: `docs/runbooks/pruebas-fallback.md`.

---

## Vuelta atrás

[VPS] Sin llamadas en curso:
```bash
sudo asterisk -rx "core show channels"
```

[VPS] Restaura motor y `.env`:
```bash
sudo cp -a /root/backup-AAAAMMDD/manolo_ari.py /root/backup-AAAAMMDD/.env /root/ai_bridge/
```

[VPS] Reinicia y mira el arranque:
```bash
sudo systemctl restart manolo_ari
```
```bash
sudo journalctl -u manolo_ari -n 20 --no-pager
```

[VPS] Quitar el healthcheck, si es lo que se despliega:
```bash
sudo systemctl disable --now manolo-healthcheck.timer
```
```bash
sudo rm /etc/systemd/system/manolo-healthcheck.service /etc/systemd/system/manolo-healthcheck.timer
```
```bash
sudo systemctl daemon-reload
```
```bash
sudo rm -f /root/ai_bridge/healthcheck.py /root/ai_bridge/healthcheck_sample.wav /root/ai_bridge/healthcheck_state.json
```

[VPS] `/etc/hosts`, si una prueba lo dejó a medias:
```bash
sudo cp -a /root/backup-AAAAMMDD/hosts /etc/hosts
```

[Móvil] Instala encima el APK anterior.
