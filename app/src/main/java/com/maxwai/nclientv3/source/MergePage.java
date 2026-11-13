/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2; see docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MergePage<T> {
    public final List<T> items;
    public final int pageCount;

    public MergePage(List<T> items, int pageCount) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.pageCount = Math.max(1, pageCount);
    }
}
