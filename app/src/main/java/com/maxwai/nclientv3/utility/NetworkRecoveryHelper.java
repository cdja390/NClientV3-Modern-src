package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-flight network recovery helper to safely reload client without dispatcher contention.
 */
public class NetworkRecoveryHelper {
    private static final AtomicBoolean isReloading = new AtomicBoolean(false);
    private static volatile long lastReloadUptime = 0L;
    private static final long COOLDOWN_MS = 3000L;

    /**
     * Atomically attempt to acquire the recovery single-flight ticket.
     * Respects 3-second cooldown to prevent storming.
     */
    public static boolean tryBeginReload() {
        long now = SystemClock.uptimeMillis();
        if (now - lastReloadUptime < COOLDOWN_MS) {
            return false;
        }
        if (isReloading.compareAndSet(false, true)) {
            lastReloadUptime = now;
            return true;
        }
        return false;
    }

    /**
     * Release the single-flight ticket once reload is complete or failed.
     */
    public static void endReload() {
        isReloading.set(false);
    }

    /**
     * Check if client rebuild is currently in flight.
     */
    public static boolean isReloading() {
        return isReloading.get();
    }
}
