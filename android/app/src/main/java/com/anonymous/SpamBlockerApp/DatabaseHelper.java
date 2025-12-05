// DatabaseHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Helper para acceder a la base de datos de spam desde Java/Android nativo
 * Replica la lógica de normalización de TypeScript (DataBaseService.ts)
 */
public class DatabaseHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DB_NAME = "spam_blocker.db";
    private Context context;

    public DatabaseHelper(Context context) {
        this.context = context;
    }

    /**
     * Normaliza un número de teléfono (misma lógica que TypeScript)
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
     * Verifica si un número está en la lista negra
     * @param number Número de teléfono (cualquier formato)
     * @return true si está en la blacklist y activo
     */
    public boolean isSpamNumber(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }

        // Normalizar antes de buscar
        String normalizedNumber = normalizePhoneNumber(number);

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            // Abrir base de datos (readonly)
            String dbPath = context.getDatabasePath(DB_NAME).getAbsolutePath();
            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);

            // Query con número normalizado
            String query = "SELECT COUNT(*) FROM spam_numbers WHERE number = ? AND is_active = 1";
            cursor = db.rawQuery(query, new String[]{normalizedNumber});

            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                boolean isSpam = count > 0;

                Log.d(TAG, "🔍 ¿\"" + number + "\" (normalizado: \"" + normalizedNumber + "\") es spam? " + (isSpam ? "✅ SÍ" : "❌ NO"));
                return isSpam;
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error verificando número spam: " + e.getMessage(), e);
            return false;
        } finally {
            // Cerrar recursos
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }

    /**
     * Verifica si la base de datos existe
     * @return true si existe
     */
    public boolean databaseExists() {
        try {
            String dbPath = context.getDatabasePath(DB_NAME).getAbsolutePath();
            return context.getDatabasePath(DB_NAME).exists();
        } catch (Exception e) {
            Log.e(TAG, "Error verificando base de datos: " + e.getMessage());
            return false;
        }
    }
}
