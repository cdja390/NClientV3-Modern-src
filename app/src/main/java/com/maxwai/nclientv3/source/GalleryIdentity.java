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
 * Added for NClientV3 Modern 4.7.2. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.source;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.ehentai.EhSimpleGallery;

/** Looks up list rows by source, not by the bare numeric id. */
public final class GalleryIdentity {
    private GalleryIdentity() {
    }

    @NonNull
    public static GallerySource ehSource(boolean exhentai) {
        return exhentai ? GallerySource.EXHENTAI : GallerySource.EHENTAI;
    }

    /**
     * Int stored on {@link GenericGallery#getId()} for an e-hentai gid.
     * Always non-negative. The sign bit is not a source flag.
     */
    public static int numericId(long gid) {
        if (gid <= 0) return 0;
        if (gid > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) gid;
    }

    @NonNull
    public static SourceKey of(@NonNull GenericGallery gallery) {
        if (gallery instanceof EhSimpleGallery) return ((EhSimpleGallery) gallery).sourceKey();
        return new SourceKey(GallerySource.NHENTAI, gallery.getId(), null);
    }

    public static boolean sameIdentity(@NonNull GallerySource a, long aId, @NonNull GallerySource b, long bId) {
        return a == b && aId == bId;
    }

    /** Nhentai history, downloads and status colors. Other sources must not use those tables. */
    public static boolean persistsInNhentaiTables(@NonNull GenericGallery gallery) {
        return persistsInNhentaiTables(of(gallery).source);
    }

    public static boolean persistsInNhentaiTables(@NonNull GallerySource source) {
        return source == GallerySource.NHENTAI;
    }
}
