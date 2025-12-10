package com.anonymous.SpamBlockerApp;

import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

/**
 * Servicio para gestionar llamadas entrantes y contestar automáticamente a spam
 *
 * Este servicio:
 * - Detecta llamadas entrantes
 * - Identifica posibles spam
 * - Aplica Answer+Hangup según el modo configurado
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

    // Helpers
    private Handler mainHandler;
    private AnswerHangupHelper answerHangupHelper;
    private LogsHelper logsHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        answerHangupHelper = new AnswerHangupHelper(this);
        logsHelper = new LogsHelper(this);

        boolean answerHangupEnabled = answerHangupHelper.isEnabled();
        AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();

        Log.d(TAG, "🚀 SpamCallService iniciado");
        Log.d(TAG, "📱 Answer+Hangup: " + (answerHangupEnabled ? "ACTIVADO" : "DESACTIVADO"));
        Log.d(TAG, "🎯 Modo configurado: " + mode.name());

        logsHelper.logInfo("🚀 SpamCallService iniciado - A+H: " + (answerHangupEnabled ? "ON" : "OFF") + ", Modo: " + mode.name());
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        String callerNumber = getCallerNumber(call);
        int callState = call.getDetails().getState();

        Log.d(TAG, "📞 ========================================");
        Log.d(TAG, "📞 LLAMADA DETECTADA en InCallService");
        Log.d(TAG, "📞 Número: " + callerNumber);
        Log.d(TAG, "📞 Estado: " + getCallStateName(callState));
        Log.d(TAG, "📞 ========================================");

        logsHelper.logInfo("📞 InCallService - Llamada de: " + callerNumber);

        // Verificar si es número de emergencia
        if (isEmergencyNumber(callerNumber)) {
            Log.d(TAG, "🚨 NÚMERO DE EMERGENCIA - No se intercepta");
            logsHelper.logWarning("🚨 Número de emergencia detectado - No se intercepta");
            return;
        }

        // 🎯 PRIORIDAD 1: Verificar si este número está marcado para Answer+Hangup
        boolean isAnswerHangupEnabled = answerHangupHelper.isEnabled();
        boolean shouldAnswerHangup = answerHangupHelper.shouldHangup(callerNumber);

        Log.d(TAG, "🔍 Answer+Hangup enabled: " + isAnswerHangupEnabled);
        Log.d(TAG, "🔍 Número marcado para A+H: " + shouldAnswerHangup);

        logsHelper.logInfo("🔍 A+H: " + (isAnswerHangupEnabled ? "ON" : "OFF") + ", Marcado: " + shouldAnswerHangup);

        // Registrar callback para cambios de estado
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                Log.d(TAG, "📞 Estado cambió a: " + getCallStateName(state));
                logsHelper.logDebug("Estado cambió a: " + getCallStateName(state));

                // Si la llamada fue contestada
                if (state == Call.STATE_ACTIVE) {
                    Log.d(TAG, "✅ Llamada ACTIVA");
                    logsHelper.logInfo("✅ Llamada ACTIVA");

                    // Si Answer+Hangup está habilitado para este número
                    if (isAnswerHangupEnabled && shouldAnswerHangup) {
                        onAnswerHangupActive(call, callerNumber);
                    } else {
                        // Llamada normal o IA conversacional
                        onCallActive(call, callerNumber);
                    }
                } else if (state == Call.STATE_DISCONNECTED) {
                    Log.d(TAG, "💀 Llamada DESCONECTADA");
                    logsHelper.logInfo("💀 Llamada desconectada");

                    // Detener IVR si estaba reproduciéndose
                    if (ivrMessageHelper.isPlaying()) {
                        ivrMessageHelper.stopIVR();
                    }
                }
            }
        });

        // 🎯 DECISIÓN: ¿Qué hacer con esta llamada?
        if (isAnswerHangupEnabled && shouldAnswerHangup) {
            // Answer+Hangup: Contestar automáticamente
            AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();
            Log.d(TAG, "🎯 DECISIÓN: Answer+Hangup (Modo: " + mode.name() + ")");
            logsHelper.logInfo("🎯 Answer+Hangup - Modo: " + mode.name());

            showToast("🔇 Answer+Hangup: Contestando spam...");

            // Contestar rápidamente (100ms de delay)
            mainHandler.postDelayed(() -> {
                autoAnswerCall(call, callerNumber);
            }, 100);
        } else if (shouldAnswerWithAI(callerNumber)) {
            Log.d(TAG, "🤖 DECISIÓN: Auto-contestar con IA");
            logsHelper.logInfo("🤖 Decisión: IA conversacional");
            autoAnswerCall(call, callerNumber);
        } else if (shouldBlock(callerNumber)) {
            Log.d(TAG, "🚫 DECISIÓN: Bloquear llamada");
            logsHelper.logInfo("🚫 Decisión: Bloquear");
            blockCall(call, callerNumber);
        } else {
            Log.d(TAG, "✅ DECISIÓN: Permitir llamada normal");
            logsHelper.logInfo("✅ Llamada normal permitida");
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
     * 🎯 Se llama cuando Answer+Hangup está activo y la llamada está contestada
     * Aquí aplicamos la lógica según el modo configurado
     */
    private void onAnswerHangupActive(Call call, String callerNumber) {
        AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();

        Log.d(TAG, "🎯 ========================================");
        Log.d(TAG, "🎯 ANSWER+HANGUP ACTIVO");
        Log.d(TAG, "🎯 Número: " + callerNumber);
        Log.d(TAG, "🎯 Modo: " + mode.name());
        Log.d(TAG, "🎯 ========================================");

        logsHelper.logInfo("🎯 Answer+Hangup ACTIVO - Modo: " + mode.name() + " - " + callerNumber);

        switch (mode) {
            case HANGUP_IMMEDIATELY:
                // Modo 1: Colgar después del delay configurado
                int delay = answerHangupHelper.getHangupDelay();
                Log.d(TAG, "📵 MODO 1: Colgando en " + delay + " segundos...");
                logsHelper.logInfo("📵 Modo 1 - Hangup programado en " + delay + "s");

                showToast("📵 Modo 1: Colgando en " + delay + "s");

                mainHandler.postDelayed(() -> {
                    Log.d(TAG, "🎯 Ejecutando hangup (Modo 1)");
                    logsHelper.logInfo("🎯 Modo 1 - Hangup ejecutado");
                    call.disconnect();
                    answerHangupHelper.clearMarked();
                }, delay * 1000L);
                break;

            case PLAY_MESSAGE:
                // Modo 2: Reproducir mensaje IVR corporativo
                Log.d(TAG, "🔊 MODO 2: Iniciando IVR corporativo...");
                logsHelper.logInfo("🔊 Modo 2 - Iniciando IVR (30s max)");

                showToast("🔊 Modo 2: Reproduciendo IVR...");

                // Obtener instancia única de IVRMessageHelper
                IVRMessageHelper ivrHelper = IVRMessageHelper.getInstance(this);

                boolean ivrStarted = ivrHelper.startIVR(
                    IVRMessageHelper.IVRType.CORPORATE_INFINITE,
                    30  // 30 segundos máximo
                );

                if (ivrStarted) {
                    Log.d(TAG, "✅ IVR iniciado correctamente");
                    logsHelper.logInfo("✅ Modo 2 - IVR iniciado correctamente");

                    // Colgar después de 31 segundos (asegurar que IVR termine)
                    mainHandler.postDelayed(() -> {
                        Log.d(TAG, "🎯 IVR terminado, colgando (Modo 2)");
                        logsHelper.logInfo("🎯 Modo 2 - IVR finalizado, ejecutando hangup");
                        IVRMessageHelper.getInstance(this).stopIVR();
                        call.disconnect();
                        answerHangupHelper.clearMarked();
                    }, 31000L);
                } else {
                    Log.e(TAG, "❌ Error iniciando IVR, colgando inmediatamente");
                    logsHelper.logError("❌ Modo 2 - Error IVR, fallback a hangup");
                    call.disconnect();
                    answerHangupHelper.clearMarked();
                }
                break;

            case AI_CONVERSATION:
                // Modo 3: Conversación IA (próximamente)
                Log.d(TAG, "🤖 MODO 3: IA Conversacional (no implementado)");
                logsHelper.logWarning("🤖 Modo 3 - No implementado, fallback a hangup");

                showToast("🤖 Modo 3: No disponible (colgando en 2s)");

                // Fallback: colgar después de 2 segundos
                mainHandler.postDelayed(() -> {
                    call.disconnect();
                    answerHangupHelper.clearMarked();
                }, 2000L);
                break;
        }
    }

    /**
     * Se llama cuando la llamada está activa y lista para conversación IA
     */
    private void onCallActive(Call call, String callerNumber) {
        Log.d(TAG, "🎙️ ========================================");
        Log.d(TAG, "🎙️ LLAMADA ACTIVA - LISTA PARA IA");
        Log.d(TAG, "🎙️ Número: " + callerNumber);
        Log.d(TAG, "🎙️ ========================================");

        logsHelper.logInfo("🎙️ Llamada normal activa - " + callerNumber);

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
     * Muestra un Toast en el hilo principal
     */
    private void showToast(String message) {
        mainHandler.post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
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
