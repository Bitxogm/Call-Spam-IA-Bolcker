// LogsHelper.java
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
 * Helper para almacenar logs in-app para debugging
 * Mantiene los últimos 100 logs con timestamp
 */
public class LogsHelper {
    private static final String TAG = "LogsHelper";
    private static final String PREFS_NAME = "AppLogs";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 100;

    private SharedPreferences prefs;
    private SimpleDateFormat dateFormat;

    public LogsHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    /**
     * Añade un log de tipo INFO
     */
    public void logInfo(String message) {
        addLog("INFO", message);
    }

    /**
     * Añade un log de tipo ERROR
     */
    public void logError(String message) {
        addLog("ERROR", message);
    }

    /**
     * Añade un log de tipo DEBUG
     */
    public void logDebug(String message) {
        addLog("DEBUG", message);
    }

    /**
     * Añade un log de tipo WARNING
     */
    public void logWarning(String message) {
        addLog("WARNING", message);
    }

    /**
     * Añade un log al sistema
     */
    private void addLog(String level, String message) {
        try {
            JSONArray logs = getLogs();

            // Crear nuevo log entry
            JSONObject logEntry = new JSONObject();
            logEntry.put("timestamp", dateFormat.format(new Date()));
            logEntry.put("level", level);
            logEntry.put("message", message);

            // Añadir al principio (más reciente primero)
            logs.put(0, logEntry);

            // Limitar a MAX_LOGS
            if (logs.length() > MAX_LOGS) {
                JSONArray trimmedLogs = new JSONArray();
                for (int i = 0; i < MAX_LOGS; i++) {
                    trimmedLogs.put(logs.get(i));
                }
                logs = trimmedLogs;
            }

            // Guardar
            prefs.edit().putString(KEY_LOGS, logs.toString()).apply();

            // También log en Android Logcat
            switch (level) {
                case "ERROR":
                    Log.e(TAG, message);
                    break;
                case "WARNING":
                    Log.w(TAG, message);
                    break;
                case "DEBUG":
                    Log.d(TAG, message);
                    break;
                default:
                    Log.i(TAG, message);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error añadiendo log: " + e.getMessage());
        }
    }

    /**
     * Obtiene todos los logs como JSONArray
     */
    private JSONArray getLogs() {
        String logsJson = prefs.getString(KEY_LOGS, "[]");
        try {
            return new JSONArray(logsJson);
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando logs: " + e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * Obtiene todos los logs como String formateado
     */
    public String getLogsAsString() {
        try {
            JSONArray logs = getLogs();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.getJSONObject(i);
                String timestamp = log.getString("timestamp");
                String level = log.getString("level");
                String message = log.getString("message");

                sb.append("[").append(timestamp).append("] ");
                sb.append("[").append(level).append("] ");
                sb.append(message).append("\n");
            }

            return sb.toString();

        } catch (JSONException e) {
            Log.e(TAG, "Error formateando logs: " + e.getMessage());
            return "Error leyendo logs: " + e.getMessage();
        }
    }

    /**
     * Obtiene todos los logs como lista de objetos
     */
    public List<LogEntry> getLogsList() {
        List<LogEntry> logsList = new ArrayList<>();

        try {
            JSONArray logs = getLogs();

            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.getJSONObject(i);
                LogEntry entry = new LogEntry(
                    log.getString("timestamp"),
                    log.getString("level"),
                    log.getString("message")
                );
                logsList.add(entry);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error obteniendo lista de logs: " + e.getMessage());
        }

        return logsList;
    }

    /**
     * Limpia todos los logs
     */
    public void clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply();
        Log.i(TAG, "Logs cleared");
    }

    /**
     * Clase para representar una entrada de log
     */
    public static class LogEntry {
        public String timestamp;
        public String level;
        public String message;

        public LogEntry(String timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }
}
