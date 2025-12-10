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
    private SharedPreferencesHelper prefsHelper;
    private ContactsHelper contactsHelper;
    private AnswerHangupHelper answerHangupHelper;
    private LogsHelper logsHelper;
    private CallHistoryHelper callHistoryHelper;

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.d(TAG, "📞 Nueva llamada detectada");

        // Inicializar helpers
        if (prefsHelper == null) {
            prefsHelper = new SharedPreferencesHelper(this);
        }
        if (contactsHelper == null) {
            contactsHelper = new ContactsHelper(this);
        }
        if (answerHangupHelper == null) {
            answerHangupHelper = new AnswerHangupHelper(this);
        }
        if (logsHelper == null) {
            logsHelper = new LogsHelper(this);
        }
        if (callHistoryHelper == null) {
            callHistoryHelper = new CallHistoryHelper(this);
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
            Log.d(TAG, "🤖 SPAM POTENCIAL");
            showToast("🤖 SPAM DETECTADO: " + callerNumber);
            logsHelper.logWarning("SPAM detectado: " + callerNumber);

            // 🎯 ANSWER+HANGUP: Si está activado, contestar silenciosamente y colgar
            try {
                // DEBUG: Verificar estado de Answer+Hangup
                boolean answerHangupHelperIsNull = (answerHangupHelper == null);
                boolean answerHangupIsEnabled = (answerHangupHelper != null && answerHangupHelper.isEnabled());
                Log.d(TAG, "🔍 DEBUG - answerHangupHelper null? " + answerHangupHelperIsNull);
                Log.d(TAG, "🔍 DEBUG - isEnabled()? " + answerHangupIsEnabled);
                showToast("🔍 A+H: null=" + answerHangupHelperIsNull + ", enabled=" + answerHangupIsEnabled);
                logsHelper.logDebug("Answer+Hangup: null=" + answerHangupHelperIsNull + ", enabled=" + answerHangupIsEnabled);

                if (answerHangupHelper != null && answerHangupHelper.isEnabled()) {
                    Log.d(TAG, "🔇 Answer+Hangup ACTIVO - Marcar para colgar");
                    showToast("🔇 Spam: Contestar y colgar automáticamente");
                    logsHelper.logInfo("Answer+Hangup activado para: " + callerNumber);
                    callHistoryHelper.addSpamCall(callerNumber, "Spam", "Answer+Hangup");

                    // Marcar número para answer+hangup
                    answerHangupHelper.markForAnswerHangup(callerNumber);

                    // PERMITIR la llamada normalmente (CallStateReceiver la contestará y colgará)
                    // NO intentar silenciar aquí - causa "Invalid response State"
                    CallResponse response = new CallResponse.Builder()
                        .setDisallowCall(false)      // NO bloquear (permitir)
                        .setRejectCall(false)        // NO rechazar (permitir que suene)
                        // .setSilenceCall(true)     // ❌ REMOVIDO - causa error
                        .setSkipCallLog(false)       // SÍ registrar en log
                        .setSkipNotification(false)  // SÍ mostrar notificación del sistema
                        .build();

                    respondToCall(callDetails, response);
                } else {
                // Modo normal: Mostrar notificación
                Log.d(TAG, "📲 Mostrando notificación de spam");
                logsHelper.logInfo("Notificación de spam enviada para: " + callerNumber);
                callHistoryHelper.addSpamCall(callerNumber, "Spam", "Notification");
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
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error en Answer+Hangup: " + e.getMessage(), e);
                showToast("❌ Error Answer+Hangup: " + e.getMessage());
                logsHelper.logError("Error Answer+Hangup: " + e.getMessage() + " para número: " + callerNumber);

                // Fallback: Modo normal con notificación
                SpamNotificationManager.showIncomingSpamNotification(this, callerNumber);
                callHistoryHelper.addSpamCall(callerNumber, "Spam", "Notification (fallback)");
                CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
                respondToCall(callDetails, response);
            }
        } else {
            Log.d(TAG, "✅ Número conocido/confiable - Permitiendo");
            showToast("✅ Número normal: " + callerNumber);
            logsHelper.logInfo("Llamada permitida: " + callerNumber);
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
     * ORDEN DE PRIORIDAD:
     * 1. Blacklist (máxima prioridad, incluso sobre contactos)
     * 2. Contactos (whitelist automática)
     * 3. Modo Radical (si NO es contacto)
     * 4. Números desconocidos/privados
     * 5. Números premium (900, 902, etc.)
     */
    private boolean shouldShowNotification(String number) {
        // 🚫 PRIORIDAD 1: BLACKLIST (incluso si es contacto)
        if (prefsHelper != null && prefsHelper.isInBlacklist(number)) {
            Log.d(TAG, "🚫 Número en LISTA NEGRA - BLOQUEAR");
            showToast("🚫 SPAM DETECTADO: " + number);
            return true;
        }

        // 👤 PRIORIDAD 2: Verificar si está en CONTACTOS (Whitelist automática)
        boolean isContact = false;
        ContactsHelper.ContactInfo contact = null;
        try {
            contact = contactsHelper.findContactByNumber(number);
            if (contact != null) {
                isContact = true;
                Log.d(TAG, "👤 ES CONTACTO: " + contact.name + " - PERMITIR");
                showToast("👤 Contacto: " + contact.name);
                return false;  // NO mostrar notificación, es contacto conocido
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verificando contactos: " + e.getMessage());
        }

        // 📵 PRIORIDAD 3: MODO RADICAL (solo si NO es contacto)
        boolean modoRadical = isModoRadicalEnabled();
        if (modoRadical && !isContact) {
            Log.d(TAG, "🚫 MODO RADICAL ACTIVO - NO es contacto → BLOQUEAR");
            showToast("🚫 MODO RADICAL: No es contacto");
            return true;  // Mostrar notificación de spam
        }

        // 📱 PRIORIDAD 4: Número desconocido/privado
        if (number.equals("Desconocido") ||
            number.equals("Privado") ||
            number.equals("Número oculto")) {
            Log.d(TAG, "📱 Número desconocido - BLOQUEAR");
            return true;
        }

        // 📞 PRIORIDAD 5: Números premium y comerciales (800, 900, 901, 902, etc.)
        String cleaned = number.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("800") ||  // Números gratuitos comerciales
            cleaned.startsWith("900") ||  // Tarificación especial
            cleaned.startsWith("901") ||  // Tarificación especial
            cleaned.startsWith("902") ||  // Tarificación especial
            cleaned.startsWith("803") ||  // Servicios de participación
            cleaned.startsWith("806") ||  // Servicios de entretenimiento
            cleaned.startsWith("807") ||  // Servicios de entretenimiento
            cleaned.startsWith("905")) {  // Servicios de valor añadido
            Log.d(TAG, "📞 Número de tarificación especial/comercial - BLOQUEAR: " + cleaned.substring(0, 3));
            return true;
        }

        // ✅ Todo lo demás: Número normal
        Log.d(TAG, "✅ Número normal: " + number);
        return false;  // Número normal, no mostrar notificación
    }

    /**
     * Verifica si el "Modo Radical" está activo.
     * Modo Radical = Solo permitir llamadas de contactos, bloquear todo lo demás.
     */
    private boolean isModoRadicalEnabled() {
        try {
            return getSharedPreferences("spam_blocker_settings", MODE_PRIVATE)
                .getBoolean("modo_radical", false);
        } catch (Exception e) {
            Log.e(TAG, "Error leyendo modo_radical: " + e.getMessage());
            return false;
        }
    }
}
