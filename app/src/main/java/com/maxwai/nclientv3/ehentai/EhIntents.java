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

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.MainActivity;

/** Intent extras shared by the e-hentai screens. */
public final class EhIntents {
    static final String EXTRA_GID = "eh_gid";
    static final String EXTRA_TOKEN = "eh_token";
    static final String EXTRA_EX = "eh_ex";
    static final String EXTRA_TITLE = "eh_title";
    static final String EXTRA_THUMB = "eh_thumb";
    static final String EXTRA_PAGES = "eh_pages";
    static final String EXTRA_START = "eh_start";
    static final String EXTRA_QUERY = "eh_query";
    private static final String EXTRA_MERGED = "eh_merged";

    private EhIntents() {
    }

    @NonNull
    public static Intent gallery(@NonNull Context context, @NonNull EhGalleryKey key, @Nullable String title,
                                 @Nullable String thumb) {
        return put(new Intent(context, EhGalleryActivity.class), key)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_THUMB, thumb);
    }

    @NonNull
    public static Intent gallery(@NonNull Context context, @NonNull EhGalleryInfo info) {
        return gallery(context, info.key, info.title, info.thumb);
    }

    @NonNull
    static Intent mergedGallery(@NonNull Context context, @NonNull EhGalleryInfo info) {
        return gallery(context, info).putExtra(EXTRA_MERGED, true);
    }

    @NonNull
    static Intent tag(@NonNull Context context, @NonNull Intent gallery, String namespace, String name) {
        if (!gallery.getBooleanExtra(EXTRA_MERGED, false))
            return search(context, EhGalleryActivity.tagQuery(namespace, name)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String pkg = context.getPackageName();
        return new Intent(context, MainActivity.class)
            .putExtra(pkg + ".ISBYTAG", true)
            .putExtra(pkg + ".TAG", EhTags.forMergedSearch(namespace, name));
    }

    @NonNull
    static Intent reader(@NonNull Context context, @NonNull EhGalleryInfo info, int startPage) {
        return put(new Intent(context, EhReaderActivity.class), info.key)
            .putExtra(EXTRA_TITLE, info.title)
            .putExtra(EXTRA_THUMB, info.thumb)
            .putExtra(EXTRA_PAGES, info.pages)
            .putExtra(EXTRA_START, startPage);
    }

    @NonNull
    public static Intent search(@NonNull Context context, @Nullable String query) {
        return new Intent(context, EhBrowseActivity.class).putExtra(EXTRA_QUERY, query);
    }

    @NonNull
    private static Intent put(@NonNull Intent i, @NonNull EhGalleryKey key) {
        return i.putExtra(EXTRA_GID, key.gid).putExtra(EXTRA_TOKEN, key.token).putExtra(EXTRA_EX, key.exhentai);
    }

    @Nullable
    static EhGalleryKey key(@Nullable Intent i) {
        if (i == null) return null;
        long gid = i.getLongExtra(EXTRA_GID, -1);
        String token = i.getStringExtra(EXTRA_TOKEN);
        if (gid <= 0 || token == null) {
            // Deep link: https://e-hentai.org/g/<gid>/<token>/
            return i.getData() == null ? null : EhGalleryKey.findIn(i.getData().toString());
        }
        return new EhGalleryKey(gid, token, i.getBooleanExtra(EXTRA_EX, false));
    }
}
