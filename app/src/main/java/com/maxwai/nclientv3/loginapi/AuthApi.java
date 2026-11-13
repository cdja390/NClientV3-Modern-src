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
 * Based on loginapi/AuthApi.java and loginapi/KeyProvisioner.java from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * Modified for NClientV3 Modern: own OkHttp chain (keeps the NClient/4.5.0 User-Agent, never
 * attaches the stored API key to the login call), MiniJson instead of org.json, no response
 * bodies in logs, localised errors are produced by the caller. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.loginapi;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.MiniJson;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * nhentai's native login: GET /api/v2/captcha, GET /api/v2/pow, POST /api/v2/auth/login,
 * POST /api/v2/user/keys. Blocking; call from a background thread.
 */
public final class AuthApi {
    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_API_KEY = "api_key";
    /** Same identification the rest of the app sends to /api/v2/ (kept at 4.5.0 on purpose). */
    static final String USER_AGENT = "NClient/4.5.0 (https://github.com/ILFforever/NClientV3)";
    private static final MediaType JSON = MediaType.get("application/json");

    private AuthApi() {
    }

    public static final class PowChallenge {
        public final String challenge;
        public final int difficulty;

        PowChallenge(String challenge, int difficulty) {
            this.challenge = challenge;
            this.difficulty = difficulty;
        }
    }

    public static final class LoginTokens {
        public final String accessToken;
        @Nullable
        public final String refreshToken;

        LoginTokens(String accessToken, @Nullable String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static class ApiException extends IOException {
        public final int httpCode;
        @Nullable
        public final String error;

        public ApiException(int httpCode, @Nullable String error) {
            super("HTTP " + httpCode + (error == null ? "" : ": " + error));
            this.httpCode = httpCode;
            this.error = error;
        }

        public boolean isCaptchaRejected() {
            return error != null && error.toLowerCase(java.util.Locale.ROOT).contains("captcha");
        }
    }

    /**
     * A derived client: same cookie jar (cf_clearance) and connection pool as the app client but
     * without its interceptors, so a stored API key is never sent along with a password login and
     * a 401 for a wrong password cannot mark that key invalid.
     */
    @NonNull
    private static OkHttpClient client(@NonNull Context context) {
        OkHttpClient.Builder b = Global.getClient(context).newBuilder();
        b.interceptors().clear();
        b.networkInterceptors().clear();
        b.addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()));
        b.callTimeout(45, TimeUnit.SECONDS);
        return b.build();
    }

    @NonNull
    public static String fetchCaptchaSiteKey(@NonNull Context context) throws IOException {
        Map<String, Object> json = getJson(context, Utility.getBaseUrl() + "api/v2/captcha");
        String key = MiniJson.optString(json, "site_key");
        if (key == null || key.isEmpty()) throw new IOException("Malformed CAPTCHA response");
        return key;
    }

    @NonNull
    public static PowChallenge fetchPowChallenge(@NonNull Context context, @NonNull String action) throws IOException {
        Map<String, Object> json = getJson(context, Utility.getBaseUrl() + "api/v2/pow?action=" + action);
        String challenge = MiniJson.optString(json, "challenge");
        long difficulty = MiniJson.optLong(json, "difficulty", -1);
        if (challenge == null || difficulty < 0) throw new IOException("Malformed PoW response");
        return new PowChallenge(challenge, (int) difficulty);
    }

    @NonNull
    public static LoginTokens login(@NonNull Context context, @NonNull String username, @NonNull String password,
                                    @NonNull PowChallenge pow, @NonNull String nonce,
                                    @NonNull String captchaToken) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("pow_challenge", pow.challenge);
        body.put("pow_nonce", nonce);
        body.put("captcha_response", captchaToken);
        Map<String, Object> json = postJson(context, Utility.getBaseUrl() + "api/v2/auth/login", body, null);
        LoginTokens tokens = parseTokens(json);
        if (tokens == null) throw new IOException("Login response carried no access token");
        return tokens;
    }

    /** Parses the TokenResponse of /api/v2/auth/login (also tolerates a nested "tokens" object). */
    @Nullable
    @SuppressWarnings("unchecked")
    public static LoginTokens parseTokens(@Nullable Map<String, Object> json) {
        if (json == null) return null;
        String access = MiniJson.optString(json, "access_token");
        String refresh = MiniJson.optString(json, "refresh_token");
        Object nested = json.get("tokens");
        if ((access == null || access.isEmpty()) && nested instanceof Map) {
            Map<String, Object> t = (Map<String, Object>) nested;
            access = MiniJson.optString(t, "access_token");
            if (access == null) access = MiniJson.optString(t, "access");
            refresh = MiniJson.optString(t, "refresh_token");
            if (refresh == null) refresh = MiniJson.optString(t, "refresh");
        }
        if (access == null || access.trim().isEmpty()) return null;
        return new LoginTokens(access.trim(), refresh);
    }

    /** Creates an API key for the signed-in user; returns the raw key (shown only once). */
    @NonNull
    public static String createApiKey(@NonNull Context context, @NonNull String name,
                                      @NonNull PowChallenge pow, @NonNull String nonce,
                                      @NonNull String captchaToken) throws IOException {
        String authorization = Login.getUserTokenAuthorizationHeader();
        if (authorization == null) throw new ApiException(401, "not signed in");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("purpose", "NClientV3 Modern");
        body.put("pow_challenge", pow.challenge);
        body.put("pow_nonce", nonce);
        body.put("captcha_response", captchaToken);
        Map<String, Object> json = postJson(context, Utility.getBaseUrl() + "api/v2/user/keys", body, authorization);
        String key = MiniJson.optString(json, "key");
        if (key == null || key.trim().isEmpty()) throw new IOException("API key response carried no key");
        return key.trim();
    }

    @NonNull
    private static Map<String, Object> getJson(Context context, String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client(context).newCall(request).execute()) {
            return parseOrThrow(response);
        }
    }

    @NonNull
    private static Map<String, Object> postJson(Context context, String url, Map<String, Object> body,
                                                @Nullable String authorization) throws IOException {
        Request.Builder rb = new Request.Builder()
            .url(url)
            .post(RequestBody.create(MiniJson.write(body), JSON));
        if (authorization != null) rb.header("Authorization", authorization);
        try (Response response = client(context).newCall(rb.build()).execute()) {
            return parseOrThrow(response);
        }
    }

    @NonNull
    private static Map<String, Object> parseOrThrow(Response response) throws IOException {
        ResponseBody rb = response.body();
        String text = rb == null ? "" : rb.string();
        Map<String, Object> json = MiniJson.parseObject(text);
        if (!response.isSuccessful()) {
            // The body may echo request fields; only the status and error code are logged.
            String error = errorOf(json);
            LogUtility.e("AuthApi: HTTP " + response.code() + " for " + response.request().url().encodedPath()
                + (error == null ? "" : " error=" + error));
            throw new ApiException(response.code(), error);
        }
        if (json == null) {
            if (text.trim().isEmpty()) return new LinkedHashMap<>();
            throw new IOException("Malformed JSON response");
        }
        return json;
    }

    @Nullable
    static String errorOf(@Nullable Map<String, Object> json) {
        if (json == null) return null;
        String error = MiniJson.optString(json, "error");
        if (error == null) error = MiniJson.optString(json, "detail");
        if (error == null) error = MiniJson.optString(json, "message");
        return error;
    }
}
