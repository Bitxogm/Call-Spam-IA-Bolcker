#!/bin/bash
# Deploy Modo 3 — Manolo AGI
echo "=== Instalando dependencias ==="
/root/ai_bridge/venv/bin/pip install gtts SpeechRecognition

echo "=== Copiando scripts ==="
sudo cp vps_backend/manolo_agi.py /usr/share/asterisk/agi-bin/manolo_agi.py
sudo cp vps_backend/extensions.conf /etc/asterisk/extensions.conf

echo "=== Permisos ==="
sudo chown asterisk:asterisk /usr/share/asterisk/agi-bin/manolo_agi.py
sudo chmod +x /usr/share/asterisk/agi-bin/manolo_agi.py

echo "=== Reiniciando Asterisk ==="
sudo systemctl restart asterisk

echo "=== Listo ==="
echo "Modo 3 (Manolo) desplegado correctamente"
