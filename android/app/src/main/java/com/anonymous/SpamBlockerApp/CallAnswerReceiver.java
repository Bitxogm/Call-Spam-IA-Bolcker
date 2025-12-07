// CallAnswerReceiver.java
package com.anonymous.SpamBlockerApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * Recibe acciones de las notificaciones (contestar con IA o ignorar)
 */
public class CallAnswerReceiver extends BroadcastReceiver {
    private static final String TAG = "CallAnswerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG, "📬 Acción recibida: " + action);

        if (SpamNotificationManager.ACTION_ANSWER_WITH_AI.equals(action)) {
            handleAnswerWithAI(context, intent);
        } else if ("DISMISS".equals(action)) {
            handleDismiss(context);
        }
    }

    /**
     * Usuario eligió contestar con IA
     */
    private void handleAnswerWithAI(Context context, Intent intent) {
        String phoneNumber = intent.getStringExtra(SpamNotificationManager.EXTRA_PHONE_NUMBER);

        Log.d(TAG, "🤖 Usuario eligió contestar con IA");
        Log.d(TAG, "Número: " + phoneNumber);

        // Cancelar notificación
        SpamNotificationManager.cancelNotification(context);

        // TODO: Aquí conectaremos con SpamCallService para contestar la llamada
        // Por ahora, solo logueamos

        // En siguiente fase:
        // 1. Obtener la llamada activa usando TelecomManager
        // 2. Llamar a call.answer()
        // 3. Iniciar conversación con IA

        Log.d(TAG, "⚠️ Funcionalidad de contestar pendiente de implementar");

        // Mostrar notificación temporal de confirmación
        showAIActivatedNotification(context, phoneNumber);
    }

    /**
     * Usuario eligió ignorar
     */
    private void handleDismiss(Context context) {
        Log.d(TAG, "❌ Usuario eligió ignorar");
        SpamNotificationManager.cancelNotification(context);
    }

    /**
     * Muestra notificación confirmando que IA fue activada
     */
    private void showAIActivatedNotification(Context context, String phoneNumber) {
        // TODO: Implementar notificación de confirmación
        Log.d(TAG, "✅ IA activada para: " + phoneNumber);
    }
}
