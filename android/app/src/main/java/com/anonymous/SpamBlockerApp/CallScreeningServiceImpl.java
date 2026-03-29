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
                    AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();
                    Log.d(TAG, "🔇 Answer+Hangup ACTIVO - Modo: " + mode.name());
                    
                    if (mode == AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY) {
                        // MODO 1: Permitir que suene para que AccessibilityService/Receiver lo conteste y cuelgue
                        Log.d(TAG, "🔓 MODO 1: Permitir llamada para auto-contestación local");
                        showToast("🔇 Escudo 1: Se contestará pronto...");
                        
                        // Marcar número para answer+hangup
                        answerHangupHelper.markForAnswerHangup(callerNumber);

                        CallResponse response = new CallResponse.Builder()
                            .setDisallowCall(false)      // NO bloquear (permitir)
                            .setRejectCall(false)        // NO rechazar (permitir que suene)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .build();

                        respondToCall(callDetails, response);
                    } else {
                        // MODO 2 y 3 (BACKEND): RECHAZAR con señal BUSY usando TelecomManager
                        // Esto envía señal "Busy" real y activa el desvío condicional (*67*...)
                        Log.d(TAG, "🔒 MODO BACKEND: Rechazando con TelecomManager.endCall()...");
                        showToast("🛡️ Desviando a Víctor...");
                        
                        // Usar TelecomManager para enviar señal BUSY
                        try {
                            android.telecom.TelecomManager telecomManager = 
                                (android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE);
                            
                            if (telecomManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                boolean success = telecomManager.endCall();
                                Log.d(TAG, "✅ TelecomManager.endCall() ejecutado: " + success);
                            } else {
                                Log.w(TAG, "⚠️ TelecomManager no disponible, usando fallback");
                                // Fallback: usar CallResponse normal
                                CallResponse response = new CallResponse.Builder()
                                    .setDisallowCall(true)
                                    .setRejectCall(true)
                                    .setSilenceCall(true)
                                    .setSkipCallLog(false)
                                    .setSkipNotification(true)
                                    .build();
                                respondToCall(callDetails, response);
                                return;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error usando TelecomManager.endCall(): " + e.getMessage());
                        }
                        
                        // Después de endCall(), aún necesitamos responder al CallScreeningService
                        CallResponse response = new CallResponse.Builder()
                            .setDisallowCall(true)
                            .setRejectCall(true)
                            .setSilenceCall(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(true)
                            .build();
                        
                        respondToCall(callDetails, response);
                    }
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
        // 🚫 PRIORIDAD 1: BLACKLIST (incluso si es contacto) - TIEMPO: < 5ms
        if (prefsHelper != null && prefsHelper.isInBlacklist(number)) {
            Log.d(TAG, "🚫 Número en LISTA NEGRA - BLOQUEAR");
            showToast("🚫 SPAM DETECTADO (Blacklist)");
            return true;
        }

        // 📱 PRIORIDAD 2: Número desconocido/privado - TIEMPO: < 1ms
        if (number.equals("Desconocido") || number.equals("Privado") ||
            number.equals("Número oculto") || number.equals("Unknown") ||
            number.equals("Oculto") || number.equals("Privada") ||
            number.isEmpty() || number.equals("")) {
            Log.d(TAG, "📱 Número desconocido - BLOQUEAR");
            return true;
        }

        // 📞 PRIORIDAD 3: Números premium y comerciales - TIEMPO: < 1ms
        String cleaned = number.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("0034")) cleaned = cleaned.substring(4);
        else if (cleaned.startsWith("34") && cleaned.length() > 10) cleaned = cleaned.substring(2);

        // Prefijos bloqueados por ley española (servicios premium/tarificación especial)
        if (cleaned.startsWith("800") || cleaned.startsWith("801") ||
            cleaned.startsWith("802") || cleaned.startsWith("803") ||
            cleaned.startsWith("804") || cleaned.startsWith("805") ||
            cleaned.startsWith("806") || cleaned.startsWith("807") ||
            cleaned.startsWith("808") || cleaned.startsWith("809") ||
            cleaned.startsWith("900") || cleaned.startsWith("901") ||
            cleaned.startsWith("902") || cleaned.startsWith("903") ||
            cleaned.startsWith("904") || cleaned.startsWith("905") ||
            cleaned.startsWith("906") || cleaned.startsWith("907") ||
            cleaned.startsWith("908") || cleaned.startsWith("909") ||
            cleaned.startsWith("118")) {
            Log.d(TAG, "📞 Prefijo bloqueado por ley: " + cleaned.substring(0, 3));
            return true;
        }

        // Prefijos de call centers frecuentes en España
        if (cleaned.startsWith("510") || cleaned.startsWith("511") ||
            cleaned.startsWith("512") || cleaned.startsWith("513") ||
            cleaned.startsWith("514") || cleaned.startsWith("515") ||
            cleaned.startsWith("860") || cleaned.startsWith("861") ||
            cleaned.startsWith("862") || cleaned.startsWith("863") ||
            cleaned.startsWith("864") || cleaned.startsWith("865") ||
            cleaned.startsWith("866") || cleaned.startsWith("867") ||
            cleaned.startsWith("868") || cleaned.startsWith("869")) {
            Log.d(TAG, "📞 Prefijo call center detectado: " + cleaned.substring(0, 3));
            return true;
        }

        // 👤 PRIORIDAD 4: Verificar si está en CONTACTOS (Whitelist automática) - TIEMPO: 100ms - 1000ms
        // Solo llegamos aquí si NO es blacklist, NO es oculto y NO es premium.
        long startTime = System.currentTimeMillis();
        boolean isContact = false;
        ContactsHelper.ContactInfo contact = null;
        try {
            contact = contactsHelper.findContactByNumber(number);
            if (contact != null) {
                isContact = true;
                Log.d(TAG, "👤 ES CONTACTO: " + contact.name + " - PERMITIR (Búsqueda: " + (System.currentTimeMillis() - startTime) + "ms)");
                // showToast("👤 Contacto: " + contact.name); // Quitamos toast para velocidad
                return false;  // NO mostrar notificación, es contacto conocido
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verificando contactos: " + e.getMessage());
        }
        Log.d(TAG, "🔍 Búsqueda contactos completada en " + (System.currentTimeMillis() - startTime) + "ms");

        // 📵 PRIORIDAD 5: MODO RADICAL (solo si NO es contacto)
        boolean modoRadical = isModoRadicalEnabled();
        if (modoRadical && !isContact) {
            Log.d(TAG, "🚫 MODO RADICAL ACTIVO - NO es contacto → BLOQUEAR");
            showToast("🚫 MODO RADICAL");
            return true;  // Mostrar notificación de spam
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
