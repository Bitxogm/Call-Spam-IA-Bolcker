// CallInterceptorService.java - Versión simplificada
package com.anonymous.SpamBlockerApp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class CallInterceptorService extends Service {
    private static final String TAG = "CallInterceptor";
    private static final String CHANNEL_ID = "call_interceptor_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 CallInterceptorService creado");
        
        createNotificationChannel();
        startForegroundService();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Call Interceptor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servicio para interceptar llamadas spam");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundService() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ SpamBlocker Activo")
            .setContentText("Listo para interceptar llamadas spam...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚀 Servicio iniciado");
        return START_STICKY; // Reiniciar si se mata
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "🔗 Servicio vinculado");
        return null; // Servicio no vinculado
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💀 CallInterceptorService destruido");
    }
}