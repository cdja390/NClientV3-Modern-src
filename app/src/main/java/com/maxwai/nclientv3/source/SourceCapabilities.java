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

    /** EH and EX only support recent; the user's saved NH sort is left untouched. */
    public static List<SortType> sortOptions(Collection<GallerySource> sources) {
        for (GallerySource source : sources) {
            if (source != GallerySource.NHENTAI) {
                return Collections.singletonList(SortType.RECENT_ALL_TIME);
            }
        }
        return Collections.unmodifiableList(Arrays.asList(SortType.values()));
    }

    public static SortType effectiveSort(Collection<GallerySource> sources, SortType requested) {
        return requested != null && sortOptions(sources).contains(requested)
            ? requested : SortType.RECENT_ALL_TIME;
    }
}
