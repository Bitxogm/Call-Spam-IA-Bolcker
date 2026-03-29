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

    // Modos de Answer+Hangup
    public enum Mode {
        HANGUP_IMMEDIATELY,    // Escudo 1: Rechazo local (Hangup)
        BACKEND_FIXED,         // Escudo 2: Rechazo con desvío a Mensaje Fijo
        BACKEND_AI             // Escudo 3: Rechazo con desvío a IA Víctor
    }

    // Keys para SharedPreferences
    private static final String KEY_ENABLED = "answer_hangup_enabled";
    private static final String KEY_MODE = "answer_hangup_mode";
    private static final String KEY_LAST_SPAM_NUMBER = "last_spam_number";
    private static final String KEY_LAST_SPAM_TIME = "last_spam_time";
    private static final String KEY_HANGUP_DELAY = "hangup_delay_seconds";

    // Defaults
    private static final int DEFAULT_DELAY_SECONDS = 2;
    private static final long MAX_TIME_DIFF_MS = 10000; // 10 segundos max diferencia
    private static final Mode DEFAULT_MODE = Mode.HANGUP_IMMEDIATELY;

    private final Context context;
    private final SharedPreferences prefs;

    public AnswerHangupHelper(Context context) {
        this.context = context;
        // Usamos MODE_MULTI_PROCESS para forzar la recarga desde disco entre procesos (UI vs Service)
        // Aunque está deprecated, para SharedPreferences simples ayuda en la mayoría de dispositivos
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 4); // 4 = MODE_MULTI_PROCESS
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
     * Obtiene el modo actual de Answer+Hangup
     */
    public Mode getMode() {
        String modeString = prefs.getString(KEY_MODE, DEFAULT_MODE.name());
        Log.d(TAG, "📖 Leyendo modo de SharedPreferences: '" + modeString + "'");

        try {
            Mode mode = Mode.valueOf(modeString);
            Log.d(TAG, "✅ Modo parseado correctamente: " + mode.name());
            return mode;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "⚠️ Modo inválido en prefs ('" + modeString + "'), usando default: " + DEFAULT_MODE.name());
            return DEFAULT_MODE;
        }
    }

    /**
     * Configura el modo de Answer+Hangup
     */
    public void setMode(Mode mode) {
        Log.d(TAG, "💾 Guardando modo: " + mode.name());

        boolean success = prefs.edit().putString(KEY_MODE, mode.name()).commit(); // Usar commit() en lugar de apply()

        if (success) {
            // Verificar que se guardó correctamente
            String saved = prefs.getString(KEY_MODE, null);
            Log.d(TAG, "✅ Modo guardado exitosamente. Verificación: '" + saved + "'");

            if (!mode.name().equals(saved)) {
                Log.e(TAG, "❌ ERROR: El modo guardado ('" + saved + "') NO coincide con el modo solicitado ('" + mode.name() + "')");
            }
        } else {
            Log.e(TAG, "❌ ERROR: No se pudo guardar el modo en SharedPreferences");
        }
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
        Log.d(TAG, "🔍 ========================================");
        Log.d(TAG, "🔍 Verificando shouldHangup()");
        Log.d(TAG, "🔍 Número entrante: " + number);
        Log.d(TAG, "🔍 Answer+Hangup enabled: " + isEnabled());

        if (!isEnabled()) {
            Log.d(TAG, "❌ Answer+Hangup desactivado");
            Log.d(TAG, "🔍 ========================================");
            return false;
        }

        String markedNumber = prefs.getString(KEY_LAST_SPAM_NUMBER, null);
        long markedTime = prefs.getLong(KEY_LAST_SPAM_TIME, 0);

        Log.d(TAG, "🔍 Número marcado: " + markedNumber);
        Log.d(TAG, "🔍 Tiempo marcado: " + markedTime);

        if (markedNumber == null || markedNumber.isEmpty()) {
            Log.d(TAG, "❌ No hay número marcado para colgar");
            Log.d(TAG, "🔍 ========================================");
            return false;
        }

        // Verificar que el tiempo sea reciente (últimos 10 segundos)
        long now = System.currentTimeMillis();
        long timeDiff = now - markedTime;

        Log.d(TAG, "🔍 Tiempo actual: " + now);
        Log.d(TAG, "🔍 Diferencia tiempo: " + timeDiff + "ms (max: " + MAX_TIME_DIFF_MS + "ms)");

        if (timeDiff > MAX_TIME_DIFF_MS) {
            Log.e(TAG, "❌ TIMEOUT! Tiempo excedido (" + timeDiff + "ms > " + MAX_TIME_DIFF_MS + "ms)");
            Log.d(TAG, "🔍 ========================================");
            clearMarked(); // Limpiar para evitar colgar llamadas equivocadas
            return false;
        }

        // Normalizar números para comparar (últimos 9 dígitos)
        String normalizedIncoming = normalizeNumber(number);
        String normalizedMarked = normalizeNumber(markedNumber);

        Log.d(TAG, "🔍 Normalizado entrante: " + normalizedIncoming);
        Log.d(TAG, "🔍 Normalizado marcado: " + normalizedMarked);

        boolean match = normalizedIncoming.equals(normalizedMarked);

        if (match) {
            Log.i(TAG, "✅ ¡MATCH! Número coincide, debe colgarse");
            Log.d(TAG, "🔍 ========================================");
            // NO limpiar aquí - se limpiará después del hangup exitoso
        } else {
            Log.e(TAG, "❌ NO MATCH! Números NO coinciden");
            Log.d(TAG, "🔍 ========================================");
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
