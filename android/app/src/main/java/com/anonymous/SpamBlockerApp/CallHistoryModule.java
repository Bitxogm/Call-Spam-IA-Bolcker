// CallHistoryModule.java
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
 * Módulo React Native para acceder al historial de llamadas spam
 */
public class CallHistoryModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "CallHistoryModule";
    private CallHistoryHelper historyHelper;

    public CallHistoryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.historyHelper = new CallHistoryHelper(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Obtiene el historial de llamadas spam
     */
    @ReactMethod
    public void getHistory(Promise promise) {
        try {
            List<CallHistoryHelper.SpamCallRecord> history = historyHelper.getHistoryList();
            WritableArray historyArray = Arguments.createArray();

            for (CallHistoryHelper.SpamCallRecord record : history) {
                WritableMap recordMap = Arguments.createMap();
                recordMap.putString("timestamp", record.timestamp);
                recordMap.putString("number", record.number);
                recordMap.putString("reason", record.reason);
                recordMap.putString("action", record.action);
                historyArray.pushMap(recordMap);
            }

            promise.resolve(historyArray);
        } catch (Exception e) {
            promise.reject("GET_HISTORY_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Obtiene el total de llamadas spam
     */
    @ReactMethod
    public void getTotalSpamCalls(Promise promise) {
        try {
            int total = historyHelper.getTotalSpamCalls();
            promise.resolve(total);
        } catch (Exception e) {
            promise.reject("GET_TOTAL_ERROR", e.getMessage(), e);
        }
    }

    /**
     * Limpia el historial
     */
    @ReactMethod
    public void clearHistory(Promise promise) {
        try {
            historyHelper.clearHistory();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_HISTORY_ERROR", e.getMessage(), e);
        }
    }
}
