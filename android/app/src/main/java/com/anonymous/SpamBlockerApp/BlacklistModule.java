// BlacklistModule.java
package com.anonymous.SpamBlockerApp;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;

import java.util.Set;

/**
 * Native Module para gestionar la blacklist desde React Native
 * Expone métodos para añadir, eliminar y obtener números de la lista negra
 */
public class BlacklistModule extends ReactContextBaseJavaModule {
    private SharedPreferencesHelper prefsHelper;

    public BlacklistModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.prefsHelper = new SharedPreferencesHelper(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return "BlacklistModule";
    }

    /**
     * Añade un número a la lista negra
     */
    @ReactMethod
    public void addNumber(String number, Promise promise) {
        try {
            boolean success = prefsHelper.addToBlacklist(number);
            if (success) {
                promise.resolve(true);
            } else {
                promise.reject("ADD_ERROR", "No se pudo añadir el número");
            }
        } catch (Exception e) {
            promise.reject("ADD_ERROR", e.getMessage());
        }
    }

    /**
     * Elimina un número de la lista negra
     */
    @ReactMethod
    public void removeNumber(String number, Promise promise) {
        try {
            boolean success = prefsHelper.removeFromBlacklist(number);
            if (success) {
                promise.resolve(true);
            } else {
                promise.reject("REMOVE_ERROR", "No se pudo eliminar el número");
            }
        } catch (Exception e) {
            promise.reject("REMOVE_ERROR", e.getMessage());
        }
    }

    /**
     * Verifica si un número está en la lista negra
     */
    @ReactMethod
    public void isInBlacklist(String number, Promise promise) {
        try {
            boolean isSpam = prefsHelper.isInBlacklist(number);
            promise.resolve(isSpam);
        } catch (Exception e) {
            promise.reject("CHECK_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene todos los números de la lista negra
     */
    @ReactMethod
    public void getAllNumbers(Promise promise) {
        try {
            Set<String> blacklist = prefsHelper.getBlacklist();
            WritableArray array = new WritableNativeArray();

            for (String number : blacklist) {
                array.pushString(number);
            }

            promise.resolve(array);
        } catch (Exception e) {
            promise.reject("GET_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene el número de elementos en la lista negra
     */
    @ReactMethod
    public void getCount(Promise promise) {
        try {
            int count = prefsHelper.getBlacklistCount();
            promise.resolve(count);
        } catch (Exception e) {
            promise.reject("COUNT_ERROR", e.getMessage());
        }
    }

    /**
     * Limpia toda la lista negra
     */
    @ReactMethod
    public void clearAll(Promise promise) {
        try {
            prefsHelper.clearBlacklist();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_ERROR", e.getMessage());
        }
    }
}
