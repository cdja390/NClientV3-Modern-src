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
import org.jsoup.select.Elements;

/** Favorite-folder names (favorites.php header and the add-to-favorites popup). */
public final class EhFavoritesParser {
    public static final int SLOT_COUNT = 10;

    private EhFavoritesParser() {
    }

    /**
     * Reads the ten folder names from favorites.php ({@code .ido .fp}: count, icon, name) or from
     * gallerypopups.php (radio favcat + label). Missing names stay null.
     */
    @NonNull
    public static String[] folderNames(@Nullable String html) {
        String[] names = new String[SLOT_COUNT];
        if (html == null || html.isEmpty()) return names;
        Document doc = Jsoup.parse(html);
        Elements fps = doc.select(".ido .fp");
        int slot = 0;
        for (Element fp : fps) {
            if (fp.hasClass("fps") && fp.text().trim().isEmpty()) continue;
            if (slot >= SLOT_COUNT) break;
            Elements divs = fp.children();
            String name = divs.size() >= 3 ? divs.get(2).text().trim() : fp.text().trim();
            if (!name.isEmpty()) names[slot] = name;
            slot++;
        }
        if (slot > 0) return names;
        for (Element input : doc.select("input[name=favcat]")) {
            String v = input.attr("value");
            if (!v.matches("\\d")) continue;
            int i = Integer.parseInt(v);
            Element parent = input.parent();
            Element label = parent == null ? null : parent.parent();
            String text = label == null ? "" : label.text().trim();
            if (!text.isEmpty()) names[i] = text;
        }
        return names;
    }

    /** True when the popup says the gallery is already in a folder (offers "Remove from Favorites"). */
    public static boolean popupHasFavorite(@Nullable String html) {
        return html != null && html.contains("value=\"favdel\"");
    }
}
