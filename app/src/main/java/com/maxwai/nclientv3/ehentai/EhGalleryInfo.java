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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One gallery as listed in search results, favorites, history or downloads. */
public class EhGalleryInfo {
    @NonNull
    public final EhGalleryKey key;
    @NonNull
    public String title = "";
    @Nullable
    public String thumb;
    @NonNull
    public EhCategory category = EhCategory.UNKNOWN;
    public int pages;
    /** 0..5 in half steps; negative when unknown. */
    public float rating = -1;
    @Nullable
    public String posted;
    @Nullable
    public String uploader;
    @Nullable
    public String language;
    /** Online favorite slot 0..9, or -1. */
    public int favoriteSlot = -1;

    public EhGalleryInfo(@NonNull EhGalleryKey key) {
        this.key = key;
    }
}
