// CallHistoryHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Helper para almacenar historial de llamadas spam detectadas
 * Mantiene los últimos 200 registros
 */
public class CallHistoryHelper {
    private static final String TAG = "CallHistoryHelper";
    private static final String PREFS_NAME = "CallHistory";
    private static final String KEY_HISTORY = "spam_calls";
    private static final int MAX_RECORDS = 200;

    private SharedPreferences prefs;
    private SimpleDateFormat dateFormat;

    public CallHistoryHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    /**
     * Añade una llamada spam al historial (versión mejorada con score y categoría)
     */
    public void addSpamCall(String number, String reason, String action, int spamScore, String category) {
        try {
            JSONArray history = getHistory();

            // Crear nuevo registro
            JSONObject record = new JSONObject();
            record.put("timestamp", dateFormat.format(new Date()));
            record.put("number", number);
            record.put("reason", reason);  // "Blacklist", "Modo Radical", "Premium", etc.
            record.put("action", action);  // "Answer+Hangup", "Notification", "Blocked"
            record.put("spamScore", spamScore);  // 0-100
            record.put("category", category);  // "TELEMARKETING_LEGAL", "PREMIUM", etc.

            // Añadir al principio (más reciente primero)
            JSONArray newHistory = new JSONArray();
            newHistory.put(record);

            for (int i = 0; i < history.length() && i < MAX_RECORDS - 1; i++) {
                newHistory.put(history.get(i));
            }

            // Guardar
            prefs.edit().putString(KEY_HISTORY, newHistory.toString()).apply();

            Log.d(TAG, "Spam call recorded: " + number + " (" + reason + ", score:" + spamScore + ", cat:" + category + ")");

        } catch (JSONException e) {
            Log.e(TAG, "Error añadiendo llamada al historial: " + e.getMessage());
        }
    }

    /**
     * Añade una llamada spam al historial (versión legacy sin score)
     * @deprecated Usar addSpamCall con score y categoría
     */
    @Deprecated
    public void addSpamCall(String number, String reason, String action) {
        addSpamCall(number, reason, action, 0, "UNKNOWN");
    }

    /**
     * Obtiene el historial como JSONArray
     */
    private JSONArray getHistory() {
        String historyJson = prefs.getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(historyJson);
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando historial: " + e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * Obtiene el historial como lista de objetos
     */
    public List<SpamCallRecord> getHistoryList() {
        List<SpamCallRecord> recordsList = new ArrayList<>();

        try {
            JSONArray history = getHistory();

            for (int i = 0; i < history.length(); i++) {
                JSONObject record = history.getJSONObject(i);
                SpamCallRecord entry = new SpamCallRecord(
                    record.getString("timestamp"),
                    record.getString("number"),
                    record.getString("reason"),
                    record.getString("action"),
                    record.optInt("spamScore", 0),  // Default 0 si no existe
                    record.optString("category", "UNKNOWN")  // Default UNKNOWN
                );
                recordsList.add(entry);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error obteniendo lista de historial: " + e.getMessage());
        }

        return recordsList;
    }

    /**
     * Obtiene el historial como String JSON
     */
    public String getHistoryAsJson() {
        return getHistory().toString();
    }

    /**
     * Cuenta total de llamadas spam
     */
    public int getTotalSpamCalls() {
        return getHistory().length();
    }

    /**
     * Limpia todo el historial
     */
    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
        Log.i(TAG, "Call history cleared");
    }

    /**
     * Clase para representar un registro de llamada spam
     */
    public static class SpamCallRecord {
        public String timestamp;
        public String number;
        public String reason;
        public String action;
        public int spamScore;      // 0-100
        public String category;    // TELEMARKETING_LEGAL, PREMIUM, etc.

        public SpamCallRecord(String timestamp, String number, String reason, String action, int spamScore, String category) {
            this.timestamp = timestamp;
            this.number = number;
            this.reason = reason;
            this.action = action;
            this.spamScore = spamScore;
            this.category = category;
        }

        // Constructor legacy para compatibilidad
        @Deprecated
        public SpamCallRecord(String timestamp, String number, String reason, String action) {
            this(timestamp, number, reason, action, 0, "UNKNOWN");
        }
    }
}
