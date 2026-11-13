package com.maxwai.nclientv3;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.views.TurnstileDialog;
import com.maxwai.nclientv3.loginapi.AuthApi;
import com.maxwai.nclientv3.loginapi.LoadTags;
import com.maxwai.nclientv3.loginapi.PowSolver;
import com.maxwai.nclientv3.loginapi.User;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Sign-in screen.
 * <p>
 * 4.7.0 (NClientV3 Modern): the primary path is nhentai's native login - username and password
 * posted to /api/v2/auth/login together with a proof of work and a Cloudflare Turnstile token
 * (flow adapted from yosefario-dev/NClientV3, Apache-2.0, see docs/PATCHED_FILES.md). The old
 * WebView login stays as a fallback and now gives up after 60 seconds instead of polling forever.
 * The API-key screen is reachable from here, and once signed in an API key can optionally be
 * created for this device.
 */
public class LoginActivity extends GeneralActivity {
    static final long WEB_LOGIN_TIMEOUT_MS = 60_000;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    CookieWaiter waiter;
    WebView webView;
    private View form;
    private EditText usernameField;
    private EditText passwordField;
    private TextView statusView;
    private ProgressBar progress;
    private MaterialButton signInButton;
    private MaterialButton createKeyButton;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_login);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        webView = findViewById(R.id.webView);
        form = findViewById(R.id.login_form);
        usernameField = findViewById(R.id.username);
        passwordField = findViewById(R.id.password);
        statusView = findViewById(R.id.status);
        progress = findViewById(R.id.login_progress);
        signInButton = findViewById(R.id.sign_in_button);
        createKeyButton = findViewById(R.id.create_key_button);

        signInButton.setOnClickListener(v -> onSignInClicked());
        createKeyButton.setOnClickListener(v -> onCreateKeyClicked());
        findViewById(R.id.use_api_key_button).setOnClickListener(v ->
            startActivity(new Intent(this, ApiKeyActivity.class)));
        findViewById(R.id.web_login_button).setOnClickListener(v -> startWebLogin());
        passwordField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSignInClicked();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.getVisibility() == View.VISIBLE) stopWebLogin(false);
                else finish();
            }
        });
        refreshSignedInState();
    }

    @Override
    protected void onDestroy() {
        stopWaiter();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshSignedInState() {
        boolean signedIn = Login.getUserTokenAuthorizationHeader() != null;
        createKeyButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (signedIn && !busy) setStatus(getString(R.string.login_status_already_signed_in));
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!value);
        createKeyButton.setEnabled(!value);
        usernameField.setEnabled(!value);
        passwordField.setEnabled(!value);
    }

    private void setStatus(@NonNull String message) {
        statusView.setText(message);
    }

    private static String textOf(@Nullable EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    // ---- native login ----

    private void onSignInClicked() {
        if (busy) return;
        String username = textOf(usernameField);
        String password = passwordField.getText() == null ? "" : passwordField.getText().toString();
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            setStatus(getString(R.string.login_status_missing_fields));
            return;
        }
        requestCaptcha(false, token -> runLogin(username, password, token));
    }

    private interface TokenConsumer {
        void accept(@NonNull String token);
    }

    private void requestCaptcha(boolean secondCheck, @NonNull TokenConsumer onToken) {
        setBusy(true);
        setStatus(getString(R.string.login_status_captcha_loading));
        io.execute(() -> {
            String siteKey;
            try {
                siteKey = AuthApi.fetchCaptchaSiteKey(this);
            } catch (IOException e) {
                LogUtility.e("Login: captcha info failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus(getString(R.string.login_status_captcha_error, describe(e)));
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setStatus(getString(R.string.login_status_verify));
                new TurnstileDialog(this, siteKey, new TurnstileDialog.Callback() {
                    @Override
                    public void onToken(@NonNull String token) {
                        onToken.accept(token);
                    }

                    @Override
                    public void onCancelled() {
                        setBusy(false);
                        setStatus(getString(R.string.login_status_captcha_cancelled));
                    }
                }).show(secondCheck);
            });
        });
    }

    private void runLogin(String username, String password, String captchaToken) {
        setStatus(getString(R.string.login_status_solving));
        io.execute(() -> {
            try {
                AuthApi.PowChallenge pow = AuthApi.fetchPowChallenge(this, AuthApi.ACTION_LOGIN);
                long t0 = System.nanoTime();
                String nonce = PowSolver.solve(pow.challenge, pow.difficulty);
                LogUtility.d("Login: PoW difficulty " + pow.difficulty + " solved in "
                    + ((System.nanoTime() - t0) / 1_000_000) + " ms");
                runOnUiThread(() -> setStatus(getString(R.string.login_status_signing_in)));
                AuthApi.LoginTokens tokens = AuthApi.login(this, username, password, pow, nonce, captchaToken);
                // Same storage path as the WebView login: cookie jar + AuthStore.saveUserToken,
                // expiry read from the token's exp claim.
                Login.importCookieHeader(this, Login.LOGIN_COOKIE + "=" + tokens.accessToken);
                if (AuthStore.getUserToken(this) == null) {
                    // importCookieHeader only stores when the cookie parses; make sure it is kept.
                    AuthStore.saveUserToken(this, tokens.accessToken,
                        System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(30));
                    Login.restorePersistedAccessToken(this);
                }
                runOnUiThread(this::onLoginSucceeded);
            } catch (AuthApi.ApiException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus(getString(R.string.login_status_login_failed, describe(e)));
                });
            } catch (IOException | RuntimeException e) {
                LogUtility.e("Login: native login failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus(getString(R.string.login_status_login_failed, describe(e)));
                });
            }
        });
    }

    private void onLoginSucceeded() {
        setBusy(false);
        if (passwordField.getText() != null) passwordField.getText().clear();
        loadUser();
        setStatus(getString(R.string.login_status_signed_in));
        Toast.makeText(this, R.string.login_status_signed_in, Toast.LENGTH_SHORT).show();
        refreshSignedInState();
        setStatus(getString(R.string.login_status_signed_in_key_hint));
    }

    private void loadUser() {
        android.content.Context app = getApplicationContext();
        User.createUser(app, loaded -> {
            if (loaded != null) new LoadTags(app).start();
        });
    }

    // ---- optional API key ----

    private void onCreateKeyClicked() {
        if (busy) return;
        requestCaptcha(true, token -> {
            setStatus(getString(R.string.login_status_creating_key));
            io.execute(() -> {
                try {
                    AuthApi.PowChallenge pow = AuthApi.fetchPowChallenge(this, AuthApi.ACTION_API_KEY);
                    String nonce = PowSolver.solve(pow.challenge, pow.difficulty);
                    String key = AuthApi.createApiKey(this, "NClientV3 Modern - " + Build.MODEL, pow, nonce, token);
                    AuthStore.saveApiKey(this, key, true);
                    runOnUiThread(() -> {
                        setBusy(false);
                        setStatus(getString(R.string.login_status_key_saved));
                    });
                } catch (IOException | RuntimeException e) {
                    LogUtility.e("Login: API key creation failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        setBusy(false);
                        setStatus(getString(R.string.login_status_key_failed, describe(e)));
                    });
                }
            });
        });
    }

    @NonNull
    private String describe(@NonNull Exception e) {
        if (e instanceof AuthApi.ApiException) {
            AuthApi.ApiException api = (AuthApi.ApiException) e;
            if (api.isCaptchaRejected()) return getString(R.string.login_error_captcha);
            if (api.httpCode == 401) return getString(R.string.login_error_credentials);
            if (api.httpCode == 429) return getString(R.string.login_error_rate_limited);
            if (api.error != null && !api.error.isEmpty()) return api.error;
            return getString(R.string.login_error_http, api.httpCode);
        }
        if (e instanceof IOException) return getString(R.string.login_error_network);
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ---- WebView fallback (pre-4.7 behaviour plus a timeout) ----

    private void startWebLogin() {
        if (busy) return;
        form.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(Utility.getBaseUrl() + "login/");
        stopWaiter();
        waiter = new CookieWaiter();
        waiter.start();
    }

    private void stopWebLogin(boolean timedOut) {
        stopWaiter();
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        if (timedOut) setStatus(getString(R.string.login_web_timeout));
    }

    private void stopWaiter() {
        if (waiter != null) {
            waiter.cancelled = true;
            if (waiter.isAlive()) waiter.interrupt();
        }
        waiter = null;
    }

    class CookieWaiter extends Thread {
        // Utility.threadSleep swallows the interrupt, so cancellation also needs a flag.
        volatile boolean cancelled;

        @Override
        public void run() {
            CookieManager manager = CookieManager.getInstance();
            long deadline = System.currentTimeMillis() + WEB_LOGIN_TIMEOUT_MS;
            String cookies = "";
            while (cookies == null || !cookies.contains("access_token")) {
                Utility.threadSleep(100);
                if (cancelled || isInterrupted()) return;
                if (System.currentTimeMillis() > deadline) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) stopWebLogin(true);
                    });
                    return;
                }
                cookies = manager.getCookie(Utility.getBaseUrl());
            }
            if (cancelled) return;
            applyAllCookies(cookies);
            runOnUiThread(LoginActivity.this::finish);
        }

        private void applyAllCookies(String cookieString) {
            Login.importCookieHeader(LoginActivity.this, cookieString);
            User.createUser(LoginActivity.this, null);
        }
    }
}
