package com.anonymous.SpamBlockerApp;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

public class SpamCallService extends InCallService {
    private static final String TAG = "SpamCallService";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        
        String callerNumber = getCallerNumber(call);
        Log.d(TAG, "📞 LLAMADA DETECTADA: " + callerNumber);
        
        // Por ahora solo loggeamos, no hacemos nada más
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        String callerNumber = getCallerNumber(call);
        Log.d(TAG, "📞 Llamada finalizada: " + callerNumber);
    }

    private String getCallerNumber(Call call) {
        if (call.getDetails() != null && call.getDetails().getHandle() != null) {
            return call.getDetails().getHandle().getSchemeSpecificPart();
        }
        return "Número desconocido";
    }
}