package com.anonymous.SpamBlockerApp;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

/**
 * Módulo nativo para manejar la app de marcador predeterminada
 *
 * Este módulo permite:
 * 1. Verificar si la app es el marcador predeterminado
 * 2. Solicitar al usuario que la establezca como predeterminada
 * 3. Desactivarla si está activa
 *
 * ¿Por qué esto es importante?
 * Android/Samsung bloquean InCallService para apps de terceros.
 * SOLO la app de marcador predeterminada puede usar InCallService completamente.
 * Esto permitirá que el audio IVR se enrute correctamente al caller.
 */
public class DefaultDialerModule extends ReactContextBaseJavaModule {
    private static final String TAG = "DefaultDialerModule";
    private final ReactApplicationContext reactContext;

    // Request code para el resultado de solicitar ser marcador predeterminado
    private static final int REQUEST_CODE_SET_DEFAULT_DIALER = 12345;

    public DefaultDialerModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @Override
    public String getName() {
        return "DefaultDialerModule";
    }

    /**
     * Verifica si esta app es el marcador predeterminado
     */
    @ReactMethod
    public void isDefaultDialer(Promise promise) {
        try {
            String packageName = reactContext.getPackageName();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelecomManager telecomManager = (TelecomManager) reactContext.getSystemService(Context.TELECOM_SERVICE);

                if (telecomManager != null) {
                    String defaultDialer = telecomManager.getDefaultDialerPackage();
                    boolean isDefault = packageName.equals(defaultDialer);

                    Log.d(TAG, "🔍 Default dialer: " + defaultDialer);
                    Log.d(TAG, "🔍 Esta app: " + packageName);
                    Log.d(TAG, "🔍 Es default: " + isDefault);

                    promise.resolve(isDefault);
                } else {
                    Log.e(TAG, "❌ TelecomManager no disponible");
                    promise.resolve(false);
                }
            } else {
                // Android < 6.0 no soporta cambiar marcador predeterminado
                Log.w(TAG, "⚠️ Android < 6.0 no soporta default dialer");
                promise.resolve(false);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error verificando default dialer: " + e.getMessage());
            promise.reject("CHECK_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Solicita al usuario establecer esta app como marcador predeterminado
     *
     * IMPORTANTE: El usuario verá un diálogo del sistema donde debe seleccionar tu app
     */
    @ReactMethod
    public void requestSetDefaultDialer(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usa RoleManager
                requestViaRoleManager(promise);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6-9 usa TelecomManager
                requestViaTelecomManager(promise);
            } else {
                Log.w(TAG, "⚠️ Android < 6.0 no soporta cambiar default dialer");
                promise.reject("NOT_SUPPORTED", "Android version no soporta default dialer");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error solicitando default dialer: " + e.getMessage());
            promise.reject("REQUEST_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Solicita usando RoleManager (Android 10+)
     */
    private void requestViaRoleManager(Promise promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager roleManager = (RoleManager) reactContext.getSystemService(Context.ROLE_SERVICE);

                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                        // Solicitar el rol de marcador
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);

                        if (getCurrentActivity() != null) {
                            getCurrentActivity().startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER);
                            Log.d(TAG, "✅ Solicitando rol de marcador via RoleManager");
                            promise.resolve(true);
                        } else {
                            Log.e(TAG, "❌ No hay Activity actual para mostrar diálogo");
                            promise.reject("NO_ACTIVITY", "No hay Activity actual");
                        }
                    } else {
                        Log.d(TAG, "✅ Ya es el marcador predeterminado");
                        promise.resolve(true);
                    }
                } else {
                    Log.e(TAG, "❌ RoleManager no disponible o rol no disponible");
                    promise.reject("ROLE_NOT_AVAILABLE", "RoleManager no disponible");
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error con RoleManager: " + e.getMessage());
                promise.reject("ROLE_ERROR", e.getMessage(), e);
            }
        }
    }

    /**
     * Solicita usando TelecomManager (Android 6-9)
     */
    private void requestViaTelecomManager(Promise promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                TelecomManager telecomManager = (TelecomManager) reactContext.getSystemService(Context.TELECOM_SERVICE);

                if (telecomManager != null) {
                    String packageName = reactContext.getPackageName();
                    String currentDialer = telecomManager.getDefaultDialerPackage();

                    if (!packageName.equals(currentDialer)) {
                        // Crear intent para solicitar ser marcador predeterminado
                        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName);

                        if (getCurrentActivity() != null) {
                            getCurrentActivity().startActivity(intent);
                            Log.d(TAG, "✅ Solicitando ser marcador predeterminado via TelecomManager");
                            promise.resolve(true);
                        } else {
                            Log.e(TAG, "❌ No hay Activity actual para mostrar diálogo");
                            promise.reject("NO_ACTIVITY", "No hay Activity actual");
                        }
                    } else {
                        Log.d(TAG, "✅ Ya es el marcador predeterminado");
                        promise.resolve(true);
                    }
                } else {
                    Log.e(TAG, "❌ TelecomManager no disponible");
                    promise.reject("TELECOM_NOT_AVAILABLE", "TelecomManager no disponible");
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error con TelecomManager: " + e.getMessage());
                promise.reject("TELECOM_ERROR", e.getMessage(), e);
            }
        }
    }

    /**
     * Obtiene el nombre del paquete del marcador predeterminado actual
     */
    @ReactMethod
    public void getCurrentDefaultDialer(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelecomManager telecomManager = (TelecomManager) reactContext.getSystemService(Context.TELECOM_SERVICE);

                if (telecomManager != null) {
                    String defaultDialer = telecomManager.getDefaultDialerPackage();
                    Log.d(TAG, "🔍 Marcador predeterminado actual: " + defaultDialer);
                    promise.resolve(defaultDialer != null ? defaultDialer : "");
                } else {
                    promise.resolve("");
                }
            } else {
                promise.resolve("");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error obteniendo default dialer: " + e.getMessage());
            promise.reject("GET_ERROR", e.getMessage(), e);
        }
    }
}
