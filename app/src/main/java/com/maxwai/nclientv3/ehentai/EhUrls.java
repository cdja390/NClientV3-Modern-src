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
 * Added for NClientV3 Modern 4.7.0. Selectors and URL rules follow api/source/ehentai/* from
 * yosefario-dev/NClientV3 (https://github.com/yosefario-dev/NClientV3, Apache-2.0), rewritten as
 * pure Java so they can be unit tested. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/** URL building for e-hentai.org / exhentai.org. Pure Java (no android.net.Uri) for tests. */
public final class EhUrls {
    public static final String HOST_EH = "https://e-hentai.org/";
    public static final String HOST_EX = "https://exhentai.org/";
    public static final String FORUMS_LOGIN = "https://forums.e-hentai.org/index.php?act=Login&CODE=00";

    private EhUrls() {
    }

    @NonNull
    public static String host(boolean exhentai) {
        return exhentai ? HOST_EX : HOST_EH;
    }

    public static boolean isEhHost(@Nullable String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("e-hentai.org") || h.endsWith(".e-hentai.org")
            || h.equals("exhentai.org") || h.endsWith(".exhentai.org");
    }

    @NonNull
    static String encode(@NonNull String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Front page / search. {@code shownCategories} is a "show only" bit set (0 = all);
     * {@code cursor} is the gid from the previous page's "next" link (null for the first page).
     */
    @NonNull
    public static String searchUrl(boolean exhentai, @Nullable String query, int shownCategories,
                                   int minRating, @Nullable String cursor) {
        StringBuilder b = new StringBuilder(host(exhentai));
        char sep = '?';
        int exclude = EhCategory.excludeMaskForShown(shownCategories);
        if (exclude != 0) {
            b.append(sep).append("f_cats=").append(exclude);
            sep = '&';
        }
        if (query != null && !query.trim().isEmpty()) {
            b.append(sep).append("f_search=").append(encode(query.trim()));
            sep = '&';
        }
        if (minRating >= 2 && minRating <= 5) {
            b.append(sep).append("advsearch=1&f_srdd=").append(minRating);
            sep = '&';
        }
        if (cursor != null && !cursor.isEmpty()) b.append(sep).append("next=").append(encode(cursor));
        return b.toString();
    }

    @NonNull
    public static String popularUrl(boolean exhentai) {
        return host(exhentai) + "popular";
    }

    @NonNull
    public static String favoritesUrl(boolean exhentai, int favcat, @Nullable String query, @Nullable String cursor) {
        StringBuilder b = new StringBuilder(host(exhentai)).append("favorites.php?favcat=")
            .append(favcat < 0 ? "all" : Integer.toString(favcat));
        if (query != null && !query.trim().isEmpty()) b.append("&f_search=").append(encode(query.trim()));
        if (cursor != null && !cursor.isEmpty()) b.append("&next=").append(encode(cursor));
        return b.toString();
    }

    @NonNull
    public static String galleryUrl(@NonNull EhGalleryKey key, int previewPage) {
        String base = host(key.exhentai) + "g/" + key.gid + "/" + key.token + "/";
        return previewPage <= 0 ? base : base + "?p=" + previewPage;
    }

    @NonNull
    public static String favoritePopupUrl(@NonNull EhGalleryKey key) {
        return host(key.exhentai) + "gallerypopups.php?gid=" + key.gid + "&t=" + key.token + "&act=addfav";
    }

    /** Appends e-hentai's reload key (from the viewer's nl() call) to a viewer page URL. */
    @NonNull
    public static String withReloadKey(@NonNull String pageUrl, @NonNull String nl) {
        return pageUrl + (pageUrl.contains("?") ? "&" : "?") + "nl=" + encode(nl);
    }

    @NonNull
    public static String absolute(@NonNull String url) {
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }
}
