#!/bin/bash

###############################################################################
# SpamBlocker - Instalador como App de Sistema (BCP Method)
#
# Este script instala SpamBlocker como app privilegiada de sistema
# para permitir inyección de audio IVR en uplink telefónico
#
# Requisitos:
# - ADB instalado y en PATH
# - Dispositivo con root (Magisk, GrapheneOS, LineageOS)
# - Bootloader desbloqueado
#
# Uso:
#   ./install-as-system-app.sh <ruta-al-apk>
#
# Ejemplo:
#   ./install-as-system-app.sh android/app/build/outputs/apk/release/app-release.apk
#
###############################################################################

set -e  # Exit on error

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Variables
APK_PATH="$1"
PACKAGE_NAME="com.anonymous.SpamBlockerApp"
SYSTEM_APP_DIR="/system/priv-app/SpamBlocker"
PERMISSIONS_FILE="android/privapp-permissions-spamblocker.xml"
SYSTEM_PERMISSIONS_DIR="/system/etc/permissions"

###############################################################################
# Funciones auxiliares
###############################################################################

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_info() {
    echo -e "${GREEN}ℹ️  $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

check_adb() {
    if ! command -v adb &> /dev/null; then
        print_error "ADB no encontrado. Por favor instala Android SDK Platform Tools."
        exit 1
    fi
}

check_device() {
    if ! adb get-state &> /dev/null; then
        print_error "No hay dispositivo conectado. Conecta tu dispositivo y activa USB debugging."
        exit 1
    fi
}

check_root() {
    if ! adb shell su -c "echo test" &> /dev/null; then
        print_error "Dispositivo no tiene root o no se concedió permiso su."
        print_warning "Requiere: Magisk, GrapheneOS root ADB, o LineageOS"
        exit 1
    fi
}

check_apk() {
    if [ -z "$APK_PATH" ]; then
        print_error "Debes proporcionar la ruta al APK"
        echo "Uso: $0 <ruta-al-apk>"
        exit 1
    fi

    if [ ! -f "$APK_PATH" ]; then
        print_error "APK no encontrado: $APK_PATH"
        exit 1
    fi
}

check_permissions_file() {
    if [ ! -f "$PERMISSIONS_FILE" ]; then
        print_error "Archivo de permisos no encontrado: $PERMISSIONS_FILE"
        exit 1
    fi
}

###############################################################################
# Proceso de instalación
###############################################################################

install_system_app() {
    print_header "📱 Instalando SpamBlocker como App de Sistema"

    # 1. Verificar si app ya está instalada (versión normal)
    print_info "Verificando instalación existente..."
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        print_warning "App ya instalada. Desinstalando versión anterior..."
        adb uninstall "$PACKAGE_NAME" || true
    fi

    # 2. Montar /system como RW
    print_info "Montando /system como lectura-escritura..."
    adb shell su -c "mount -o rw,remount /system" || {
        print_error "No se pudo montar /system como RW"
        print_warning "Intentando con /system_root..."
        adb shell su -c "mount -o rw,remount /system_root"
    }

    # 3. Crear directorio para la app
    print_info "Creando directorio $SYSTEM_APP_DIR..."
    adb shell su -c "mkdir -p $SYSTEM_APP_DIR"

    # 4. Copiar APK al dispositivo (primero a /sdcard, luego a /system)
    print_info "Copiando APK al dispositivo..."
    adb push "$APK_PATH" /sdcard/SpamBlocker.apk

    print_info "Moviendo APK a $SYSTEM_APP_DIR..."
    adb shell su -c "cp /sdcard/SpamBlocker.apk $SYSTEM_APP_DIR/SpamBlocker.apk"
    adb shell su -c "rm /sdcard/SpamBlocker.apk"

    # 5. Establecer permisos del APK
    print_info "Estableciendo permisos del APK..."
    adb shell su -c "chmod 644 $SYSTEM_APP_DIR/SpamBlocker.apk"
    adb shell su -c "chown root:root $SYSTEM_APP_DIR/SpamBlocker.apk"

    # 6. Copiar archivo de permisos privilegiados
    print_info "Copiando permisos privilegiados..."
    adb push "$PERMISSIONS_FILE" /sdcard/privapp-permissions-spamblocker.xml
    adb shell su -c "cp /sdcard/privapp-permissions-spamblocker.xml $SYSTEM_PERMISSIONS_DIR/"
    adb shell su -c "rm /sdcard/privapp-permissions-spamblocker.xml"

    # 7. Establecer permisos del archivo XML
    print_info "Estableciendo permisos del XML..."
    adb shell su -c "chmod 644 $SYSTEM_PERMISSIONS_DIR/privapp-permissions-spamblocker.xml"
    adb shell su -c "chown root:root $SYSTEM_PERMISSIONS_DIR/privapp-permissions-spamblocker.xml"

    # 8. Remontar /system como RO
    print_info "Remontando /system como solo lectura..."
    adb shell su -c "mount -o ro,remount /system" || {
        adb shell su -c "mount -o ro,remount /system_root"
    }

    print_success "Instalación completada!"
}

verify_installation() {
    print_header "🔍 Verificando Instalación"

    # Verificar que APK está en /system
    print_info "Verificando ubicación del APK..."
    if adb shell su -c "ls $SYSTEM_APP_DIR/SpamBlocker.apk" &> /dev/null; then
        print_success "APK encontrado en $SYSTEM_APP_DIR/"
    else
        print_error "APK NO encontrado en $SYSTEM_APP_DIR/"
        return 1
    fi

    # Verificar permisos
    print_info "Verificando archivo de permisos..."
    if adb shell su -c "ls $SYSTEM_PERMISSIONS_DIR/privapp-permissions-spamblocker.xml" &> /dev/null; then
        print_success "Permisos privilegiados configurados"
    else
        print_error "Archivo de permisos NO encontrado"
        return 1
    fi

    print_success "Verificación completa!"
}

reboot_device() {
    print_header "🔄 Reiniciando Dispositivo"

    print_warning "El dispositivo necesita reiniciarse para que los cambios tomen efecto."
    read -p "¿Deseas reiniciar ahora? (s/n): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Ss]$ ]]; then
        print_info "Reiniciando..."
        adb reboot
        print_success "Dispositivo reiniciándose..."
        print_info "Espera ~30 segundos y verifica la instalación"
    else
        print_warning "Recuerda reiniciar manualmente para completar la instalación"
    fi
}

post_reboot_check() {
    print_header "✅ Pasos Post-Instalación"

    echo ""
    print_info "Después del reinicio, verifica que:"
    echo "  1. La app aparece en el launcher como 'Spam Blocker'"
    echo "  2. Ejecuta: adb shell pm path $PACKAGE_NAME"
    echo "     Debe mostrar: package:$SYSTEM_APP_DIR/SpamBlocker.apk"
    echo ""
    echo "  3. Abre la app y configura Modo 2 (IVR Corporativo)"
    echo "  4. Haz una llamada de prueba y revisa logs:"
    echo "     adb logcat -s IVRAudioTrackPlayer | grep TYPE_TELEPHONY"
    echo ""
    print_info "Si ves 'TYPE_TELEPHONY encontrado' → ¡Éxito! ✅"
    print_warning "Si ves 'TYPE_TELEPHONY no disponible' → Dispositivo no compatible ⚠️"
    echo ""
}

###############################################################################
# Main
###############################################################################

main() {
    print_header "🚀 SpamBlocker System App Installer"

    # Verificaciones previas
    print_info "Ejecutando verificaciones previas..."
    check_adb
    check_device
    check_root
    check_apk
    check_permissions_file

    print_success "Todas las verificaciones pasadas!"
    echo ""

    # Mostrar info del dispositivo
    DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
    ANDROID_VERSION=$(adb shell getprop ro.build.version.release | tr -d '\r')

    print_info "Dispositivo: $DEVICE_MODEL"
    print_info "Android: $ANDROID_VERSION"
    echo ""

    # Confirmación
    print_warning "ADVERTENCIA: Esta instalación requiere modificar /system"
    print_warning "Asegúrate de tener backup en caso de problemas"
    echo ""
    read -p "¿Continuar con la instalación? (s/n): " -n 1 -r
    echo

    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        print_info "Instalación cancelada"
        exit 0
    fi

    # Instalación
    install_system_app
    echo ""

    # Verificación
    verify_installation
    echo ""

    # Reinicio
    reboot_device
    echo ""

    # Instrucciones post-reboot
    post_reboot_check

    print_success "¡Proceso completado!"
}

# Ejecutar
main
