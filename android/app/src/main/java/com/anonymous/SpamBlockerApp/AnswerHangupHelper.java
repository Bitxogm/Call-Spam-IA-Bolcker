// AnswerHangupHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Helper para gestionar Answer+Hangup de llamadas spam.
 *
 * Flujo:
 * 1. CallScreeningService marca número como spam y guarda info
 * 2. CallStateReceiver detecta cuando llamada es contestada
 * 3. CallStateReceiver espera delay y cuelga automáticamente
 */
public class AnswerHangupHelper {
    private static final String TAG = "AnswerHangupHelper";
    private static final String PREFS_NAME = "answer_hangup_prefs";

    // Keys para SharedPreferences
    private static final String KEY_ENABLED = "answer_hangup_enabled";
    private static final String KEY_LAST_SPAM_NUMBER = "last_spam_number";
    private static final String KEY_LAST_SPAM_TIME = "last_spam_time";
    private static final String KEY_HANGUP_DELAY = "hangup_delay_seconds";

    // Defaults
    private static final int DEFAULT_DELAY_SECONDS = 2;
    private static final long MAX_TIME_DIFF_MS = 10000; // 10 segundos max diferencia

    private final Context context;
    private final SharedPreferences prefs;

    public AnswerHangupHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Verifica si Answer+Hangup está habilitado
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    /**
     * Activa o desactiva Answer+Hangup
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.d(TAG, "Answer+Hangup " + (enabled ? "ACTIVADO" : "DESACTIVADO"));
    }

    /**
     * Obtiene el delay en segundos antes de colgar
     */
    public int getHangupDelay() {
        return prefs.getInt(KEY_HANGUP_DELAY, DEFAULT_DELAY_SECONDS);
    }

    /**
     * Configura el delay antes de colgar (1-5 segundos)
     */
    public void setHangupDelay(int seconds) {
        if (seconds < 1) seconds = 1;
        if (seconds > 5) seconds = 5;

        prefs.edit().putInt(KEY_HANGUP_DELAY, seconds).apply();
        Log.d(TAG, "Hangup delay configurado: " + seconds + " segundos");
    }

    /**
     * Marca un número como spam para answer+hangup
     * Llamado por CallScreeningService cuando detecta spam
     */
    public void markForAnswerHangup(String number) {
        long now = System.currentTimeMillis();

        prefs.edit()
            .putString(KEY_LAST_SPAM_NUMBER, number)
            .putLong(KEY_LAST_SPAM_TIME, now)
            .apply();

        Log.d(TAG, "📞 Marcado para Answer+Hangup: " + number);
    }

    /**
     * Verifica si un número debe ser colgado
     * Llamado por CallStateReceiver cuando llamada es contestada
     *
     * @param number Número que está llamando
     * @return true si debe colgarse, false si no
     */
    public boolean shouldHangup(String number) {
        if (!isEnabled()) {
            Log.d(TAG, "Answer+Hangup desactivado");
            return false;
        }

        String markedNumber = prefs.getString(KEY_LAST_SPAM_NUMBER, null);
        long markedTime = prefs.getLong(KEY_LAST_SPAM_TIME, 0);

        if (markedNumber == null || markedNumber.isEmpty()) {
            Log.d(TAG, "No hay número marcado para colgar");
            return false;
        }

        // Verificar que el tiempo sea reciente (últimos 10 segundos)
        long now = System.currentTimeMillis();
        long timeDiff = now - markedTime;

        if (timeDiff > MAX_TIME_DIFF_MS) {
            Log.d(TAG, "Tiempo excedido (" + timeDiff + "ms), no cuelgo");
            clearMarked(); // Limpiar para evitar colgar llamadas equivocadas
            return false;
        }

        // Normalizar números para comparar (últimos 9 dígitos)
        String normalizedIncoming = normalizeNumber(number);
        String normalizedMarked = normalizeNumber(markedNumber);

        boolean match = normalizedIncoming.equals(normalizedMarked);

        if (match) {
            Log.d(TAG, "✅ Número coincide, debe colgarse: " + number);
            clearMarked(); // Limpiar para no colgar múltiples veces
        } else {
            Log.d(TAG, "❌ Número NO coincide (" + normalizedIncoming + " vs " + normalizedMarked + ")");
        }

        return match;
    }

    /**
     * Limpia el número marcado (después de colgar o timeout)
     */
    public void clearMarked() {
        prefs.edit()
            .remove(KEY_LAST_SPAM_NUMBER)
            .remove(KEY_LAST_SPAM_TIME)
            .apply();

        Log.d(TAG, "Número marcado limpiado");
    }

    /**
     * Normaliza un número de teléfono (últimos 9 dígitos)
     */
    private String normalizeNumber(String number) {
        if (number == null) return "";

        // Limpiar: solo dígitos
        String cleaned = number.replaceAll("[^0-9]", "");

        // Últimos 9 dígitos (formato España)
        if (cleaned.length() > 9) {
            cleaned = cleaned.substring(cleaned.length() - 9);
        }

        return cleaned;
    }

    /**
     * Obtiene info de debug
     */
    public String getDebugInfo() {
        return "Answer+Hangup: " + (isEnabled() ? "ENABLED" : "DISABLED") + "\n" +
               "Delay: " + getHangupDelay() + "s\n" +
               "Last marked: " + prefs.getString(KEY_LAST_SPAM_NUMBER, "none") + "\n" +
               "Last time: " + prefs.getLong(KEY_LAST_SPAM_TIME, 0);
    }
}
