package com.anonymous.SpamBlockerApp;

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
