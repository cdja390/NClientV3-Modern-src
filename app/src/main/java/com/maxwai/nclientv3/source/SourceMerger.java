/* Copyright 2026 yosefario-dev and NClientV3 contributors (InspectorV3.mergeAcross).
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Rewritten for Modern 4.7.2 with injectable sources; see docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import com.maxwai.nclientv3.api.enums.SortType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Round-robin results, with source failures isolated and identity scoped to this inspector. */
public final class SourceMerger<T> {
    private final Function<T, String> identity;
    private final Set<String> seen = new HashSet<>();

    public SourceMerger(Function<T, String> identity) {
        this.identity = identity;
    }

    public interface Fetcher<T> {
        MergePage<T> fetch(SortType effectiveSort) throws Exception;
    }

    public static <T> MergeSource<T> source(GallerySource id, Fetcher<T> fetcher) {
        return new MergeSource<T>() {
            @Override
            public GallerySource id() {
                return id;
            }

            @Override
            public MergePage<T> fetch(SortType effectiveSort) throws Exception {
                return fetcher.fetch(effectiveSort);
            }
        };
    }

    public MergePage<T> merge(List<MergeSource<T>> sources, SortType effectiveSort, int page,
                              BiConsumer<GallerySource, Exception> onFailure) throws IOException {
        List<MergePage<T>> successful = new ArrayList<>();
        Exception last = null;
        int pageCount = 1;
        int longest = 0;
        for (MergeSource<T> source : sources) {
            try {
                MergePage<T> result = source.fetch(effectiveSort);
                successful.add(result);
                pageCount = Math.max(pageCount, result.pageCount);
                longest = Math.max(longest, result.items.size());
            } catch (Exception failure) {
                last = failure;
                onFailure.accept(source.id(), failure);
            }
        }
        if (successful.isEmpty() && last != null) {
            if (last instanceof IOException) throw (IOException) last;
            throw new IOException(last);
        }
        if (page <= 1) seen.clear();
        List<T> merged = new ArrayList<>();
        for (int row = 0; row < longest; row++) {
            for (MergePage<T> result : successful) {
                if (row >= result.items.size()) continue;
                T item = result.items.get(row);
                if (item != null && seen.add(identity.apply(item))) merged.add(item);
            }
        }
        return new MergePage<>(merged, pageCount);
    }
}
