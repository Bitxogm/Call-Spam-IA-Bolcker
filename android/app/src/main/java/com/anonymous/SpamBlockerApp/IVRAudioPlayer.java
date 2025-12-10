package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

/**
 * Reproductor de audio IVR pre-generado durante llamadas
 * Usa MediaPlayer para rutear audio al stream de la llamada
 *
 * VENTAJAS sobre TTS:
 * - Audio de alta calidad (ElevenLabs)
 * - Routing correcto a call stream
 * - Funciona offline después de pre-generar
 */
public class IVRAudioPlayer {
    private static final String TAG = "IVRAudioPlayer";
    private static IVRAudioPlayer instance;
    private static final Object lock = new Object();

    private final Context context;
    private final AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    // Handler para loops
    private Handler loopHandler;
    private int maxLoops = 0;
    private int currentLoop = 0;

    // Estado previo del audio
    private int previousAudioMode;
    private boolean previousSpeakerphoneOn;

    /**
     * Constructor privado (Singleton)
     */
    private IVRAudioPlayer(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.loopHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Obtiene la instancia única
     */
    public static IVRAudioPlayer getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    Log.d(TAG, "🔧 Creando nueva instancia de IVRAudioPlayer (Singleton)");
                    instance = new IVRAudioPlayer(context);
                }
            }
        }
        return instance;
    }

    /**
     * Reproduce audio IVR desde archivo
     *
     * @param audioFilePath Ruta absoluta al archivo de audio (MP3)
     * @param loops Número de veces a repetir (0 = infinito hasta timeout)
     * @param timeoutSeconds Timeout máximo en segundos
     */
    public boolean playIVR(String audioFilePath, int loops, int timeoutSeconds) {
        try {
            Log.i(TAG, "🔊 Iniciando IVR desde: " + audioFilePath);

            // Verificar que el archivo existe
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Archivo de audio no existe: " + audioFilePath);
                return false;
            }

            // Guardar estado actual del audio
            previousAudioMode = audioManager.getMode();
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

            // ✅ CONFIGURACIÓN CRÍTICA: Audio debe ir al call stream
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);  // NO al speaker local

            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: OFF");

            // Configurar MediaPlayer
            mediaPlayer = new MediaPlayer();

            // ✅ CONFIGURACIÓN CRÍTICA: AudioAttributes para routing a llamada
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)  // ← CLAVE: rutea a llamada
                .build();

            mediaPlayer.setAudioAttributes(audioAttributes);

            // Configurar fuente de audio
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();

            // Configurar loop
            this.maxLoops = loops;
            this.currentLoop = 0;

            if (loops == 0) {
                // Loop infinito con MediaPlayer
                mediaPlayer.setLooping(true);
            }

            // Listener de finalización (para loops manuales)
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPlaying) {
                    return;
                }

                currentLoop++;
                Log.d(TAG, "🔁 Loop " + currentLoop + "/" + maxLoops);

                if (maxLoops > 0 && currentLoop >= maxLoops) {
                    Log.d(TAG, "✅ Loops completados, deteniendo");
                    stopIVR();
                } else if (maxLoops > 0) {
                    // Repetir manualmente
                    try {
                        mediaPlayer.seekTo(0);
                        mediaPlayer.start();
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error reiniciando loop: " + e.getMessage());
                        stopIVR();
                    }
                }
            });

            // Listener de errores
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "❌ Error en MediaPlayer: what=" + what + ", extra=" + extra);
                stopIVR();
                return true;
            });

            // Iniciar reproducción
            mediaPlayer.start();
            isPlaying = true;

            Log.i(TAG, "✅ IVR iniciado - Loops: " + (loops == 0 ? "∞" : loops) + ", Timeout: " + timeoutSeconds + "s");

            // Programar timeout
            if (timeoutSeconds > 0) {
                loopHandler.postDelayed(() -> {
                    Log.d(TAG, "⏱️ Timeout alcanzado (" + timeoutSeconds + "s)");
                    stopIVR();
                }, timeoutSeconds * 1000L);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando IVR: " + e.getMessage(), e);
            stopIVR();
            return false;
        }
    }

    /**
     * Detiene la reproducción IVR
     */
    public void stopIVR() {
        try {
            Log.d(TAG, "🛑 Deteniendo IVR...");

            isPlaying = false;

            // Cancelar timeout pendiente
            if (loopHandler != null) {
                loopHandler.removeCallbacksAndMessages(null);
            }

            // Detener MediaPlayer
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error deteniendo MediaPlayer: " + e.getMessage());
                }
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
        return isPlaying && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
     * Limpieza al destruir
     */
    public void cleanup() {
        stopIVR();
        Log.d(TAG, "♻️ IVRAudioPlayer limpiado");
    }
}
