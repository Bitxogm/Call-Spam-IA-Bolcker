# 📊 Estado Actual - Modo 2 (IVR Corporativo)

**Fecha:** 12 de Diciembre, 2025
**Branch:** `claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf`
**Estado:** Implementado pero NO funcional para transmisión a caller

---

## 🎯 Objetivo del Modo 2

**Reproducir mensaje IVR corporativo** al caller (spammer) cuando recibe una llamada, en lugar de solo colgar.

**Resultado esperado:**
- Llamada spam entra
- App contesta automáticamente
- Caller escucha mensaje pregrabado: *"Este número no admite llamadas comerciales..."*
- Después de 30s, se cuelga

---

## 🔧 Implementación Realizada

### **Arquitectura Intentada:**

```
┌─────────────────────────────────────────────────────┐
│  1. Llamada entrante detectada                      │
│     ↓                                                │
│  2. CallAccessibilityService / SpamCallService      │
│     detecta spam                                     │
│     ↓                                                │
│  3. App contesta llamada automáticamente            │
│     ↓                                                │
│  4. IVRAudioTrackPlayer reproduce audio MP3         │
│     ↓                                                │
│  5. Audio decodificado (MP3 → PCM)                  │
│     ↓                                                │
│  6. AudioTrack reproduce en STREAM_VOICE_CALL       │
│     ↓                                                │
│  7. ❌ PROBLEMA: Audio solo se escucha localmente   │
│     Caller NO escucha nada                          │
└─────────────────────────────────────────────────────┘
```

---

## 📁 Archivos Implementados

### **Componentes Core:**

| Archivo | Descripción | Estado |
|---------|-------------|--------|
| `IVRAudioPlayer.java` | Reproductor con MediaPlayer | ⚠️ Funciona localmente |
| `IVRAudioTrackPlayer.java` | Reproductor con AudioTrack + MediaCodec | ⚠️ Funciona localmente |
| `SpamCallService.java` | InCallService que maneja llamadas | ✅ Funciona |
| `CallAccessibilityService.java` | Detecta llamadas entrantes | ✅ Funciona |
| `AnswerHangupHelper.java` | Lógica de decisión Answer+Hangup | ✅ Funciona |
| `InCallActivity.java` | UI de llamada con botones | ⚠️ No aparece siempre |

### **Configuración:**

| Archivo | Propósito | Estado |
|---------|-----------|--------|
| `AndroidManifest.xml` | Permisos + Default Dialer + InCallService | ✅ Configurado |
| `DefaultDialerModule.java` | React Native bridge para Default Dialer | ✅ Funciona |

---

## ⚙️ Tecnologías Utilizadas

### **Para Reproducción de Audio:**

1. **MediaPlayer** (Intento 1):
   ```java
   AudioAttributes audioAttributes = new AudioAttributes.Builder()
       .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
       .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
       .build();

   mediaPlayer.setAudioAttributes(audioAttributes);
   mediaPlayer.setDataSource(audioFilePath);
   mediaPlayer.start();
   ```
   **Resultado:** Audio solo en earpiece local, NO transmite al caller.

2. **AudioTrack + MediaCodec** (Intento 2):
   ```java
   // Decodificar MP3 → PCM
   MediaExtractor extractor = new MediaExtractor();
   MediaCodec codec = MediaCodec.createDecoderByType(mime);

   // Reproducir PCM en STREAM_VOICE_CALL
   AudioTrack audioTrack = new AudioTrack(
       AudioManager.STREAM_VOICE_CALL,
       sampleRate, channelConfig,
       AudioFormat.ENCODING_PCM_16BIT,
       bufferSize, AudioTrack.MODE_STREAM
   );
   ```
   **Resultado:** Audio solo en speaker local, NO transmite al caller.

3. **Workaround Speaker + Micrófono** (Intento 3):
   ```java
   audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
   audioManager.setSpeakerphoneOn(true);
   audioManager.setStreamVolume(STREAM_VOICE_CALL, 70%);
   ```
   **Resultado:** Audio en speaker local, micrófono NO lo captura correctamente (AEC lo filtra).

---

## ❌ Por Qué NO Funciona

### **Limitación Fundamental de Android:**

> **Android NO permite que apps normales inyecten audio en el uplink (stream hacia el caller) de llamadas telefónicas tradicionales (GSM/VoLTE).**

**Razones técnicas:**

1. **APIs cerradas desde Android 6-10:**
   - Las APIs que permitían grabar/inyectar audio en llamadas fueron eliminadas
   - Solo apps del sistema (dialer, operador) tienen acceso completo

2. **AudioTrack con USAGE_VOICE_COMMUNICATION:**
   - Reproduce audio en earpiece/speaker LOCAL
   - NO inyecta en el canal RTP de la llamada
   - Diseñado para apps VoIP que manejan TODA la llamada ellas mismas

3. **Acoustic Echo Cancellation (AEC):**
   - Android filtra audio del speaker para evitar feedback
   - El micrófono NO captura lo que sale del speaker (por diseño)

4. **Arquitectura GSM/VoLTE:**
   - El uplink viene EXCLUSIVAMENTE del micrófono físico
   - No hay API pública para inyectar audio programático

### **Confirmación de Fuentes:**

- ✅ Perplexity AI
- ✅ Documentación oficial de Android
- ✅ Stack Overflow (múltiples threads)
- ✅ Apps comerciales (Truecaller, etc.) NO hacen IVR local

---

## 🎓 Aprendizajes Clave

### **Lo que SÍ funciona:**

1. ✅ **Modo 1 (Colgar directo):**
   - Detectar spam con Accessibility Service
   - Contestar y colgar inmediatamente
   - Sin permisos invasivos
   - Funciona al 100%

2. ✅ **Default Dialer:**
   - Permite usar InCallService sin bloqueos de Samsung
   - Pero requiere implementar UI completa (teclado, contactos, etc.)
   - Trade-off no favorable

3. ✅ **Audio local:**
   - MediaPlayer y AudioTrack funcionan perfectamente
   - Para reproducción LOCAL (no transmisión)

### **Lo que NO funciona:**

1. ❌ **IVR local a caller:**
   - Imposible con APIs públicas de Android
   - Requiere root, ROM personalizada, o APIs privadas

2. ❌ **Default Dialer sin UI completa:**
   - Usuario pierde teclado, DTMF, funcionalidad básica
   - No es viable para app pública

---

## 📊 Estadísticas de Desarrollo

**Tiempo invertido:** ~2 días completos
**Commits:** 10+
**Archivos creados:** 15+
**Líneas de código:** ~2000+
**Intentos/Enfoques:** 3 principales (MediaPlayer, AudioTrack, Workaround)

**Lecciones aprendidas:**
- ✅ Comprensión profunda de Android audio APIs
- ✅ MediaCodec para decodificación MP3 → PCM
- ✅ AudioTrack para reproducción de bajo nivel
- ✅ Limitaciones reales de Android para telefonía

---

## 🔄 Código Útil para el Futuro

Aunque Modo 2 no funciona como esperado, el código tiene valor:

### **Reutilizable para Modo 1:**
- `CallAccessibilityService.java` - Detección de llamadas
- `AnswerHangupHelper.java` - Lógica de decisión
- `CallHistoryHelper.java` - Historial y estadísticas
- `LogsHelper.java` - Sistema de logs

### **Referencia para VoIP futuro:**
- `IVRAudioTrackPlayer.java` - Decodificación MP3 → PCM con MediaCodec
- `IVRAudioPlayer.java` - Uso de MediaPlayer para audio
- Configuración de AudioManager para llamadas
- Threading para reproducción en background

### **No reutilizable:**
- `InCallActivity.java` - Solo si se mantiene Default Dialer
- Default Dialer configuration - Abandonar

---

## 🚀 Próximos Pasos

Ver: [OPCIONES_FUTURAS_IVR.md](./OPCIONES_FUTURAS_IVR.md)

**Decisión tomada:**
- ✅ Refactorizar para **Modo 1 Optimizado**
- 📦 Guardar código de Modo 2 en esta branch
- 🔬 Investigar **VoIP/Asterisk** para IVR real en el futuro

---

## 📚 Referencias

### **Documentación:**
- [Android InCallService](https://developer.android.com/reference/android/telecom/InCallService)
- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)
- [Android MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

### **Artículos:**
- [Perplexity AI - Análisis de limitaciones Android](docs/perplexity_analysis.md)
- [Stack Overflow - Audio injection in calls](https://stackoverflow.com/questions/tagged/android-call-audio)

### **Código de referencia:**
- [VoIP Repository](https://github.com/danielepelleg/VoIP)
- [Asterisk IVR](https://www.asterisk.org/get-started/applications/ivr/)

---

**Conclusión:** Modo 2 (IVR local) NO es viable técnicamente con APIs públicas de Android. La única solución real es usar arquitectura VoIP con servidor externo (Asterisk, FreePBX, etc.).
