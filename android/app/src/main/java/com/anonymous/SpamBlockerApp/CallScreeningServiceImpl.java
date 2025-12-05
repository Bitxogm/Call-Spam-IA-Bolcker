// CallScreeningServiceImpl.java
package com.anonymous.SpamBlockerApp;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Service para detectar y filtrar llamadas entrantes (Android 10+)
 * No requiere ser app de marcador predeterminada.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class CallScreeningServiceImpl extends CallScreeningService {
    private static final String TAG = "CallScreeningService";
    private DatabaseHelper dbHelper;

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.d(TAG, "📞 Nueva llamada detectada");

        // Inicializar helper de base de datos
        if (dbHelper == null) {
            dbHelper = new DatabaseHelper(this);
        }

        // Obtener información de la llamada
        String callerNumber = getCallerNumber(callDetails);
        boolean isIncoming = callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING;

        Log.d(TAG, "Número: " + callerNumber);
        Log.d(TAG, "Dirección: " + (isIncoming ? "ENTRANTE" : "SALIENTE"));

        // TOAST VISIBLE para debugging
        showToast("📞 Llamada detectada: " + callerNumber);

        if (!isIncoming) {
            Log.d(TAG, "Llamada saliente, ignorando");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        // Verificar si es número de emergencia
        if (isEmergencyNumber(callerNumber)) {
            Log.d(TAG, "🚨 NÚMERO DE EMERGENCIA - No se intercepta");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        // Verificar si es spam o desconocido
        boolean isPotentialSpam = shouldShowNotification(callerNumber);

        if (isPotentialSpam) {
            Log.d(TAG, "🤖 SPAM POTENCIAL - Mostrando notificación");
            showToast("🤖 SPAM DETECTADO: " + callerNumber);

            // Mostrar notificación con opción de contestar con IA
            SpamNotificationManager.showIncomingSpamNotification(
                this,
                callerNumber
            );

            showToast("📲 Notificación enviada");

            // NO bloquear la llamada, dejar que suene
            // El usuario decidirá si contestar con IA
            CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)  // NO bloquear
                .setRejectCall(false)    // NO rechazar
                .setSkipCallLog(false)   // SÍ registrar en log
                .setSkipNotification(false)  // SÍ mostrar notificación del sistema
                .build();

            respondToCall(callDetails, response);
        } else {
            Log.d(TAG, "✅ Número conocido/confiable - Permitiendo");
            showToast("✅ Número normal: " + callerNumber);
            respondToCall(callDetails, new CallResponse.Builder().build());
        }
    }

    /**
     * Muestra un Toast en el hilo principal (para debugging visual)
     */
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Obtiene el número del caller
     */
    private String getCallerNumber(Call.Details callDetails) {
        if (callDetails.getHandle() != null) {
            String number = callDetails.getHandle().getSchemeSpecificPart();
            return number != null ? number : "Desconocido";
        }
        return "Desconocido";
    }

    /**
     * Verifica si es número de emergencia
     */
    private boolean isEmergencyNumber(String number) {
        if (number == null) return false;

        String cleaned = number.replaceAll("[^0-9]", "");

        return cleaned.equals("112") ||
               cleaned.equals("911") ||
               cleaned.equals("091") ||  // Policía España
               cleaned.equals("080") ||  // Bomberos España
               cleaned.equals("061");    // Urgencias médicas España
    }

    /**
     * Decide si mostrar notificación de spam
     */
    private boolean shouldShowNotification(String number) {
        // Casos donde mostrar notificación:

        // 1. Número desconocido/privado
        if (number.equals("Desconocido") ||
            number.equals("Privado") ||
            number.equals("Número oculto")) {
            Log.d(TAG, "📱 Número desconocido");
            return true;
        }

        // 2. Números 900, 901, 902 (números de tarificación especial)
        String cleaned = number.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("900") ||
            cleaned.startsWith("901") ||
            cleaned.startsWith("902") ||
            cleaned.startsWith("803") ||
            cleaned.startsWith("806") ||
            cleaned.startsWith("807")) {
            Log.d(TAG, "📞 Número de tarificación especial");
            return true;
        }

        // 3. ✅ VERIFICAR EN BASE DE DATOS DE SPAM
        if (dbHelper != null && dbHelper.isSpamNumber(number)) {
            Log.d(TAG, "🚫 Número en LISTA NEGRA");
            return true;
        }

        Log.d(TAG, "✅ Número normal: " + number);
        return false;  // Número normal, no mostrar notificación
    }
}
