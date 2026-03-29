# Instrucciones de Despliegue en VPS (Hetzner)

He preparado estos archivos para que el **Modo 3 (Zadarma)** sea totalmente dinámico desde tu App.

## 1. Subir archivos al VPS
Copia los archivos de la carpeta `vps_backend` a tu carpeta `/root/ai_bridge/` en el VPS:
- `control_api.py` -> `/root/ai_bridge/control_api.py`
- `agi-bin/decision_agi.py` -> `/var/lib/asterisk/agi-bin/decision_agi.py`

## 2. Instalar Flask (en el venv del VPS)
En tu terminal de Hetzner:
```bash
cd /root/ai_bridge
source venv/bin/activate
pip install flask
```

## 3. Configurar Permisos
Asegúrate de que Asterisk pueda ejecutar el nuevo AGI:
```bash
chmod +x /var/lib/asterisk/agi-bin/decision_agi.py
chown asterisk:asterisk /var/lib/asterisk/agi-bin/decision_agi.py
```

## 4. Iniciar la API de Control
Para que la App pueda hablar con el servidor:
```bash
screen -S control_api
source venv/bin/activate
python3 control_api.py
# (Presiona Ctrl+A, luego D para dejarlo en segundo plano)
```

## 5. Actualizar Asterisk (`extensions.conf`)
Edita tu `/etc/asterisk/extensions.conf` para usar el `decision_agi.py` en la entrada de llamadas:

```ini
[from-zadarma]
exten => _.,1,NoOp(Llamada entrante de Zadarma)
exten => _.,n,Answer()
exten => _.,n,AGI(decision_agi.py) ; <-- Este ahora decide si poner mensaje fijo o Víctor IA
exten => _.,n,Hangup()
```

---
**Nota:** El archivo `decision_agi.py` ahora lee lo que la App le dice y decide si debe ejecutar `victor_agi.py` (IA) o si simplemente pone un audio fijo de spam.
