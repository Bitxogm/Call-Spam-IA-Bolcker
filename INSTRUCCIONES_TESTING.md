# 📱 Instrucciones para Probar la App en Tu Teléfono

## ✅ Cambios Implementados

### 🔧 Infraestructura
- ✅ Habilitado módulo nativo `CallInterceptorPackage`
- ✅ Agregados permisos críticos para gestión de llamadas
- ✅ Declarado servicio `CallInterceptorService` en manifest
- ✅ Implementada auto-respuesta de llamadas en `SpamCallService`

### 🤖 Funcionalidades Activas
- ✅ Detecta llamadas entrantes
- ✅ Auto-contesta números desconocidos/privados (después de 2 segundos)
- ✅ Protege números de emergencia (112, 911, 091, 092, 080, 061)
- ✅ Logging extensivo en Logcat para debugging
- ⏳ Integración con IA (preparada, requiere configuración)

---

## 🚀 Pasos para Probar en tu Teléfono

### 1️⃣ Configurar API Keys

**IMPORTANTE:** La app necesita al menos la API key de Gemini para funcionar.

```bash
# En la raíz del proyecto
cp .env.example .env
```

Edita el archivo `.env` y añade tus API keys:

```bash
# OBLIGATORIO - Obtener en: https://makersuite.google.com/app/apikey
EXPO_PUBLIC_GEMINI_API_KEY=tu_api_key_aqui

# OPCIONAL - Obtener en: https://elevenlabs.io/
EXPO_PUBLIC_ELEVENLABS_API_KEY=tu_api_key_aqui
```

**Nota sobre API keys:**
- **Gemini**: Gratuito, cuota generosa. Necesario para conversaciones IA.
- **ElevenLabs**: Opcional, 10K caracteres/mes gratis. Para voz realista. Si no tienes key, usará TTS nativo del teléfono.

---

### 2️⃣ Compilar la App

#### Opción A: Compilar APK directamente (Recomendado)

```bash
# Asegúrate de estar en la raíz del proyecto
cd android

# Limpiar builds anteriores
./gradlew clean

# Compilar APK de debug
./gradlew assembleDebug

# El APK estará en:
# android/app/build/outputs/apk/debug/app-debug.apk
```

#### Opción B: Usando Android Studio

1. Abre Android Studio
2. File → Open → Selecciona la carpeta `android/`
3. Espera a que sincronice Gradle
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. Cuando termine, click en "locate" para encontrar el APK

---

### 3️⃣ Instalar en tu Teléfono

#### Transferir e Instalar

```bash
# Método 1: ADB (si tienes el teléfono conectado por USB)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Método 2: Transferir manualmente
# - Copia app-debug.apk a tu teléfono (USB, email, Drive, etc.)
# - Abre el archivo desde el teléfono
# - Acepta "Instalar de fuentes desconocidas" si te lo pide
```

---

### 4️⃣ Configurar Permisos en el Teléfono

#### A. Conceder Permisos Básicos

Cuando abras la app por primera vez, concede:
- ✅ Teléfono (gestionar llamadas)
- ✅ Contactos (para modo radical)
- ✅ Micrófono (para grabar audio del spammer)
- ✅ Notificaciones (para servicio en segundo plano)

#### B. Configurar como Aplicación de Teléfono Predeterminada

**CRÍTICO:** Sin este paso, la app NO puede contestar llamadas.

**Método 1: Desde los Ajustes del Sistema**
1. Ajustes → Aplicaciones → Apps predeterminadas → Aplicación de teléfono
2. Selecciona "SpamBlockerApp"

**Método 2: Desde la App** (si implementaste el botón en la UI)
1. Abre SpamBlockerApp
2. Ve al Dashboard
3. Click en "Configurar como marcador predeterminado"
4. Acepta en el diálogo del sistema

#### C. Verificar que Funciona

En Android Studio Logcat (o adb logcat):
```bash
# Filtrar solo logs de nuestra app
adb logcat -s SpamCallService

# O más completo
adb logcat | grep -E "SpamCallService|CallInterceptor"
```

Deberías ver:
```
SpamCallService: 🚀 SpamCallService iniciado - Auto-respuesta: ACTIVADA
```

---

### 5️⃣ Probar la Detección de Llamadas

#### Prueba Básica (sin IA todavía)

1. **Llama a tu teléfono desde otro número**
   - Usa un número que NO esté en tus contactos
   - Mejor si es número privado/oculto

2. **Observa Logcat** (mientras recibes la llamada):
   ```bash
   adb logcat -s SpamCallService
   ```

3. **Logs Esperados:**
   ```
   📞 ========================================
   📞 LLAMADA DETECTADA
   📞 Número: +34612345678 (o "Número desconocido")
   📞 Estado: SONANDO
   📞 ========================================
   🤔 ¿Contestar con IA? SÍ/NO (Número: ...)
   🤖 DECISIÓN: Auto-contestar con IA (o "Permitir llamada normal")
   ```

4. **Si auto-contesta:**
   ```
   🤖 Contestando llamada automáticamente...
   ✅ Llamada contestada - Esperando activación para IA
   📞 Estado cambió a: ACTIVA
   🎙️ ========================================
   🎙️ LLAMADA ACTIVA - LISTA PARA IA
   🎙️ Número: +34612345678
   🎙️ ========================================
   🤖 [SIMULACIÓN] Agente IA: '¿Dígame? ¿Con quién hablo?'
   ```

---

## 🐛 Troubleshooting

### Problema: La app no contesta automáticamente

**Solución 1:** Verifica que eres app predeterminada
```
Ajustes → Apps → Apps predeterminadas → App de teléfono
Debe decir: "SpamBlockerApp"
```

**Solución 2:** Verifica permisos
```
Ajustes → Apps → SpamBlockerApp → Permisos
Debe tener TODOS los permisos concedidos
```

**Solución 3:** Revisa Logcat
```bash
adb logcat -s SpamCallService
# Busca errores con ❌
```

---

### Problema: No veo logs en Logcat

**Solución:**
```bash
# Reinicia el servidor ADB
adb kill-server
adb start-server

# Conecta el teléfono y verifica
adb devices

# Activa logs de desarrollador en el teléfono
# Ajustes → Acerca del teléfono → Toca "Número de compilación" 7 veces
# Ajustes → Opciones de desarrollador → Depuración USB: ON
```

---

### Problema: La app crashea al recibir llamada

**Solución 1:** Mira el stacktrace
```bash
adb logcat | grep -A 20 "FATAL EXCEPTION"
```

**Solución 2:** Reinstala limpiamente
```bash
# Desinstalar
adb uninstall com.anonymous.SpamBlockerApp

# Limpiar y recompilar
cd android
./gradlew clean
./gradlew assembleDebug

# Reinstalar
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

---

### Problema: Gemini API no funciona

**Verifica tu API key:**
```bash
# En Logcat busca:
GeminiService: 🔧 DEBUG Variables de entorno:
GeminiService: API Key configurada: SÍ/NO
```

**Si dice "NO":**
1. Verifica que creaste el archivo `.env` (no `.env.example`)
2. Verifica que la key empieza con `EXPO_PUBLIC_`
3. Reinicia el metro bundler:
   ```bash
   # Ctrl+C para detener
   # Luego:
   npx expo start -c
   ```

**Validar API key manualmente:**
```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=TU_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"contents":[{"parts":[{"text":"Hola"}]}]}'
```

---

## 📊 Comportamiento Actual

### ✅ Lo que YA funciona:
- Detecta llamadas entrantes
- Auto-contesta números desconocidos/privados
- Registra todo en Logcat con emojis para fácil lectura
- Protege números de emergencia
- No toca llamadas de contactos conocidos

### ⏳ Lo que está preparado pero requiere configuración:
- Conversación con Gemini IA (requiere API key en .env)
- TTS con ElevenLabs (opcional, requiere API key)
- Fallback a TTS nativo si no hay ElevenLabs

### 🚧 Lo que falta implementar (siguiente fase):
- Bridge de comunicación nativo → React Native
- Sistema de audio bidireccional (escuchar al spammer + responder)
- Integración completa con GeminiService desde el servicio nativo
- UI para ver llamadas en tiempo real
- Base de datos de spam acumulado

---

## 📞 Próximos Pasos

### Fase 2 (Integración IA Completa):
```
1. Crear bridge React Native ↔ Native Service
2. Implementar AudioRecord para capturar voz del spammer
3. Integrar con Speech-to-Text (transcribir spammer)
4. Enviar transcripción a GeminiService
5. Recibir respuesta de Gemini
6. Sintetizar con TTS (ElevenLabs o nativo)
7. Reproducir respuesta en la llamada
```

### Fase 3 (Mejoras):
```
1. UI para monitorear llamadas en tiempo real
2. Historial de conversaciones
3. Base de datos local de spam
4. Whitelist/blacklist personalizada
5. Modo "radical" mejorado
6. Sincronización cloud (opcional)
```

---

## 💡 Tips

1. **Testing con número privado/oculto:**
   - Es la mejor forma de probar sin afectar contactos
   - Configura otro teléfono para llamar con número oculto
   - Marcador → *Configuración* → Ocultar número

2. **Ver logs en tiempo real:**
   ```bash
   # Terminal con colores
   adb logcat -s SpamCallService -v color
   ```

3. **Backup de la app:**
   - Guarda el APK en un lugar seguro
   - Si algo falla, puedes volver a esta versión

4. **Revertir app de teléfono predeterminada:**
   - Ajustes → Apps → Apps predeterminadas → App de teléfono
   - Selecciona tu app de teléfono anterior (Google Teléfono, etc.)

---

## ❓ ¿Preguntas?

Si encuentras algún problema:
1. Revisa Logcat primero (busca ❌ o "ERROR")
2. Verifica que todos los permisos están concedidos
3. Asegúrate de ser app predeterminada
4. Comprueba que el archivo .env existe y tiene la API key

---

**¡Listo para probar!** 🚀

Recuerda: Por ahora la app **solo contesta números desconocidos/privados** para no interferir con tus llamadas normales. Esto se puede cambiar en `SpamCallService.java` línea 155 (método `shouldAnswerWithAI`).
