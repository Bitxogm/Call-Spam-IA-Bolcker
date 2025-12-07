// LogsModule.java
package com.anonymous.SpamBlockerApp;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import androidx.annotation.NonNull;
import java.util.List;

/**
 * Módulo React Native para acceder a los logs de la app
 */
public class LogsModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "LogsModule";
    private LogsHelper logsHelper;

    public LogsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.logsHelper = new LogsHelper(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Añade un log de tipo INFO
     */
    @ReactMethod
    public void logInfo(String message, Promise promise) {
        try {
            logsHelper.logInfo(message);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LOG_INFO_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Añade un log de tipo ERROR
     */
    @ReactMethod
    public void logError(String message, Promise promise) {
        try {
            logsHelper.logError(message);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LOG_ERROR_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Añade un log de tipo DEBUG
     */
    @ReactMethod
    public void logDebug(String message, Promise promise) {
        try {
            logsHelper.logDebug(message);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LOG_DEBUG_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Añade un log de tipo WARNING
     */
    @ReactMethod
    public void logWarning(String message, Promise promise) {
        try {
            logsHelper.logWarning(message);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LOG_WARNING_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene todos los logs como string formateado
     */
    @ReactMethod
    public void getLogsAsString(Promise promise) {
        try {
            String logs = logsHelper.getLogsAsString();
            promise.resolve(logs);
        } catch (Exception e) {
            promise.reject("GET_LOGS_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene todos los logs como array de objetos
     */
    @ReactMethod
    public void getLogs(Promise promise) {
        try {
            List<LogsHelper.LogEntry> logs = logsHelper.getLogsList();
            WritableArray logsArray = Arguments.createArray();

            for (LogsHelper.LogEntry log : logs) {
                WritableMap logMap = Arguments.createMap();
                logMap.putString("timestamp", log.timestamp);
                logMap.putString("level", log.level);
                logMap.putString("message", log.message);
                logsArray.pushMap(logMap);
            }

            promise.resolve(logsArray);
        } catch (Exception e) {
            promise.reject("GET_LOGS_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Limpia todos los logs
     */
    @ReactMethod
    public void clearLogs(Promise promise) {
        try {
            logsHelper.clearLogs();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_LOGS_ERROR", e.getMessage(), e);
        }
    }
}
