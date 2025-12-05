// SharedPreferencesHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper simple para gestionar lista negra usando SharedPreferences
 * Inspirado en el repo spam-call-blocker-app de GitHub
 * Mucho más simple y confiable que SQLite para este caso
 */
public class SharedPreferencesHelper {
    private static final String TAG = "SharedPrefsHelper";
    private static final String PREFS_NAME = "spam_blocker_prefs";
    private static final String KEY_BLACKLIST = "blacklist_numbers";

    private Context context;
    private SharedPreferences prefs;

    public SharedPreferencesHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Normaliza un número de teléfono (misma lógica que antes)
     * - Elimina todos los caracteres no numéricos
     * - Toma los últimos 9 dígitos (formato español)
     */
    private String normalizePhoneNumber(String number) {
        if (number == null || number.isEmpty()) {
            return "";
        }

        // Eliminar todo excepto dígitos
        String cleaned = number.replaceAll("[^0-9]", "");

        // Si tiene más de 9 dígitos, tomar los últimos 9 (maneja +34, etc.)
        if (cleaned.length() > 9) {
            cleaned = cleaned.substring(cleaned.length() - 9);
        }

        Log.d(TAG, "📞 Normalizado: \"" + number + "\" → \"" + cleaned + "\"");
        return cleaned;
    }

    /**
     * Añade un número a la lista negra
     */
    public boolean addToBlacklist(String number) {
        try {
            String normalized = normalizePhoneNumber(number);

            Set<String> blacklist = getBlacklist();
            blacklist.add(normalized);

            prefs.edit().putStringSet(KEY_BLACKLIST, blacklist).apply();

            Log.d(TAG, "✅ Número añadido a blacklist: " + normalized);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error añadiendo a blacklist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un número de la lista negra
     */
    public boolean removeFromBlacklist(String number) {
        try {
            String normalized = normalizePhoneNumber(number);

            Set<String> blacklist = getBlacklist();
            blacklist.remove(normalized);

            prefs.edit().putStringSet(KEY_BLACKLIST, blacklist).apply();

            Log.d(TAG, "🗑️ Número eliminado de blacklist: " + normalized);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error eliminando de blacklist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si un número está en la lista negra
     */
    public boolean isInBlacklist(String number) {
        try {
            String normalized = normalizePhoneNumber(number);
            Set<String> blacklist = getBlacklist();

            boolean isSpam = blacklist.contains(normalized);

            Log.d(TAG, "🔍 ¿\"" + number + "\" (normalizado: \"" + normalized + "\") en blacklist? " +
                  (isSpam ? "✅ SÍ" : "❌ NO"));

            return isSpam;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error verificando blacklist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene toda la lista negra
     */
    public Set<String> getBlacklist() {
        try {
            Set<String> blacklist = prefs.getStringSet(KEY_BLACKLIST, new HashSet<>());
            // Crear copia mutable
            return new HashSet<>(blacklist);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error obteniendo blacklist: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Limpia toda la lista negra
     */
    public void clearBlacklist() {
        prefs.edit().remove(KEY_BLACKLIST).apply();
        Log.d(TAG, "🗑️ Blacklist limpiada");
    }

    /**
     * Obtiene el número de elementos en la blacklist
     */
    public int getBlacklistCount() {
        return getBlacklist().size();
    }
}
