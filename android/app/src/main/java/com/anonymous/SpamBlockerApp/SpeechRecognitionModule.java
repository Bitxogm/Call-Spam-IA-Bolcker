// SpeechRecognitionModule.java
package com.anonymous.SpamBlockerApp;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Native Module para Speech Recognition (Speech-to-Text)
 * Usa Google Speech Recognition integrado en Android
 */
public class SpeechRecognitionModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "SpeechRecognitionModule";
    private static final String TAG = "SpeechRecognition";

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private ReactApplicationContext reactContext;

    public SpeechRecognitionModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Verifica si Speech Recognition está disponible
     */
    @ReactMethod
    public void isAvailable(Promise promise) {
        try {
            boolean available = SpeechRecognizer.isRecognitionAvailable(reactContext);
            promise.resolve(available);
        } catch (Exception e) {
            promise.reject("CHECK_AVAILABILITY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Inicia el reconocimiento de voz
     */
    @ReactMethod
    public void startListening(String language, Promise promise) {
        try {
            if (isListening) {
                Log.w(TAG, "⚠️ Ya está escuchando");
                promise.reject("ALREADY_LISTENING", "Ya está escuchando");
                return;
            }

            Log.d(TAG, "🎤 Iniciando reconocimiento de voz...");

            // Crear SpeechRecognizer si no existe
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(reactContext);
                speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
            }

            // Configurar Intent
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            // Configurar idioma (español por defecto)
            String locale = language != null ? language : "es-ES";
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);

            // Configuración adicional
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);

            // Iniciar reconocimiento
            speechRecognizer.startListening(intent);
            isListening = true;

            Log.i(TAG, "✅ Reconocimiento iniciado");
            promise.resolve(true);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando reconocimiento: " + e.getMessage());
            promise.reject("START_LISTENING_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Detiene el reconocimiento de voz
     */
    @ReactMethod
    public void stopListening(Promise promise) {
        try {
            if (!isListening) {
                Log.w(TAG, "⚠️ No está escuchando");
                promise.resolve(false);
                return;
            }

            Log.d(TAG, "🛑 Deteniendo reconocimiento...");

            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }

            isListening = false;
            Log.i(TAG, "✅ Reconocimiento detenido");
            promise.resolve(true);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error deteniendo reconocimiento: " + e.getMessage());
            promise.reject("STOP_LISTENING_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Cancela el reconocimiento de voz
     */
    @ReactMethod
    public void cancel(Promise promise) {
        try {
            Log.d(TAG, "❌ Cancelando reconocimiento...");

            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }

            isListening = false;
            promise.resolve(true);

        } catch (Exception e) {
            promise.reject("CANCEL_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Destruye el reconocedor
     */
    @ReactMethod
    public void destroy(Promise promise) {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }

            isListening = false;
            promise.resolve(true);

        } catch (Exception e) {
            promise.reject("DESTROY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Listener para eventos de reconocimiento
     */
    private class SpeechRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "🎤 Listo para hablar");
            sendEvent("onSpeechStart", null);
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "🗣️ Detectado inicio de voz");
            sendEvent("onSpeechRecognized", null);
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Nivel de volumen (opcional, para visualización)
            WritableMap data = Arguments.createMap();
            data.putDouble("value", rmsdB);
            sendEvent("onSpeechVolumeChanged", data);
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // No usado
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "🛑 Fin de voz detectado");
            isListening = false;
            sendEvent("onSpeechEnd", null);
        }

        @Override
        public void onError(int error) {
            String errorMessage = getErrorMessage(error);
            Log.e(TAG, "❌ Error: " + errorMessage);

            isListening = false;

            WritableMap data = Arguments.createMap();
            data.putInt("code", error);
            data.putString("message", errorMessage);
            sendEvent("onSpeechError", data);
        }

        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "✅ Resultados recibidos");

            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

            if (matches != null && !matches.isEmpty()) {
                WritableMap data = Arguments.createMap();
                data.putString("value", matches.get(0)); // Mejor resultado

                if (scores != null && scores.length > 0) {
                    data.putDouble("confidence", scores[0]);
                }

                Log.i(TAG, "📝 Transcripción: " + matches.get(0));
                sendEvent("onSpeechResults", data);
            }

            isListening = false;
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches != null && !matches.isEmpty()) {
                WritableMap data = Arguments.createMap();
                data.putString("value", matches.get(0));

                sendEvent("onSpeechPartialResults", data);
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // No usado
        }
    }

    /**
     * Envía eventos a React Native
     */
    private void sendEvent(String eventName, WritableMap data) {
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, data);
        } catch (Exception e) {
            Log.e(TAG, "Error enviando evento: " + e.getMessage());
        }
    }

    /**
     * Convierte código de error a mensaje legible
     */
    private String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Error de audio";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Error del cliente";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Permisos insuficientes";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Error de red";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Timeout de red";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No se reconoció voz";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Reconocedor ocupado";
            case SpeechRecognizer.ERROR_SERVER:
                return "Error del servidor";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Timeout de voz";
            default:
                return "Error desconocido: " + error;
        }
    }
}
