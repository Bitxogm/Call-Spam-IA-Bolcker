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

        // Obtener estado de la llamada ANTES de verificar isEnabled
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        // LOG CRÍTICO - SIEMPRE logear el estado recibido (incluso si disabled)
        logsHelper.logInfo("📞 BROADCAST RECIBIDO - Estado: " + (state != null ? state : "NULL") + ", A+H: " + (isEnabled ? "ON" : "OFF"));
        logsHelper.logDebug("onReceive: action=" + action + ", A+H enabled=" + isEnabled);

        // Verificar si Answer+Hangup está habilitado
        if (!isEnabled) {
            Log.d(TAG, "⚠️ Answer+Hangup desactivado, ignorando evento");
            logsHelper.logWarning("⚠️ Answer+Hangup DESACTIVADO - evento ignorado");
            return;
        }

        if (state == null) {
            Log.w(TAG, "⚠️ Estado es null");
            logsHelper.logError("❌ Estado de llamada es NULL");
            return;
        }

        Log.d(TAG, "📞 Estado de llamada: " + state);
        logsHelper.logInfo("📞 Phone state: " + state);

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

        // LOG MODO ACTUAL (antes de shouldProcess)
        AnswerHangupHelper.Mode currentMode = answerHangupHelper.getMode();
        Log.d(TAG, "📱 MODO ACTUAL CARGADO: " + currentMode.name());
        logsHelper.logInfo("📱 Modo cargado desde prefs: " + currentMode.name());

        // Verificar si debe procesarse (hangup, IVR, o IA)
        boolean shouldProcess = answerHangupHelper.shouldHangup(incomingNumber);
        Log.d(TAG, "🔍 shouldProcess() = " + shouldProcess);
        logsHelper.logDebug("shouldProcess para " + incomingNumber + ": " + shouldProcess);

        if (shouldProcess) {
            // Obtener modo configurado
            AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();
            Log.d(TAG, "🎯 Modo configurado: " + mode.name());
            logsHelper.logInfo("🎯 Ejecutando modo: " + mode.name() + " para " + incomingNumber);

            // Crear variable final para lambdas
            final String number = incomingNumber;

            switch (mode) {
                case HANGUP_IMMEDIATELY:
                    // Modo 1: Colgar después de delay
                    int delay = answerHangupHelper.getHangupDelay();
                    Log.d(TAG, "⏱️ MODO 1: Esperando " + delay + " segundos antes de colgar...");
                    logsHelper.logInfo("⏱️ Modo 1 - Programando hangup en " + delay + "s");

                    showToast(context, "🔇 Modo 1: Colgando automáticamente");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Log.d(TAG, "🎯 Ejecutando hangup (Modo 1)");
                        logsHelper.logInfo("🎯 Modo 1 - Hangup ejecutado");
                        hangupCall(context, number);
                    }, delay * 1000L);
                    break;

                case PLAY_MESSAGE:
                    // Modo 2: Reproducir IVR corporativo
                    Log.d(TAG, "🔊 MODO 2: Iniciando IVR corporativo...");
                    logsHelper.logInfo("🔊 Modo 2 - Iniciando IVR");

                    showToast(context, "🔊 Modo 2: Reproduciendo IVR");

                    // Obtener instancia única de IVRMessageHelper
                    IVRMessageHelper ivrHelper = IVRMessageHelper.getInstance(context);

                    // Iniciar IVR (30 segundos máximo)
                    boolean ivrStarted = ivrHelper.startIVR(
                        IVRMessageHelper.IVRType.CORPORATE_INFINITE,
                        30  // 30 segundos máximo
                    );

                    if (ivrStarted) {
                        Log.d(TAG, "✅ IVR iniciado correctamente");
                        logsHelper.logInfo("✅ Modo 2 - IVR iniciado (30s max)");

                        // Colgar después de 30 segundos
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Log.d(TAG, "🎯 IVR terminado, colgando (Modo 2)");
                            logsHelper.logInfo("🎯 Modo 2 - IVR finalizado, ejecutando hangup");
                            IVRMessageHelper.getInstance(context).stopIVR();
                            hangupCall(context, number);
                        }, 31000L);  // 31s para asegurar que el IVR termine
                    } else {
                        Log.e(TAG, "❌ Error iniciando IVR, colgando directamente");
                        logsHelper.logError("❌ Modo 2 - Error IVR, fallback a hangup");
                        hangupCall(context, number);
                    }
                    break;

                case AI_CONVERSATION:
                    // Modo 3: IA conversacional (futuro)
                    Log.d(TAG, "🤖 MODO 3: IA Conversacional (no implementado aún)");
                    logsHelper.logWarning("🤖 Modo 3 - No implementado, fallback a hangup");

                    showToast(context, "🤖 Modo 3: No disponible (colgando)");

                    // Fallback: colgar después de 2 segundos
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        hangupCall(context, number);
                    }, 2000L);
                    break;
            }
        } else {
            Log.d(TAG, "⚠️ shouldProcess = false, NO se procesará");
            logsHelper.logWarning("shouldProcess = false - número NO marcado: " + incomingNumber);
        }
    }

    /**
     * Maneja estado IDLE (sin llamada)
     */
    private void handleIdle(Context context) {
        Log.d(TAG, "📞 IDLE (sin llamada)");

        // Detener IVR si está reproduciéndose
        IVRMessageHelper ivrHelper = IVRMessageHelper.getInstance(context);
        if (ivrHelper.isPlaying()) {
            Log.d(TAG, "🛑 Deteniendo IVR (llamada finalizada)");
            ivrHelper.stopIVR();
        }

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

                        // Limpiar número marcado después del hangup exitoso
                        answerHangupHelper.clearMarked();
                        Log.d(TAG, "🧹 Número marcado limpiado después de hangup exitoso");
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
