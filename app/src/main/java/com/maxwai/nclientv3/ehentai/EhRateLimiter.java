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

/**
 * Spaces out page loads so browsing never hammers the site: at least {@link #MIN_INTERVAL_MS}
 * between two HTML requests (plus jitter), and a longer pause after the server pushed back.
 */
public final class EhRateLimiter {
    static final long MIN_INTERVAL_MS = 1000;
    private static final long MAX_JITTER_MS = 400;
    private static final Object LOCK = new Object();
    private static long nextAllowed = 0;

    private EhRateLimiter() {
    }

    /** Blocks until the next request may go out. */
    public static void acquire() throws InterruptedException {
        long wait;
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextAllowed);
            wait = slot - now;
            nextAllowed = slot + MIN_INTERVAL_MS + (long) (Math.random() * MAX_JITTER_MS);
        }
        if (wait > 0) Thread.sleep(wait);
    }

    /** After a 429/503: hold every request back for {@code millis}. */
    public static void backOff(long millis) {
        synchronized (LOCK) {
            nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + millis);
        }
    }

    /** Exponential backoff for retry {@code attempt} (0-based): 2 s, 4 s, 8 s, capped at 30 s. */
    static long backoffFor(int attempt) {
        long ms = 2000L << Math.max(0, Math.min(attempt, 4));
        return Math.min(ms, 30_000L);
    }
}
