/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import android.content.Context;

import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.ehentai.EhDownloads;
import com.maxwai.nclientv3.ehentai.EhSimpleGallery;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GalleryDownloads {
    private GalleryDownloads() {
    }

    public static void enqueue(Context context, Collection<? extends GenericGallery> rows) {
        Context app = context.getApplicationContext();
        route(rows, GalleryIdentity::of, g -> DownloadGalleryV2.downloadGallery(app, g),
            g -> EhDownloads.enqueue(app, ((EhSimpleGallery) g).info));
    }

    static <T> void route(Collection<? extends T> rows, Function<T, SourceKey> identity,
                          Consumer<T> nh, Consumer<T> eh) {
        Set<SourceKey> enqueued = new HashSet<>();
        for (T row : rows) {
            SourceKey key = identity.apply(row);
            if (key.id <= 0 || (key.source != GallerySource.NHENTAI && (key.token == null || key.token.isEmpty()))) continue;
            if (enqueued.add(key)) {
                if (key.source == GallerySource.NHENTAI) nh.accept(row);
                else eh.accept(row);
            }
        }
    }
}
