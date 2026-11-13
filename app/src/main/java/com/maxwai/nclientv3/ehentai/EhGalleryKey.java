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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifies one e-hentai/exhentai gallery: gallery id, 10-hex token, and which site. */
public final class EhGalleryKey {
    static final Pattern GALLERY_PATH = Pattern.compile("/g/(\\d{1,10})/([0-9a-fA-F]{10})");
    private static final Pattern LINK = Pattern.compile(
        "https?://(?:[\\w-]+\\.)?(e-hentai|exhentai)\\.org" + GALLERY_PATH.pattern(), Pattern.CASE_INSENSITIVE);

    public final long gid;
    @NonNull
    public final String token;
    public final boolean exhentai;

    public EhGalleryKey(long gid, @NonNull String token, boolean exhentai) {
        this.gid = gid;
        this.token = token.toLowerCase(Locale.ROOT);
        this.exhentai = exhentai;
    }

    /** Finds the first e-hentai or exhentai gallery link in arbitrary text. */
    @Nullable
    public static EhGalleryKey findIn(@Nullable CharSequence text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        if (!m.find()) return null;
        try {
            return new EhGalleryKey(Long.parseLong(m.group(2)), m.group(3),
                "exhentai".equalsIgnoreCase(m.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a /g/gid/token path from an href that may be relative. */
    @Nullable
    static EhGalleryKey fromHref(@Nullable String href, boolean exhentaiDefault) {
        if (href == null) return null;
        Matcher m = GALLERY_PATH.matcher(href);
        if (!m.find()) return null;
        boolean ex = href.contains("exhentai.org") || (exhentaiDefault && !href.contains("e-hentai.org"));
        try {
            return new EhGalleryKey(Long.parseLong(m.group(1)), m.group(2), ex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    public String url() {
        return EhUrls.galleryUrl(this, 0);
    }

    boolean hasFileToken() {
        return token.matches("[0-9a-f]{10}");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EhGalleryKey)) return false;
        EhGalleryKey k = (EhGalleryKey) o;
        return gid == k.gid && exhentai == k.exhentai && token.equals(k.token);
    }

    @Override
    public int hashCode() {
        return (Long.hashCode(gid) * 31 + token.hashCode()) * 31 + Boolean.hashCode(exhentai);
    }

    @NonNull
    @Override
    public String toString() {
        return gid + "/" + token + (exhentai ? "/ex" : "");
    }
}
