// AnswerHangupModule.java
package com.anonymous.SpamBlockerApp;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

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
}
