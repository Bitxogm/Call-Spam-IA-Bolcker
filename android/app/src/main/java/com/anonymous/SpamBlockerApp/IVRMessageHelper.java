// IVRMessageHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;

/**
 * Helper para generar y reproducir mensajes IVR usando Text-to-Speech
 * durante una llamada activa.
 *
 * Mensajes corporativos irritantes estilo "pulse 1, pulse 2..."
 *
 * PATRÓN SINGLETON: Solo existe una instancia para evitar race conditions
 * y asegurar que TTS esté siempre inicializado.
 */
public class IVRMessageHelper {
    private static final String TAG = "IVRMessageHelper";
    private static IVRMessageHelper instance;
    private static final Object lock = new Object();

    // Mensajes IVR predefinidos
    public enum IVRType {
        CORPORATE_INFINITE,    // IVR corporativo infinito
        ABSURD,                // Mensaje absurdo/gracioso
        SERIOUS_DISSUASIVE     // Mensaje serio disuasivo
    }

    private final Context context;
    private final AudioManager audioManager;
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private boolean isPlaying = false;

    // Handler para loops
    private Handler loopHandler;
    private Runnable loopRunnable;

    // Estado previo del audio
    private int previousAudioMode;
    private boolean previousSpeakerphoneOn;

    /**
     * Constructor privado para Singleton
     */
    private IVRMessageHelper(Context context) {
        this.context = context.getApplicationContext(); // Usar ApplicationContext para evitar leaks
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.loopHandler = new Handler(Looper.getMainLooper());
        initializeTTS();
    }

    /**
     * Obtiene la instancia única (Singleton)
     */
    public static IVRMessageHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    Log.d(TAG, "🔧 Creando nueva instancia de IVRMessageHelper (Singleton)");
                    instance = new IVRMessageHelper(context);
                }
            }
        }
        return instance;
    }

    /**
     * Inicializa Text-to-Speech
     */
    private void initializeTTS() {
        Log.d(TAG, "🔧 Iniciando inicialización de TTS...");

        tts = new TextToSpeech(context, status -> {
            synchronized (lock) {
                if (status == TextToSpeech.SUCCESS) {
                    // Configurar idioma español
                    int result = tts.setLanguage(new Locale("es", "ES"));

                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "⚠️ Español no soportado, usando inglés");
                        tts.setLanguage(Locale.US);
                    }

                    // Configurar velocidad normal (0.9 = ligeramente más lento para mejor comprensión)
                    tts.setSpeechRate(0.9f);

                    // Configurar pitch normal
                    tts.setPitch(1.0f);

                    isInitialized = true;
                    Log.i(TAG, "✅ TTS inicializado correctamente (callback completado)");

                    // Notificar a threads esperando
                    lock.notifyAll();

                } else {
                    Log.e(TAG, "❌ Error inicializando TTS: " + status);
                    lock.notifyAll(); // Notificar incluso en error
                }
            }
        });

        // Listener para progreso de utterances
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "🔊 TTS iniciado: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "✅ TTS completado: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "❌ TTS error: " + utteranceId);
            }
        });
    }

    /**
     * Espera a que TTS esté inicializado (con timeout)
     *
     * @param timeoutMs Tiempo máximo de espera en milisegundos
     * @return true si TTS se inicializó, false si timeout
     */
    private boolean waitForInitialization(int timeoutMs) {
        if (isInitialized) {
            return true;
        }

        Log.d(TAG, "⏳ Esperando inicialización de TTS (timeout: " + timeoutMs + "ms)...");

        synchronized (lock) {
            long startTime = System.currentTimeMillis();
            long remainingTime = timeoutMs;

            while (!isInitialized && remainingTime > 0) {
                try {
                    lock.wait(remainingTime);
                    remainingTime = timeoutMs - (System.currentTimeMillis() - startTime);
                } catch (InterruptedException e) {
                    Log.w(TAG, "⚠️ Espera de TTS interrumpida");
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        if (isInitialized) {
            Log.i(TAG, "✅ TTS listo después de esperar");
        } else {
            Log.e(TAG, "❌ Timeout esperando TTS (" + timeoutMs + "ms)");
        }

        return isInitialized;
    }

    /**
     * Inicia el IVR durante una llamada
     */
    public boolean startIVR(IVRType type, int maxDurationSeconds) {
        // Esperar hasta 3 segundos para que TTS se inicialice
        if (!waitForInitialization(3000)) {
            Log.e(TAG, "❌ TTS no pudo inicializarse a tiempo");
            return false;
        }

        try {
            Log.i(TAG, "🔊 Iniciando IVR tipo: " + type.name());

            // Guardar estado actual del audio
            previousAudioMode = audioManager.getMode();
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

            // Configurar audio para llamada
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);  // ❌ FALSE: No activar speaker local

            // IMPORTANTE: Necesitamos que el audio vaya al stream de la llamada,
            // NO al speaker local del teléfono. MODE_IN_COMMUNICATION + Speaker OFF
            // debería rutear el audio TTS al stream de voz de la llamada.

            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: OFF (routing to call stream)");

            // Configurar atributos de audio para TTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build();

                tts.setAudioAttributes(audioAttributes);
            }

            // Iniciar el mensaje según tipo
            isPlaying = true;
            playIVRMessage(type);

            // Programar timeout
            if (maxDurationSeconds > 0) {
                loopHandler.postDelayed(() -> {
                    Log.d(TAG, "⏱️ Timeout alcanzado (" + maxDurationSeconds + "s)");
                    stopIVR();
                }, maxDurationSeconds * 1000L);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando IVR: " + e.getMessage(), e);
            stopIVR();
            return false;
        }
    }

    /**
     * Reproduce el mensaje IVR según el tipo
     */
    private void playIVRMessage(IVRType type) {
        String message = getIVRMessage(type);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API 21+
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL);

            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "IVR_" + type.name());

        } else {
            // API < 21 (fallback)
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_VOICE_CALL));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "IVR_" + type.name());

            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params);
        }

        // Si es tipo loop (corporativo), repetir después de terminar
        if (type == IVRType.CORPORATE_INFINITE && isPlaying) {
            loopHandler.postDelayed(() -> {
                if (isPlaying) {
                    playIVRMessage(type); // Loop infinito
                }
            }, 8000); // 8 segundos de delay entre repeticiones
        }
    }

    /**
     * Obtiene el texto del mensaje según el tipo
     */
    private String getIVRMessage(IVRType type) {
        switch (type) {
            case CORPORATE_INFINITE:
                return "Bienvenido al sistema de atención telefónica. " +
                       "Para ventas, pulse 1. " +
                       "Para soporte técnico, pulse 2. " +
                       "Para hablar con un operador, pulse 3. " +
                       "Para repetir este menú, pulse 9.";

            case ABSURD:
                return "Ha llamado a la línea anti spam. " +
                       "Ironías del destino, ¿verdad? " +
                       "Pulse 1 si es un robot. " +
                       "Pulse 2 si es un humano que se comporta como robot. " +
                       "Error 404, destinatario no encontrado. " +
                       "Reiniciando...";

            case SERIOUS_DISSUASIVE:
                return "Esta línea telefónica está protegida. " +
                       "Su número será reportado a las autoridades competentes. " +
                       "Esta llamada está siendo grabada. " +
                       "Gracias por su comprensión.";

            default:
                return "Sistema de bloqueo de spam activado.";
        }
    }

    /**
     * Detiene el IVR y restaura el audio
     */
    public void stopIVR() {
        try {
            Log.d(TAG, "🛑 Deteniendo IVR...");

            isPlaying = false;

            // Cancelar loops pendientes
            if (loopHandler != null) {
                loopHandler.removeCallbacksAndMessages(null);
            }

            // Detener TTS
            if (tts != null) {
                tts.stop();
            }

            // Restaurar audio
            if (audioManager != null) {
                audioManager.setMode(previousAudioMode);
                audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
                Log.d(TAG, "🔊 Audio restaurado");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error deteniendo IVR: " + e.getMessage());
        }
    }

    /**
     * Verifica si está reproduciendo
     */
    public boolean isPlaying() {
        return isPlaying && tts != null && tts.isSpeaking();
    }

    /**
     * Limpieza al destruir
     */
    public void cleanup() {
        stopIVR();

        if (tts != null) {
            tts.shutdown();
            tts = null;
        }

        Log.d(TAG, "♻️ IVRMessageHelper limpiado");
    }
}
