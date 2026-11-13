/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors
 * Copyright 2026 NClientV3-Modern contributors (modifications)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Based on components/views/TurnstileDialog.java from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * Modified for NClientV3 Modern: system WebView User-Agent (no app UA override), navigation
 * limited to the inline page and challenges.cloudflare.com, load timeout. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.components.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.maxwai.nclientv3.R;

/**
 * Shows Cloudflare Turnstile in a small WebView and hands the single-use token back. The widget
 * is rendered from an inline page whose base URL is nhentai.net, the domain the site key accepts.
 */
public final class TurnstileDialog {

    public interface Callback {
        void onToken(@NonNull String token);

        void onCancelled();
    }

    private static final String TURNSTILE_API_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js";
    private static final String BASE_URL = "https://nhentai.net/";
    private static final long LOAD_TIMEOUT_MS = 30_000;

    private final Activity activity;
    private final String siteKey;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private AlertDialog dialog;
    private WebView webView;
    private CircularProgressIndicator progress;
    private TextView errorView;
    private boolean delivered;
    private boolean ready;

    public TurnstileDialog(@NonNull Activity activity, @NonNull String siteKey, @NonNull Callback callback) {
        this.activity = activity;
        this.siteKey = siteKey;
        this.callback = callback;
    }

    public void show(boolean secondCheck) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_turnstile, null);
        TextView title = view.findViewById(R.id.turnstile_dialog_title);
        TextView subtitle = view.findViewById(R.id.turnstile_dialog_subtitle);
        title.setText(secondCheck ? R.string.turnstile_title_second : R.string.turnstile_title);
        subtitle.setText(secondCheck ? R.string.turnstile_subtitle_second : R.string.turnstile_subtitle);
        progress = view.findViewById(R.id.turnstile_progress);
        errorView = view.findViewById(R.id.turnstile_error);
        errorView.setOnClickListener(v -> reload());
        webView = view.findViewById(R.id.turnstile_webview);
        setupWebView();

        dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener(d -> onDismiss())
            .create();
        dialog.show();
        loadWidget();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        webView.setBackgroundColor(Color.TRANSPARENT);
        // 4.7.1: the widget lives in a challenges.cloudflare.com frame inside an nhentai.net page;
        // without third-party cookies it can keep re-verifying instead of handing back a token.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri == null ? null : uri.getHost();
                // Only Cloudflare's challenge frames may navigate; everything else stays put.
                return host == null || !host.endsWith("challenges.cloudflare.com");
            }
        });
        webView.addJavascriptInterface(new Bridge(), "NClientBridge");
    }

    private void loadWidget() {
        ready = false;
        int night = activity.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        String cfTheme = night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
        String html = "<!DOCTYPE html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>html,body{margin:0;padding:0;background:transparent;"
            + "display:flex;justify-content:center;align-items:center;height:100%}</style>"
            + "<script>"
            + "function onCaptchaSuccess(t){try{NClientBridge.onToken(t);}catch(e){}}"
            + "function onCaptchaError(c){try{NClientBridge.onError(String(c));}catch(e){}}"
            + "function onCaptchaExpired(){try{NClientBridge.onExpired();}catch(e){}}"
            + "function onTurnstileLoad(){try{NClientBridge.onReady();"
            + "window.__w=turnstile.render('#cf',{sitekey:'" + escape(siteKey) + "',"
            + "theme:'" + cfTheme + "',callback:onCaptchaSuccess,'error-callback':onCaptchaError,"
            + "'expired-callback':onCaptchaExpired});}catch(e){NClientBridge.onError('render');}}"
            + "function refreshWidget(){try{if(window.turnstile)turnstile.reset(window.__w);}catch(e){}}"
            + "</script>"
            + "<script src=\"" + TURNSTILE_API_URL + "?onload=onTurnstileLoad&render=explicit\" async defer></script>"
            + "</head><body><div id=\"cf\"></div></body></html>";
        webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null);
        handler.postDelayed(() -> {
            if (!ready && !delivered && webView != null) showError();
        }, LOAD_TIMEOUT_MS);
    }

    private void reload() {
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (webView == null) return;
        if (ready) webView.evaluateJavascript("refreshWidget();", null);
        else loadWidget();
    }

    private void onDismiss() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("NClientBridge");
            webView.destroy();
            webView = null;
        }
        if (!delivered) callback.onCancelled();
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady() {
            activity.runOnUiThread(() -> {
                ready = true;
                if (progress != null) progress.setVisibility(View.GONE);
            });
        }

        @JavascriptInterface
        public void onToken(@Nullable String token) {
            if (token == null || token.isEmpty()) return;
            activity.runOnUiThread(() -> {
                if (delivered) return;
                delivered = true;
                callback.onToken(token);
                if (dialog != null) dialog.dismiss();
            });
        }

        @JavascriptInterface
        public void onError(@Nullable String code) {
            activity.runOnUiThread(TurnstileDialog.this::showError);
        }

        @JavascriptInterface
        public void onExpired() {
            activity.runOnUiThread(TurnstileDialog.this::reload);
        }
    }

    private void showError() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("<", "").replace(">", "");
    }
}
