package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.api.components.GenericGallery;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Per-gallery reading count and total reading time.
 * Off by default (setting record_reading_stats); the data is included in backup export/import.
 *
 *
 * Fully independent from ReadingSessionHelper: own state, own setting, own table; no shared
 * mutable state. Every ZoomActivity hook is called AFTER the matching ReadingSessionHelper call.
 *
 * ZoomActivity hooks:
 *  - onCreate, after ReadingSessionHelper.onSessionStart: onSessionStart(activity, startPage, savedInstanceState)
 *  - setPageText(I), after ReadingSessionHelper.onPageChanged: onPageChanged(activity, gallery, page)
 *  - dispatchTouchEvent, first line: onTouch(activity, ev)  (ACTION_DOWN only, never consumes)
 *  - NEW onResume, after super.onResume: onResume(activity, gallery)
 *  - onPause, after ReadingSessionHelper.onLeave, before super.onPause: onPause(activity, gallery)
 * GalleryActivity hooks:
 *  - onCreateOptionsMenu, before "return true": setupGalleryMenu(activity, menu, gallery)
 *  - onOptionsItemSelected, first: if (onGalleryMenuItemSelected(activity, item, gallery)) return true
 * GeneralPreferenceFragment hooks (mainMenu end and onResume): bindPreference(fragment)
 * Backup hooks:
 *  - Exporter.dumpDB, after the SCHEMAS loop, before writer.endObject(): exportTable(db, writer)
 *  - Importer.importDB, before db.delete(tableName): onImportTable(db, tableName)
 *
 * Rules (setting "record_reading_stats", SwitchPreferenceCompat in settings.xml, default OFF):
 *  - SWITCH OFF: nothing is recorded at all - no count, no time, no row, and the table is not
 *    created. Only onResume (running=true, the only path that enables timing and the count) and
 *    onPause (the only unconditional flush) check the switch; every other write path requires
 *    running == true. Existing data stays viewable / clearable / exportable while off.
 *  - COUNT (user decision 2026-11-12, "when in doubt count more"): every NEW ZoomActivity instance
 *    (onCreate with savedInstanceState == null) is +1, written at its first onResume with the
 *    feature on and gallery id > 0. No time / page threshold, no merge window.
 *    NOT counted: screen rotation (configChanges, no new instance), background -> foreground
 *    (onPause/onResume of the same instance), and re-creation with a saved state (config change
 *    that recreates, split-screen resize, process death restore) because savedInstanceState != null.
 *  - TIME (user decision 2026-11-12 20:37): the full foreground time of the reader, i.e.
 *    SystemClock.elapsedRealtime() from onResume to onPause (or to the last flush). NO idle cap and
 *    NO per-opening cap: idle time and unattended auto page turning are counted too. Background,
 *    lock screen and other activities on top are not counted (onPause stops timing).
 *  - Flush: at the first onResume (the count), on onPause, and opportunistically on page change /
 *    touch down at most every 30 s.
 *  - Galleries with id <= 0 are skipped (no shared -1 row).
 *  - Table ReadingStats is created lazily (CREATE TABLE IF NOT EXISTS); DATABASE_VERSION unchanged.
 *    All numeric columns are declared INTEGER and are ALWAYS read with getLong / written with
 *    bindLong (total_ms and the wall-clock columns need 64 bits; never use getInt on them).
 *  - Backup: included in Database.json as table "ReadingStats" when it has at least one row
 *    (old-format rows, same JSON shape as Exporter). Import creates the table first if missing;
 *    backups without the key leave local stats untouched (Importer only replaces listed tables).
 *  - Everything is inside try/catch(Throwable); failures only log (tag NClientReadStats).
 *    exportTable lets IOException from the JsonWriter propagate like the original Exporter.
 */
public class ReadingStatsHelper {

    public static final String PREF_SETTINGS = "Settings";
    public static final String PREF_ENABLED = "record_reading_stats";
    public static final String PREF_CLEAR = "clear_reading_stats";
    public static final int MENU_ID_READING_STATS = 0x70ffff20;

    private static final String TAG = "NClientReadStats";
    static final String TABLE = "ReadingStats";
    static final long FLUSH_INTERVAL_MS = 30000L;

    /** Column order of export rows and of query(): index 0 is the key. */
    static final String[] COLUMNS = {"gallery", "read_count", "total_ms", "first_read", "last_read", "max_page"};

    // INTEGER (not INT) on purpose: SQLite stores up to 64 bits either way, but the declaration
    // documents that total_ms / first_read / last_read are Java long (getLong / bindLong only).
    private static final String CREATE_SQL =
        "CREATE TABLE IF NOT EXISTS `ReadingStats` ("
            + "`gallery` INTEGER NOT NULL PRIMARY KEY,"
            + "`read_count` INTEGER NOT NULL DEFAULT 0,"
            + "`total_ms` INTEGER NOT NULL DEFAULT 0,"    // 64-bit ms
            + "`first_read` INTEGER NOT NULL DEFAULT 0,"  // 64-bit wall-clock ms
            + "`last_read` INTEGER NOT NULL DEFAULT 0,"   // 64-bit wall-clock ms
            + "`max_page` INTEGER NOT NULL DEFAULT 0);";

    static final class Session {
        boolean fresh;        // created by onCreate with savedInstanceState == null -> counts once
        boolean counted;      // the +1 of this instance has been written
        int galleryId;
        boolean running;
        long countedUntil;
        long lastActivity;
        long lastFlush;
        long pendingMs;       // active ms not yet written
        long sessionMs;       // active ms of this instance (written + pending)
        int turns;
        int lastPage;
        int maxPage;
        int writtenMaxPage;
    }

    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<Activity, Session>();
    private static SQLiteDatabase sCreatedOn;

    // ------------------------------------------------------------------ ZoomActivity hooks

    /**
     * onCreate, right after the initial setPageText(startPage). page is 1-based.
     * savedInstanceState != null means the system re-created the reader (not a new opening): no count.
     */
    public static void onSessionStart(Activity activity, int page, Bundle savedInstanceState) {
        try {
            if (activity == null) return;
            Session s = new Session();
            s.fresh = savedInstanceState == null;
            s.lastPage = page;
            s.maxPage = page;
            synchronized (SESSIONS) {
                SESSIONS.put(activity, s);
            }
        } catch (Throwable t) {
            log("onSessionStart failed", t);
        }
    }

    /** New ZoomActivity.onResume, after super.onResume(). */
    public static void onResume(Activity activity, GenericGallery gallery) {
        try {
            if (activity == null) return;
            Session s = session(activity);
            if (s == null) {
                // onCreate did not reach the hook (should not happen): time only, no count.
                s = new Session();
                synchronized (SESSIONS) {
                    SESSIONS.put(activity, s);
                }
            }
            if (!isEnabled(activity)) {
                // Switch off: record NOTHING (no count, no time, no table). Drop anything pending.
                s.running = false;
                s.pendingMs = 0;
                return;
            }
            int id = gallery == null ? 0 : gallery.getId();
            if (id <= 0) {
                s.running = false;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            s.galleryId = id;
            s.running = true;
            s.countedUntil = now;
            s.lastActivity = now;
            s.lastFlush = now;
            // Count immediately (no threshold); a later onResume of the same instance never counts.
            if (s.fresh && !s.counted) flush(s, now);
        } catch (Throwable t) {
            log("onResume failed", t);
        }
    }

    /** setPageText(I), after ReadingSessionHelper.onPageChanged. page is 1-based. */
    public static void onPageChanged(Activity activity, GenericGallery gallery, int page) {
        try {
            if (activity == null) return;
            Session s = session(activity);
            // The initial setPageText in onCreate runs before onSessionStart: ignored.
            if (s == null) return;
            boolean turned = page != s.lastPage;
            s.lastPage = page;
            // Only pages reached while timing is running count (feature off / id<=0 -> nothing).
            if (!s.running) return;
            if (turned) s.turns++;
            if (page > s.maxPage) s.maxPage = page;
            long now = SystemClock.elapsedRealtime();
            markActivity(s, now);
            if (now - s.lastFlush >= FLUSH_INTERVAL_MS) flush(s, now);
        } catch (Throwable t) {
            log("onPageChanged failed", t);
        }
    }

    /** First line of dispatchTouchEvent. Never consumes, never throws. */
    public static void onTouch(Activity activity, MotionEvent ev) {
        try {
            if (activity == null || ev == null) return;
            if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return;
            Session s = session(activity);
            if (s == null || !s.running) return;
            long now = SystemClock.elapsedRealtime();
            markActivity(s, now);
            if (now - s.lastFlush >= FLUSH_INTERVAL_MS) flush(s, now);
        } catch (Throwable t) {
            log("onTouch failed", t);
        }
    }

    /** onPause, after ReadingSessionHelper.onLeave and before super.onPause(). */
    public static void onPause(Activity activity, GenericGallery gallery) {
        try {
            if (activity == null) return;
            Session s = session(activity);
            if (s == null) return;
            long now = SystemClock.elapsedRealtime();
            if (!isEnabled(activity)) {
                // Switch off (re-checked here as well): never write, never create the table.
                s.running = false;
                s.pendingMs = 0;
                return;
            }
            if (s.running) {
                tick(s, now);
                s.running = false;
            }
            flush(s, now);
        } catch (Throwable t) {
            log("onPause failed", t);
        }
    }

    // ------------------------------------------------------------------ timing

    /** Credits all foreground time since countedUntil (no idle cap, no per-opening cap). */
    private static void tick(Session s, long now) {
        if (!s.running) return;
        if (now > s.countedUntil) {
            long d = now - s.countedUntil;
            s.pendingMs += d;
            s.sessionMs += d;
            s.countedUntil = now;
        }
    }

    private static void markActivity(Session s, long now) {
        tick(s, now);
        s.lastActivity = now;
    }

    // ------------------------------------------------------------------ database

    private static void flush(Session s, long nowElapsed) {
        s.lastFlush = nowElapsed;
        if (s.galleryId <= 0) return;
        int inc = (s.fresh && !s.counted) ? 1 : 0;
        int maxPage = Math.max(s.maxPage, 0);
        if (inc == 0 && s.pendingMs <= 0 && maxPage <= s.writtenMaxPage) return;
        SQLiteDatabase db = database();
        if (db == null || !db.isOpen()) return;   // count stays pending, retried at the next flush
        if (!ensureTable(db)) return;
        long wall = System.currentTimeMillis();
        db.beginTransaction();
        try {
            SQLiteStatement ins = db.compileStatement(
                "INSERT OR IGNORE INTO `ReadingStats` (`gallery`,`read_count`,`total_ms`,`first_read`,`last_read`,`max_page`) VALUES (?,0,0,?,?,0)");
            ins.bindLong(1, s.galleryId);
            ins.bindLong(2, wall);
            ins.bindLong(3, wall);
            ins.executeInsert();
            ins.close();
            SQLiteStatement up = db.compileStatement(
                "UPDATE `ReadingStats` SET `read_count`=`read_count`+?, `total_ms`=`total_ms`+?, `last_read`=?, `max_page`=MAX(`max_page`,?) WHERE `gallery`=?");
            up.bindLong(1, inc);
            up.bindLong(2, Math.max(0L, s.pendingMs));
            up.bindLong(3, wall);
            up.bindLong(4, maxPage);
            up.bindLong(5, s.galleryId);
            up.executeUpdateDelete();
            up.close();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (inc == 1) s.counted = true;
        s.pendingMs = 0;
        s.writtenMaxPage = maxPage;
        Log.i(TAG, "gallery " + s.galleryId + ": +" + inc + " count, session=" + s.sessionMs
            + "ms, turns=" + s.turns + ", max=" + maxPage);
    }

    private static boolean ensureTable(SQLiteDatabase db) {
        try {
            if (sCreatedOn == db) return true;
            db.execSQL(CREATE_SQL);
            sCreatedOn = db;
            return true;
        } catch (Throwable t) {
            log("create table failed", t);
            return false;
        }
    }

    private static boolean tableExists(SQLiteDatabase db) {
        if (sCreatedOn == db) return true;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{TABLE});
            boolean exists = c != null && c.moveToFirst();
            if (exists) sCreatedOn = db;
            return exists;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(c);
        }
    }

    /** @return {read_count, total_ms, first_read, last_read, max_page} or null. Never creates the table. */
    public static long[] query(int galleryId) {
        if (galleryId <= 0) return null;
        Cursor c = null;
        try {
            SQLiteDatabase db = database();
            if (db == null || !db.isOpen() || !tableExists(db)) return null;
            c = db.rawQuery("SELECT `read_count`,`total_ms`,`first_read`,`last_read`,`max_page` FROM `ReadingStats` WHERE `gallery`=?",
                new String[]{String.valueOf(galleryId)});
            if (c == null || !c.moveToFirst()) return null;
            return new long[]{c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4)};
        } catch (Throwable t) {
            log("query failed", t);
            return null;
        } finally {
            closeQuietly(c);
        }
    }

    /** Number of galleries with a stats row, or 0. Never creates the table. */
    public static int countRows() {
        Cursor c = null;
        try {
            SQLiteDatabase db = database();
            if (db == null || !db.isOpen() || !tableExists(db)) return 0;
            c = db.rawQuery("SELECT COUNT(*) FROM `ReadingStats`", null);
            if (c == null || !c.moveToFirst()) return 0;
            return c.getInt(0);
        } catch (Throwable t) {
            return 0;
        } finally {
            closeQuietly(c);
        }
    }

    /** Deletes only ReadingStats rows. History, Favorite, Downloads, Status, StatusManga, Resume untouched. */
    static boolean clear(int galleryId) {
        try {
            SQLiteDatabase db = database();
            if (db == null || !db.isOpen()) return false;
            if (!tableExists(db)) return true;
            if (galleryId > 0) {
                db.delete(TABLE, "`gallery`=?", new String[]{String.valueOf(galleryId)});
            } else {
                db.delete(TABLE, null, null);
            }
            return true;
        } catch (Throwable t) {
            log("clear failed", t);
            return false;
        }
    }

    private static SQLiteDatabase database() {
        // Queries.db is package-private in the real class: read it by reflection
        // (same approach as LocalTagFilterHelper), never through the public stub field.
        try {
            Class<?> q = Class.forName("com.maxwai.nclientv3.async.database.Queries");
            Field f = q.getDeclaredField("db");
            f.setAccessible(true);
            Object o = f.get(null);
            return o instanceof SQLiteDatabase ? (SQLiteDatabase) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ backup (Exporter / Importer)

    /**
     * Exporter.dumpDB, after the SCHEMAS loop and before writer.endObject(); db and writer are the
     * ones dumpDB uses. Writes "ReadingStats": [ {gallery, read_count, total_ms, first_read,
     * last_read, max_page}, ... ] in the same shape as the other tables (numbers via value(long)).
     * Writes NOTHING when the table does not exist or is empty, so backups of users who never used
     * the feature stay byte-compatible with older builds. Rows are read completely before the first
     * write: a database error skips the key and never leaves half-written JSON.
     */
    public static void exportTable(SQLiteDatabase db, JsonWriter writer) throws IOException {
        if (db == null || writer == null) return;
        List<long[]> rows = new ArrayList<long[]>();
        Cursor c = null;
        try {
            if (!db.isOpen() || !tableExists(db)) return;
            c = db.rawQuery("SELECT `gallery`,`read_count`,`total_ms`,`first_read`,`last_read`,`max_page` FROM `ReadingStats` ORDER BY `gallery`", null);
            if (c != null && c.moveToFirst()) {
                do {
                    long[] r = new long[COLUMNS.length];
                    for (int i = 0; i < COLUMNS.length; i++) r[i] = c.getLong(i);
                    rows.add(r);
                } while (c.moveToNext());
            }
        } catch (Throwable t) {
            log("export read failed, ReadingStats not exported", t);
            return;
        } finally {
            closeQuietly(c);
        }
        if (rows.isEmpty()) return;
        writer.name(TABLE).beginArray();
        for (long[] r : rows) {
            writer.beginObject();
            for (int i = 0; i < COLUMNS.length; i++) writer.name(COLUMNS[i]).value(r[i]);
            writer.endObject();
        }
        writer.endArray();
        Log.i(TAG, "exported " + rows.size() + " ReadingStats rows");
    }

    /**
     * Importer.importDB, just before db.delete(tableName) of each table found in the backup.
     * For "ReadingStats" it makes sure the table exists (fresh install / feature never used), so the
     * generic delete + insertWithOnConflict(REPLACE) path works. Not cached: we are inside the
     * importer's transaction, which may roll back.
     */
    public static void onImportTable(SQLiteDatabase db, String tableName) {
        try {
            if (db == null || !TABLE.equals(tableName)) return;
            db.execSQL(CREATE_SQL);
            Log.i(TAG, "import: ReadingStats table ensured");
        } catch (Throwable t) {
            log("import ensure table failed", t);
        }
    }

    // ------------------------------------------------------------------ GalleryActivity

    /** onCreateOptionsMenu, just before "return true". GalleryActivity.onResume re-invalidates the menu. */
    public static void setupGalleryMenu(Activity activity, Menu menu, GenericGallery gallery) {
        try {
            if (activity == null || menu == null) return;
            int id = gallery == null ? 0 : gallery.getId();
            long[] row = id > 0 ? query(id) : null;
            boolean show = id > 0 && (row != null || isEnabled(activity));
            MenuItem item = menu.findItem(MENU_ID_READING_STATS);
            if (!show) {
                if (item != null) menu.removeItem(MENU_ID_READING_STATS);
                return;
            }
            boolean zh = isZh(activity);
            String title;
            if (row == null) {
                title = zh ? "\u9605\u8bfb\u7edf\u8ba1\uff08\u6682\u65e0\uff09" : "Reading stats (none yet)";
            } else {
                title = (zh ? "\u9605\u8bfb\u7edf\u8ba1\uff08" : "Reading stats (")
                    + formatCount(row[0], zh) + " \u00b7 " + formatDuration(row[1], zh)
                    + (zh ? "\uff09" : ")");
            }
            if (item == null) {
                item = menu.add(0, MENU_ID_READING_STATS, 1001, title);
            } else {
                item.setTitle(title);
            }
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        } catch (Throwable t) {
            log("setupGalleryMenu failed", t);
        }
    }

    /** onOptionsItemSelected, first statement. Returns true only for our own item. */
    public static boolean onGalleryMenuItemSelected(Activity activity, MenuItem item, GenericGallery gallery) {
        try {
            if (item == null || item.getItemId() != MENU_ID_READING_STATS) return false;
            showStatsDialog(activity, gallery);
            return true;
        } catch (Throwable t) {
            log("onGalleryMenuItemSelected failed", t);
            return item != null && item.getItemId() == MENU_ID_READING_STATS;
        }
    }

    private static void showStatsDialog(final Activity activity, GenericGallery gallery) {
        if (activity == null || activity.isFinishing() || gallery == null) return;
        final int id = gallery.getId();
        if (id <= 0) return;
        final boolean zh = isZh(activity);
        long[] row = query(id);
        StringBuilder sb = new StringBuilder();
        if (row == null) {
            sb.append(zh ? "\u8fd9\u672c\u8fd8\u6ca1\u6709\u9605\u8bfb\u7edf\u8ba1\u3002" : "No reading stats for this gallery yet.");
            if (!isEnabled(activity)) {
                sb.append(zh ? "\n\u8bbe\u7f6e\u91cc\u300c\u8bb0\u5f55\u9605\u8bfb\u65f6\u957f\u4e0e\u6b21\u6570\u300d\u5f53\u524d\u5173\u95ed\u3002"
                    : "\n\"Record reading time and count\" is off in Settings.");
            }
        } else {
            int total = 0;
            try {
                total = gallery.getPageCount();
            } catch (Throwable ignored) {
            }
            sb.append(zh ? "\u9605\u8bfb\u6b21\u6570\uff1a" : "Times read: ").append(row[0]).append('\n');
            sb.append(zh ? "\u7d2f\u8ba1\u65f6\u957f\uff1a" : "Total time: ").append(formatDuration(row[1], zh)).append('\n');
            sb.append(zh ? "\u9996\u6b21\u9605\u8bfb\uff1a" : "First read: ").append(formatDate(row[2])).append('\n');
            sb.append(zh ? "\u4e0a\u6b21\u9605\u8bfb\uff1a" : "Last read: ").append(formatDate(row[3])).append('\n');
            sb.append(zh ? "\u6700\u8fdc\u5230\u8fbe\uff1a\u7b2c " : "Furthest page: ").append(row[4]);
            if (total > 0) sb.append(" / ").append(total);
            sb.append(zh ? " \u9875" : "");
        }
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
            .setTitle(zh ? "\u9605\u8bfb\u7edf\u8ba1" : "Reading stats")
            .setMessage(sb.toString())
            .setPositiveButton(zh ? "\u5173\u95ed" : "Close", null);
        if (row != null) {
            b.setNegativeButton(zh ? "\u6e05\u9664\u672c\u4f5c\u7edf\u8ba1" : "Clear for this gallery",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmClear(activity, id, null);
                    }
                });
        }
        b.create().show();
    }

    // ------------------------------------------------------------------ Settings

    /** GeneralPreferenceFragment.mainMenu() end and onResume(), next to the other helpers. */
    public static void bindPreference(final PreferenceFragmentCompat fragment) {
        try {
            if (fragment == null) return;
            final Preference clearPref = fragment.findPreference(PREF_CLEAR);
            if (clearPref == null) return;
            updateClearSummary(clearPref);
            clearPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    confirmClear(preference.getContext(), 0, preference);
                    return true;
                }
            });
        } catch (Throwable t) {
            log("bindPreference failed", t);
        }
    }

    private static void updateClearSummary(Preference pref) {
        try {
            Context ctx = pref.getContext();
            if (ctx == null) return;
            boolean zh = isZh(ctx);
            int n = countRows();
            pref.setSummary(zh
                ? "\u53ea\u6e05\u7a7a\u9605\u8bfb\u6b21\u6570\u4e0e\u65f6\u957f\u7edf\u8ba1\uff08\u5f53\u524d " + n + " \u672c\uff09\uff0c\u4e0d\u5f71\u54cd\u9605\u8bfb\u5386\u53f2\u3001\u6536\u85cf\u3001\u5df2\u4e0b\u8f7d\u3001\u9605\u8bfb\u72b6\u6001\u548c\u65ad\u70b9\u7eed\u8bfb"
                : "Only clears reading time and count (" + n + " galleries now). History, favorites, downloads, status and resume progress are not affected");
        } catch (Throwable t) {
            log("updateClearSummary failed", t);
        }
    }

    /** galleryId <= 0 means all galleries. Cancel is the default (focused) button. */
    static void confirmClear(final Context ctx, final int galleryId, final Preference pref) {
        try {
            if (ctx == null) return;
            if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return;
            final boolean zh = isZh(ctx);
            String title;
            String message;
            if (galleryId > 0) {
                title = zh ? "\u6e05\u9664\u672c\u4f5c\u9605\u8bfb\u7edf\u8ba1\uff1f" : "Clear reading stats for this gallery?";
                message = zh
                    ? "\u5c06\u5220\u9664\u8fd9\u672c\u7684\u9605\u8bfb\u6b21\u6570\u548c\u65f6\u957f\uff0c\u65e0\u6cd5\u64a4\u9500\u3002\n\u4e0d\u4f1a\u5f71\u54cd\u9605\u8bfb\u5386\u53f2\u3001\u6536\u85cf\u3001\u5df2\u4e0b\u8f7d\u3001\u9605\u8bfb\u72b6\u6001\u548c\u65ad\u70b9\u7eed\u8bfb\u3002"
                    : "This deletes the reading count and time of this gallery. This cannot be undone.\nHistory, favorites, downloads, status and resume progress are not affected.";
            } else {
                int n = countRows();
                title = zh ? "\u6e05\u7a7a\u5168\u90e8\u9605\u8bfb\u7edf\u8ba1\uff1f" : "Clear all reading stats?";
                message = zh
                    ? "\u5c06\u5220\u9664\u5168\u90e8 " + n + " \u672c\u7684\u9605\u8bfb\u6b21\u6570\u548c\u65f6\u957f\uff0c\u65e0\u6cd5\u64a4\u9500\uff08\u5df2\u5bfc\u51fa\u7684\u5907\u4efd\u6587\u4ef6\u4e0d\u53d7\u5f71\u54cd\uff09\u3002\n\u4e0d\u4f1a\u5f71\u54cd\u9605\u8bfb\u5386\u53f2\u3001\u6536\u85cf\u3001\u5df2\u4e0b\u8f7d\u3001\u9605\u8bfb\u72b6\u6001\u548c\u65ad\u70b9\u7eed\u8bfb\u3002"
                    : "This deletes the reading count and time of all " + n + " galleries. This cannot be undone (backup files already exported are not affected).\nHistory, favorites, downloads, status and resume progress are not affected.";
            }
            final AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(zh ? "\u53d6\u6d88" : "Cancel", null)
                .setPositiveButton(zh ? "\u6e05\u9664" : "Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        boolean ok = clear(galleryId);
                        try {
                            Toast.makeText(ctx.getApplicationContext(), ok
                                ? (zh ? "\u5df2\u6e05\u9664\u9605\u8bfb\u7edf\u8ba1" : "Reading stats cleared")
                                : (zh ? "\u6e05\u9664\u5931\u8d25" : "Clear failed"), Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) {
                        }
                        if (pref != null) updateClearSummary(pref);
                        if (ctx instanceof Activity) {
                            try {
                                ((Activity) ctx).invalidateOptionsMenu();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                })
                .create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    try {
                        View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                        if (cancel != null) cancel.requestFocus();
                    } catch (Throwable ignored) {
                    }
                }
            });
            dialog.show();
        } catch (Throwable t) {
            log("confirmClear failed", t);
        }
    }

    // ------------------------------------------------------------------ utils

    private static Session session(Activity activity) {
        synchronized (SESSIONS) {
            return SESSIONS.get(activity);
        }
    }

    private static boolean isEnabled(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
            return sp.getBoolean(PREF_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    static String formatCount(long n, boolean zh) {
        return zh ? (n + " \u6b21") : (n == 1 ? "1 time" : n + " times");
    }

    static String formatDuration(long ms, boolean zh) {
        long min = ms / 60000L;
        if (min < 1) return zh ? "\u4e0d\u8db3 1 \u5206\u949f" : "< 1 min";
        long h = min / 60L;
        long m = min % 60L;
        if (h == 0) return zh ? (m + " \u5206\u949f") : (m + " min");
        if (zh) return m == 0 ? (h + " \u5c0f\u65f6") : (h + " \u5c0f\u65f6 " + m + " \u5206\u949f");
        return m == 0 ? (h + " h") : (h + " h " + m + " min");
    }

    private static String formatDate(long wall) {
        if (wall <= 0) return "-";
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(wall));
        } catch (Throwable t) {
            return "-";
        }
    }

    private static boolean isZh(Context ctx) {
        try {
            Locale locale;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                locale = ctx.getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = ctx.getResources().getConfiguration().locale;
            }
            if (locale == null) return false;
            String lang = locale.getLanguage();
            return lang != null && lang.toLowerCase(Locale.US).startsWith("zh");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void closeQuietly(Cursor c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    private static void log(String msg, Throwable t) {
        try {
            Log.w(TAG, msg, t);
        } catch (Throwable ignored) {
        }
    }
}
