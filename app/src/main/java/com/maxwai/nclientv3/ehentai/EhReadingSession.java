/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.ehentai;

/** Accumulates foreground time on the database executor until a write succeeds. */
final class EhReadingSession {
    interface Store {
        void add(long count, long elapsedMs, long wallTime, int maxPage);
    }

    private final Store store;
    private boolean countOnResume;
    private boolean running;
    private long since;
    private long pendingCount;
    private long pendingMs;
    private int maxPage;

    EhReadingSession(boolean fresh, Store store) {
        countOnResume = fresh;
        this.store = store;
    }

    void resume(boolean enabled, long elapsed, long wall, int page) {
        if (!enabled || running) return;
        running = true;
        since = elapsed;
        if (countOnResume) {
            pendingCount++;
            countOnResume = false;
        }
        flush(elapsed, wall, page);
    }

    void flush(long elapsed, long wall, int page) {
        if (running) {
            pendingMs += Math.max(0, elapsed - since);
            since = elapsed;
            maxPage = Math.max(maxPage, page);
        }
        if (pendingCount == 0 && pendingMs == 0) return;
        store.add(pendingCount, pendingMs, wall, maxPage);
        pendingCount = pendingMs = 0;
    }

    void pause(long elapsed, long wall, int page) {
        try {
            flush(elapsed, wall, page);
        } finally {
            running = false;
        }
    }
}
