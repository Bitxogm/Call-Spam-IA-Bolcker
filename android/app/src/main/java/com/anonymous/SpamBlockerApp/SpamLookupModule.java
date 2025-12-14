// SpamLookupModule.java
package com.anonymous.SpamBlockerApp;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

/**
 * Native Module para consultar números en servicios externos
 * (ListaSpam, CleverDialer)
 */
public class SpamLookupModule extends ReactContextBaseJavaModule {
    private static final String TAG = "SpamLookupModule";

    public SpamLookupModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "SpamLookupModule";
    }

    /**
     * Abre ListaSpam.com para consultar número
     */
    @ReactMethod
    public void openListaSpam(String phoneNumber, Promise promise) {
        try {
            String cleanNumber = cleanPhoneNumber(phoneNumber);
            String url = "https://www.listaspam.com/busca.php?Telefono=" + cleanNumber;

            Log.d(TAG, "🔍 Abriendo ListaSpam: " + url);
            openUrlInCustomTab(url);

            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error abriendo ListaSpam: " + e.getMessage());
            promise.reject("ERROR", "No se pudo abrir ListaSpam: " + e.getMessage());
        }
    }

    /**
     * Abre CleverDialer.es para consultar número
     */
    @ReactMethod
    public void openCleverDialer(String phoneNumber, Promise promise) {
        try {
            String cleanNumber = cleanPhoneNumber(phoneNumber);
            String url = "https://www.cleverdialer.es/numero/" + cleanNumber;

            Log.d(TAG, "🔍 Abriendo CleverDialer: " + url);
            openUrlInCustomTab(url);

            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error abriendo CleverDialer: " + e.getMessage());
            promise.reject("ERROR", "No se pudo abrir CleverDialer: " + e.getMessage());
        }
    }

    /**
     * Limpia el número de teléfono (quita +34, espacios, etc.)
     */
    private String cleanPhoneNumber(String phoneNumber) {
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");

        // Quitar prefijo español
        if (cleaned.startsWith("34")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("0034")) {
            cleaned = cleaned.substring(4);
        }

        return cleaned;
    }

    /**
     * Abre URL usando Chrome Custom Tabs (evita redirecciones a app stores)
     */
    private void openUrlInCustomTab(String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        customTabsIntent.launchUrl(getReactApplicationContext(), Uri.parse(url));
    }
}
