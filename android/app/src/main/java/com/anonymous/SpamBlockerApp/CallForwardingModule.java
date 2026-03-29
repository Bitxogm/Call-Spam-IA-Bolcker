package com.anonymous.SpamBlockerApp;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import androidx.annotation.NonNull;

/**
 * Módulo React Native para gestionar desvíos de llamadas
 */
public class CallForwardingModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CallForwardingModule";
    private final CallForwardingManager forwardingManager;

    public CallForwardingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.forwardingManager = new CallForwardingManager(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return "CallForwardingModule";
    }

    /**
     * Activa desvío incondicional a Zadarma (*21*+34919933065#)
     */
    @ReactMethod
    public void enableForwarding(Promise promise) {
        try {
            Log.d(TAG, "📞 enableForwarding() llamado desde React Native");
            boolean success = forwardingManager.enableForwarding();
            promise.resolve(success);
        } catch (Exception e) {
            Log.e(TAG, "Error en enableForwarding: " + e.getMessage(), e);
            promise.reject("FORWARDING_ERROR", e.getMessage());
        }
    }

    /**
     * Desactiva desvío incondicional (##21#)
     */
    @ReactMethod
    public void disableForwarding(Promise promise) {
        try {
            Log.d(TAG, "📞 disableForwarding() llamado desde React Native");
            boolean success = forwardingManager.disableForwarding();
            promise.resolve(success);
        } catch (Exception e) {
            Log.e(TAG, "Error en disableForwarding: " + e.getMessage(), e);
            promise.reject("FORWARDING_ERROR", e.getMessage());
        }
    }

    /**
     * Consulta estado actual del desvío (*#21#)
     */
    @ReactMethod
    public void checkForwardingStatus(Promise promise) {
        try {
            Log.d(TAG, "📞 checkForwardingStatus() llamado desde React Native");
            boolean success = forwardingManager.checkForwardingStatus();
            promise.resolve(success);
        } catch (Exception e) {
            Log.e(TAG, "Error en checkForwardingStatus: " + e.getMessage(), e);
            promise.reject("FORWARDING_ERROR", e.getMessage());
        }
    }

    /**
     * Configura desvío según modo (HANGUP_IMMEDIATELY, BACKEND_FIXED, BACKEND_AI)
     */
    @ReactMethod
    public void configureForMode(String modeStr, Promise promise) {
        try {
            Log.d(TAG, "⚙️ configureForMode() llamado con modo: " + modeStr);

            AnswerHangupHelper.Mode mode;

            switch (modeStr) {
                case "HANGUP_IMMEDIATELY":
                    mode = AnswerHangupHelper.Mode.HANGUP_IMMEDIATELY;
                    break;
                case "BACKEND_FIXED":
                    mode = AnswerHangupHelper.Mode.BACKEND_FIXED;
                    break;
                case "BACKEND_AI":
                    mode = AnswerHangupHelper.Mode.BACKEND_AI;
                    break;
                default:
                    promise.reject("INVALID_MODE", "Modo no válido: " + modeStr);
                    return;
            }

            boolean success = forwardingManager.configureForMode(mode);
            promise.resolve(success);

        } catch (Exception e) {
            Log.e(TAG, "Error en configureForMode: " + e.getMessage(), e);
            promise.reject("FORWARDING_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene el número Zadarma configurado
     */
    @ReactMethod
    public void getZadarmaNumber(Promise promise) {
        try {
            String number = CallForwardingManager.getZadarmaNumber();
            promise.resolve(number);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}
