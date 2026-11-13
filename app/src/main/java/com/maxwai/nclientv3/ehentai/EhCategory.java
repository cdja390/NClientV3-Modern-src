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

/** The ten gallery categories; bits match e-hentai's f_cats exclusion mask. */
public enum EhCategory {
    MISC(0x1, "Misc", "ct1", 0xFF777777),
    DOUJINSHI(0x2, "Doujinshi", "ct2", 0xFFC62828),
    MANGA(0x4, "Manga", "ct3", 0xFFEF6C00),
    ARTIST_CG(0x8, "Artist CG", "ct4", 0xFFB59F00),
    GAME_CG(0x10, "Game CG", "ct5", 0xFF2E7D32),
    IMAGE_SET(0x20, "Image Set", "ct6", 0xFF1565C0),
    COSPLAY(0x40, "Cosplay", "ct7", 0xFF6A1B9A),
    ASIAN_PORN(0x80, "Asian Porn", "ct8", 0xFFAD1457),
    NON_H(0x100, "Non-H", "ct9", 0xFF00838F),
    WESTERN(0x200, "Western", "cta", 0xFF558B2F),
    UNKNOWN(0, "Unknown", "", 0xFF9E9E9E);

    /** All real category bits. */
    public static final int ALL = 0x3ff;
    /** Upstream explicit CATEGORY Private: hide all ordinary categories; not a global UI category. */
    public static final int REQUEST_PRIVATE = 0x400;

    public final int bit;
    @NonNull
    public final String label;
    @NonNull
    final String cssClass;
    public final int color;

    EhCategory(int bit, @NonNull String label, @NonNull String cssClass, int color) {
        this.bit = bit;
        this.label = label;
        this.cssClass = cssClass;
        this.color = color;
    }

    private static String normalize(String s) {
        return s.replace(" ", "").replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    @NonNull
    public static EhCategory fromLabel(@Nullable String text) {
        if (text == null) return UNKNOWN;
        String n = normalize(text.trim());
        if (n.equals("private")) return UNKNOWN;
        for (EhCategory c : values()) {
            if (c != UNKNOWN && normalize(c.label).equals(n)) return c;
        }
        return UNKNOWN;
    }

    @NonNull
    public static EhCategory fromCssClasses(@Nullable String classes) {
        if (classes == null) return UNKNOWN;
        for (String token : classes.split("\\s+")) {
            for (EhCategory c : values()) {
                if (c != UNKNOWN && c.cssClass.equals(token)) return c;
            }
        }
        return UNKNOWN;
    }

    @NonNull
    public static EhCategory fromBit(int bit) {
        for (EhCategory c : values()) if (c.bit == bit) return c;
        return UNKNOWN;
    }

    /**
     * e-hentai's f_cats lists the categories to HIDE. Converts "show only these" into that mask;
     * 0 (nothing selected) and ALL both mean no filter.
     */
    public static int excludeMaskForShown(int shownBits) {
        int shown = shownBits & ALL;
        if ((shownBits & REQUEST_PRIVATE) != 0) return ALL & ~shown;
        if (shown == 0 || shown == ALL) return 0;
        return ALL & ~shown;
    }
}
