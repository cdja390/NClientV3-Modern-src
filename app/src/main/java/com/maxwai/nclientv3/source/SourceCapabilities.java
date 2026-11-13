/* Copyright 2026 yosefario-dev and NClientV3 contributors.
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Adapted for Modern 4.7.2 from SourceCapabilities at b2ee7ed.
 * See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import com.maxwai.nclientv3.api.enums.SortType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SourceCapabilities {
    private SourceCapabilities() {
    }

    public static boolean isNhOnly(Collection<GallerySource> sources) {
        return sources.size() == 1 && sources.contains(GallerySource.NHENTAI);
    }

    public static List<SortType> sortOptions(Collection<GallerySource> sources, boolean unfilteredBrowse) {
        if (isNhOnly(sources)) return Collections.unmodifiableList(Arrays.asList(SortType.values()));
        if (unfilteredBrowse && !sources.isEmpty()) {
            return Arrays.asList(SortType.RECENT_ALL_TIME, SortType.POPULAR_DAILY);
        }
        return Collections.singletonList(SortType.RECENT_ALL_TIME);
    }

    public static SortType effectiveSort(Collection<GallerySource> sources, SortType requested,
                                         boolean unfilteredBrowse) {
        return requested != null && sortOptions(sources, unfilteredBrowse).contains(requested)
            ? requested : SortType.RECENT_ALL_TIME;
    }
}
