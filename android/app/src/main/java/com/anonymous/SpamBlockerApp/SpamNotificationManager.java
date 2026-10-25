// SpamNotificationManager.java
package com.anonymous.SpamBlockerApp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gestiona las notificaciones de spam y acciones de respuesta con IA
 */
public class SpamNotificationManager {
    private static final String TAG = "SpamNotificationMgr";

    private static final String CHANNEL_ID = "spam_calls_channel";
    private static final String CHANNEL_NAME = "Llamadas Spam";
    private static final int NOTIFICATION_ID = 1001;

    // Modos 2 y 3: un grupo con como mucho BackendCallNotice.MAX_VISIBLES notificaciones
    // (ids 2100-2104, rotando) y un resumen (2099)
    private static final String GROUP_BACKEND = "spam_backend_calls";
    private static final int BACKEND_SUMMARY_ID = 2099;
    private static final int BACKEND_FIRST_ID = 2100;
    private static final String PREFS_BACKEND = "spam_notifications";
    private static final Uri URI_HISTORIAL = Uri.parse("spamblocker://historial");

    // Acción para contestar con IA

    /**
     * Muestra notificación de spam detectado con botón para contestar con IA
     */
    public static void showIncomingSpamNotification(Context context, String phoneNumber, String modoUsado) {
        Log.d(TAG, "📲 Mostrando notificación de spam: " + phoneNumber + " (" + modoUsado + ")");

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

        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

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
            .setContentText(phoneNumber + " · " + modoUsado)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Llamada de: " + phoneNumber + "\nModo: " + modoUsado + "\nHora: " + timestamp))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setColor(Color.rgb(255, 102, 0))  // Naranja
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
     * Modos 2 y 3: la llamada se ha desviado al servidor. Al pulsar, abre el Historial.
     */
    public static void showBackendCallNotification(Context context, String phoneNumber, boolean modoIA) {
        // Cubre el permiso de Android 13+ y las notificaciones desactivadas a mano
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "🔕 Notificaciones desactivadas o sin permiso: no se avisa de " + phoneNumber);
            return;
        }
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager no disponible");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_BACKEND, Context.MODE_PRIVATE);
        List<String> anteriores = leerRecientes(prefs);
        int contador = prefs.getInt("contador", 0);
        int total = prefs.getInt("total", 0);
        // Si ya no queda ninguna a la vista (descartadas o abiertas), se empieza de cero
        if (!hayNotificacionesBackend(notificationManager)) {
            anteriores = new ArrayList<>();
            total = 0;
        }

        String titulo = BackendCallNotice.titulo(phoneNumber, modoIA);
        String hora = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        List<String> recientes = BackendCallNotice.recientes(
            anteriores, BackendCallNotice.linea(hora, phoneNumber, modoIA));
        total += 1;
        prefs.edit()
            .putInt("contador", contador + 1)
            .putInt("total", total)
            .putString("recientes", new JSONArray(recientes).toString())
            .apply();

        PendingIntent abrirHistorial = abrirHistorial(context);

        NotificationCompat.Builder individual = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(titulo)
            .setContentText(modoIA ? "Atendida en el servidor" : "Mensaje fijo en el servidor")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setColor(Color.rgb(255, 102, 0))
            .setContentIntent(abrirHistorial)
            .setAutoCancel(true)
            .setGroup(GROUP_BACKEND);

        NotificationCompat.InboxStyle lineas = new NotificationCompat.InboxStyle()
            .setSummaryText("Toca para ver el historial");
        for (String linea : recientes) {
            lineas.addLine(linea);
        }
        NotificationCompat.Builder resumen = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(BackendCallNotice.tituloResumen(total))
            .setContentText(recientes.get(0))
            .setStyle(lineas)
            .setColor(Color.rgb(255, 102, 0))
            .setContentIntent(abrirHistorial)
            .setAutoCancel(true)
            .setGroup(GROUP_BACKEND)
            .setGroupSummary(true)
            // Que suene la individual, no también el resumen
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        try {
            notificationManager.notify(BACKEND_FIRST_ID + BackendCallNotice.hueco(contador), individual.build());
            notificationManager.notify(BACKEND_SUMMARY_ID, resumen.build());
            Log.d(TAG, "✅ Notificación de llamada desviada: " + titulo);
        } catch (SecurityException e) {
            Log.w(TAG, "🔕 Sin permiso para notificar: " + e.getMessage());
        }
    }

    private static PendingIntent abrirHistorial(Context context) {
        // Intent explícito: no hace falta declarar el esquema en el manifest ni
        // exponerlo a otras apps. React Native lo recibe por Linking (ACTION_VIEW + URI)
        Intent intent = new Intent(Intent.ACTION_VIEW, URI_HISTORIAL, context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
            context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static List<String> leerRecientes(SharedPreferences prefs) {
        List<String> lista = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(prefs.getString("recientes", "[]"));
            for (int i = 0; i < json.length(); i++) {
                lista.add(json.getString(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "recientes corruptos, se empieza de cero: " + e.getMessage());
        }
        return lista;
    }

    private static boolean hayNotificacionesBackend(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        for (StatusBarNotification n : notificationManager.getActiveNotifications()) {
            if (n.getId() >= BACKEND_SUMMARY_ID && n.getId() < BACKEND_FIRST_ID + BackendCallNotice.MAX_VISIBLES) {
                return true;
            }
        }
        return false;
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
