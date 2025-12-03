// DialerActivity.java
package com.anonymous.SpamBlockerApp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * Activity requerida para que Android reconozca esta app como marcador predeterminado.
 * Maneja intents DIAL, VIEW (tel://), y CALL.
 */
public class DialerActivity extends Activity {
    private static final String TAG = "DialerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        Log.d(TAG, "📞 DialerActivity iniciado - Acción: " + action);

        if (Intent.ACTION_DIAL.equals(action)) {
            handleDialIntent(data);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            handleViewIntent(data);
        } else if (Intent.ACTION_CALL.equals(action)) {
            handleCallIntent(data);
        } else {
            // Intent desconocido, abrir app principal
            Log.d(TAG, "⚠️ Intent desconocido, abriendo app principal");
            openMainApp();
        }

        // Esta activity no tiene UI propia, termina después de procesar
        finish();
    }

    /**
     * Maneja intent ACTION_DIAL (cuando usuario quiere marcar un número)
     */
    private void handleDialIntent(Uri data) {
        Log.d(TAG, "📱 DIAL intent recibido: " + data);

        // Por ahora, redirigir a la app principal
        // TODO: En el futuro, abrir una pantalla de marcador personalizada
        openMainApp();
    }

    /**
     * Maneja intent ACTION_VIEW con esquema tel:// (cuando usuario toca un número)
     */
    private void handleViewIntent(Uri data) {
        Log.d(TAG, "👁️ VIEW intent recibido: " + data);

        if (data != null && "tel".equals(data.getScheme())) {
            String phoneNumber = data.getSchemeSpecificPart();
            Log.d(TAG, "📞 Número detectado: " + phoneNumber);

            // Opción 1: Abrir app principal con el número
            openMainApp();

            // Opción 2: Iniciar llamada directamente (comentado por seguridad)
            // placeCall(phoneNumber);
        } else {
            openMainApp();
        }
    }

    /**
     * Maneja intent ACTION_CALL (solicitud de llamada directa)
     */
    private void handleCallIntent(Uri data) {
        Log.d(TAG, "☎️ CALL intent recibido: " + data);

        if (data != null && "tel".equals(data.getScheme())) {
            String phoneNumber = data.getSchemeSpecificPart();
            Log.d(TAG, "📞 Iniciando llamada a: " + phoneNumber);

            // Realizar la llamada usando TelecomManager
            placeCall(phoneNumber);
        } else {
            Log.e(TAG, "❌ CALL intent sin número válido");
            openMainApp();
        }
    }

    /**
     * Realiza una llamada telefónica usando el sistema
     */
    private void placeCall(String phoneNumber) {
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);

            if (telecomManager != null) {
                Uri uri = Uri.fromParts("tel", phoneNumber, null);
                telecomManager.placeCall(uri, null);
                Log.d(TAG, "✅ Llamada iniciada a: " + phoneNumber);
            } else {
                Log.e(TAG, "❌ TelecomManager no disponible");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permiso denegado para realizar llamada: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error al iniciar llamada: " + e.getMessage());
        }
    }

    /**
     * Abre la actividad principal de la app (Dashboard)
     */
    private void openMainApp() {
        try {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mainIntent);
            Log.d(TAG, "✅ App principal abierta");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error abriendo app principal: " + e.getMessage());
        }
    }
}
