#!/bin/bash
# deploy.sh - Script de despliegue para el VPS (Hetzner)
# Este script automatiza la instalación de dependencias y configuración básica.

set -e

echo "🚀 Iniciando despliegue de Víctor IA Bridge en VPS..."

# 1. Crear directorio de trabajo
mkdir -p /root/ai_bridge
cd /root/ai_bridge

# 2. Instalar dependencias del sistema
echo "📦 Instalando dependencias del sistema..."
apt-get update
apt-get install -y python3-pip python3-venv ffmpeg screen asterisk

# 3. Configurar entorno virtual de Python
echo "🐍 Configurando entorno virtual..."
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi
source venv/bin/activate
pip install flask gtts

# 4. Los archivos deben ser subidos manualmente o vía git, 
# pero nos aseguramos de que el AGI esté en su sitio si el usuario ya los copió.
echo "⚙️ Configurando permisos de Asterisk..."
if [ -f "/root/ai_bridge/agi-bin/decision_agi.py" ]; then
    cp /root/ai_bridge/agi-bin/decision_agi.py /var/lib/asterisk/agi-bin/
    chmod +x /var/lib/asterisk/agi-bin/decision_agi.py
    chown asterisk:asterisk /var/lib/asterisk/agi-bin/decision_agi.py
    echo "✅ decision_agi.py instalado en Asterisk"
else
    echo "⚠️ Advertencia: agi-bin/decision_agi.py no encontrado en /root/ai_bridge/"
fi

echo ""
echo "🎉 ¡Preparación del sistema completada!"
echo "======================================="
echo "PASOS RESTANTES:"
echo "1. Asegúrate de que Zadarma esté configurado en /etc/asterisk/sip.conf o pjsip.conf"
echo "2. Añade la lógica a /etc/asterisk/extensions.conf (ver README_VPS.md)"
echo "3. Inicia la API de control:"
echo "   screen -S victor_api"
echo "   source /root/ai_bridge/venv/bin/activate"
echo "   python3 control_api.py"
echo "======================================="
