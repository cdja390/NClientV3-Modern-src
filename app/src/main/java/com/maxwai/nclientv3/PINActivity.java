package com.maxwai.nclientv3;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.maxwai.nclientv3.components.activities.GeneralActivity;

import java.util.concurrent.Executor;

public class PINActivity extends GeneralActivity {
    /** Intent to start instead of MainActivity once unlocked (used by ShareReceiverActivity). */
    public static final String EXTRA_FORWARD = "com.maxwai.nclientv3.PIN_FORWARD";
    private SharedPreferences preferences;
    private boolean authSuccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);
        setContentView(R.layout.activity_pin);
        preferences = getSharedPreferences("Settings", 0);
        if (!hasPin()) {
            finish();
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
            executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        getString(R.string.auth_error),
                        Toast.LENGTH_SHORT)
                    .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                authSuccess = true;
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(),
                        getString(R.string.auth_error),
                        Toast.LENGTH_SHORT)
                    .show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_title))
            .setSubtitle(getString(R.string.auth_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
            .build();

        Button unlockButton = findViewById(R.id.unlockButton);
        unlockButton.setOnClickListener(view -> biometricPrompt.authenticate(promptInfo));
        unlockButton.performClick();
    }

    /** True when the device-credential lock is enabled in settings. */
    public static boolean isLocked(Context context) {
        return context.getSharedPreferences("Settings", 0)
            .getBoolean(context.getString(R.string.preference_key_has_credentials), false);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPin() {
        return preferences.getBoolean(getString(R.string.preference_key_has_credentials), false);
    }

    @Override
    public void finish() {
        Intent i = forwardIntent();
        if (i == null) i = new Intent(this, MainActivity.class);
        if (!hasPin() || authSuccess) startActivity(i);
        authSuccess = false;
        super.finish();
    }

    private Intent forwardIntent() {
        Intent forward;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            forward = getIntent().getParcelableExtra(EXTRA_FORWARD, Intent.class);
        } else {
            forward = getIntent().getParcelableExtra(EXTRA_FORWARD);
        }
        // PINActivity is exported: only forward to the screens a shared link can open.
        if (forward == null || forward.getComponent() == null
            || !getPackageName().equals(forward.getComponent().getPackageName())) return null;
        String cls = forward.getComponent().getClassName();
        if (!cls.equals(MainActivity.class.getName()) && !cls.equals(GalleryActivity.class.getName())
            && !cls.equals(RandomActivity.class.getName())
            && !cls.equals(TagFilterActivity.class.getName())) return null;
        forward.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return forward;
    }
}
