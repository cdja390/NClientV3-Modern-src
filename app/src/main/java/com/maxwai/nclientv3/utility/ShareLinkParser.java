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
 * Routing order adapted from ShareReceiverActivity in yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * Modified for NClientV3 Modern: pure-Java parser, mirror-aware host check, page sanitising,
 * no e-hentai branch. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.utility;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text shared from another app into a destination. Kept free of Android classes so the
 * routing rules can be unit tested.
 */
public final class ShareLinkParser {

    public enum Kind {EMPTY, GALLERY, SITE_URL, ID, SEARCH}

    /** Decides whether a URL host belongs to the site (configured mirror or original domain). */
    public interface HostCheck {
        boolean isSiteHost(@Nullable String host);
    }

    public static final class Result {
        @NonNull
        public final Kind kind;
        /** Gallery id for GALLERY/ID, sanitised URL for SITE_URL, query for SEARCH. */
        @NonNull
        public final String value;

        Result(@NonNull Kind kind, @NonNull String value) {
            this.kind = kind;
            this.value = value;
        }

        @NonNull
        @Override
        public String toString() {
            return kind + ":" + value;
        }
    }

    private static final Pattern URL = Pattern.compile(
        "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GALLERY_PATH = Pattern.compile("^/g/(\\d{1,9})(?:/.*)?$");
    private static final Pattern NUMBER = Pattern.compile("^#?(\\d{1,9})$");
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]'\"";

    private ShareLinkParser() {
    }

    @NonNull
    public static Result parse(@Nullable CharSequence shared, @NonNull HostCheck hosts) {
        String text = shared == null ? "" : shared.toString().trim();
        if (text.isEmpty()) return new Result(Kind.EMPTY, "");
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String url = trimTrailing(m.group());
            URI uri;
            try {
                uri = new URI(url);
            } catch (Exception e) {
                continue;
            }
            if (!hosts.isSiteHost(uri.getHost())) continue;
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            Matcher g = GALLERY_PATH.matcher(path);
            if (g.matches()) return new Result(Kind.GALLERY, g.group(1));
            return new Result(Kind.SITE_URL, sanitizePage(url));
        }
        Matcher n = NUMBER.matcher(text);
        if (n.matches()) return new Result(Kind.ID, n.group(1));
        return new Result(Kind.SEARCH, text);
    }

    @NonNull
    static String trimTrailing(@NonNull String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) end--;
        return url.substring(0, end);
    }

    /**
     * Drops a {@code page} query parameter that is not a positive number: MainActivity parses it
     * with Integer.parseInt and would crash on {@code ?page=abc}.
     */
    @NonNull
    static String sanitizePage(@NonNull String url) {
        int hash = url.indexOf('#');
        String fragment = hash >= 0 ? url.substring(hash) : "";
        String noFragment = hash >= 0 ? url.substring(0, hash) : url;
        int q = noFragment.indexOf('?');
        if (q < 0) return url;
        String base = noFragment.substring(0, q);
        String[] params = noFragment.substring(q + 1).split("&", -1);
        StringBuilder kept = new StringBuilder();
        for (String p : params) {
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String name = (eq >= 0 ? p.substring(0, eq) : p).toLowerCase(Locale.ROOT);
            if ("page".equals(name)) {
                String v = eq >= 0 ? p.substring(eq + 1) : "";
                if (!isPositiveInt(v)) continue;
            }
            if (kept.length() > 0) kept.append('&');
            kept.append(p);
        }
        return kept.length() == 0 ? base + fragment : base + "?" + kept + fragment;
    }

    private static boolean isPositiveInt(String v) {
        if (v.isEmpty() || v.length() > 9) return false;
        for (int i = 0; i < v.length(); i++) {
            if (v.charAt(i) < '0' || v.charAt(i) > '9') return false;
        }
        return Integer.parseInt(v) > 0;
    }
}
