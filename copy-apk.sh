#!/bin/bash

# Script para copiar APK con fecha y versión única
# Uso: ./copy-apk.sh [descripcion_opcional]

# Obtener fecha y hora actual
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Descripción opcional (primer argumento)
DESCRIPTION=${1:-"build"}

# Nombre del APK de salida
APK_SOURCE="android/app/build/outputs/apk/debug/app-debug.apk"
APK_DEST="$HOME/Downloads/Call-Spam-Blocker_${DESCRIPTION}_${TIMESTAMP}.apk"

# Verificar que existe el APK fuente
if [ ! -f "$APK_SOURCE" ]; then
    echo "❌ Error: No se encuentra el APK en $APK_SOURCE"
    echo "🔨 Ejecuta primero: ./build-fresh.sh"
    exit 1
fi

# Copiar con nombre único
cp "$APK_SOURCE" "$APK_DEST"

if [ $? -eq 0 ]; then
    echo "✅ APK copiado exitosamente:"
    echo "📦 $APK_DEST"
    echo ""
    echo "📊 Tamaño: $(du -h "$APK_DEST" | cut -f1)"
    echo "🕐 Fecha: $(date)"
    echo ""
    echo "📱 Para instalar:"
    echo "   adb install \"$APK_DEST\""
else
    echo "❌ Error copiando APK"
    exit 1
fi
