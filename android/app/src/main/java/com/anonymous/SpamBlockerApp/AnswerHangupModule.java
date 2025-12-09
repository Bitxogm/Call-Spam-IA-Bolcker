// AnswerHangupModule.java
package com.anonymous.SpamBlockerApp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

/**
 * Native Module para gestionar Answer+Hangup desde React Native
 */
public class AnswerHangupModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "AnswerHangupModule";
    private final AnswerHangupHelper helper;

    public AnswerHangupModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.helper = new AnswerHangupHelper(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Verifica si Answer+Hangup está habilitado
     */
    @ReactMethod
    public void isEnabled(Promise promise) {
        try {
            boolean enabled = helper.isEnabled();
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("IS_ENABLED_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Activa o desactiva Answer+Hangup
     */
    @ReactMethod
    public void setEnabled(boolean enabled, Promise promise) {
        try {
            helper.setEnabled(enabled);
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("SET_ENABLED_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene el delay actual en segundos
     */
    @ReactMethod
    public void getHangupDelay(Promise promise) {
        try {
            int delay = helper.getHangupDelay();
            promise.resolve(delay);
        } catch (Exception e) {
            promise.reject("GET_DELAY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Configura el delay en segundos (1-5)
     */
    @ReactMethod
    public void setHangupDelay(int seconds, Promise promise) {
        try {
            helper.setHangupDelay(seconds);
            promise.resolve(seconds);
        } catch (Exception e) {
            promise.reject("SET_DELAY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene información de debug
     */
    @ReactMethod
    public void getDebugInfo(Promise promise) {
        try {
            String info = helper.getDebugInfo();
            promise.resolve(info);
        } catch (Exception e) {
            promise.reject("GET_DEBUG_INFO_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene el modo actual
     */
    @ReactMethod
    public void getMode(Promise promise) {
        try {
            AnswerHangupHelper.Mode mode = helper.getMode();
            promise.resolve(mode.name());
        } catch (Exception e) {
            promise.reject("GET_MODE_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Configura el modo de Answer+Hangup
     */
    @ReactMethod
    public void setMode(String modeName, Promise promise) {
        try {
            AnswerHangupHelper.Mode mode = AnswerHangupHelper.Mode.valueOf(modeName);
            helper.setMode(mode);
            promise.resolve(true);
        } catch (IllegalArgumentException e) {
            promise.reject("INVALID_MODE", "Modo inválido: " + modeName, e);
        } catch (Exception e) {
            promise.reject("SET_MODE_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Verifica permisos requeridos para Answer+Hangup
     *
     * En Android 9+, para recibir broadcasts de PHONE_STATE con detalles de llamadas,
     * se requiere el permiso READ_CALL_LOG (además de READ_PHONE_STATE).
     *
     * Sin READ_CALL_LOG, solo se reciben broadcasts de IDLE (llamada terminada),
     * pero NO de RINGING o OFFHOOK, lo que impide que funcione Answer+Hangup.
     */
    @ReactMethod
    public void checkCallLogPermission(Promise promise) {
        try {
            WritableMap result = Arguments.createMap();

            // Verificar READ_PHONE_STATE
            boolean hasPhoneState = ContextCompat.checkSelfPermission(
                getReactApplicationContext(),
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED;

            // Verificar READ_CALL_LOG (CRÍTICO para Android 9+)
            boolean hasCallLog = ContextCompat.checkSelfPermission(
                getReactApplicationContext(),
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED;

            // Verificar ANSWER_PHONE_CALLS
            boolean hasAnswerCalls = ContextCompat.checkSelfPermission(
                getReactApplicationContext(),
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED;

            result.putBoolean("hasPhoneState", hasPhoneState);
            result.putBoolean("hasCallLog", hasCallLog);
            result.putBoolean("hasAnswerCalls", hasAnswerCalls);
            result.putBoolean("allGranted", hasPhoneState && hasCallLog && hasAnswerCalls);

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("CHECK_PERMISSION_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Verifica si el servicio de Accesibilidad está habilitado
     *
     * CRÍTICO: Para que funcione Modo 2 (IVR) y Modo 3 (IA), necesitamos
     * Accessibility Service que permita auto-contestar llamadas.
     */
    @ReactMethod
    public void isAccessibilityServiceEnabled(Promise promise) {
        try {
            boolean isEnabled = isAccessibilityEnabled(getReactApplicationContext());
            promise.resolve(isEnabled);
        } catch (Exception e) {
            promise.reject("CHECK_ACCESSIBILITY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Abre la configuración de Accesibilidad para que el usuario active el servicio
     */
    @ReactMethod
    public void openAccessibilitySettings(Promise promise) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (getCurrentActivity() != null) {
                getCurrentActivity().startActivity(intent);
                promise.resolve(true);
            } else {
                getReactApplicationContext().startActivity(intent);
                promise.resolve(true);
            }
        } catch (Exception e) {
            promise.reject("OPEN_ACCESSIBILITY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Helper para verificar si Accessibility Service está habilitado
     */
    private boolean isAccessibilityEnabled(Context context) {
        String serviceName = context.getPackageName() + "/.CallAccessibilityService";

        try {
            // Log para debugging
            android.util.Log.d("AnswerHangupModule", "🔍 Buscando servicio: " + serviceName);
            android.util.Log.d("AnswerHangupModule", "📦 Package name: " + context.getPackageName());

            int accessibilityEnabled = Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            );

            android.util.Log.d("AnswerHangupModule", "♿ Accessibility enabled flag: " + accessibilityEnabled);

            if (accessibilityEnabled != 1) {
                android.util.Log.w("AnswerHangupModule", "⚠️ Accessibility está DESACTIVADO en el sistema");
                return false;
            }

            String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );

            android.util.Log.d("AnswerHangupModule", "📋 Servicios habilitados: " + settingValue);

            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);

                while (splitter.hasNext()) {
                    String accessibilityService = splitter.next();
                    android.util.Log.d("AnswerHangupModule", "  🔹 Comparando: " + accessibilityService);
                    if (accessibilityService.equalsIgnoreCase(serviceName)) {
                        android.util.Log.i("AnswerHangupModule", "✅ ¡Servicio encontrado!");
                        return true;
                    }
                }
            } else {
                android.util.Log.w("AnswerHangupModule", "⚠️ No hay servicios de accesibilidad habilitados");
            }

            android.util.Log.w("AnswerHangupModule", "❌ Servicio NO encontrado en la lista");
        } catch (Exception e) {
            android.util.Log.e("AnswerHangupModule", "❌ Error verificando Accessibility: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}
