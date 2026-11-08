// BuildInfoModule.java
package com.anonymous.SpamBlockerApp;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

/** Fecha de compilación del APK (BuildConfig.BUILD_TIME, la pone Gradle en cada build). */
public class BuildInfoModule extends ReactContextBaseJavaModule {

    public BuildInfoModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return "BuildInfoModule";
    }

    @ReactMethod
    public void getInfo(Promise promise) {
        WritableMap info = Arguments.createMap();
        info.putString("buildTime", BuildConfig.BUILD_TIME);
        info.putBoolean("debug", BuildConfig.DEBUG);
        promise.resolve(info);
    }
}
