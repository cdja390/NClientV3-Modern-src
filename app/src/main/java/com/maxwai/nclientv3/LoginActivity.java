package com.maxwai.nclientv3;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    /**
     * 4.7.1: idle timeout of the WebView login, restarted on every page load. The 4.7.0 60 s total
     * ran out while the user was still passing Cloudflare and typing.
     */
    static final long WEB_LOGIN_TIMEOUT_MS = 180_000;
    private static final int MAX_LOGIN_REDIRECTS = 3;

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
                // Same storage path as the WebView login (cookie jar + AuthStore, expiry from the
                // exp claim). 4.7.1: the refresh token and the returned profile are kept too.
                Login.saveLoginTokens(this, tokens);
                LogUtility.d("Login: native login OK, profile " + (Login.getUser() != null)
                    + ", refresh token " + (tokens.refreshToken != null));
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

    /**
     * 4.7.1: closes the screen like yosefario's finalizeLogin. 4.7.0 stayed on the form with only a
     * status line changed, which read as "stuck after the captcha"; the API key can still be
     * created from this screen later (the button shows while signed in).
     */
    private void onLoginSucceeded() {
        setBusy(false);
        if (isFinishing() || isDestroyed()) return;
        if (passwordField.getText() != null) passwordField.getText().clear();
        loadUser();
        Toast.makeText(this, R.string.login_status_signed_in, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
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

    private int loginRedirects;
    @Nullable
    private String staleAccessToken;

    private void startWebLogin() {
        if (busy) return;
        form.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        loginRedirects = 0;
        // A token left in the WebView by an older session must not count as a new sign-in.
        staleAccessToken = cookieValue(CookieManager.getInstance().getCookie(Utility.getBaseUrl()), Login.LOGIN_COOKIE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (waiter != null) waiter.touch();
                // After the Cloudflare check the site may land on its home page rather than the
                // form; send the user back to /login until signed in (bounded, no loops).
                if (shouldRedirectToLogin(url, loginRedirects, MAX_LOGIN_REDIRECTS)
                    && !hasNewAccessToken()) {
                    loginRedirects++;
                    view.loadUrl(Utility.getBaseUrl() + "login");
                }
            }
        });
        // No trailing slash: /login/ is a 308 to /login on the rebuilt site.
        webView.loadUrl(Utility.getBaseUrl() + "login");
        stopWaiter();
        waiter = new CookieWaiter();
        waiter.start();
    }

    /** Pages of the sign-in flow the WebView may stay on; anything else is sent back to /login. */
    static boolean shouldRedirectToLogin(@Nullable String url, int redirectsDone, int maxRedirects) {
        if (url == null || redirectsDone >= maxRedirects) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !Utility.isSiteHost(host)) return false;
        String path = uri.getPath() == null ? "/" : uri.getPath();
        return !(path.startsWith("/login") || path.startsWith("/register") || path.startsWith("/reset")
            || path.startsWith("/cdn-cgi/"));
    }

    @Nullable
    static String cookieValue(@Nullable String cookieHeader, @NonNull String name) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            if (name.equals(trimmed.substring(0, eq))) {
                String value = trimmed.substring(eq + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    boolean hasNewAccessToken() {
        String token = cookieValue(CookieManager.getInstance().getCookie(Utility.getBaseUrl()), Login.LOGIN_COOKIE);
        return token != null && !token.equals(staleAccessToken);
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
        private volatile long lastActivity = System.currentTimeMillis();

        void touch() {
            lastActivity = System.currentTimeMillis();
        }

        @Override
        public void run() {
            CookieManager manager = CookieManager.getInstance();
            // 4.7.1: match the cookie by name (access_token, set HttpOnly by the site's /auth/login)
            // instead of a substring, and ignore a token that was already there before.
            while (!hasNewAccessToken()) {
                Utility.threadSleep(150);
                if (cancelled || isInterrupted()) return;
                if (System.currentTimeMillis() - lastActivity > WEB_LOGIN_TIMEOUT_MS) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) stopWebLogin(true);
                    });
                    return;
                }
            }
            if (cancelled) return;
            manager.flush();
            applyAllCookies(manager.getCookie(Utility.getBaseUrl()));
            LogUtility.d("Login: web login cookie imported, refresh token "
                + (AuthStore.getRefreshToken(LoginActivity.this) != null));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(LoginActivity.this, R.string.login_status_signed_in, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        }

        private void applyAllCookies(String cookieString) {
            // importCookieHeader persists access_token (cookie jar + AuthStore) and, since 4.7.1,
            // refresh_token (AuthStore), so the sign-in survives the access token's short life.
            Login.importCookieHeader(LoginActivity.this, cookieString);
            User.createUser(LoginActivity.this, null);
        }
    }
}
