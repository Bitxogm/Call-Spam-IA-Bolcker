package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Gestiona la activación/desactivación automática de desvíos de llamadas
 * mediante códigos USSD.
 *
 * Desvíos configurados:
 * - *21*+34919933065# → Desvío incondicional a Zadarma (Modo 2/3)
 * - ##21# → Desactivar desvío incondicional (Modo 1)
 *
 * IMPORTANTE:
 * - Requiere permiso CALL_PHONE
 * - Los códigos USSD varían por operador (estos son estándar GSM)
 * - Algunos operadores pueden bloquear USSD desde apps
 */
public class CallForwardingManager {
    private static final String TAG = "CallForwardingManager";

    // Número Zadarma para desvío
    private static final String ZADARMA_NUMBER = "+34919933065";

    // Códigos USSD para desvíos
    private static final String USSD_ENABLE_UNCONDITIONAL = "*21*" + ZADARMA_NUMBER + Uri.encode("#");
    private static final String USSD_DISABLE_UNCONDITIONAL = "##21" + Uri.encode("#");
    private static final String USSD_CHECK_STATUS = "*#21" + Uri.encode("#");

    private final Context context;
    private final Handler mainHandler;

    public CallForwardingManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Activa desvío incondicional a Zadarma (para Modo 2/3)
     *
     * Ejecuta: *21*+34919933065#
     *
     * @return true si se ejecutó el código, false si hubo error
     */
    public boolean enableForwarding() {
        Log.i(TAG, "🔄 Activando desvío incondicional a Zadarma...");
        return executeUSSD(USSD_ENABLE_UNCONDITIONAL, "Activando desvío a Zadarma");
    }

    /**
     * Desactiva desvío incondicional (para Modo 1)
     *
     * Ejecuta: ##21#
     *
     * @return true si se ejecutó el código, false si hubo error
     */
    public boolean disableForwarding() {
        Log.i(TAG, "🔄 Desactivando desvío incondicional...");
        return executeUSSD(USSD_DISABLE_UNCONDITIONAL, "Desactivando desvío");
    }

    /**
     * Consulta el estado actual del desvío
     *
     * Ejecuta: *#21#
     *
     * Nota: La respuesta del operador se mostrará en pantalla automáticamente
     *
     * @return true si se ejecutó el código, false si hubo error
     */
    public boolean checkForwardingStatus() {
        Log.i(TAG, "🔍 Consultando estado de desvío...");
        return executeUSSD(USSD_CHECK_STATUS, "Consultando estado de desvío");
    }

    /**
     * Ejecuta un código USSD
     *
     * IMPORTANTE: Desde Android 12+, algunos operadores bloquean USSD desde apps.
     * Fallback: Mostrar instrucciones al usuario para hacerlo manualmente.
     *
     * @param ussdCode Código USSD a ejecutar (ej: "*21*+34919933065#")
     * @param description Descripción para logs y toast
     * @return true si se pudo ejecutar, false si no
     */
    private boolean executeUSSD(String ussdCode, String description) {
        try {
            // Método 1: Usar TelecomManager (Android 9+, puede fallar en algunos operadores)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                TelecomManager telecomManager = context.getSystemService(TelecomManager.class);

                if (telecomManager != null) {
                    Uri uri = Uri.fromParts("tel", ussdCode, null);

                    Log.d(TAG, "📞 Ejecutando USSD vía TelecomManager: " + ussdCode);

                    // placeCall() ejecuta el USSD
                    telecomManager.placeCall(uri, null);

                    showToast(description + "...");

                    // Esperar 3 segundos para que el USSD se ejecute
                    mainHandler.postDelayed(() -> {
                        Log.d(TAG, "✅ USSD ejecutado (timeout 3s)");
                    }, 3000);

                    return true;
                }
            }

            // Método 2: Intent ACTION_CALL (fallback para Android < 9)
            Log.d(TAG, "📞 Ejecutando USSD vía Intent: " + ussdCode);

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + ussdCode));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);

            showToast(description + "...");

            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permiso CALL_PHONE no concedido: " + e.getMessage());
            showToast("⚠️ Permiso de llamadas requerido");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error ejecutando USSD: " + e.getMessage(), e);

            // Fallback: Mostrar instrucciones manuales
            showManualInstructions(ussdCode);
            return false;
        }
    }

    /**
     * Muestra instrucciones para configurar desvío manualmente
     * (fallback si USSD automático no funciona)
     */
    private void showManualInstructions(String ussdCode) {
        String message;

        if (ussdCode.contains("*21*")) {
            message = "⚠️ No se pudo activar desvío automáticamente.\n\n" +
                     "Por favor, marca manualmente:\n" +
                     "*21*+34919933065#\n\n" +
                     "en el teclado de tu teléfono";
        } else if (ussdCode.contains("##21")) {
            message = "⚠️ No se pudo desactivar desvío automáticamente.\n\n" +
                     "Por favor, marca manualmente:\n" +
                     "##21#\n\n" +
                     "en el teclado de tu teléfono";
        } else {
            message = "⚠️ No se pudo ejecutar código USSD.\n\n" +
                     "Por favor, marca manualmente:\n" +
                     ussdCode + "\n\n" +
                     "en el teclado de tu teléfono";
        }

        Log.w(TAG, message);
        showToast(message);
    }

    /**
     * Muestra un toast en el hilo principal
     */
    private void showToast(final String message) {
        mainHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Configura el desvío según el modo de Answer+Hangup
     *
     * @param mode Modo activo (HANGUP_IMMEDIATELY, BACKEND_FIXED, BACKEND_AI)
     * @return true si se ejecutó correctamente
     */
    public boolean configureForMode(AnswerHangupHelper.Mode mode) {
        Log.i(TAG, "⚙️ Configurando desvío para modo: " + mode.name());

        switch (mode) {
            case HANGUP_IMMEDIATELY:
                // Modo 1: Desactivar desvío (llamadas llegan normalmente)
                return disableForwarding();

            case BACKEND_FIXED:
            case BACKEND_AI:
                // Modo 2/3: Activar desvío a Zadarma
                return enableForwarding();

            default:
                Log.w(TAG, "⚠️ Modo desconocido: " + mode);
                return false;
        }
    }

    /**
     * Obtiene el número Zadarma configurado
     */
    public static String getZadarmaNumber() {
        return ZADARMA_NUMBER;
    }
}
