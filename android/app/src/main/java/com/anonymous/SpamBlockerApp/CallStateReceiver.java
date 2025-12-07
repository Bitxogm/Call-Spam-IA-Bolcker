// CallStateReceiver.java
package com.anonymous.SpamBlockerApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

/**
 * BroadcastReceiver que monitorea el estado de las llamadas.
 *
 * Escucha cambios de estado:
 * - IDLE (sin llamada)
 * - RINGING (llamada entrante)
 * - OFFHOOK (llamada contestada)
 *
 * Cuando detecta OFFHOOK + número marcado para hangup:
 * - Espera delay configurado
 * - Cuelga la llamada automáticamente
 */
public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    private AnswerHangupHelper answerHangupHelper;
    private LogsHelper logsHelper;
    private String lastIncomingNumber = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        // LOG INMEDIATO para debug
        String action = intent.getAction();
        Log.d(TAG, "🔔 onReceive llamado - Action: " + action);

        // Inicializar helpers
        if (answerHangupHelper == null) {
            answerHangupHelper = new AnswerHangupHelper(context);
        }
        if (logsHelper == null) {
            logsHelper = new LogsHelper(context);
        }

        // LOG: Estado de Answer+Hangup
        boolean isEnabled = answerHangupHelper.isEnabled();
        Log.d(TAG, "🔔 Answer+Hangup enabled: " + isEnabled);
        logsHelper.logDebug("onReceive: action=" + action + ", A+H enabled=" + isEnabled);

        // Verificar si Answer+Hangup está habilitado
        if (!isEnabled) {
            Log.d(TAG, "⚠️ Answer+Hangup desactivado, ignorando evento");
            return;
        }

        // Obtener estado de la llamada
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (state == null) {
            Log.w(TAG, "⚠️ Estado es null");
            return;
        }

        Log.d(TAG, "📞 Estado de llamada: " + state);
        logsHelper.logDebug("Phone state: " + state);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            handleRinging(context, intent);
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            handleOffhook(context, intent);
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            handleIdle(context);
        }
    }

    /**
     * Maneja estado RINGING (llamada entrante)
     */
    private void handleRinging(Context context, Intent intent) {
        // Guardar número entrante
        lastIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (lastIncomingNumber != null) {
            Log.d(TAG, "📞 RINGING: " + lastIncomingNumber);
            logsHelper.logDebug("RINGING detectado: " + lastIncomingNumber);

            // Si este número está marcado para Answer+Hangup, contestar automáticamente
            if (answerHangupHelper.shouldHangup(lastIncomingNumber)) {
                Log.d(TAG, "🔇 Spam detectado - Contestando automáticamente...");
                logsHelper.logInfo("Contestando automáticamente spam: " + lastIncomingNumber);
                answerCall(context, lastIncomingNumber);
            }
        }
    }

    /**
     * Contesta la llamada automáticamente
     */
    private void answerCall(Context context, String number) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

                if (telecomManager != null) {
                    // acceptRingingCall() es void, no retorna boolean
                    telecomManager.acceptRingingCall();
                    Log.d(TAG, "✅ Llamada contestada automáticamente: " + number);
                    logsHelper.logInfo("✅ Llamada spam contestada automáticamente: " + number);
                    showToast(context, "🔇 Spam contestado automáticamente");
                } else {
                    Log.e(TAG, "TelecomManager es null");
                    logsHelper.logError("❌ TelecomManager es null - No se puede contestar");
                }
            } else {
                Log.w(TAG, "Android < 9: acceptRingingCall() no disponible");
                logsHelper.logWarning("⚠️ Android < 9: Auto-answer no disponible (API " + Build.VERSION.SDK_INT + ")");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Sin permiso ANSWER_PHONE_CALLS", e);
            logsHelper.logError("❌ SecurityException al contestar: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error contestando llamada", e);
            logsHelper.logError("❌ Exception al contestar: " + e.getMessage());
        }
    }

    /**
     * Maneja estado OFFHOOK (llamada contestada)
     * AQUÍ es donde ejecutamos el hangup
     */
    private void handleOffhook(Context context, Intent intent) {
        Log.d(TAG, "📞 OFFHOOK (contestada)");
        logsHelper.logInfo("OFFHOOK detectado - llamada contestada");

        // Si no tenemos número guardado, intentar obtenerlo del intent
        String incomingNumber = lastIncomingNumber;
        if (incomingNumber == null) {
            incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        }

        Log.d(TAG, "🔍 Número entrante: " + incomingNumber);
        logsHelper.logDebug("Número OFFHOOK: " + incomingNumber);

        if (incomingNumber == null) {
            Log.d(TAG, "No hay número de llamada entrante");
            logsHelper.logWarning("OFFHOOK sin número - ignorando");
            return;
        }

        // Verificar si debe colgarse
        boolean shouldHangup = answerHangupHelper.shouldHangup(incomingNumber);
        Log.d(TAG, "🔍 shouldHangup() = " + shouldHangup);
        logsHelper.logDebug("shouldHangup para " + incomingNumber + ": " + shouldHangup);

        if (shouldHangup) {
            int delay = answerHangupHelper.getHangupDelay();
            Log.d(TAG, "⏱️ Esperando " + delay + " segundos antes de colgar...");
            logsHelper.logInfo("⏱️ Programando hangup en " + delay + " segundos para: " + incomingNumber);

            showToast(context, "🔇 Llamada spam contestada silenciosamente");

            // Crear variable final para lambda
            final String numberToHangup = incomingNumber;

            // Esperar delay y colgar
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "🎯 Ejecutando hangup después de delay");
                logsHelper.logInfo("🎯 Delay completado - ejecutando hangup");
                hangupCall(context, numberToHangup);
            }, delay * 1000L);
        } else {
            Log.d(TAG, "⚠️ shouldHangup = false, NO se colgará");
            logsHelper.logWarning("shouldHangup = false - número NO marcado para hangup: " + incomingNumber);
        }
    }

    /**
     * Maneja estado IDLE (sin llamada)
     */
    private void handleIdle(Context context) {
        Log.d(TAG, "📞 IDLE (sin llamada)");
        lastIncomingNumber = null;
    }

    /**
     * Cuelga la llamada usando TelecomManager
     */
    private void hangupCall(Context context, String number) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ (API 28+)
                TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

                if (telecomManager != null) {
                    boolean success = telecomManager.endCall();

                    if (success) {
                        Log.d(TAG, "✅ Llamada colgada: " + number);
                        showToast(context, "✅ Spam colgado automáticamente");
                        logsHelper.logInfo("✅ Spam colgado automáticamente: " + number);
                    } else {
                        Log.e(TAG, "❌ No se pudo colgar la llamada");
                        showToast(context, "❌ Error colgando llamada");
                        logsHelper.logError("❌ TelecomManager.endCall() retornó false para: " + number);
                    }
                } else {
                    Log.e(TAG, "TelecomManager es null");
                    logsHelper.logError("❌ TelecomManager es null - No se puede colgar");
                }
            } else {
                // Android 8 y anteriores: usar reflexión (menos confiable)
                Log.w(TAG, "Android < 9: TelecomManager.endCall() no disponible");
                showToast(context, "⚠️ Answer+Hangup requiere Android 9+");
                logsHelper.logWarning("⚠️ Android < 9: Answer+Hangup no disponible (API " + Build.VERSION.SDK_INT + ")");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Sin permiso ANSWER_PHONE_CALLS", e);
            showToast(context, "❌ Permiso ANSWER_PHONE_CALLS requerido");
            logsHelper.logError("❌ SecurityException: Sin permiso ANSWER_PHONE_CALLS - " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error colgando llamada", e);
            showToast(context, "❌ Error: " + e.getMessage());
            logsHelper.logError("❌ Exception colgando llamada: " + e.getMessage() + " para número: " + number);
        }
    }

    /**
     * Muestra un Toast en el hilo principal
     */
    private void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
}
