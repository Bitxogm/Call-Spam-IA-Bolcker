// CallAnswerReceiver.java
package com.anonymous.SpamBlockerApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Recibe los botones de las notificaciones: "Ignorar" de la de spam, y "Pasar a Manolo"
 * y "Pasar y bloquear" de la de llamada entrante (modos 2 y 3).
 */
public class CallAnswerReceiver extends BroadcastReceiver {
    private static final String TAG = "CallAnswerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG, "📬 Acción recibida: " + action);

        if ("DISMISS".equals(action)) {
            handleDismiss(context);
        } else if (IncomingCallNotice.ACCION_PASAR.equals(action)) {
            pasarAManolo(context, intent.getStringExtra(IncomingCallNotice.EXTRA_NUMERO), false);
        } else if (IncomingCallNotice.ACCION_PASAR_Y_BLOQUEAR.equals(action)) {
            pasarAManolo(context, intent.getStringExtra(IncomingCallNotice.EXTRA_NUMERO), true);
        }
    }

    /**
     * Rechaza la llamada que está sonando: el desvío "si está ocupado" (*67*) del operador
     * la manda al servidor, igual que hace el modo 2/3 con el spam detectado.
     */
    private void pasarAManolo(Context context, String numero, boolean bloquear) {
        SpamNotificationManager.cancelIncomingCallChoice(context);
        LogsHelper logs = new LogsHelper(context);
        logs.logInfo("🔘 Pulsado \"" + (bloquear ? "Pasar y bloquear" : "Pasar a Manolo") + "\" para " + numero);

        // endCall() cuelga la llamada activa: si ya se contestó, colgaría la conversación
        if (!sigueSonando(context)) {
            Log.w(TAG, "⏭️ La llamada ya no está sonando: no se rechaza " + numero);
            logs.logWarning("Pasar a Manolo ignorado: la llamada ya no suena (" + numero + ")");
            return;
        }

        try {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecom == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                logs.logError("Pasar a Manolo: TelecomManager no disponible");
                return;
            }
            boolean rechazada = telecom.endCall();
            Log.d(TAG, "🤖 Pasada a Manolo (" + (bloquear ? "y bloqueada" : "sin bloquear") + "): "
                + numero + " → endCall=" + rechazada);
            logs.logInfo("Pasada a Manolo a mano: " + numero + (bloquear ? " (y bloqueada)" : ""));
        } catch (SecurityException e) {
            logs.logError("Pasar a Manolo sin permiso ANSWER_PHONE_CALLS: " + e.getMessage());
            return;
        }

        if (bloquear && IncomingCallNotice.sePuedeBloquear(numero)) {
            new SharedPreferencesHelper(context).addToBlacklist(numero);
        }
        new CallHistoryHelper(context).addSpamCall(
            numero, IncomingCallNotice.MOTIVO_HISTORIAL, IncomingCallNotice.accionHistorial(bloquear));
    }

    private boolean sigueSonando(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getCallState() == TelephonyManager.CALL_STATE_RINGING;
        } catch (SecurityException e) {
            Log.w(TAG, "Sin READ_PHONE_STATE para comprobar la llamada: " + e.getMessage());
            return false;
        }
    }

    /**
     * Usuario eligió ignorar
     */
    private void handleDismiss(Context context) {
        Log.d(TAG, "❌ Usuario eligió ignorar");
        SpamNotificationManager.cancelNotification(context);
    }
}
