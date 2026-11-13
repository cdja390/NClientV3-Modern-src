/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0. */
package com.maxwai.nclientv3.ehentai;

final class EhPageNavigation {
    private EhPageNavigation() {
    }

    static int edgeDelta(float fraction, boolean enabled, boolean rtl) {
        if (!enabled) return 0;
        if (fraction < .2f) return rtl ? 1 : -1;
        if (fraction > .8f) return rtl ? -1 : 1;
        return 0;
    }

    static int volumeDelta(boolean up, boolean plusNext, boolean rtl) {
        return (up == plusNext) != rtl ? 1 : -1;
    }

    static int clamp(int page, int pages) {
        return pages > 0 ? Math.max(1, Math.min(page, pages)) : 0;
    }
}
