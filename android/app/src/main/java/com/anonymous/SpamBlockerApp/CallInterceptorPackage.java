// CallInterceptorPackage.java
package com.anonymous.SpamBlockerApp;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallInterceptorPackage implements ReactPackage {

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new CallInterceptorModule(reactContext));
        modules.add(new BlacklistModule(reactContext));     // ✅ Módulo de blacklist
        modules.add(new ContactsModule(reactContext));      // ✅ Módulo de contactos/whitelist
        modules.add(new AnswerHangupModule(reactContext));  // ✅ Módulo de answer+hangup
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}