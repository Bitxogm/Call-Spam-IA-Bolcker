// AnswerHangupModule.java
package com.anonymous.SpamBlockerApp;

import android.Manifest;
import android.content.pm.PackageManager;
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
}
