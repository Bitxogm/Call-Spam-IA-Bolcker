# Runbook — contraseña de aplicación de Google para el healthcheck

`healthcheck.py` envía por `smtp.gmail.com:587` (STARTTLS) con la cuenta de
`ALERT_EMAIL_FROM`. Gmail no acepta la contraseña normal: hace falta una
contraseña de aplicación.

1. La cuenta remitente necesita la verificación en dos pasos activa:
   https://myaccount.google.com/signinoptions/twosv
2. Entra en https://myaccount.google.com/apppasswords, nombre "Manolo healthcheck", **Crear**.
3. Google muestra 16 caracteres **una sola vez**. Cópialos **sin espacios**.

[VPS] Ponlos en el `.env`:
```bash
sudo nano /root/ai_bridge/.env
```
```
ALERT_EMAIL_FROM=remitente@gmail.com
ALERT_EMAIL_TO=destino@gmail.com
ALERT_EMAIL_PASSWORD=xxxxxxxxxxxxxxxx
```

No hace falta reiniciar nada: el timer carga el `.env` en cada ejecución.

Para comprobarlo, la prueba 4 de `docs/runbooks/pruebas-fallback.md`.

Para revocarla: la misma página de contraseñas de aplicación, papelera junto a "Manolo healthcheck".
