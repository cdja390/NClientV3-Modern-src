/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (merged source feed design)
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
 * Added for NClientV3 Modern 4.7.1. The merge follows InspectorV3.mergeAcross, EHentaiPager and
 * EHentaiSource.buildSearchQuery from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0), rewritten on top of this app's
 * e-hentai client (EhRepository / EhListParser). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.enums.Language;

import java.util.Locale;

/**
 * An e-hentai gallery shown in the main (nhentai) list, the counterpart of yosefario's
 * SimpleGallery with source=EHENTAI. Its id is the negative gid so it never collides with an
 * nhentai id in the adapter's status cache or MainActivity's "opened gallery" bookkeeping; taps
 * are routed to {@link EhGalleryActivity} by {@link #open}.
 * <p>
 * Not parcelled as itself: InspectorV3 leaves these out of its saved state (they would come back
 * as plain nhentai galleries with a meaningless id).
 */
public class EhSimpleGallery extends SimpleGallery {
    @NonNull
    public final EhGalleryInfo info;

    public EhSimpleGallery(@NonNull EhGalleryInfo info) {
        super(info.title, idFor(info.key), info.thumb == null ? Uri.EMPTY : Uri.parse(info.thumb),
            languageOf(info.language), Math.max(0, info.pages));
        this.info = info;
    }

    static int idFor(@NonNull EhGalleryKey key) {
        long gid = Math.min(key.gid, Integer.MAX_VALUE);
        return (int) -Math.max(1, gid);
    }

    @NonNull
    static Language languageOf(@Nullable String language) {
        if (language == null) return Language.UNKNOWN;
        String l = language.toLowerCase(Locale.ROOT);
        if (l.contains("chinese")) return Language.CHINESE;
        if (l.contains("english")) return Language.ENGLISH;
        if (l.contains("japanese")) return Language.JAPANESE;
        return Language.UNKNOWN;
    }

    @NonNull
    public android.content.Intent open(@NonNull android.content.Context context) {
        return EhIntents.gallery(context, info);
    }

    @Override
    public boolean isValid() {
        return info.key.gid > 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "EhSimpleGallery{gid=" + info.key.gid + ", title='" + info.title + "'}";
    }
}
