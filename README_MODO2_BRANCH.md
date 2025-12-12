# 📦 Branch: Modo 2 - IVR Corporativo (Experimental)

**Branch Name:** `claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf`
**Status:** ⚠️ Experimental - NO funcional para producción
**Date:** Diciembre 2025

---

## ⚠️ IMPORTANTE

**Este branch contiene el intento de implementación de Modo 2 (IVR local) que NO funciona debido a limitaciones fundamentales de Android.**

**NO usar este código para producción. Ver branch `claude/modo1-optimized` para código funcional.**

---

## 🎯 Propósito de Este Branch

Este branch sirve como:

1. **📚 Referencia técnica** - Documentación de lo intentado
2. **🔬 Aprendizaje** - Código que muestra cómo funcionan APIs de Android
3. **💡 Base para VoIP** - Código reutilizable si se implementa arquitectura VoIP

---

## 📁 Estructura del Código

### **Archivos Principales (Modo 2):**

```
android/app/src/main/java/.../
├── IVRAudioPlayer.java              # ⚠️ MediaPlayer - no transmite
├── IVRAudioTrackPlayer.java         # ⚠️ AudioTrack + MediaCodec - no transmite
├── InCallActivity.java              # ⚠️ UI de llamada - problemática
└── SpamCallService.java             # ⚠️ Configurado para Default Dialer

android/app/src/main/res/
├── layout/
│   └── activity_in_call.xml         # UI de InCallActivity
└── drawable/
    ├── button_hangup.xml
    └── button_secondary.xml
```

### **Archivos Reutilizables (Modo 1):**

```
android/app/src/main/java/.../
├── CallAccessibilityService.java    # ✅ Detección de llamadas
├── AnswerHangupHelper.java          # ✅ Lógica de decisión
├── CallHistoryHelper.java           # ✅ Historial
├── LogsHelper.java                  # ✅ Sistema de logs
└── AnswerHangupModule.java          # ✅ React Native bridge
```

---

## 🔬 Qué Se Intentó

### **Intento 1: MediaPlayer**
```java
AudioAttributes attrs = new AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .build();
mediaPlayer.setAudioAttributes(attrs);
```
**Resultado:** Audio en earpiece local, NO transmite.

### **Intento 2: AudioTrack + MediaCodec**
```java
AudioTrack audioTrack = new AudioTrack(
    AudioManager.STREAM_VOICE_CALL,
    sampleRate, channelConfig,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize, AudioTrack.MODE_STREAM
);
```
**Resultado:** Audio en speaker local, NO transmite.

### **Intento 3: Workaround Speaker + Mic**
```java
audioManager.setSpeakerphoneOn(true);
// Esperando que el micrófono capte el audio del speaker
```
**Resultado:** AEC (Acoustic Echo Cancellation) filtra el audio.

---

## ❌ Por Qué NO Funciona

Ver documentación detallada: [docs/MODO2_ESTADO_ACTUAL.md](docs/MODO2_ESTADO_ACTUAL.md)

**Resumen:**
- Android NO permite inyectar audio en call uplink desde apps normales
- Solo apps del sistema tienen acceso completo
- APIs cerradas desde Android 6-10
- VoIP es la única solución real

---

## 🚀 Alternativas Viables

Ver documentación completa: [docs/OPCIONES_FUTURAS_IVR.md](docs/OPCIONES_FUTURAS_IVR.md)

### **Opción Recomendada: VoIP/Asterisk**

```
App detecta spam → API al servidor → Asterisk llama al spammer → IVR
```

**Costo:** ~$7/mes
**Viabilidad:** ✅ Alta
**Complejidad:** Media

---

## 🔄 Cómo Usar Este Branch

### **Para Referencia:**
```bash
git checkout claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf
# Revisar código
# Leer documentación
```

### **Para Desarrollar Modo 1:**
```bash
# NO usar este branch
# Usar en su lugar:
git checkout claude/modo1-optimized
```

### **Para Investigar VoIP:**
```bash
# Revisar estos archivos:
# - IVRAudioTrackPlayer.java (decodificación MP3 → PCM)
# - IVRAudioPlayer.java (reproducción de audio)
# - docs/OPCIONES_FUTURAS_IVR.md (guías de VoIP)
```

---

## 📚 Documentación

| Documento | Descripción |
|-----------|-------------|
| [MODO2_ESTADO_ACTUAL.md](docs/MODO2_ESTADO_ACTUAL.md) | Estado completo de Modo 2 |
| [OPCIONES_FUTURAS_IVR.md](docs/OPCIONES_FUTURAS_IVR.md) | Guías para VoIP/Asterisk |
| [BUGS_FIXED.md](docs/BUGS_FIXED.md) | Bugs resueltos durante desarrollo |

---

## 🎓 Aprendizajes

### **Lo que Aprendimos:**
- ✅ Cómo funciona InCallService
- ✅ Diferencia entre INCOMING/OUTGOING calls
- ✅ AudioTrack para reproducción de bajo nivel
- ✅ MediaCodec para decodificación MP3 → PCM
- ✅ AudioManager modes y routing
- ✅ Limitaciones reales de Android

### **Lo que NO es posible:**
- ❌ IVR local con APIs públicas
- ❌ Default Dialer sin UI completa
- ❌ Inyección de audio en call stream

---

## ⚙️ Configuración para Pruebas

Si quieres probar este código (aunque no funcione para producción):

### **1. Hacer app Default Dialer:**
```
Settings → Apps → Default apps → Phone app → SpamBlocker
```

### **2. Permisos necesarios:**
- ANSWER_PHONE_CALLS
- READ_PHONE_STATE
- CALL_PHONE
- BIND_INCALL_SERVICE

### **3. Generar audio IVR:**
```bash
# Usar ElevenLabs o TTS para generar:
# /android/app/files/ivr_corporate.mp3
```

### **4. Probar:**
- Recibir llamada marcada como spam
- Observar que audio se reproduce localmente
- Confirmar que caller NO lo escucha

---

## 🔗 Relación con Otros Branches

```
main
├── claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf  ← ESTE BRANCH (Modo 2 - experimental)
└── claude/modo1-optimized                                ← Branch para producción (Modo 1)
```

**NO hacer merge de este branch a main** sin antes refactorizar para Modo 1.

---

## 👥 Contribuciones

**Desarrolladores:**
- Usuario (Product Owner + Testing)
- Claude (Implementación + Documentación)

**Tiempo invertido:** ~2 días
**Commits:** 10+
**Archivos:** 15+
**Líneas de código:** ~2000+

---

## 📝 Notas Finales

Este branch NO es un "fracaso" - es una **investigación valiosa** que:
- Confirma limitaciones de Android
- Valida necesidad de arquitectura VoIP
- Proporciona código de referencia
- Documenta lo que NO funciona (igual de importante)

**"Failure is success in progress"** - aprendimos mucho en el proceso.

---

## 🆘 Soporte

Si tienes preguntas sobre este branch:
1. Lee primero [MODO2_ESTADO_ACTUAL.md](docs/MODO2_ESTADO_ACTUAL.md)
2. Lee [OPCIONES_FUTURAS_IVR.md](docs/OPCIONES_FUTURAS_IVR.md)
3. Si aún tienes dudas, revisa el código con comentarios

---

**Última actualización:** 12 Diciembre 2025
