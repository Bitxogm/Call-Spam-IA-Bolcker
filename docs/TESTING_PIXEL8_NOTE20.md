# Testing Plan: Pixel 8 GrapheneOS + Samsung Galaxy Note 20

**Fecha:** 15 Enero 2026
**Branch:** `claude/ivr-bcp-method-01KAWsdwPsM88H55dEWN8Xqf`
**Objetivo:** Validar método BCP para inyección de audio IVR

---

## 📱 Tus Dispositivos

### **Dispositivo A: Google Pixel 8 (GrapheneOS)**
- ✅ **Compatible al 100%** con TYPE_TELEPHONY
- ✅ GrapheneOS permite root ADB fácilmente
- ✅ Dispositivo perfecto para método BCP
- **Rol:** Receptor de llamadas spam (donde se instala SpamBlocker)

### **Dispositivo B: Samsung Galaxy Note 20**
- ⚠️ **Posible compatibilidad** con TYPE_TELEPHONY (algunos Samsung lo soportan)
- ⚠️ Si no funciona BCP → usará fallback (speaker + mic)
- **Roles:**
  - Test 1: Llamar al Pixel 8 (simular spammer)
  - Test 2: Instalar SpamBlocker y probar si funciona BCP

---

## 🎯 Plan de Testing (3 Fases)

### **FASE 1: Validación en Pixel 8 GrapheneOS** ⭐ PRIORIDAD

**Objetivo:** Confirmar que TYPE_TELEPHONY existe y BCP funciona

#### **1.1. Compilar APK**

```bash
cd /home/user/Call-Spam-IA-Bolcker

# Asegurarte de estar en rama correcta
git checkout claude/ivr-bcp-method-01KAWsdwPsM88H55dEWN8Xqf

# Compilar release APK
npx expo run:android --variant release
```

El APK estará en:
```
android/app/build/outputs/apk/release/app-release.apk
```

---

#### **1.2. Habilitar Root ADB en GrapheneOS**

En el Pixel 8:
1. Ir a **Settings → Security → Debugging**
2. Activar **USB Debugging**
3. Conectar al PC
4. Aceptar fingerprint ADB

Para root ADB en GrapheneOS:
```bash
# Verificar conexión
adb devices

# Verificar root
adb shell su -c "echo test"
```

**Si no tienes root en GrapheneOS:**
- GrapheneOS NO viene con root por defecto
- Necesitas instalar Magisk:
  1. Download Magisk APK desde: https://github.com/topjohnwu/Magisk/releases
  2. Patch boot image
  3. Flash con fastboot

**Alternativa sin root:** Crear custom ROM con SpamBlocker preinstalado (más complejo)

---

#### **1.3. Instalar como App de Sistema**

```bash
# Ejecutar script automático
./android/install-as-system-app.sh android/app/build/outputs/apk/release/app-release.apk
```

El script hará:
- ✅ Montar /system como RW
- ✅ Copiar APK a /system/priv-app/SpamBlocker/
- ✅ Copiar permisos a /system/etc/permissions/
- ✅ Establecer propietario root y permisos 644
- ✅ Remontar /system como RO
- ✅ Preguntar si reiniciar

**Responde "s" para reiniciar.**

---

#### **1.4. Verificación Post-Reboot**

Después de que el Pixel 8 reinicie:

```bash
# 1. Verificar que está instalado como sistema
adb shell pm path com.anonymous.SpamBlockerApp

# Debe mostrar:
# package:/system/priv-app/SpamBlocker/SpamBlocker.apk

# 2. Verificar permisos privilegiados
adb shell dumpsys package com.anonymous.SpamBlockerApp | grep "MODIFY"

# Debe mostrar:
# android.permission.MODIFY_PHONE_STATE: granted=true
# android.permission.MODIFY_AUDIO_ROUTING: granted=true
```

---

#### **1.5. Configurar App**

En el Pixel 8:
1. Abrir **Spam Blocker**
2. Conceder TODOS los permisos (llamadas, contactos, etc.)
3. Activar servicios:
   - **CallScreeningService** ✅
   - **Accessibility Service** ✅ (si lo usa)
   - **InCallService** ✅
4. Ir a **Answer+Hangup Settings**
5. Seleccionar **Modo 2: IVR Corporativo**
6. Delay: 0 segundos
7. Guardar

---

#### **1.6. Test de Dispositivos Audio**

Antes de probar llamada, verificar que TYPE_TELEPHONY existe:

```bash
# Iniciar logcat
adb logcat -c  # Limpiar log
adb logcat -s IVRAudioTrackPlayer:* | tee pixel8-test.log
```

Ahora desde el **Samsung Note 20**, llama al **Pixel 8**.

---

#### **1.7. Logs Esperados (ÉXITO)**

Si TYPE_TELEPHONY existe y BCP funciona:

```
D IVRAudioTrackPlayer: 🔊 Iniciando IVR AudioTrack desde: /data/user/0/.../ivr_corporativo.mp3
D IVRAudioTrackPlayer: 🔍 Buscando dispositivo TYPE_TELEPHONY entre 8 dispositivos
D IVRAudioTrackPlayer:   📱 Dispositivo: Earpiece - Tipo: EARPIECE (1)
D IVRAudioTrackPlayer:   📱 Dispositivo: Speaker - Tipo: SPEAKER (2)
D IVRAudioTrackPlayer:   📱 Dispositivo: Telephony - Tipo: TYPE_TELEPHONY (18)  ← CLAVE
I IVRAudioTrackPlayer:   ✅ TYPE_TELEPHONY encontrado: Telephony
I IVRAudioTrackPlayer: ✅ setPreferredDevice(TYPE_TELEPHONY) exitoso - Audio se inyectará en uplink
I IVRAudioTrackPlayer: ✅ AudioTrack iniciado con TYPE_TELEPHONY - Audio inyectado en UPLINK
```

**Resultado desde Note 20:**
- ✅ Llamada contestada automáticamente
- ✅ **ESCUCHAS** mensaje IVR: "Este número no admite llamadas comerciales..."
- ✅ Después de ~30s, llamada se cuelga

---

#### **1.8. Logs Esperados (FALLBACK)**

Si TYPE_TELEPHONY NO existe (improbable en Pixel):

```
W IVRAudioTrackPlayer: ⚠️ TYPE_TELEPHONY no disponible en este dispositivo
W IVRAudioTrackPlayer: ⚠️ Usando fallback (speaker + mic)
```

**Resultado desde Note 20:**
- ⚠️ Llamada contestada
- ⚠️ Escuchas algo distorsionado o eco
- ⚠️ Calidad muy mala

---

### **FASE 2: Testing en Samsung Note 20** (Secundario)

Una vez confirmado que funciona en Pixel 8, probar en Note 20:

```bash
# 1. Desinstalar de Pixel 8 (opcional)
# 2. Instalar APK en Note 20 como sistema
./android/install-as-system-app.sh android/app/build/outputs/apk/release/app-release.apk
```

**Nota:** Samsung requiere root con Magisk.

#### **Expectativa:**
- ⚠️ ~50% probabilidad de que Note 20 tenga TYPE_TELEPHONY
- ✅ Si lo tiene → BCP funciona igual que Pixel
- ❌ Si no lo tiene → fallback (mala calidad)

#### **Verificar:**
```bash
adb logcat -s IVRAudioTrackPlayer | grep "TYPE_TELEPHONY"
```

Si ves "TYPE_TELEPHONY encontrado" → ¡Note 20 es compatible! ✅

---

### **FASE 3: Comparativa de Resultados**

Completar tabla después de tests:

| Dispositivo | TYPE_TELEPHONY | BCP Funciona | Caller Escucha IVR | Calidad |
|-------------|----------------|--------------|-------------------|---------|
| Pixel 8 (GrapheneOS) | ✅ / ❌ | ✅ / ❌ | ✅ / ❌ | Perfecta / Mala |
| Samsung Note 20 | ✅ / ❌ | ✅ / ❌ | ✅ / ❌ | Perfecta / Mala |

---

## 🐛 Troubleshooting

### **Problema: "No such file or directory: /system"**

**Solución:**
```bash
# GrapheneOS usa /system_root en algunos casos
adb shell su -c "mount -o rw,remount /system_root"
adb shell su -c "mkdir -p /system_root/system/priv-app/SpamBlocker"
# etc.
```

---

### **Problema: "setPreferredDevice() falló"**

**Causa:** Permisos privilegiados no concedidos

**Solución:**
```bash
# Verificar archivo de permisos existe
adb shell su -c "ls /system/etc/permissions/privapp-permissions-spamblocker.xml"

# Si no existe, copiar manualmente
adb push android/privapp-permissions-spamblocker.xml /sdcard/
adb shell su -c "cp /sdcard/privapp-permissions-spamblocker.xml /system/etc/permissions/"
adb shell su -c "chmod 644 /system/etc/permissions/privapp-permissions-spamblocker.xml"
adb reboot
```

---

### **Problema: "Caller no escucha nada"**

**Debug:**
```bash
# Ver TODOS los dispositivos detectados
adb logcat -s IVRAudioTrackPlayer:* | grep "Dispositivo:"

# Si NO aparece TYPE_TELEPHONY (18):
# → Dispositivo no compatible
# → Usar fallback o considerar arquitectura VoIP
```

---

## 📊 Información a Recopilar

Durante los tests, guardar:

### **1. Logs completos:**
```bash
adb logcat > logs-pixel8-test1.txt
```

### **2. Lista de dispositivos audio:**
```bash
adb shell dumpsys audio | grep -A50 "Available AudioDeviceInfo"
```

### **3. Información del sistema:**
```bash
adb shell getprop | grep -E "ro.product.model|ro.build.version.release|ro.hardware"
```

---

## ✅ Criterios de Éxito

### **Éxito Completo:**
- ✅ Pixel 8 tiene TYPE_TELEPHONY
- ✅ setPreferredDevice() exitoso
- ✅ Caller escucha IVR perfectamente
- ✅ Sin eco, sin distorsión
- ✅ Logs muestran "Audio inyectado en UPLINK"

### **Éxito Parcial:**
- ✅ Pixel 8 funciona con BCP
- ❌ Note 20 usa fallback (calidad mala)
- **Conclusión:** BCP funciona, pero solo en Pixel/compatible

### **Fallo:**
- ❌ Ni Pixel 8 tiene TYPE_TELEPHONY (muy improbable)
- ❌ setPreferredDevice() siempre falla
- **Acción:** Revisar instalación, permisos, o buscar alternativa

---

## 🚀 Próximos Pasos (Si Funciona)

1. **Crear Módulo Magisk** para instalación fácil
2. **Integrar Gemini API** para conversación IA con spammer
3. **Probar en más dispositivos** (Pixel 3/4/5, Samsung S20/S21)
4. **Publicar en XDA Developers** para comunidad
5. **App base en Play Store** + módulo separado

---

## 📝 Notas

- **GrapheneOS:** Si tienes problemas con root, considera usar LineageOS que viene con root ADB
- **Magisk:** Si Pixel 8 GrapheneOS no tiene Magisk, necesitas instalarlo primero
- **Backup:** Siempre haz backup antes de modificar /system

---

**¡Buena suerte con los tests!** 🎉

Comparte los resultados (logs, tabla comparativa) para analizar y mejorar.
