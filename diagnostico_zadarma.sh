#!/bin/bash
# Script de diagnóstico para Zadarma + Asterisk
# Ejecutar en el VPS: bash diagnostico_zadarma.sh

echo "==================================="
echo "DIAGNÓSTICO ZADARMA + ASTERISK"
echo "==================================="
echo ""

# 1. Habilitar logging de Asterisk
echo "📝 PASO 1: Habilitando logging de Asterisk..."
echo ""

# Backup del logger.conf original
if [ -f /etc/asterisk/logger.conf ]; then
    cp /etc/asterisk/logger.conf /etc/asterisk/logger.conf.backup
    echo "✅ Backup creado: /etc/asterisk/logger.conf.backup"
fi

# Crear configuración de logging
cat > /etc/asterisk/logger.conf << 'EOF'
[general]

[logfiles]
; Full log (todo) - IMPORTANTE para debug
full => notice,warning,error,debug,verbose

; Log de consola
console => notice,warning,error

; Log de mensajes importantes
messages => notice,warning,error
EOF

echo "✅ logger.conf configurado"

# Crear directorio de logs si no existe
mkdir -p /var/log/asterisk
chown asterisk:asterisk /var/log/asterisk 2>/dev/null || chown root:root /var/log/asterisk

# Recargar configuración de logging
echo "🔄 Recargando logger..."
asterisk -rx "logger reload" 2>/dev/null
asterisk -rx "core set verbose 5" 2>/dev/null
asterisk -rx "core set debug 5" 2>/dev/null

echo "✅ Logging habilitado: /var/log/asterisk/full"
echo ""

# 2. Verificar estado de Asterisk
echo "📊 PASO 2: Verificando estado de Asterisk..."
echo ""

if pgrep -x asterisk > /dev/null; then
    echo "✅ Asterisk está corriendo"

    # Mostrar versión
    echo ""
    echo "Versión de Asterisk:"
    asterisk -rx "core show version" 2>/dev/null | head -3

    # Endpoints PJSIP
    echo ""
    echo "🔌 Endpoints PJSIP registrados:"
    asterisk -rx "pjsip show endpoints" 2>/dev/null | grep -E "Endpoint|zadarma|====="

    # AORs
    echo ""
    echo "📞 AORs configurados:"
    asterisk -rx "pjsip show aors" 2>/dev/null | grep -E "Aor|zadarma|====="

else
    echo "❌ Asterisk NO está corriendo"
    echo "Iniciando Asterisk..."
    systemctl start asterisk 2>/dev/null || service asterisk start 2>/dev/null
    sleep 3
fi

echo ""

# 3. Verificar configuración de Zadarma
echo "📋 PASO 3: Verificando configuración de Zadarma..."
echo ""

echo "pjsip.conf (Zadarma sections):"
echo "---"
grep -A 10 "zadarma" /etc/asterisk/pjsip.conf 2>/dev/null || echo "⚠️ No se encontró configuración de zadarma en pjsip.conf"
echo ""

# 4. Verificar puertos
echo "🔌 PASO 4: Verificando puertos SIP..."
echo ""

netstat -tulpn | grep -E "5060|5061" | grep -v "grep"
if [ $? -eq 0 ]; then
    echo "✅ Puertos SIP abiertos"
else
    echo "⚠️ No se detectan puertos SIP abiertos (5060/5061)"
fi

echo ""

# 5. Verificar firewall
echo "🛡️ PASO 5: Verificando firewall..."
echo ""

if command -v ufw &> /dev/null; then
    echo "UFW status:"
    ufw status | grep -E "5060|5061|Status"
fi

if command -v iptables &> /dev/null; then
    echo ""
    echo "Reglas iptables para SIP:"
    iptables -L -n | grep -E "5060|5061" || echo "No hay reglas específicas para SIP"
fi

echo ""

# 6. Test de conectividad con Zadarma
echo "🌐 PASO 6: Testing conectividad con Zadarma..."
echo ""

echo "Ping a sips.zadarma.com:"
ping -c 3 sips.zadarma.com 2>/dev/null || echo "⚠️ No se pudo hacer ping a sips.zadarma.com"

echo ""
echo "Resolución DNS de sips.zadarma.com:"
nslookup sips.zadarma.com 2>/dev/null || dig sips.zadarma.com 2>/dev/null || echo "⚠️ No se pudo resolver DNS"

echo ""

# 7. Verificar extensions.conf
echo "📞 PASO 7: Verificando dialplan (extensions.conf)..."
echo ""

if [ -f /etc/asterisk/extensions.conf ]; then
    echo "Contexto [from-zadarma]:"
    grep -A 15 "\[from-zadarma\]" /etc/asterisk/extensions.conf 2>/dev/null || echo "⚠️ No se encontró contexto [from-zadarma]"
else
    echo "⚠️ extensions.conf no existe"
fi

echo ""

# 8. Mostrar últimas líneas del log
echo "📜 PASO 8: Últimas líneas del log de Asterisk..."
echo ""

if [ -f /var/log/asterisk/full ]; then
    echo "Últimas 20 líneas del log:"
    echo "---"
    tail -20 /var/log/asterisk/full
else
    echo "⚠️ Log aún no existe (se creará con la primera llamada)"
fi

echo ""
echo ""
echo "==================================="
echo "RESUMEN DE DIAGNÓSTICO"
echo "==================================="
echo ""
echo "✅ Pasos completados"
echo ""
echo "🔍 PARA DEBUGGING EN TIEMPO REAL:"
echo "   tail -f /var/log/asterisk/full"
echo ""
echo "📞 SIGUIENTE PASO:"
echo "   1. Llama al número +34 919 93 30 65 desde CUALQUIER teléfono"
echo "   2. Observa el log en tiempo real con: tail -f /var/log/asterisk/full"
echo "   3. Si no ves NADA en el log → El problema está en Zadarma (no llega la llamada al VPS)"
echo "   4. Si ves la llamada pero falla → El problema está en la configuración de Asterisk"
echo ""
echo "🔧 VERIFICAR EN PANEL DE ZADARMA:"
echo "   - Número +34 919 93 30 65 debe estar en estado 'Activo'"
echo "   - Debe tener configurado el destino PBX: 157.180.35.161"
echo "   - SIP trunk debe estar activo y conectado"
echo ""
echo "==================================="
