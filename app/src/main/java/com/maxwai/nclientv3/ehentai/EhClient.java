/*
 * Copyright 2026 NClientV3-Modern contributors
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
 * Added for NClientV3 Modern 4.7.0 (e-hentai source, no GeckoView). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.content.Context;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * HTTP for e-hentai. A separate OkHttp client from the nhentai one: it must not carry the
 * NClient User-Agent or the nhentai Cloudflare/403 recovery, and it adds the e-hentai account
 * cookies itself. Every HTML request goes through {@link EhRateLimiter}; 429/503 are retried
 * with backoff; 509 and the 509 placeholder become {@link EhException.Kind#QUOTA_EXCEEDED}.
 */
public final class EhClient {
    private static final String FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
    private static final int MAX_RETRIES = 3;
    private static volatile OkHttpClient client;
    private static volatile String userAgent;

    private EhClient() {
    }

    @NonNull
    public static String userAgent(@NonNull Context context) {
        String ua = userAgent;
        if (ua == null) {
            try {
                ua = WebSettings.getDefaultUserAgent(context.getApplicationContext());
            } catch (Throwable t) {
                ua = null;
            }
            if (ua == null || ua.isEmpty()) ua = FALLBACK_UA;
            userAgent = ua;
        }
        return ua;
    }

    @NonNull
    static OkHttpClient client(@NonNull Context context) {
        OkHttpClient c = client;
        if (c == null) {
            synchronized (EhClient.class) {
                c = client;
                if (c == null) {
                    final Context app = context.getApplicationContext();
                    final String ua = userAgent(app);
                    c = new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(40, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .addInterceptor(chain -> {
                            Request r = chain.request();
                            Request.Builder b = r.newBuilder().header("User-Agent", ua);
                            String host = r.url().host();
                            if (EhUrls.isEhHost(host) && r.header("Cookie") == null)
                                b.header("Cookie", EhAuth.cookieHeader(app, host.endsWith("exhentai.org")));
                            return chain.proceed(b.build());
                        })
                        .build();
                    client = c;
                }
            }
        }
        return c;
    }

    /** Rate-limited GET of an HTML page. */
    @NonNull
    public static String getHtml(@NonNull Context context, @NonNull String url) throws IOException {
        return execute(context, new Request.Builder().url(url).build(), url.contains("exhentai.org"));
    }

    @NonNull
    public static String postForm(@NonNull Context context, @NonNull String url, @NonNull FormBody body) throws IOException {
        return execute(context, new Request.Builder().url(url).post(body).build(), url.contains("exhentai.org"));
    }

    @NonNull
    private static String execute(Context context, Request request, boolean exhentai) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                EhRateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("cancelled");
            }
            try (Response response = client(context).newCall(request).execute()) {
                int code = response.code();
                if (code == 429 || code == 503) {
                    long wait = EhRateLimiter.backoffFor(attempt);
                    EhRateLimiter.backOff(wait);
                    LogUtility.w("e-hentai HTTP " + code + ", backing off " + wait + " ms");
                    last = new EhException(EhException.Kind.RATE_LIMITED, code, "HTTP " + code);
                    continue;
                }
                if (code == 509) throw new EhException(EhException.Kind.QUOTA_EXCEEDED, code, "HTTP 509");
                if (code == 404) throw new EhException(EhException.Kind.GALLERY_GONE, code, "HTTP 404");
                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();
                if (!response.isSuccessful()) throw new EhException(EhException.Kind.HTTP, code, "HTTP " + code);
                if (exhentai && text.isEmpty())
                    throw new EhException(EhException.Kind.SAD_PANDA, "empty exhentai page");
                return text;
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    /**
     * Downloads an image to {@code target} (via a .part file). Not paced here: callers pace page
     * resolution, and the image hosts (H@H) are separate from the page servers.
     */
    public static void download(@NonNull Context context, @NonNull String url, @NonNull File target) throws IOException {
        if (EhPageParser.isQuotaImage(url)) throw new EhException(EhException.Kind.QUOTA_EXCEEDED, "509 image");
        File part = new File(target.getPath() + ".part");
        Request request = new Request.Builder().url(url).build();
        try (Response response = client(context).newCall(request).execute()) {
            if (response.code() == 509 || EhPageParser.isQuotaImage(response.request().url().toString()))
                throw new EhException(EhException.Kind.QUOTA_EXCEEDED, response.code(), "509");
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null)
                throw new EhException(EhException.Kind.HTTP, response.code(), "HTTP " + response.code());
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("cancelled");
                    out.write(buf, 0, n);
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        }
        if (part.length() == 0 || (!target.delete() && target.exists()) || !part.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("could not write " + target.getName());
        }
    }

    /** Headers Glide needs for exhentai thumbnails (s.exhentai.org checks the cookies). */
    @Nullable
    public static String cookieFor(@NonNull Context context, @Nullable String url) {
        if (url == null) return null;
        if (url.contains("exhentai.org")) return EhAuth.cookieHeader(context, true);
        return null;
    }
}
