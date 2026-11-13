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
 * Sign-in flow follows EHentaiLoginActivity from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0), rewritten without GeckoView: a system
 * WebView on the forums login with cookie polling, plus a paste-cookies alternative.
 * Added for NClientV3 Modern 4.7.0. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.activities.GeneralActivity;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Signs in to e-hentai. The forums login page runs in a WebView; once the browser holds
 * {@code ipb_member_id} and {@code ipb_pass_hash} they are copied into the e-hentai account store
 * and ExHentai is probed for its {@code igneous} cookie. Users who already have the cookies can
 * paste them instead. The password never passes through the app.
 */
public class EhLoginActivity extends GeneralActivity {
    private static final long POLL_MS = 1500;
    private static final String[] WEB_COOKIE_URLS = {
        "https://forums.e-hentai.org/", "https://e-hentai.org/", "https://exhentai.org/"};
    private static final String[] COOKIE_NAMES = {
        "ipb_member_id", "ipb_pass_hash", "igneous", "ipb_session_id", "sk", "star", "yay", "hath_perks"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView web;
    private ProgressBar progress;
    private boolean finishing;
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (checkCookies()) return;
            handler.postDelayed(this, POLL_MS);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eh_login);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.eh_login_title);
        }
        progress = findViewById(R.id.eh_login_progress);
        web = findViewById(R.id.eh_login_web);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                // Stay on e-hentai's own pages; anything else is not part of the sign-in.
                return host == null || !EhUrls.isEhHost(host);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                checkCookies();
            }
        });
        findViewById(R.id.eh_login_paste).setOnClickListener(v -> showPasteDialog());
        findViewById(R.id.eh_login_check).setOnClickListener(v -> {
            if (!checkCookies()) Toast.makeText(this, R.string.eh_login_not_yet, Toast.LENGTH_SHORT).show();
        });
        web.loadUrl(EhUrls.FORUMS_LOGIN);
        handler.postDelayed(poll, POLL_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        io.shutdownNow();
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** True once the account cookies were found and stored. */
    private boolean checkCookies() {
        if (finishing) return true;
        CookieManager cm = CookieManager.getInstance();
        StringBuilder all = new StringBuilder();
        for (String url : WEB_COOKIE_URLS) {
            String c = cm.getCookie(url);
            if (c != null && !c.isEmpty()) all.append(c).append("; ");
        }
        Map<String, String> c = EhAuth.parseCookieHeader(all.toString());
        if (!EhAuth.store(this, c.get("ipb_member_id"), c.get("ipb_pass_hash"), c.get("igneous"))) return false;
        onSignedIn();
        return true;
    }

    private void showPasteDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint(R.string.eh_login_paste_hint);
        input.setMinLines(3);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_login_paste)
            .setMessage(R.string.eh_login_paste_message)
            .setView(box)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                if (EhAuth.storeFromCookieHeader(this, input.getText().toString())) onSignedIn();
                else Toast.makeText(this, R.string.eh_login_paste_invalid, Toast.LENGTH_LONG).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void onSignedIn() {
        if (finishing) return;
        finishing = true;
        handler.removeCallbacks(poll);
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            boolean ex = EhAuth.hasExhentaiAccess(this);
            if (!ex) {
                try {
                    String igneous = EhClient.fetchIgneous(this);
                    if (igneous != null) {
                        EhAuth.storeIgneous(this, igneous);
                        ex = true;
                    }
                } catch (Exception ignore) {
                    // No ExHentai access is normal for new accounts.
                }
            }
            final boolean exAccess = ex;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Toast.makeText(this, exAccess ? R.string.eh_login_success_ex : R.string.eh_login_success,
                    Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    /** Expires the e-hentai cookies the sign-in WebView kept (other sites are left alone). */
    public static void clearWebCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            for (String url : WEB_COOKIE_URLS) {
                String domain = url.contains("exhentai") ? ".exhentai.org" : ".e-hentai.org";
                for (String name : COOKIE_NAMES) {
                    cm.setCookie(url, name + "=; Max-Age=0; Path=/; Domain=" + domain);
                    cm.setCookie(url, name + "=; Max-Age=0; Path=/");
                }
            }
            cm.flush();
        } catch (Throwable ignore) {
            // No WebView installed: nothing was stored there either.
        }
    }
}
