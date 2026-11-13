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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything shown on a gallery's detail page. */
public class EhGalleryDetail extends EhGalleryInfo {
    @Nullable
    public String titleJpn;
    @Nullable
    public String fileSize;
    public int favoritedCount = -1;
    public int ratingCount = -1;
    /** namespace ("" for none) to tag names, in page order. */
    @NonNull
    public final Map<String, List<String>> tags = new LinkedHashMap<>();
    /** Previews of the requested preview page. */
    @NonNull
    public final List<EhPreview> previews = new ArrayList<>();
    /** Number of preview pages (?p=0..previewPages-1); at least 1. */
    public int previewPages = 1;
    @Nullable
    public String favoriteName;
    @Nullable
    public EhGalleryKey parent;

    public EhGalleryDetail(@NonNull EhGalleryKey key) {
        super(key);
    }

    /** Previews per preview page, learned from page 0 (20 or 40 depending on the account). */
    public int previewsPerPage() {
        return previews.isEmpty() ? 20 : previews.size();
    }

    /** One thumbnail on the gallery page, linking to a single-image viewer page. */
    public static final class EhPreview {
        /** 1-based page number in the gallery. */
        public final int index;
        /** Viewer page, https://e-hentai.org/s/{pageToken}/{gid}-{index}. */
        @NonNull
        public final String pageUrl;
        /** Thumbnail or sprite sheet URL. */
        @Nullable
        public final String imageUrl;
        /** Sprite crop; width == 0 means imageUrl is a standalone thumbnail. */
        public final int offsetX;
        public final int offsetY;
        public final int width;
        public final int height;

        public EhPreview(int index, @NonNull String pageUrl, @Nullable String imageUrl,
                         int offsetX, int offsetY, int width, int height) {
            this.index = index;
            this.pageUrl = pageUrl;
            this.imageUrl = imageUrl;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
        }

        public boolean isSprite() {
            return width > 0 && height > 0;
        }
    }
}
