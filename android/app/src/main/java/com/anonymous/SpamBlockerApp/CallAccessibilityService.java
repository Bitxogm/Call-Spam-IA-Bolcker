package com.anonymous.SpamBlockerApp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Servicio de Accesibilidad para auto-contestar llamadas spam
 *
 * Este servicio:
 * 1. Detecta llamadas entrantes (escucha eventos de AccessibilityEvent)
 * 2. Verifica si el número está marcado para Answer+Hangup
 * 3. Simula click en botón "Contestar"
 * 4. Permite que InCallService reproduzca IVR o cuelgue
 *
 * IMPORTANTE: El usuario debe activar este servicio manualmente en:
 * Settings → Accessibility → SpamBlocker → ON
 */
public class CallAccessibilityService extends AccessibilityService {
    private static final String TAG = "CallAccessibilityService";

    // Helpers
    private AnswerHangupHelper answerHangupHelper;
    private LogsHelper logsHelper;
    private Handler mainHandler;

    // Phone state listener para detectar llamadas entrantes
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    // IVR Audio Player para Modo 2
    private IVRAudioPlayer ivrAudioPlayer;
    private boolean ivrPlaying = false;
    private Runnable hangupRunnable;
    private boolean wasRingingBeforeOffhook = false;

    // Textos EXÁCTOS de botón "Contestar" para evitar falsos positivos
    private static final String[] ANSWER_BUTTON_KEYWORDS = {
        "contestar", "responder", "aceptar", "answer", "accept"
    };

    // Paquetes de apps de llamadas comunes
    // NOTA: NO incluimos systemui para evitar conflictos con nuestra propia UI
    // En su lugar, usamos TelecomManager como fallback para contestar
    private static final String[] PHONE_PACKAGES = {
        "com.android.incallui",           // Android stock
        "com.android.dialer",             // Google Dialer
        "com.google.android.dialer",      // Google Dialer (alternativo)
        "com.samsung.android.incallui",   // Samsung
        "com.android.server.telecom"      // Telecom system
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mainHandler = new Handler(Looper.getMainLooper());
        answerHangupHelper = new AnswerHangupHelper(this);
        logsHelper = new LogsHelper(this);

        boolean isEnabled = answerHangupHelper.isEnabled();
        AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();

        Log.d(TAG, "🔓 CallAccessibilityService iniciado");
        Log.d(TAG, "📱 Answer+Hangup: " + (isEnabled ? "ACTIVADO" : "DESACTIVADO"));
        Log.d(TAG, "🎯 Modo: " + mode.name());

        logsHelper.logInfo("🔓 AccessibilityService iniciado - A+H: " + (isEnabled ? "ON" : "OFF"));

        // Registrar listener para detectar llamadas entrantes
        setupPhoneStateListener();
    }

    /**
     * Verifica si somos el marcador predeterminado
     */
    private boolean isDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                String defaultDialer = telecomManager.getDefaultDialerPackage();
                String packageName = getPackageName();
                return packageName.equals(defaultDialer);
            }
        }
        return false;
    }

    /**
     * Configura el listener para detectar llamadas entrantes
     */
    private void setupPhoneStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);

                if (!answerHangupHelper.isEnabled()) {
                    return;
                }

                // 🎯 NUEVO: Si somos default dialer, SpamCallService manejará TODO
                // AccessibilityService solo se usa como fallback cuando NO somos default
                boolean isDefault = isDefaultDialer();
                Log.d(TAG, "🔍 Default Dialer: " + (isDefault ? "SÍ (SpamCallService lo manejará)" : "NO (AccessibilityService lo manejará)"));

                if (isDefault) {
                    Log.d(TAG, "✅ Somos default dialer - SpamCallService (InCallService) manejará esta llamada");
                    logsHelper.logInfo("✅ Default dialer activo - InCallService tiene control total");
                    // NO hacer nada aquí, SpamCallService lo manejará
                    return;
                }

                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    wasRingingBeforeOffhook = true;
                    Log.d(TAG, "📞 Llamada entrante detectada via PhoneStateListener");
                    logsHelper.logInfo("📞 Llamada entrante detectada");

                    // Esperar 1 segundo para dar tiempo a que la UI aparezca
                        // 🎯 CRÍTICO: Verificar si el número es SPAM antes de contestar
                        if (answerHangupHelper.shouldHangup(phoneNumber)) {
                            AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();
                            if (mode == AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY) {
                                Log.d(TAG, "✅ SPAM detectado y MODO 1 - Contestando...");
                                answerCallProgrammatically();
                            } else {
                                Log.d(TAG, "⏭️ SPAM detectado pero MODO BACKEND - NO contestar (dejando que SpamCallService rechace)");
                            }
                        } else {
                            Log.d(TAG, "⏭️ No es spam - No contestar por accesibilidad");
                        }

                } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    if (!wasRingingBeforeOffhook) {
                        Log.d(TAG, "📞 Llamada SALIENTE detectada - ignorando");
                        return;
                    }
                    wasRingingBeforeOffhook = false;
                    // Llamada ACTIVA - Reproducir IVR si está en Modo 2
                    Log.d(TAG, "📞 Llamada ACTIVA detectada");
                    logsHelper.logInfo("📞 Llamada activa");

                    AnswerHangupHelper.Mode mode = answerHangupHelper.getMode();

                    if (mode == AnswerHangupHelper.Mode.BACKEND_FIXED) {
                        Log.d(TAG, "🔊 MODO 2: Iniciando IVR en AccessibilityService...");
                        logsHelper.logInfo("🔊 Modo 2 - Iniciando IVR desde AccessibilityService");

                        // Esperar 1 segundo para que el audio esté listo
                        mainHandler.postDelayed(() -> {
                            startIVRPlayback();
                        }, 1000);

                    } else if (mode == AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY) {
                        // Modo 1: Colgar después del delay
                        int delay = answerHangupHelper.getHangupDelay();
                        Log.d(TAG, "📵 MODO 1: Colgando en " + delay + " segundos...");
                        logsHelper.logInfo("📵 Modo 1 - Hangup programado en " + delay + "s");

                        mainHandler.postDelayed(() -> {
                            hangupCall();
                        }, delay * 1000L);
                    }

                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    // Llamada terminada - detener IVR si estaba reproduciéndose
                    Log.d(TAG, "💀 Llamada terminada");
                    logsHelper.logInfo("💀 Llamada terminada");
                    stopIVRPlayback();
                    wasRingingBeforeOffhook = false;
                }
            }
        };

        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            Log.d(TAG, "✅ PhoneStateListener registrado");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Solo procesar si Answer+Hangup está habilitado
        if (!answerHangupHelper.isEnabled()) {
            return;
        }

        // CRÍTICO: El servicio de Accesibilidad SOLO debe auto-contestar
        // si estamos en Modo 1 (HANGUP_IMMEDIATELY).
        // Para Escudo 2 y 3, queremos que la llamada se RECHACE para que desvíe.
        if (answerHangupHelper.getMode() != AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY) {
            return;
        }

        int eventType = event.getEventType();
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // CRÍTICO: NO procesar NINGÚN evento de nuestra propia app
        // (para evitar clickear en nuestros propios botones)
        if (packageName.equals(getPackageName())) {
            return; // Salir inmediatamente, sin logging
        }

        // DEBUG: Loguear TODOS los eventos de systemui para entender qué pasa durante una llamada
        if (packageName.contains("systemui")) {
            Log.d(TAG, "🔍 DEBUG SystemUI Event: type=" + eventTypeToString(eventType) +
                       ", package=" + packageName +
                       ", className=" + (event.getClassName() != null ? event.getClassName() : "null"));
        }

        // Solo procesar eventos de ventana nueva o contenido cambiado
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        // Log para debugging de eventos relevantes
        Log.d(TAG, "📱 AccessibilityEvent: type=" + eventTypeToString(eventType) +
                   ", package=" + packageName);

        // Verificar si es un evento de una app de llamadas
        if (!isPhonePackage(packageName)) {
            return;
        }

        // Log cuando detectamos una app de llamadas
        Log.d(TAG, "📞 Evento de app de llamadas detectado: " + packageName);
        logsHelper.logDebug("Evento app llamadas: " + packageName);

        // Intentar encontrar y clickear botón de contestar
        // Damos un pequeño delay para asegurar que la UI esté lista
        mainHandler.postDelayed(() -> {
            tryToAnswerCall();
        }, 500); // 500ms delay
    }

    /**
     * Intenta encontrar y clickear el botón de contestar
     * Si no lo encuentra, usa TelecomManager como fallback
     */
    private void tryToAnswerCall() {
        Log.d(TAG, "🔍 Intentando contestar llamada...");
        logsHelper.logDebug("🔍 Buscando botón de contestar");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        if (rootNode == null) {
            Log.w(TAG, "⚠️ No se pudo obtener nodo raíz - intentando TelecomManager");
            logsHelper.logWarning("⚠️ No se pudo obtener ventana activa");
            answerCallProgrammatically();
            return;
        }

        try {
            // Buscar botón de contestar
            AccessibilityNodeInfo answerButton = findAnswerButton(rootNode);

            if (answerButton != null) {
                Log.d(TAG, "✅ Botón de contestar encontrado!");
                logsHelper.logInfo("✅ Botón contestar encontrado - Simulando click");

                // Simular click
                boolean clicked = answerButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                if (clicked) {
                    Log.d(TAG, "🎯 Click simulado exitosamente");
                    logsHelper.logInfo("🎯 Llamada contestada automáticamente via Accessibility");
                } else {
                    Log.w(TAG, "⚠️ No se pudo simular click - intentando TelecomManager");
                    logsHelper.logWarning("⚠️ Error al simular click - usando fallback");
                    answerCallProgrammatically();
                }

                answerButton.recycle();
            } else {
                Log.d(TAG, "🔍 Botón de contestar no encontrado - es heads-up notification");
                logsHelper.logDebug("🔍 Botón no encontrado - usando TelecomManager");
                answerCallProgrammatically();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error buscando botón: " + e.getMessage(), e);
            logsHelper.logError("❌ Error Accessibility: " + e.getMessage());
            answerCallProgrammatically();
        } finally {
            rootNode.recycle();
        }
    }

    /**
     * Contesta la llamada programáticamente usando TelecomManager
     * Este método funciona con heads-up notifications y no requiere buscar botones
     */
    private void answerCallProgrammatically() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "⚠️ TelecomManager.acceptRingingCall requiere Android 8.0+");
            logsHelper.logWarning("⚠️ Android version no soporta TelecomManager.acceptRingingCall");
            return;
        }

        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);

            if (telecomManager == null) {
                Log.e(TAG, "❌ No se pudo obtener TelecomManager");
                logsHelper.logError("❌ TelecomManager no disponible");
                return;
            }

            // Contestar la llamada programáticamente
            telecomManager.acceptRingingCall();

            Log.i(TAG, "✅ Llamada contestada programáticamente via TelecomManager");
            logsHelper.logInfo("✅ Llamada contestada automáticamente (TelecomManager)");

        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permiso denegado: " + e.getMessage());
            logsHelper.logError("❌ Falta permiso ANSWER_PHONE_CALLS");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error contestando llamada: " + e.getMessage(), e);
            logsHelper.logError("❌ Error TelecomManager: " + e.getMessage());
        }
    }

    /**
     * Busca el botón de contestar en el árbol de nodos
     */
    private AccessibilityNodeInfo findAnswerButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        // Verificar si este nodo es un botón de contestar
        if (isAnswerButton(node)) {
            return node;
        }

        // Buscar recursivamente en hijos
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findAnswerButton(child);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }

        return null;
    }

    /**
     * Verifica si un nodo es el botón de contestar
     */
    private boolean isAnswerButton(AccessibilityNodeInfo node) {
        // Ignorar nodos de nuestra propia app
        CharSequence nodePackage = node.getPackageName();
        if (nodePackage != null && nodePackage.toString().equals(getPackageName())) {
            return false;
        }

        // Debe ser clickeable
        if (!node.isClickable()) {
            return false;
        }

        // Verificar texto del nodo
        CharSequence text = node.getText();
        CharSequence contentDescription = node.getContentDescription();

        String textStr = text != null ? text.toString().toLowerCase() : "";
        String descStr = contentDescription != null ? contentDescription.toString().toLowerCase() : "";

        // Buscar coincidencias exactas o casi exactas
        for (String keyword : ANSWER_BUTTON_KEYWORDS) {
            // REGLAS ESTRICTAS PARA EVITAR AUTO-CLICKS EN NUESTRA APP:
            // 1. El texto debe ser IGUAL a la palabra clave (ej: "Contestar")
            // 2. O la descripción debe ser igual a la palabra clave
            // 3. La longitud debe ser corta (un botón no tiene un párrafo)
            if (textStr.equals(keyword) || descStr.equals(keyword)) {
                Log.d(TAG, "🎯 MATCH EXACTO: " + keyword);
                return true;
            }
            
            // Si contiene la palabra pero es muy corta (ej: "Contestar llamada")
            if ((textStr.contains(keyword) && textStr.length() < 15) || 
                (descStr.contains(keyword) && descStr.length() < 15)) {
                Log.d(TAG, "🎯 MATCH CERCANO: " + (textStr.isEmpty() ? descStr : textStr));
                return true;
            }
        }

        // Verificar resource ID (algunos sistemas usan IDs específicos)
        String resourceId = node.getViewIdResourceName();
        if (resourceId != null) {
            String resourceIdLower = resourceId.toLowerCase();
            if (resourceIdLower.contains("answer") ||
                resourceIdLower.contains("accept") ||
                resourceIdLower.contains("contestar")) {
                Log.d(TAG, "🎯 Botón encontrado por ID: " + resourceId);
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica si el paquete pertenece a una app de llamadas
     */
    private boolean isPhonePackage(String packageName) {
        for (String phonePackage : PHONE_PACKAGES) {
            if (packageName.contains(phonePackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convierte tipo de evento a string legible
     */
    private String eventTypeToString(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "WINDOW_STATE_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                return "VIEW_CLICKED";
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                return "NOTIFICATION_STATE_CHANGED";
            default:
                return "TYPE_" + eventType;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "⚠️ Servicio interrumpido");
        logsHelper.logWarning("⚠️ AccessibilityService interrumpido");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        // Configurar el servicio
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        // Eventos que queremos escuchar
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

        // Tipo de feedback
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        // Paquetes a observar (solo apps de llamadas)
        info.packageNames = PHONE_PACKAGES;

        // Flags
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        // Delay de notificación
        info.notificationTimeout = 100;

        setServiceInfo(info);

        Log.d(TAG, "✅ Servicio configurado y conectado");
        logsHelper.logInfo("✅ AccessibilityService conectado");
    }

    /**
     * Inicia la reproducción del IVR (Modo 2)
     */
    private void startIVRPlayback() {
        if (ivrPlaying) {
            Log.w(TAG, "⚠️ IVR ya está reproduciéndose");
            return;
        }

        // Ruta del archivo de audio pre-generado
        String audioPath = getFilesDir().getAbsolutePath() + "/ivr_corporate.mp3";
        java.io.File audioFile = new java.io.File(audioPath);

        Log.d(TAG, "🔍 DEBUG - Ruta IVR: " + audioPath);
        Log.d(TAG, "🔍 DEBUG - Archivo existe: " + audioFile.exists());

        if (audioFile.exists()) {
            Log.d(TAG, "🔍 DEBUG - Tamaño: " + audioFile.length() + " bytes");
            Log.d(TAG, "✅ Audio IVR encontrado, usando MediaPlayer");
            logsHelper.logInfo("✅ Modo 2 - Usando audio pre-generado");

            // Inicializar IVRAudioPlayer
            ivrAudioPlayer = IVRAudioPlayer.getInstance(this);

            // Reproducir IVR (0 loops = repetir infinitamente, 30s timeout)
            boolean started = ivrAudioPlayer.playIVR(audioPath, 0, 30);

            if (started) {
                ivrPlaying = true;
                Log.d(TAG, "✅ IVR (MediaPlayer) iniciado correctamente");
                logsHelper.logInfo("✅ IVR iniciado con MediaPlayer");

                // Programar hangup después de 31 segundos
                hangupRunnable = () -> {
                    Log.d(TAG, "🎯 IVR terminado, colgando (Modo 2)");
                    logsHelper.logInfo("🎯 Modo 2 - IVR finalizado, ejecutando hangup");
                    stopIVRPlayback();
                    hangupCall();
                };

                mainHandler.postDelayed(hangupRunnable, 31000L);

            } else {
                Log.e(TAG, "❌ Error iniciando IVR (MediaPlayer), colgando");
                logsHelper.logError("❌ Modo 2 - Error IVR MediaPlayer");
                hangupCall();
            }

        } else {
            // Fallback: colgar inmediatamente si no hay audio
            Log.w(TAG, "⚠️ Audio IVR no encontrado: " + audioPath);
            logsHelper.logWarning("⚠️ Modo 2 - Audio no encontrado, colgando");
            hangupCall();
        }
    }

    /**
     * Detiene la reproducción del IVR
     */
    private void stopIVRPlayback() {
        if (ivrAudioPlayer != null && ivrPlaying) {
            ivrAudioPlayer.stopIVR();
            ivrPlaying = false;
            Log.d(TAG, "🛑 IVR detenido");
            logsHelper.logInfo("🛑 IVR detenido");
        }

        // Cancelar hangup programado si existe
        if (hangupRunnable != null && mainHandler != null) {
            mainHandler.removeCallbacks(hangupRunnable);
            hangupRunnable = null;
        }
    }

    /**
     * Cuelga la llamada activa
     */
    private void hangupCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "⚠️ TelecomManager.endCall requiere Android 9.0+");
            logsHelper.logWarning("⚠️ Android version no soporta TelecomManager.endCall");
            return;
        }

        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);

            if (telecomManager == null) {
                Log.e(TAG, "❌ No se pudo obtener TelecomManager");
                logsHelper.logError("❌ TelecomManager no disponible");
                return;
            }

            // Colgar la llamada
            boolean ended = telecomManager.endCall();

            if (ended) {
                Log.i(TAG, "✅ Llamada colgada correctamente");
                logsHelper.logInfo("✅ Llamada colgada automáticamente");
            } else {
                Log.w(TAG, "⚠️ No se pudo colgar (sin llamada activa?)");
                logsHelper.logWarning("⚠️ Hangup falló - sin llamada activa");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permiso denegado para colgar: " + e.getMessage());
            logsHelper.logError("❌ Falta permiso ANSWER_PHONE_CALLS para hangup");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error colgando llamada: " + e.getMessage(), e);
            logsHelper.logError("❌ Error TelecomManager.endCall: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Detener IVR si está reproduciéndose
        stopIVRPlayback();

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        // Desregistrar PhoneStateListener
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            Log.d(TAG, "✅ PhoneStateListener desregistrado");
        }

        Log.d(TAG, "💀 CallAccessibilityService destruido");
        logsHelper.logInfo("💀 AccessibilityService destruido");
    }
}
