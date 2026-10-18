# Runbook — rotar `CONTROL_API_TOKEN`

El token viaja incrustado en cada APK (`EXPO_PUBLIC_CONTROL_API_TOKEN`), así que
hay que rotarlo cada vez que un APK salga de las manos de Víctor o el token
aparezca en un chat. La fecha de la última rotación está en `CLAUDE.local.md`.

[VPS] 1. Genera uno nuevo:
```bash
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

[VPS] 2. Sustitúyelo en `CONTROL_API_TOKEN=`:
```bash
sudo nano /root/ai_bridge/.env
```

[VPS] 3. Reinicia la API:
```bash
sudo systemctl restart asterisk-control-api
```

[VPS] 4. El log debe decir "🔑 Token: configurado":
```bash
sudo journalctl -u asterisk-control-api -n 5 --no-pager
```

[VPS] 5. El viejo ya no vale. Debe dar `401`:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "X-API-Key: <TOKEN-VIEJO>" https://<API_HOST>/get_mode
```

[local] 6. Pon el nuevo en `EXPO_PUBLIC_CONTROL_API_TOKEN` del `.env` y recompila:
```bash
./build-fresh.sh
```

[local] 7. Copia el APK con fecha:
```bash
./copy-apk.sh token-rotado
```

8. Instálalo **encima** de la app (sin desinstalar, para no perder permisos).

⚠️ **Borra los APK antiguos de `~/Downloads`.** Cada uno lleva incrustado el token de
su compilación. Tras rotar ya no les sirve, pero siguen siendo material sensible.
