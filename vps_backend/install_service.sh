#!/bin/bash
# install_service.sh - Instala el servicio systemd en el VPS

set -e

echo "🔧 Instalando servicio systemd para Asterisk Control API..."

# Copiar archivo de servicio
cp asterisk-control-api.service /etc/systemd/system/

# Recargar systemd
systemctl daemon-reload

# Habilitar para que arranque en boot
systemctl enable asterisk-control-api.service

# Iniciar el servicio
systemctl start asterisk-control-api.service

# Verificar estado
systemctl status asterisk-control-api.service

echo ""
echo "✅ Servicio instalado y arrancado correctamente"
echo "📋 Comandos útiles:"
echo "   systemctl status asterisk-control-api   # Ver estado"
echo "   systemctl restart asterisk-control-api  # Reiniciar"
echo "   journalctl -u asterisk-control-api -f   # Ver logs en tiempo real"
