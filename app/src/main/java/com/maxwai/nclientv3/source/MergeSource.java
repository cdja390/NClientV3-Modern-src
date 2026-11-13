/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Source merging adapted from yosefario-dev/NClientV3 at b2ee7ed (Apache-2.0).
 * Added for Modern 4.7.2; see docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import com.maxwai.nclientv3.api.enums.SortType;

/** A request-bound source. Each source fetch can fail independently. */
public interface MergeSource<T> {
    GallerySource id();

    MergePage<T> fetch(SortType effectiveSort) throws Exception;
}
