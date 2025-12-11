package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Reproductor de audio IVR usando AudioTrack para transmisión al caller
 *
 * DIFERENCIA CLAVE vs MediaPlayer:
 * - MediaPlayer solo reproduce localmente (earpiece/speaker)
 * - AudioTrack escribe a STREAM_VOICE_CALL que se transmite al caller
 *
 * Flujo:
 * 1. MediaExtractor lee MP3
 * 2. MediaCodec decodifica MP3 → PCM
 * 3. AudioTrack reproduce PCM en STREAM_VOICE_CALL
 * 4. Android transmite este audio al caller (como si fuera micrófono)
 */
public class IVRAudioTrackPlayer {
    private static final String TAG = "IVRAudioTrackPlayer";
    private static IVRAudioTrackPlayer instance;
    private static final Object lock = new Object();

    private final Context context;
    private final AudioManager audioManager;

    // AudioTrack y decoding
    private AudioTrack audioTrack;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;

    // Loop y timeout
    private Handler timeoutHandler;
    private int maxLoops = 0;
    private int currentLoop = 0;

    // Estado previo del audio
    private int previousAudioMode;
    private boolean previousSpeakerphoneOn;

    /**
     * Constructor privado (Singleton)
     */
    private IVRAudioTrackPlayer(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Obtiene la instancia única
     */
    public static IVRAudioTrackPlayer getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    Log.d(TAG, "🔧 Creando IVRAudioTrackPlayer (Singleton) - Transmisión a caller");
                    instance = new IVRAudioTrackPlayer(context);
                }
            }
        }
        return instance;
    }

    /**
     * Reproduce audio IVR desde archivo MP3
     *
     * @param audioFilePath Ruta absoluta al archivo de audio (MP3)
     * @param loops Número de veces a repetir (0 = infinito hasta timeout)
     * @param timeoutSeconds Timeout máximo en segundos
     */
    public boolean playIVR(String audioFilePath, int loops, int timeoutSeconds) {
        try {
            Log.i(TAG, "🔊 Iniciando IVR AudioTrack desde: " + audioFilePath);

            // Verificar que el archivo existe
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Archivo de audio no existe: " + audioFilePath);
                return false;
            }

            // Guardar estado actual del audio
            previousAudioMode = audioManager.getMode();
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

            // ✅ WORKAROUND: Reproducir en SPEAKER para que el micrófono lo capte
            // Android no permite inyectar audio directamente en el uplink de llamadas telefónicas
            // Por lo tanto, reproducimos en speaker → micrófono captura → caller escucha
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);  // ✅ SPEAKER ON para que micrófono capte

            // Ajustar volumen del speaker para transmisión óptima
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int targetVolume = (int)(maxVolume * 0.7);  // 70% del volumen máximo
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVolume, 0);

            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: ON (para transmisión vía mic)");

            // Configurar loop
            this.maxLoops = loops;
            this.currentLoop = 0;
            this.isPlaying = true;

            // Iniciar thread de reproducción
            playbackThread = new Thread(() -> {
                playbackLoop(audioFilePath);
            });
            playbackThread.start();

            Log.i(TAG, "✅ IVR AudioTrack iniciado - Loops: " + (loops == 0 ? "∞" : loops) + ", Timeout: " + timeoutSeconds + "s");

            // Programar timeout
            if (timeoutSeconds > 0) {
                timeoutHandler.postDelayed(() -> {
                    Log.d(TAG, "⏱️ Timeout alcanzado (" + timeoutSeconds + "s)");
                    stopIVR();
                }, timeoutSeconds * 1000L);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando IVR AudioTrack: " + e.getMessage(), e);
            stopIVR();
            return false;
        }
    }

    /**
     * Loop principal de reproducción y decodificación
     */
    private void playbackLoop(String audioFilePath) {
        try {
            while (isPlaying && (maxLoops == 0 || currentLoop < maxLoops)) {
                Log.d(TAG, "🔁 Loop " + (currentLoop + 1) + "/" + (maxLoops == 0 ? "∞" : maxLoops));

                if (!decodeAndPlay(audioFilePath)) {
                    Log.e(TAG, "❌ Error en decode/play, deteniendo");
                    break;
                }

                currentLoop++;

                // Si no es loop infinito y alcanzamos el límite, detener
                if (maxLoops > 0 && currentLoop >= maxLoops) {
                    Log.d(TAG, "✅ Loops completados, deteniendo");
                    break;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en playbackLoop: " + e.getMessage(), e);
        } finally {
            // Asegurar cleanup
            timeoutHandler.post(() -> stopIVR());
        }
    }

    /**
     * Decodifica MP3 y reproduce con AudioTrack
     */
    private boolean decodeAndPlay(String audioFilePath) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        AudioTrack audioTrack = null;

        try {
            // 1️⃣ EXTRACTOR: Lee el archivo MP3
            extractor = new MediaExtractor();
            extractor.setDataSource(audioFilePath);

            // Buscar track de audio
            MediaFormat format = null;
            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    format = trackFormat;
                    audioTrackIndex = i;
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                Log.e(TAG, "❌ No se encontró track de audio en: " + audioFilePath);
                return false;
            }

            extractor.selectTrack(audioTrackIndex);

            // Obtener parámetros del audio
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            Log.d(TAG, "📊 Audio Format - SampleRate: " + sampleRate + "Hz, Channels: " + channelCount);

            // 2️⃣ CODEC: Decodifica MP3 → PCM
            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            // 3️⃣ AUDIOTRACK: Reproduce PCM en STREAM_VOICE_CALL
            int channelConfig = channelCount == 2 ?
                AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;

            int bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            );

            // ✅ WORKAROUND: Usar STREAM_VOICE_CALL para que salga por el speaker
            // El micrófono capturará este audio y lo transmitirá al caller
            // Esta es la única forma de inyectar audio en llamadas telefónicas sin APIs privadas

            // Usar constructor LEGACY con streamType explícito
            @SuppressWarnings("deprecation")
            AudioTrack audioTrackTemp = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,      // Stream type - sale por speaker en MODE_IN_COMMUNICATION
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            audioTrack = audioTrackTemp;

            audioTrack.play();

            Log.d(TAG, "🎙️ AudioTrack iniciado - Audio se transmitirá al CALLER");

            // 4️⃣ DECODE LOOP: MediaCodec → AudioTrack
            boolean outputDone = false;
            boolean inputDone = false;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long timeoutUs = 10000; // 10ms

            while (!outputDone && isPlaying) {
                // Alimentar input del codec
                if (!inputDone) {
                    int inputBufferIndex = codec.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                // Obtener output decodificado (PCM)
                int outputBufferIndex = codec.dequeueOutputBuffer(info, timeoutUs);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);

                    if (info.size > 0) {
                        // Escribir PCM a AudioTrack (esto se transmite al caller)
                        byte[] pcmData = new byte[info.size];
                        outputBuffer.get(pcmData);
                        outputBuffer.clear();

                        audioTrack.write(pcmData, 0, pcmData.length);
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            // Esperar a que AudioTrack termine de reproducir
            if (audioTrack != null) {
                audioTrack.stop();
            }

            Log.d(TAG, "✅ Decode/Play completado");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en decodeAndPlay: " + e.getMessage(), e);
            return false;

        } finally {
            // Cleanup
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
                if (extractor != null) {
                    extractor.release();
                }
                if (audioTrack != null) {
                    audioTrack.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error en cleanup: " + e.getMessage());
            }
        }
    }

    /**
     * Detiene la reproducción IVR
     */
    public void stopIVR() {
        try {
            Log.d(TAG, "🛑 Deteniendo IVR AudioTrack...");

            isPlaying = false;

            // Cancelar timeout pendiente
            if (timeoutHandler != null) {
                timeoutHandler.removeCallbacksAndMessages(null);
            }

            // Esperar a que termine el thread
            if (playbackThread != null && playbackThread.isAlive()) {
                playbackThread.interrupt();
                try {
                    playbackThread.join(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Timeout esperando thread de playback");
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
        return isPlaying;
    }

    /**
     * Limpieza al destruir
     */
    public void cleanup() {
        stopIVR();
        Log.d(TAG, "♻️ IVRAudioTrackPlayer limpiado");
    }
}
