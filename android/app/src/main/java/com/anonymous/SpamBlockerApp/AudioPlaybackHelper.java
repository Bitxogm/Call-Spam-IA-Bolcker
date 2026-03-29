// AudioPlaybackHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Helper para reproducir audio durante una llamada activa.
 *
 * DESAFÍO: Android no permite fácilmente inyectar audio en el stream de llamada
 * por razones de seguridad. Soluciones:
 * 1. Modo IN_COMMUNICATION - reproduce por speaker/earpiece
 * 2. Modo SPEAKER - fuerza altavoz (más audible para el spammer)
 */
public class AudioPlaybackHelper {
    private static final String TAG = "AudioPlaybackHelper";

    private final Context context;
    private final AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    // Estado previo del audio para restaurar después
    private int previousAudioMode;
    private boolean previousSpeakerphoneOn;

    public AudioPlaybackHelper(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Reproduce un archivo de audio durante una llamada activa
     *
     * @param resourceId ID del recurso de audio (ej: R.raw.ivr_corporate)
     * @param looping Si debe repetir el audio en bucle
     * @param duration Duración en segundos (0 = hasta que se detenga manualmente)
     * @return true si se inició correctamente, false si error
     */
    public boolean playAudioDuringCall(int resourceId, boolean looping, int duration) {
        try {
            Log.d(TAG, "🔊 Iniciando reproducción de audio durante llamada...");

            // Liberar MediaPlayer anterior si existe
            releaseMediaPlayer();

            // Guardar estado actual del audio
            previousAudioMode = audioManager.getMode();
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

            Log.d(TAG, "📱 Estado audio anterior - Mode: " + previousAudioMode + ", Speaker: " + previousSpeakerphoneOn);

            // Configurar audio para llamada
            // MODE_IN_COMMUNICATION: Usado para VoIP, permite reproducir audio durante llamada
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            // Usar auricular para que el usuario no oiga el ruido y sea más discreto
            audioManager.setSpeakerphoneOn(false);

            Log.d(TAG, "🔊 Audio configurado - Mode: IN_COMMUNICATION, Speaker: OFF (Discreto)");

            // Crear MediaPlayer
            mediaPlayer = MediaPlayer.create(context, resourceId);

            if (mediaPlayer == null) {
                Log.e(TAG, "❌ No se pudo crear MediaPlayer para resource: " + resourceId);
                return false;
            }

            // Configurar atributos de audio
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            );

            // Configurar looping
            mediaPlayer.setLooping(looping);

            // Listener para cuando termina (si no es loop)
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "✅ Audio terminado");
                isPlaying = false;
                if (!looping) {
                    stopAudio();
                }
            });

            // Listener para errores
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "❌ Error reproduciendo audio - what: " + what + ", extra: " + extra);
                isPlaying = false;
                stopAudio();
                return true;
            });

            // Iniciar reproducción
            mediaPlayer.start();
            isPlaying = true;

            Log.d(TAG, "✅ Audio iniciado - Looping: " + looping + ", Duration: " + duration + "s");

            // Si hay duración límite, programar stop
            if (duration > 0) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "⏱️ Duración cumplida (" + duration + "s), deteniendo audio");
                    stopAudio();
                }, duration * 1000L);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando audio: " + e.getMessage(), e);
            stopAudio();
            return false;
        }
    }

    /**
     * Detiene la reproducción de audio y restaura estado anterior
     */
    public void stopAudio() {
        try {
            Log.d(TAG, "🛑 Deteniendo audio...");

            // Liberar MediaPlayer
            releaseMediaPlayer();

            // Restaurar estado de audio
            if (audioManager != null) {
                audioManager.setMode(previousAudioMode);
                audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
                Log.d(TAG, "🔊 Audio restaurado - Mode: " + previousAudioMode + ", Speaker: " + previousSpeakerphoneOn);
            }

            isPlaying = false;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error deteniendo audio: " + e.getMessage(), e);
        }
    }

    /**
     * Libera recursos del MediaPlayer
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "♻️ MediaPlayer liberado");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error liberando MediaPlayer: " + e.getMessage());
            }
        }
    }

    /**
     * Verifica si está reproduciendo actualmente
     */
    public boolean isPlaying() {
        return isPlaying && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
     * Limpieza al destruir
     */
    public void cleanup() {
        stopAudio();
    }
}
