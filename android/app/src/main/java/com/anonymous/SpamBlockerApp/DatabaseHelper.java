// DatabaseHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Helper para acceder a la base de datos de spam desde Java/Android nativo
 * Replica la lógica de normalización de TypeScript (DataBaseService.ts)
 */
public class DatabaseHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DB_NAME = "spamBlocker.db";  // ✅ Debe coincidir con TypeScript
    private Context context;

    public DatabaseHelper(Context context) {
        this.context = context;
        // Asegurar que la base de datos existe al inicializar
        ensureDatabaseExists();
    }

    /**
     * Asegura que la base de datos existe y tiene las tablas necesarias
     * Crea la BD si no existe (mismo esquema que TypeScript)
     */
    private void ensureDatabaseExists() {
        try {
            String dbPath = context.getDatabasePath(DB_NAME).getAbsolutePath();

            // Si no existe, crearla
            if (!context.getDatabasePath(DB_NAME).exists()) {
                Log.d(TAG, "🔨 Base de datos no existe, creando...");
                showToast("🔨 Creando BD...");

                // Crear directorio si no existe
                context.getDatabasePath(DB_NAME).getParentFile().mkdirs();

                // Crear base de datos
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbPath, null);

                // Crear tablas (mismo esquema que TypeScript)
                createTables(db);

                db.close();

                Log.d(TAG, "✅ Base de datos creada exitosamente");
                showToast("✅ BD creada!");
            } else {
                Log.d(TAG, "✅ Base de datos ya existe");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error asegurando base de datos: " + e.getMessage(), e);
            showToast("❌ Error creando BD:\n" + e.getMessage());
        }
    }

    /**
     * Crea las tablas de la base de datos (mismo esquema que TypeScript)
     */
    private void createTables(SQLiteDatabase db) {
        // Tabla: spam_numbers (números en lista negra)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS spam_numbers (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  number TEXT UNIQUE NOT NULL," +
            "  reason TEXT," +
            "  source TEXT NOT NULL," +
            "  date_added TEXT NOT NULL," +
            "  is_active INTEGER DEFAULT 1" +
            ");"
        );

        // Tabla: call_history (historial de llamadas)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS call_history (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  phone_number TEXT NOT NULL," +
            "  call_date TEXT NOT NULL," +
            "  was_blocked INTEGER NOT NULL," +
            "  block_reason TEXT," +
            "  ai_conversation_id TEXT" +
            ");"
        );

        // Tabla: app_settings (configuración)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS app_settings (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  setting_key TEXT UNIQUE NOT NULL," +
            "  setting_value TEXT NOT NULL" +
            ");"
        );

        Log.d(TAG, "✅ Tablas creadas: spam_numbers, call_history, app_settings");
    }

    /**
     * Muestra un Toast en el hilo principal (para debugging visual)
     */
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
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
            Log.d(TAG, "🗄️ Ruta de BD: " + dbPath);

            // TOAST: Mostrar ruta de BD
            showToast("🗄️ Buscando BD en:\n" + dbPath);

            if (!context.getDatabasePath(DB_NAME).exists()) {
                Log.e(TAG, "❌ Base de datos NO EXISTE en: " + dbPath);
                // TOAST: BD no existe
                showToast("❌ BD NO EXISTE\n" + dbPath);
                return false;
            }

            Log.d(TAG, "✅ Base de datos encontrada, abriendo...");
            // TOAST: BD encontrada
            showToast("✅ BD encontrada!");

            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
            showToast("🔓 BD abierta OK");

            // TOAST: Mostrar qué número busca
            showToast("🔎 Buscando: " + normalizedNumber);

            // Query con número normalizado
            String query = "SELECT COUNT(*) FROM spam_numbers WHERE number = ? AND is_active = 1";
            cursor = db.rawQuery(query, new String[]{normalizedNumber});
            showToast("📝 Query ejecutada");

            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                boolean isSpam = count > 0;

                Log.d(TAG, "🔍 ¿\"" + number + "\" (normalizado: \"" + normalizedNumber + "\") es spam? " + (isSpam ? "✅ SÍ" : "❌ NO"));

                // TOAST: Mostrar count encontrado
                showToast("📊 Count=" + count + " isSpam=" + isSpam);

                // TOAST: Resultado de la consulta
                if (isSpam) {
                    showToast("🚫 SPAM DETECTADO!\n" + normalizedNumber + "\n(en lista negra)");
                } else {
                    showToast("✅ Número normal\n" + normalizedNumber + "\n(no en lista)");
                }

                return isSpam;
            } else {
                showToast("⚠️ Cursor vacío (no moveToFirst)");
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error verificando número spam: " + e.getMessage(), e);
            // TOAST: Error
            showToast("❌ ERROR BD:\n" + e.getMessage());
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
