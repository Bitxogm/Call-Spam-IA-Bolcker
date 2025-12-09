package com.anonymous.SpamBlockerApp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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

    // Textos comunes de botón "Contestar" en diferentes idiomas
    private static final String[] ANSWER_BUTTON_TEXTS = {
        "answer",           // Inglés
        "contestar",        // Español
        "responder",        // Español (alternativo)
        "accept",           // Inglés (alternativo)
        "aceptar",          // Español
        "accept call",      // Inglés completo
        "answer call",      // Inglés completo
        "contestar llamada" // Español completo
    };

    // Paquetes de apps de llamadas comunes
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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Solo procesar si Answer+Hangup está habilitado
        if (!answerHangupHelper.isEnabled()) {
            return;
        }

        // Tipos de eventos que nos interesan
        int eventType = event.getEventType();
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // Log para debugging
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            Log.d(TAG, "📱 AccessibilityEvent: type=" + eventTypeToString(eventType) +
                       ", package=" + packageName);
        }

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
     */
    private void tryToAnswerCall() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        if (rootNode == null) {
            Log.w(TAG, "⚠️ No se pudo obtener nodo raíz");
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
                    Log.w(TAG, "⚠️ No se pudo simular click");
                    logsHelper.logWarning("⚠️ Error al simular click en botón contestar");
                }

                answerButton.recycle();
            } else {
                Log.d(TAG, "🔍 Botón de contestar no encontrado (aún)");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error buscando botón: " + e.getMessage(), e);
            logsHelper.logError("❌ Error Accessibility: " + e.getMessage());
        } finally {
            rootNode.recycle();
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
        // Debe ser clickeable
        if (!node.isClickable()) {
            return false;
        }

        // Verificar texto del nodo
        CharSequence text = node.getText();
        CharSequence contentDescription = node.getContentDescription();

        String textStr = text != null ? text.toString().toLowerCase() : "";
        String descStr = contentDescription != null ? contentDescription.toString().toLowerCase() : "";

        // Buscar palabras clave
        for (String keyword : ANSWER_BUTTON_TEXTS) {
            if (textStr.contains(keyword) || descStr.contains(keyword)) {
                Log.d(TAG, "🎯 Botón encontrado: text='" + textStr + "', desc='" + descStr + "'");
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
        // Nota: Si dejamos esto vacío, observa TODAS las apps
        // info.packageNames = PHONE_PACKAGES; // Descomentar si quieres filtrar

        // Flags
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        // Delay de notificación
        info.notificationTimeout = 100;

        setServiceInfo(info);

        Log.d(TAG, "✅ Servicio configurado y conectado");
        logsHelper.logInfo("✅ AccessibilityService conectado");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "💀 CallAccessibilityService destruido");
        logsHelper.logInfo("💀 AccessibilityService destruido");
    }
}
