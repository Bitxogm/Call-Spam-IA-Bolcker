// CallInterceptorModule.java
package com.anonymous.SpamBlockerApp;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telecom.TelecomManager;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.anonymous.SpamBlockerApp.CallInterceptorService;
public class CallInterceptorModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "CallInterceptorModule";
    private ReactApplicationContext reactContext;

    public CallInterceptorModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void checkPermissions(Promise promise) {
        try {
            WritableMap result = Arguments.createMap();
            
            // Verificar permisos básicos
            boolean hasPhonePermission = ContextCompat.checkSelfPermission(
                reactContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            
            boolean hasCallPermission = ContextCompat.checkSelfPermission(
                reactContext, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED;
            
            // Verificar si es la app de llamadas predeterminada
            boolean isDefaultDialer = isDefaultDialerApp();
            
            result.putBoolean("hasPhonePermission", hasPhonePermission);
            result.putBoolean("hasCallPermission", hasCallPermission);
            result.putBoolean("isDefaultDialer", isDefaultDialer);
            result.putBoolean("canInterceptCalls", hasPhonePermission && hasCallPermission && isDefaultDialer);
            
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("PERMISSION_CHECK_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void requestDefaultDialerRole(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) reactContext.getSystemService(Context.ROLE_SERVICE);
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                
                if (getCurrentActivity() != null) {
                    getCurrentActivity().startActivityForResult(intent, 1001);
                    promise.resolve("Solicitando permisos de marcador predeterminado...");
                } else {
                    promise.reject("NO_ACTIVITY", "No hay actividad actual disponible");
                }
            } else {
                // Para versiones anteriores
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, 
                    reactContext.getPackageName());
                
                if (getCurrentActivity() != null) {
                    getCurrentActivity().startActivity(intent);
                    promise.resolve("Solicitando permisos de marcador predeterminado (legacy)...");
                } else {
                    promise.reject("NO_ACTIVITY", "No hay actividad actual disponible");
                }
            }
        } catch (Exception e) {
            promise.reject("REQUEST_DIALER_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void startInterceptorService(Promise promise) {
        try {
            Intent serviceIntent = new Intent(reactContext, CallInterceptorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(serviceIntent);
            } else {
                reactContext.startService(serviceIntent);
            }
            
            promise.resolve("Servicio interceptor iniciado");
        } catch (Exception e) {
            promise.reject("START_SERVICE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopInterceptorService(Promise promise) {
        try {
            Intent serviceIntent = new Intent(reactContext, CallInterceptorService.class);
            reactContext.stopService(serviceIntent);
            
            promise.resolve("Servicio interceptor detenido");
        } catch (Exception e) {
            promise.reject("STOP_SERVICE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void openPhoneSettings(Promise promise) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);

            if (getCurrentActivity() != null) {
                getCurrentActivity().startActivity(intent);
                promise.resolve("Abriendo configuración de aplicaciones predeterminadas");
            } else {
                promise.reject("NO_ACTIVITY", "No hay actividad actual disponible");
            }
        } catch (Exception e) {
            promise.reject("OPEN_SETTINGS_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void testSpamNotification(Promise promise) {
        try {
            // Mostrar notificación de prueba
            SpamNotificationManager.showIncomingSpamNotification(
                reactContext,
                "+34 900 123 456 (TEST)"
            );
            promise.resolve("Notificación de prueba enviada");
        } catch (Exception e) {
            promise.reject("TEST_NOTIFICATION_ERROR", e.getMessage());
        }
    }

    private boolean isDefaultDialerApp() {
        try {
            TelecomManager telecomManager = (TelecomManager) reactContext.getSystemService(Context.TELECOM_SERVICE);
            return reactContext.getPackageName().equals(telecomManager.getDefaultDialerPackage());
        } catch (Exception e) {
            return false;
        }
    }
}