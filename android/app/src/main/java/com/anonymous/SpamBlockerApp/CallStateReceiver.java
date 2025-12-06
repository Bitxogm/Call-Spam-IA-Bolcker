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
    private String lastIncomingNumber = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Inicializar helper
        if (answerHangupHelper == null) {
            answerHangupHelper = new AnswerHangupHelper(context);
        }

        // Verificar si Answer+Hangup está habilitado
        if (!answerHangupHelper.isEnabled()) {
            return;
        }

        // Obtener estado de la llamada
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (state == null) {
            return;
        }

        Log.d(TAG, "📞 Estado de llamada: " + state);

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
        }
    }

    /**
     * Maneja estado OFFHOOK (llamada contestada)
     * AQUÍ es donde ejecutamos el hangup
     */
    private void handleOffhook(Context context, Intent intent) {
        Log.d(TAG, "📞 OFFHOOK (contestada)");

        // Si no tenemos número guardado, intentar obtenerlo del intent
        String incomingNumber = lastIncomingNumber;
        if (incomingNumber == null) {
            incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        }

        if (incomingNumber == null) {
            Log.d(TAG, "No hay número de llamada entrante");
            return;
        }

        // Verificar si debe colgarse
        boolean shouldHangup = answerHangupHelper.shouldHangup(incomingNumber);

        if (shouldHangup) {
            int delay = answerHangupHelper.getHangupDelay();
            Log.d(TAG, "⏱️ Esperando " + delay + " segundos antes de colgar...");

            showToast(context, "🔇 Llamada spam contestada silenciosamente");

            // Esperar delay y colgar
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hangupCall(context, incomingNumber);
            }, delay * 1000L);
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
                    } else {
                        Log.e(TAG, "❌ No se pudo colgar la llamada");
                        showToast(context, "❌ Error colgando llamada");
                    }
                } else {
                    Log.e(TAG, "TelecomManager es null");
                }
            } else {
                // Android 8 y anteriores: usar reflexión (menos confiable)
                Log.w(TAG, "Android < 9: TelecomManager.endCall() no disponible");
                showToast(context, "⚠️ Answer+Hangup requiere Android 9+");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Sin permiso ANSWER_PHONE_CALLS", e);
            showToast(context, "❌ Permiso ANSWER_PHONE_CALLS requerido");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error colgando llamada", e);
            showToast(context, "❌ Error: " + e.getMessage());
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
