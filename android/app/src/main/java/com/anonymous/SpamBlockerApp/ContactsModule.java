// ContactsModule.java
package com.anonymous.SpamBlockerApp;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

/**
 * Native Module para gestionar whitelist de contactos y Modo Radical
 */
public class ContactsModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "ContactsModule";
    private static final String PREFS_NAME = "spam_blocker_settings";
    private static final String KEY_MODO_RADICAL = "modo_radical";

    private final ContactsHelper contactsHelper;

    public ContactsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.contactsHelper = new ContactsHelper(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Verifica si un número está en los contactos del usuario
     */
    @ReactMethod
    public void isInContacts(String number, Promise promise) {
        try {
            boolean isContact = contactsHelper.isInContacts(number);
            promise.resolve(isContact);
        } catch (Exception e) {
            promise.reject("IS_IN_CONTACTS_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene información completa de un contacto
     */
    @ReactMethod
    public void getContactInfo(String number, Promise promise) {
        try {
            ContactsHelper.ContactInfo contact = contactsHelper.findContactByNumber(number);

            if (contact != null) {
                WritableMap map = new WritableNativeMap();
                map.putDouble("id", contact.id);
                map.putString("name", contact.name);
                map.putString("number", contact.number);
                map.putString("photoUri", contact.photoUri);
                promise.resolve(map);
            } else {
                promise.resolve(null);
            }
        } catch (Exception e) {
            promise.reject("GET_CONTACT_INFO_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Activa o desactiva el Modo Radical
     * Modo Radical = Solo permitir llamadas de contactos
     */
    @ReactMethod
    public void setModoRadical(boolean enabled, Promise promise) {
        try {
            SharedPreferences prefs = getReactApplicationContext()
                .getSharedPreferences(PREFS_NAME, ReactApplicationContext.MODE_PRIVATE);

            prefs.edit()
                .putBoolean(KEY_MODO_RADICAL, enabled)
                .apply();

            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("SET_MODO_RADICAL_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Lee el estado actual del Modo Radical
     */
    @ReactMethod
    public void isModoRadicalEnabled(Promise promise) {
        try {
            SharedPreferences prefs = getReactApplicationContext()
                .getSharedPreferences(PREFS_NAME, ReactApplicationContext.MODE_PRIVATE);

            boolean enabled = prefs.getBoolean(KEY_MODO_RADICAL, false);
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("IS_MODO_RADICAL_ENABLED_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Cuenta total de contactos (para stats)
     */
    @ReactMethod
    public void getContactsCount(Promise promise) {
        try {
            // No hay API directa eficiente, devolver -1 o implementar si es necesario
            promise.resolve(-1);
        } catch (Exception e) {
            promise.reject("GET_CONTACTS_COUNT_ERROR", e.getMessage(), e);
        }
    }
}
