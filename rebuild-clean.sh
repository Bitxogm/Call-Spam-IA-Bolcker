#!/bin/bash
# rebuild-clean.sh - Script de reconstrucción completa con limpieza de caché
# Este script limpia TODOS los cachés y reconstruye el APK con el bundle actualizado

echo "🧹 INICIANDO LIMPIEZA COMPLETA..."
echo ""

# 1. Matar procesos Metro y Gradle existentes
echo "1️⃣ Matando procesos existentes..."
pkill -f "expo start" 2>/dev/null || true
pkill -f "react-native start" 2>/dev/null || true
pkill -f "metro" 2>/dev/null || true
pkill -f "gradle" 2>/dev/null || true
sleep 2
echo "✅ Procesos terminados"
echo ""

# 2. Limpiar caché de Metro bundler
echo "2️⃣ Limpiando caché de Metro bundler..."
rm -rf .metro
rm -rf $TMPDIR/metro-* 2>/dev/null || true
rm -rf $TMPDIR/react-* 2>/dev/null || true
echo "✅ Caché de Metro limpiado"
echo ""

# 3. Limpiar caché de node_modules
echo "3️⃣ Limpiando caché de node_modules..."
rm -rf node_modules/.cache
echo "✅ Caché de node_modules limpiado"
echo ""

# 4. Limpiar builds de Android
echo "4️⃣ Limpiando builds de Android..."
cd android
./gradlew clean
rm -rf app/build
rm -rf build
rm -rf .gradle
cd ..
echo "✅ Builds de Android limpios"
echo ""

# 5. Limpiar caché global de Gradle (opcional pero recomendado)
echo "5️⃣ Limpiando caché de Gradle..."
rm -rf ~/.gradle/caches/
echo "✅ Caché de Gradle limpiado"
echo ""

# 6. Reconstruir con bundle limpio
echo "6️⃣ Reconstruyendo APK con bundle JavaScript actualizado..."
cd android
./gradlew assembleDebug --no-daemon --rerun-tasks
cd ..
echo ""

# 7. Verificar que el APK se creó
if [ -f "android/app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ ¡APK CREADO EXITOSAMENTE!"
    echo ""
    echo "📦 Ubicación: android/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📱 PASOS SIGUIENTES:"
    echo "   1. Desinstala la app antigua de tu teléfono"
    echo "   2. Instala el nuevo APK:"
    echo "      adb install -r android/app/build/outputs/apk/debug/app-debug.apk"
    echo "   3. Abre la app y verifica los cambios"
    echo ""
    ls -lh android/app/build/outputs/apk/debug/app-debug.apk
else
    echo "❌ ERROR: No se pudo crear el APK"
    echo "Revisa los logs arriba para ver qué falló"
    exit 1
fi
