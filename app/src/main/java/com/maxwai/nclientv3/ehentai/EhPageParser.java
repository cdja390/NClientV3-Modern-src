/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (selector and URL knowledge)
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
 * Added for NClientV3 Modern 4.7.0. Selectors follow EHentaiGalleryPageParser, EHentaiPageResolver and
 * EHentaiFavorites from yosefario-dev/NClientV3 (https://github.com/yosefario-dev/NClientV3,
 * Apache-2.0), rewritten as pure Java so they can be unit tested. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a single-image viewer page (/s/pageToken/gid-index). */
public final class EhPageParser {
    private static final Pattern NL = Pattern.compile("nl\\(\\s*'([^']+)'\\s*\\)");

    private EhPageParser() {
    }

    public static final class Result {
        @NonNull
        public final String imageUrl;
        /** Reload key for the "Reload broken image" link; null when absent. */
        @Nullable
        public final String reloadKey;
        @Nullable
        public final String originalUrl;

        Result(@NonNull String imageUrl, @Nullable String reloadKey, @Nullable String originalUrl) {
            this.imageUrl = imageUrl;
            this.reloadKey = reloadKey;
            this.originalUrl = originalUrl;
        }
    }

    @NonNull
    public static Result parse(@Nullable String html) throws EhException {
        if (html == null || html.trim().isEmpty()) throw new EhException(EhException.Kind.PARSE, "empty page");
        EhListParser.checkBanned(html);
        if (html.length() < 4000 && html.contains("exceeded your image viewing limits"))
            throw new EhException(EhException.Kind.QUOTA_EXCEEDED, "image limit reached");
        Document doc = Jsoup.parse(html);
        Element img = doc.getElementById("img");
        if (img == null || img.attr("src").isEmpty()) throw new EhException(EhException.Kind.PARSE, "no image");
        String src = EhUrls.absolute(img.attr("src"));
        if (isQuotaImage(src)) throw new EhException(EhException.Kind.QUOTA_EXCEEDED, "509 image");
        String reload = null;
        Matcher m = NL.matcher(img.attr("onerror"));
        if (m.find()) reload = m.group(1);
        if (reload == null) {
            Element lf = doc.getElementById("loadfail");
            if (lf != null) {
                Matcher m2 = NL.matcher(lf.attr("onclick"));
                if (m2.find()) reload = m2.group(1);
            }
        }
        String original = null;
        Element full = doc.selectFirst("a[href*=/fullimg]");
        if (full != null) original = EhUrls.absolute(full.attr("href"));
        return new Result(src, reload, original);
    }

    /** e-hentai serves a "509" placeholder when the image quota is used up. */
    public static boolean isQuotaImage(@Nullable String url) {
        return url != null && (url.endsWith("/509.gif") || url.endsWith("/509s.gif"));
    }
}
