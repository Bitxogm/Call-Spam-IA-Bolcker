package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Reproductor de audio IVR usando AudioTrack + BCP Method para transmisión al caller
 *
 * MÉTODO BCP (Google Pixel Call Screening):
 * - Usa AudioTrack con AudioAttributes USAGE_VOICE_COMMUNICATION
 * - Busca dispositivo TYPE_TELEPHONY (solo en Pixel y algunos Samsung)
 * - Usa setPreferredDevice() para inyectar audio en uplink telefónico
 * - Fallback: speaker + micrófono si TYPE_TELEPHONY no disponible
 *
 * Flujo:
 * 1. MediaExtractor lee MP3
 * 2. MediaCodec decodifica MP3 → PCM
 * 3. AudioTrack con setPreferredDevice(TYPE_TELEPHONY) inyecta en uplink
 * 4. Caller ESCUCHA el audio directamente (no a través de micrófono)
 *
 * Referencias:
 * - https://github.com/chenxiaolong/BCP
 * - Requiere permisos: MODIFY_PHONE_STATE, MODIFY_AUDIO_ROUTING (privilegiados)
 */
public class IVRAudioTrackPlayer {
    private static final int MAX_VOLUME_RETRY_COUNT = 5;
    private static final boolean USE_DIGITAL_INJECTION = false; // 🧪 TEST: Deshabilitar para probar "loopback" acústico
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
    private Runnable audioRoutingEnforcer;

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
     * Busca y configura el dispositivo TYPE_TELEPHONY para inyección de audio en uplink
     *
     * Este método implementa la técnica BCP (Google Pixel Call Screening)
     *
     * @return true si se encontró y configuró TYPE_TELEPHONY, false si no está disponible
     */
    private boolean setTelephonyDevice() {
        if (audioTrack == null) {
            Log.e(TAG, "❌ AudioTrack no inicializado, no se puede configurar dispositivo");
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "⚠️ API level < 23, setPreferredDevice() no disponible");
            return false;
        }

        try {
            // Obtener todos los dispositivos de audio
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

            Log.d(TAG, "🔍 Buscando dispositivo TYPE_TELEPHONY entre " + devices.length + " dispositivos");

            AudioDeviceInfo telephonyDevice = null;

            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                String typeName = getDeviceTypeName(type);

                Log.d(TAG, "  📱 Dispositivo: " + device.getProductName() + " - Tipo: " + typeName + " (" + type + ")");

                // TYPE_TELEPHONY = 18 (Android 11+)
                // Solo disponible en dispositivos Pixel y algunos Samsung
                if (type == 18) { // AudioDeviceInfo.TYPE_TELEPHONY
                    telephonyDevice = device;
                    Log.i(TAG, "  ✅ TYPE_TELEPHONY encontrado: " + device.getProductName());
                    break;
                }
            }

            if (telephonyDevice != null) {
                // Configurar dispositivo preferido para inyección en uplink
                boolean success = audioTrack.setPreferredDevice(telephonyDevice);

                if (success) {
                    Log.i(TAG, "✅ setPreferredDevice(TYPE_TELEPHONY) exitoso - Audio se inyectará en uplink");
                    return true;
                } else {
                    Log.e(TAG, "❌ setPreferredDevice() falló (posible falta de permisos privilegiados)");
                    return false;
                }
            } else {
                Log.w(TAG, "⚠️ TYPE_TELEPHONY no disponible en este dispositivo");
                Log.w(TAG, "⚠️ Requiere: Pixel/GrapheneOS o Samsung con soporte");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error configurando TYPE_TELEPHONY: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper: Convierte device type a nombre legible para logs
     */
    private String getDeviceTypeName(int type) {
        switch (type) {
            case 1: return "EARPIECE";
            case 2: return "SPEAKER";
            case 3: return "WIRED_HEADSET";
            case 4: return "WIRED_HEADPHONES";
            case 5: return "LINE_ANALOG";
            case 6: return "LINE_DIGITAL";
            case 7: return "BLUETOOTH_SCO";
            case 8: return "BLUETOOTH_A2DP";
            case 9: return "HDMI";
            case 10: return "HDMI_ARC";
            case 11: return "USB_DEVICE";
            case 12: return "USB_ACCESSORY";
            case 13: return "DOCK";
            case 14: return "FM";
            case 15: return "BUILTIN_MIC";
            case 16: return "FM_TUNER";
            case 17: return "TV_TUNER";
            case 18: return "TYPE_TELEPHONY"; // ← ESTE ES EL IMPORTANTE
            case 19: return "AUX_LINE";
            case 20: return "IP";
            case 21: return "BUS";
            case 22: return "USB_HEADSET";
            case 23: return "HEARING_AID";
            case 24: return "BUILTIN_SPEAKER_SAFE";
            case 26: return "BLE_HEADSET";
            case 27: return "BLE_SPEAKER";
            default: return "UNKNOWN(" + type + ")";
        }
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
            // Configurar audio inicial
            ensureDiscreetAudio();

            // Asegurar que el micrófono no esté silenciado (para que capte el auricular)
            audioManager.setMicrophoneMute(false);

            // Programar reforzador de ruteo (cada 500ms) para evitar que Samsung lo cambie
            audioRoutingEnforcer = new Runnable() {
                @Override
                public void run() {
                    if (isPlaying) {
                        ensureDiscreetAudio();
                        timeoutHandler.postDelayed(this, 500);
                    }
                }
            };
            timeoutHandler.postDelayed(audioRoutingEnforcer, 500);

            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: OFF (Auricular para discreción)");

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
        try {
            // 1️⃣ EXTRACTOR: Lee el archivo MP3
            this.extractor = new MediaExtractor();
            this.extractor.setDataSource(audioFilePath);

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
            this.codec = MediaCodec.createDecoderByType(mime);
            this.codec.configure(format, null, null, 0);
            this.codec.start();

            // 3️⃣ AUDIOTRACK: Reproduce PCM en STREAM_VOICE_CALL
            int channelConfig = channelCount == 2 ?
                AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;

            int bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            );

            // 🎯 MÉTODO BCP: Usar AudioAttributes + TYPE_TELEPHONY device
            // Esto funciona en Pixel (GrapheneOS) y algunos Samsung

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelConfig)
                .build();

            this.audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            // Maximizar volumen del objeto AudioTrack
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                this.audioTrack.setVolume(1.0f);
            }

            // 🔑 CLAVE: Inyectar audio en uplink (Solo si está habilitado)
            if (USE_DIGITAL_INJECTION) {
                boolean telephonyDeviceFound = setTelephonyDevice();
                if (telephonyDeviceFound) {
                    Log.i(TAG, "🚀 Usando Inyección Digital (TYPE_TELEPHONY)");
                } else {
                    Log.w(TAG, "⚠️ TYPE_TELEPHONY no encontrado, usando Earpiece (Acoustic loopback)");
                }
            } else {
                Log.i(TAG, "🧪 TEST: Inyección digital desactivada intencionadamente. Usando Earpiece.");
            }
            // Fallback: auricular alto para que el micrófono interno lo capture
            audioManager.setSpeakerphoneOn(false);

            audioTrack.play();

            // The original code had this block:
            // if (telephonyDeviceFound) {
            //     Log.i(TAG, "✅ AudioTrack iniciado con TYPE_TELEPHONY - Audio inyectado en UPLINK");
            // } else {
            //     Log.w(TAG, "⚠️ TYPE_TELEPHONY no disponible - Usando auricular a volumen máximo");
            //     // Fallback: auricular alto para que el micrófono interno lo capture
            //     audioManager.setSpeakerphoneOn(false);
            // }


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

            // Cancelar enforcer y timeout
            if (timeoutHandler != null) {
                timeoutHandler.removeCallbacksAndMessages(null);
            }
            audioRoutingEnforcer = null;

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
     * Asegura que el audio esté redirigido al auricular y al volumen máximo.
     * Útil para combatir ruteos automáticos de Samsung.
     */
    private void ensureDiscreetAudio() {
        try {
            // Asegurar modo comunicación para que el sistema permita ruteo a earpiece
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            
            // Forzar salida al auricular (EARPIECE)
            audioManager.setSpeakerphoneOn(false);
            
            // Maximizar volumen de la llamada - REPETIDAMENTE para vencer bloqueos
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            for (int i = 0; i < MAX_VOLUME_RETRY_COUNT; i++) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0);
                if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == maxVol) {
                    break; // Volumen ya está al máximo
                }
                try {
                    Thread.sleep(50); // Pequeña pausa antes de reintentar
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Desactivar mute si existe
            audioManager.setMicrophoneMute(false);
            
            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: OFF, Vol: " + maxVol);

            // Android 12+ API para forzar comunicación por auricular si está disponible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    AudioDeviceInfo earpiece = null;
                    AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo device : devices) {
                        if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                            earpiece = device;
                            break;
                        }
                    }
                    if (earpiece != null && !earpiece.equals(audioManager.getCommunicationDevice())) {
                        audioManager.setCommunicationDevice(earpiece);
                        Log.d(TAG, "🎧 Communication device forzado a EARPIECE (Android 12+)");
                    }
                } catch (Exception e) {
                    // Silencioso
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en ensureDiscreetAudio", e);
        }
    }

    /**
     * Limpieza al destruir
     */
    public void cleanup() {
        stopIVR();
        Log.d(TAG, "♻️ IVRAudioTrackPlayer limpiado");
    }
}
