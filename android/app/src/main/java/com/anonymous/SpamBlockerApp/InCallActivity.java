package com.anonymous.SpamBlockerApp;

import android.app.Activity;
import android.os.Bundle;
import android.telecom.Call;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * Pantalla de llamada básica
 *
 * Muestra información de la llamada en curso y botones para:
 * - Colgar
 * - Mute/Unmute
 * - Speaker On/Off
 *
 * Solo aparece para llamadas normales (no spam)
 */
public class InCallActivity extends Activity {
    private static final String TAG = "InCallActivity";

    private TextView callInfoTextView;
    private Button hangupButton;
    private Button muteButton;
    private Button speakerButton;

    private Call currentCall;
    private boolean isMuted = false;
    private boolean isSpeakerOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mostrar sobre lockscreen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_in_call);

        // Inicializar vistas
        callInfoTextView = findViewById(R.id.callInfoTextView);
        hangupButton = findViewById(R.id.hangupButton);
        muteButton = findViewById(R.id.muteButton);
        speakerButton = findViewById(R.id.speakerButton);

        // Obtener llamada actual
        currentCall = SpamCallService.getCurrentCall();

        if (currentCall == null) {
            Log.e(TAG, "❌ No hay llamada activa, cerrando InCallActivity");
            finish();
            return;
        }

        // Actualizar UI con info de la llamada
        updateCallInfo();

        // Configurar botones
        setupButtons();

        // Registrar callback para cambios de estado
        currentCall.registerCallback(callCallback);

        Log.d(TAG, "✅ InCallActivity iniciada");
    }

    private void updateCallInfo() {
        if (currentCall == null) return;

        String number = getCallerNumber(currentCall);
        int state = currentCall.getDetails().getState();
        String stateName = getCallStateName(state);

        String info = "📞 " + number + "\n" + stateName;
        callInfoTextView.setText(info);
    }

    private void setupButtons() {
        // Botón colgar
        hangupButton.setOnClickListener(v -> {
            Log.d(TAG, "🔴 Colgando llamada desde InCallActivity");
            if (currentCall != null) {
                currentCall.disconnect();
            }
            finish();
        });

        // Botón mute
        muteButton.setOnClickListener(v -> {
            if (currentCall == null) return;

            isMuted = !isMuted;
            currentCall.setMuted(isMuted);
            muteButton.setText(isMuted ? "🔇 Unmute" : "🎤 Mute");

            Log.d(TAG, "🎤 Mute: " + isMuted);
        });

        // Botón speaker
        speakerButton.setOnClickListener(v -> {
            if (currentCall == null) return;

            isSpeakerOn = !isSpeakerOn;
            // Nota: setAudioRoute requiere APIs adicionales
            // Por ahora, solo toggle visual
            speakerButton.setText(isSpeakerOn ? "🔊 Earpiece" : "📢 Speaker");

            Log.d(TAG, "🔊 Speaker: " + isSpeakerOn);
        });
    }

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            runOnUiThread(() -> {
                updateCallInfo();

                // Si la llamada se desconectó, cerrar actividad
                if (state == Call.STATE_DISCONNECTED) {
                    Log.d(TAG, "💀 Llamada desconectada, cerrando InCallActivity");
                    finish();
                }
            });
        }
    };

    private String getCallerNumber(Call call) {
        if (call.getDetails().getHandle() != null) {
            return call.getDetails().getHandle().getSchemeSpecificPart();
        }
        return "Desconocido";
    }

    private String getCallStateName(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NUEVA";
            case Call.STATE_RINGING: return "SONANDO";
            case Call.STATE_DIALING: return "MARCANDO";
            case Call.STATE_ACTIVE: return "ACTIVA";
            case Call.STATE_HOLDING: return "EN ESPERA";
            case Call.STATE_DISCONNECTED: return "DESCONECTADA";
            default: return "DESCONOCIDO (" + state + ")";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (currentCall != null) {
            currentCall.unregisterCallback(callCallback);
        }

        Log.d(TAG, "💀 InCallActivity destruida");
    }
}
