package com.anonymous.SpamBlockerApp;

import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/**
 * Servicio para gestionar llamadas entrantes y contestar automáticamente a spam
 *
 * Este servicio:
 * - Detecta llamadas entrantes
 * - Identifica posibles spam
 * - Contesta automáticamente (si está habilitado)
 * - Se prepara para integración con IA para conversaciones
 */
public class SpamCallService extends InCallService {
    private static final String TAG = "SpamCallService";

    // Números en whitelist (nunca se bloquean)
    private static final Set<String> EMERGENCY_NUMBERS = new HashSet<>();
    static {
        EMERGENCY_NUMBERS.add("112");
        EMERGENCY_NUMBERS.add("911");
        EMERGENCY_NUMBERS.add("091"); // Policía España
        EMERGENCY_NUMBERS.add("092"); // Policía Local España
        EMERGENCY_NUMBERS.add("080"); // Bomberos España
        EMERGENCY_NUMBERS.add("061"); // Emergencias Sanitarias España
    }

    // Estado del servicio
    private boolean autoAnswerEnabled = true; // Por defecto activado para testing
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "🚀 SpamCallService iniciado - Auto-respuesta: " + (autoAnswerEnabled ? "ACTIVADA" : "DESACTIVADA"));
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        String callerNumber = getCallerNumber(call);
        int callState = call.getDetails().getState();

        Log.d(TAG, "📞 ========================================");
        Log.d(TAG, "📞 LLAMADA DETECTADA");
        Log.d(TAG, "📞 Número: " + callerNumber);
        Log.d(TAG, "📞 Estado: " + getCallStateName(callState));
        Log.d(TAG, "📞 ========================================");

        // Verificar si es número de emergencia
        if (isEmergencyNumber(callerNumber)) {
            Log.d(TAG, "🚨 NÚMERO DE EMERGENCIA - No se intercepta");
            return;
        }

        // Registrar callback para cambios de estado
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                Log.d(TAG, "📞 Estado cambió a: " + getCallStateName(state));

                // Si la llamada fue contestada, preparar para IA
                if (state == Call.STATE_ACTIVE) {
                    Log.d(TAG, "✅ Llamada ACTIVA - Lista para conversación IA");
                    onCallActive(call, callerNumber);
                } else if (state == Call.STATE_DISCONNECTED) {
                    Log.d(TAG, "💀 Llamada DESCONECTADA");
                }
            }
        });

        // Evaluar si debe auto-contestar
        if (autoAnswerEnabled && shouldAnswerWithAI(callerNumber)) {
            Log.d(TAG, "🤖 DECISIÓN: Auto-contestar con IA");
            autoAnswerCall(call, callerNumber);
        } else if (shouldBlock(callerNumber)) {
            Log.d(TAG, "🚫 DECISIÓN: Bloquear llamada");
            blockCall(call, callerNumber);
        } else {
            Log.d(TAG, "✅ DECISIÓN: Permitir llamada normal");
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        String callerNumber = getCallerNumber(call);
        Log.d(TAG, "📞 Llamada finalizada: " + callerNumber);
    }

    /**
     * Auto-contesta la llamada y prepara para conversación con IA
     */
    private void autoAnswerCall(Call call, String callerNumber) {
        // Esperar 2 segundos antes de contestar (más realista)
        mainHandler.postDelayed(() -> {
            try {
                Log.d(TAG, "🤖 Contestando llamada automáticamente...");
                call.answer(VideoProfile.STATE_AUDIO_ONLY);
                Log.d(TAG, "✅ Llamada contestada - Esperando activación para IA");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error al contestar: " + e.getMessage());
            }
        }, 2000); // 2 segundos de delay
    }

    /**
     * Bloquea/rechaza la llamada
     */
    private void blockCall(Call call, String callerNumber) {
        try {
            Log.d(TAG, "🚫 Bloqueando llamada de: " + callerNumber);
            call.reject(false, "Spam detectado");
            call.disconnect();
            Log.d(TAG, "✅ Llamada bloqueada exitosamente");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error al bloquear: " + e.getMessage());
        }
    }

    /**
     * Se llama cuando la llamada está activa y lista para conversación
     */
    private void onCallActive(Call call, String callerNumber) {
        Log.d(TAG, "🎙️ ========================================");
        Log.d(TAG, "🎙️ LLAMADA ACTIVA - LISTA PARA IA");
        Log.d(TAG, "🎙️ Número: " + callerNumber);
        Log.d(TAG, "🎙️ ========================================");

        // TODO: Aquí se integrará con:
        // 1. React Native para notificar
        // 2. GeminiService para iniciar conversación
        // 3. Sistema TTS/STT para audio

        // Por ahora, simular saludo inicial después de 1 segundo
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "🤖 [SIMULACIÓN] Agente IA: '¿Dígame? ¿Con quién hablo?'");
            // TODO: Aquí iría la síntesis de voz real
        }, 1000);
    }

    /**
     * Determina si debe contestar automáticamente con IA
     */
    private boolean shouldAnswerWithAI(String number) {
        // Por ahora, contestar a todos los números que no sean emergencia
        // En producción, esto se integrará con:
        // 1. Lista de contactos del usuario (NO contestar a contactos)
        // 2. Base de datos de spam conocido (SÍ contestar)
        // 3. Configuración del usuario (modo radical, etc.)

        // Para testing: contestar solo a números desconocidos/ocultos
        boolean isUnknown = number.equals("Número desconocido") ||
                           number.equals("Privado") ||
                           number.equals("Desconocido");

        Log.d(TAG, "🤔 ¿Contestar con IA? " + (isUnknown ? "SÍ" : "NO") +
                   " (Número: " + number + ")");

        return isUnknown;
    }

    /**
     * Determina si debe bloquear directamente (sin contestar)
     */
    private boolean shouldBlock(String number) {
        // Por ahora no bloqueamos nada directamente
        // En producción, bloquear:
        // 1. Números en blacklist del usuario
        // 2. Spam reportado por múltiples usuarios
        // 3. Patrones de números spam (prefijos conocidos)

        return false;
    }

    /**
     * Verifica si es número de emergencia
     */
    private boolean isEmergencyNumber(String number) {
        if (number == null) return false;

        // Limpiar el número (quitar espacios, guiones, etc.)
        String cleanNumber = number.replaceAll("[\\s\\-\\(\\)]", "");

        return EMERGENCY_NUMBERS.contains(cleanNumber);
    }

    /**
     * Obtiene el número del caller
     */
    private String getCallerNumber(Call call) {
        if (call.getDetails() != null && call.getDetails().getHandle() != null) {
            return call.getDetails().getHandle().getSchemeSpecificPart();
        }
        return "Número desconocido";
    }

    /**
     * Convierte el estado de llamada a nombre legible
     */
    private String getCallStateName(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NUEVA";
            case Call.STATE_DIALING: return "MARCANDO";
            case Call.STATE_RINGING: return "SONANDO";
            case Call.STATE_ACTIVE: return "ACTIVA";
            case Call.STATE_HOLDING: return "EN ESPERA";
            case Call.STATE_DISCONNECTED: return "DESCONECTADA";
            default: return "DESCONOCIDO (" + state + ")";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        Log.d(TAG, "💀 SpamCallService destruido");
    }
}
