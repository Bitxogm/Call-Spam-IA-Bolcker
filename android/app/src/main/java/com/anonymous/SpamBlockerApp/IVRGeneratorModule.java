package com.anonymous.SpamBlockerApp;

import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

/**
 * Módulo para pre-generar audio IVR con ElevenLabs
 * Descarga y guarda el audio MP3 en almacenamiento interno
 */
public class IVRGeneratorModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "IVRGeneratorModule";
    private static final String TAG = "IVRGenerator";

    private ReactApplicationContext reactContext;

    public IVRGeneratorModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Guarda audio descargado desde URL (ElevenLabs)
     *
     * @param audioUrl URL del audio MP3 de ElevenLabs
     * @param promise Promise para devolver resultado
     */
    @ReactMethod
    public void saveIVRAudio(String audioUrl, Promise promise) {
        new Thread(() -> {
            try {
                Log.d(TAG, "📥 Descargando audio IVR desde: " + audioUrl);

                // Configurar conexión HTTP
                URL url = new URL(audioUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    promise.reject("DOWNLOAD_ERROR", "HTTP error: " + responseCode);
                    return;
                }

                // Leer datos
                InputStream input = connection.getInputStream();
                File outputFile = new File(reactContext.getFilesDir(), "ivr_corporate.mp3");
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                output.flush();
                output.close();
                input.close();
                connection.disconnect();

                Log.i(TAG, "✅ Audio IVR guardado: " + outputFile.getAbsolutePath() + " (" + totalBytes + " bytes)");

                WritableMap result = Arguments.createMap();
                result.putString("path", outputFile.getAbsolutePath());
                result.putDouble("size", totalBytes);
                result.putBoolean("success", true);

                promise.resolve(result);

            } catch (Exception e) {
                Log.e(TAG, "❌ Error descargando audio IVR: " + e.getMessage(), e);
                promise.reject("DOWNLOAD_ERROR", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Guarda audio desde Base64 (alternativa sin URL)
     */
    @ReactMethod
    public void saveIVRAudioFromBase64(String base64Audio, Promise promise) {
        new Thread(() -> {
            try {
                Log.d(TAG, "💾 Guardando audio IVR desde Base64...");

                // Decodificar Base64
                byte[] audioData = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT);

                // Guardar en archivo
                File outputFile = new File(reactContext.getFilesDir(), "ivr_corporate.mp3");
                FileOutputStream output = new FileOutputStream(outputFile);
                output.write(audioData);
                output.flush();
                output.close();

                Log.i(TAG, "✅ Audio IVR guardado: " + outputFile.getAbsolutePath() + " (" + audioData.length + " bytes)");

                WritableMap result = Arguments.createMap();
                result.putString("path", outputFile.getAbsolutePath());
                result.putDouble("size", audioData.length);
                result.putBoolean("success", true);

                promise.resolve(result);

            } catch (Exception e) {
                Log.e(TAG, "❌ Error guardando audio IVR: " + e.getMessage(), e);
                promise.reject("SAVE_ERROR", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Verifica si el audio IVR existe
     */
    @ReactMethod
    public void checkIVRAudioExists(Promise promise) {
        try {
            File audioFile = new File(reactContext.getFilesDir(), "ivr_corporate.mp3");
            boolean exists = audioFile.exists();

            WritableMap result = Arguments.createMap();
            result.putBoolean("exists", exists);

            if (exists) {
                result.putString("path", audioFile.getAbsolutePath());
                result.putDouble("size", audioFile.length());
            }

            promise.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error verificando audio IVR: " + e.getMessage());
            promise.reject("CHECK_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Genera audio IVR usando TTS nativo de Android (GRATIS, sin ElevenLabs)
     * Genera el mensaje corporativo y lo guarda como archivo WAV
     */
    @ReactMethod
    public void generateIVRWithNativeTTS(Promise promise) {
        new Thread(() -> {
            try {
                Log.d(TAG, "🔊 Generando audio IVR con TTS nativo...");

                // Mensaje IVR corporativo
                String ivrText = "Bienvenido al sistema de atención telefónica. " +
                        "Para ventas, pulse 1. " +
                        "Para soporte técnico, pulse 2. " +
                        "Para hablar con un operador, pulse 3. " +
                        "Para repetir este menú, pulse 9.";

                // Archivo de salida
                File outputFile = new File(reactContext.getFilesDir(), "ivr_corporate.wav");

                // Inicializar TTS
                // Usamos un array para poder acceder a la instancia dentro del callback
                final TextToSpeech[] ttsArray = new TextToSpeech[1];

                ttsArray[0] = new TextToSpeech(reactContext, status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        TextToSpeech tts = ttsArray[0];
                        Log.d(TAG, "✅ TTS inicializado");

                        // Configurar idioma español
                        int langResult = tts.setLanguage(new Locale("es", "ES"));

                        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "⚠️ Español no soportado, usando inglés");
                            tts.setLanguage(Locale.US);
                        }

                        // Configurar velocidad y pitch
                        tts.setSpeechRate(0.9f);  // Ligeramente más lento
                        tts.setPitch(1.0f);       // Pitch normal

                        // Listener de progreso
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                Log.d(TAG, "🔊 Generando audio...");
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                Log.i(TAG, "✅ Audio IVR generado: " + outputFile.getAbsolutePath());

                                // Convertir WAV a MP3 (simplificado: renombrar)
                                File mp3File = new File(reactContext.getFilesDir(), "ivr_corporate.mp3");

                                // Android TTS genera WAV, pero MediaPlayer acepta WAV
                                // Así que simplemente copiamos el archivo
                                try {
                                    if (outputFile.exists()) {
                                        // Renombrar/mover a .mp3 (aunque sea WAV, MediaPlayer lo acepta)
                                        if (mp3File.exists()) {
                                            mp3File.delete();
                                        }
                                        outputFile.renameTo(mp3File);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error renombrando archivo: " + e.getMessage());
                                }

                                WritableMap result = Arguments.createMap();
                                result.putString("path", mp3File.getAbsolutePath());
                                result.putDouble("size", mp3File.length());
                                result.putBoolean("success", true);
                                result.putString("method", "native_tts");

                                promise.resolve(result);

                                // Limpiar TTS
                                tts.shutdown();
                            }

                            @Override
                            public void onError(String utteranceId) {
                                Log.e(TAG, "❌ Error generando audio TTS");
                                promise.reject("TTS_ERROR", "Error generando audio con TTS");
                                tts.shutdown();
                            }
                        });

                        // Generar audio a archivo
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Bundle params = new Bundle();
                            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ivr_generation");

                            tts.synthesizeToFile(ivrText, params, outputFile, "ivr_generation");
                        } else {
                            // API < 21
                            HashMap<String, String> params = new HashMap<>();
                            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ivr_generation");

                            tts.synthesizeToFile(ivrText, params, outputFile.getAbsolutePath());
                        }

                    } else {
                        Log.e(TAG, "❌ Error inicializando TTS");
                        promise.reject("TTS_INIT_ERROR", "No se pudo inicializar TTS");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error generando audio IVR: " + e.getMessage(), e);
                promise.reject("GENERATE_ERROR", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Elimina el audio IVR guardado
     */
    @ReactMethod
    public void deleteIVRAudio(Promise promise) {
        try {
            File audioFile = new File(reactContext.getFilesDir(), "ivr_corporate.mp3");

            if (audioFile.exists()) {
                boolean deleted = audioFile.delete();

                if (deleted) {
                    Log.i(TAG, "✅ Audio IVR eliminado");
                    promise.resolve(true);
                } else {
                    promise.reject("DELETE_ERROR", "No se pudo eliminar el archivo");
                }
            } else {
                promise.resolve(false); // No existía
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error eliminando audio IVR: " + e.getMessage());
            promise.reject("DELETE_ERROR", e.getMessage(), e);
        }
    }
}
