package com.anonymous.SpamBlockerApp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DialerActivity extends Activity {
    private EditText phoneNumberInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Crear UI básica programáticamente
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        TextView title = new TextView(this);
        title.setText("SpamBlocker Dialer");
        title.setTextSize(20);
        
        phoneNumberInput = new EditText(this);
        phoneNumberInput.setHint("Número de teléfono");
        
        Button callButton = new Button(this);
        callButton.setText("Llamar");
        callButton.setOnClickListener(v -> makeCall());
        
        layout.addView(title);
        layout.addView(phoneNumberInput);
        layout.addView(callButton);
        
        setContentView(layout);
        
        // Manejar intent de marcado si viene de sistema
        handleIncomingIntent(getIntent());
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            Uri data = intent.getData();
            
            if (Intent.ACTION_DIAL.equals(action) && data != null) {
                String phoneNumber = data.getSchemeSpecificPart();
                phoneNumberInput.setText(phoneNumber);
            }
        }
    }

    private void makeCall() {
        String phoneNumber = phoneNumberInput.getText().toString().trim();
        if (!phoneNumber.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
        }
    }
}