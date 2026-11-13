package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.components.CookieInterceptor;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.NetworkRecoveryHelper;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;

import okhttp3.Cookie;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class CustomInterceptor implements Interceptor {
    private final boolean logRequests;
    private static final CookieInterceptor.Manager MANAGER = new CookieInterceptor.Manager() {
        boolean tokenFound = false;

        @Override
        public void applyCookie(String key, String value) {
            Cookie cookie = Cookie.parse(Login.BASE_HTTP_URL, key + "=" + value + "; Max-Age=31449600; Path=/; SameSite=Lax");
            Global.client.cookieJar().saveFromResponse(Login.BASE_HTTP_URL, Collections.singletonList(cookie));
            tokenFound |= isClearanceCookie(key);
        }

        @Override
        public boolean endInterceptor() {
            if (tokenFound) return true;
            String cookies = CookieManager.getInstance().getCookie(Utility.getBaseUrl());
            if (cookies == null) return false;
            return hasClearanceCookie(cookies);
        }

        @Override
        public void onFinish() {
            // Next challenge starts from scratch (the WebView cookies are wiped before it).
            tokenFound = false;
        }
    };
    @Nullable
    private final Context context;

    /**
     * 4.7.1: the rebuilt nhentai site never sets the old Django {@code csrftoken}, so waiting for
     * it kept the Cloudflare WebView on screen forever after the challenge had been passed (the
     * "stays on the nhentai web page" report). {@code cf_clearance} is what passing the challenge
     * leaves behind; csrftoken is still accepted for older mirrors.
     */
    static boolean isClearanceCookie(@Nullable String name) {
        return "cf_clearance".equals(name) || "csrftoken".equals(name);
    }

    static boolean hasClearanceCookie(@Nullable String cookieHeader) {
        if (cookieHeader == null) return false;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0 && eq < trimmed.length() - 1 && isClearanceCookie(trimmed.substring(0, eq)))
                return true;
        }
        return false;
    }

    public CustomInterceptor(@Nullable Context context, boolean logRequests) {
        this.context = context;
        this.logRequests = logRequests;
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        boolean rec = request.header("rec") != null;
        if (logRequests) LogUtility.d("Requested url: " + request.url());
        Request.Builder r = request.newBuilder();
        r.removeHeader("rec");
        r.addHeader("User-Agent", "NClient/4.5.0 (https://github.com/ILFforever/NClientV3)");
        Response response;
        try {
            response = chain.proceed(r.build());
        } catch (IOException e) {
            if (rec) throw e;
            if (!NetworkRecoveryHelper.tryBeginReload()) throw e;
            try {
                if (context != null) Global.reloadHttpClient(context);
            } finally {
                NetworkRecoveryHelper.endReload();
            }
            return Global.client.newCall(request.newBuilder().addHeader("rec", "1").build()).execute();
        }
        boolean apiV2Request = request.url().encodedPath().startsWith("/api/v2/");
        if (response.code() == HttpURLConnection.HTTP_UNAVAILABLE ||
            (response.code() == HttpURLConnection.HTTP_FORBIDDEN && !apiV2Request)) {
            if (rec) return response;
            if (!NetworkRecoveryHelper.tryBeginReload()) return response;
            try {
                CookieManager.getInstance().removeAllCookies(null);
                CookieInterceptor interceptor = new CookieInterceptor(MANAGER);
                interceptor.intercept();
                if (context != null) Global.reloadHttpClient(context);
            } finally {
                NetworkRecoveryHelper.endReload();
            }
            response.close();
            response = Global.client.newCall(request.newBuilder().addHeader("rec", "1").build()).execute();
        }
        return response;
    }
}
