#!/bin/bash
# Deploy Modo 3 — Manolo AGI
echo "=== Instalando dependencias ==="
/root/ai_bridge/venv/bin/pip install gtts SpeechRecognition

echo "=== Copiando scripts ==="
sudo cp vps_backend/victor_agi.py /var/lib/asterisk/agi-bin/victor_agi.py
sudo cp vps_backend/decision_agi.py /root/ai_bridge/decision_agi.py
sudo cp /root/ai_bridge/decision_agi.py /var/lib/asterisk/agi-bin/decision_agi.py

echo "=== Permisos ==="
sudo chown asterisk:asterisk /var/lib/asterisk/agi-bin/victor_agi.py
sudo chown asterisk:asterisk /var/lib/asterisk/agi-bin/decision_agi.py
sudo chmod +x /var/lib/asterisk/agi-bin/victor_agi.py
sudo chmod +x /var/lib/asterisk/agi-bin/decision_agi.py

echo "=== Reiniciando Asterisk ==="
sudo systemctl restart asterisk

echo "=== Listo ==="
echo "Modo 3 (Manolo) desplegado correctamente"
