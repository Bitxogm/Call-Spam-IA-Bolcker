// CallAnswerReceiver.java
package com.anonymous.SpamBlockerApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Recibe la acción "Ignorar" de la notificación de spam.
 */
public class CallAnswerReceiver extends BroadcastReceiver {
    private static final String TAG = "CallAnswerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG, "📬 Acción recibida: " + action);

        if ("DISMISS".equals(action)) {
            handleDismiss(context);
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
