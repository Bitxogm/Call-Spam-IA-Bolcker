// SpamNotificationManager.java
package com.anonymous.SpamBlockerApp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * Gestiona las notificaciones de spam y acciones de respuesta con IA
 */
public class SpamNotificationManager {
    private static final String TAG = "SpamNotificationMgr";

    private static final String CHANNEL_ID = "spam_calls_channel";
    private static final String CHANNEL_NAME = "Llamadas Spam";
    private static final int NOTIFICATION_ID = 1001;

    // Acción para contestar con IA
    public static final String ACTION_ANSWER_WITH_AI = "com.anonymous.SpamBlockerApp.ANSWER_WITH_AI";
    public static final String EXTRA_PHONE_NUMBER = "phone_number";

    /**
     * Muestra notificación de spam detectado con botón para contestar con IA
     */
    public static void showIncomingSpamNotification(Context context, String phoneNumber) {
        Log.d(TAG, "📲 Mostrando notificación de spam: " + phoneNumber);

        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager no disponible");
            return;
        }

        // Crear canal de notificación (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager);
        }

        // Intent para contestar con IA
        Intent answerIntent = new Intent(context, CallAnswerReceiver.class);
        answerIntent.setAction(ACTION_ANSWER_WITH_AI);
        answerIntent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);

        PendingIntent answerPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent para ignorar
        Intent dismissIntent = new Intent(context, CallAnswerReceiver.class);
        dismissIntent.setAction("DISMISS");

        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Construir notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)  // TODO: Usar icono personalizado
            .setContentTitle("🤖 Posible Spam Detectado")
            .setContentText(phoneNumber)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Llamada de: " + phoneNumber + "\n\n¿Dejar que tu agente IA conteste?"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setColor(Color.rgb(255, 102, 0))  // Naranja
            .addAction(
                android.R.drawable.ic_menu_call,
                "🤖 Dejar que IA Conteste",
                answerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ignorar",
                dismissPendingIntent
            );

        // Mostrar notificación
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        Log.d(TAG, "✅ Notificación mostrada");
    }

    /**
     * Cancela la notificación
     */
    public static void cancelNotification(Context context) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "❌ Notificación cancelada");
        }
    }

    /**
     * Crea el canal de notificación (Android 8+)
     */
    private static void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Notificaciones de llamadas spam detectadas");
            channel.enableLights(true);
            channel.setLightColor(Color.rgb(255, 102, 0));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});

            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "📢 Canal de notificación creado");
        }
    }
}
