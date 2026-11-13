package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.GalleryActivity;
import com.maxwai.nclientv3.api.components.GenericGallery;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 *  - binds "view_reading_stats" (list of every ReadingStats row, read-only, never creates the
 *    table) and "clear_reading_stats".
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
    public static final String PREF_VIEW = "view_reading_stats";
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
            final Preference viewPref = fragment.findPreference(PREF_VIEW);
            if (viewPref != null) {
                updateViewSummary(viewPref);
                viewPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showAllStats(preference.getContext(), preference);
                        return true;
                    }
                });
            }
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
        confirmClear(ctx, galleryId, pref, null);
    }

    /** As above; afterClear (may be null) runs on the UI thread after a successful clear. */
    static void confirmClear(final Context ctx, final int galleryId, final Preference pref, final Runnable afterClear) {
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
                        if (pref != null) refreshSummaries(pref);
                        if (ok && afterClear != null) {
                            try {
                                afterClear.run();
                            } catch (Throwable t) {
                                log("afterClear failed", t);
                            }
                        }
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

    // ------------------------------------------------------------------ Settings: all stats list

    static final int SORT_LAST_READ = 0;
    static final int SORT_TIME = 1;
    static final int SORT_COUNT = 2;
    private static int sSortMode = SORT_LAST_READ;

    /** One ReadingStats row plus a best-effort title (null when unknown). Pure Java. */
    static final class Entry {
        final int gallery;
        final long count;
        final long totalMs;
        final long firstRead;
        final long lastRead;
        final long maxPage;
        final String title;

        Entry(int gallery, long count, long totalMs, long firstRead, long lastRead, long maxPage, String title) {
            this.gallery = gallery;
            this.count = count;
            this.totalMs = totalMs;
            this.firstRead = firstRead;
            this.lastRead = lastRead;
            this.maxPage = maxPage;
            this.title = title;
        }

        String displayTitle() {
            return title == null || title.trim().isEmpty() ? "#" + gallery : title;
        }
    }

    /** Descending by last read / total time / count; ties by last read desc, then id asc. */
    static void sortEntries(List<Entry> list, final int mode) {
        if (list == null) return;
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int r = 0;
                if (mode == SORT_TIME) r = Long.compare(b.totalMs, a.totalMs);
                else if (mode == SORT_COUNT) r = Long.compare(b.count, a.count);
                if (r == 0) r = Long.compare(b.lastRead, a.lastRead);
                if (r == 0) r = Integer.compare(a.gallery, b.gallery);
                return r;
            }
        });
    }

    static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    /**
     * Every ReadingStats row, titles from History.title, then Gallery.title_pretty / title_eng /
     * title_jp. Read-only; never creates the table. If the title lookup fails for any reason the
     * rows are read again without titles ("#id").
     */
    static List<Entry> queryAll() {
        List<Entry> out = new ArrayList<Entry>();
        SQLiteDatabase db = database();
        if (db == null || !db.isOpen() || !tableExists(db)) return out;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT r.`gallery`,r.`read_count`,r.`total_ms`,r.`first_read`,r.`last_read`,r.`max_page`,"
                + "(SELECT h.`title` FROM `History` h WHERE h.`id`=r.`gallery` LIMIT 1),"
                + "(SELECT g.`title_pretty` FROM `Gallery` g WHERE g.`idGallery`=r.`gallery` LIMIT 1),"
                + "(SELECT g.`title_eng` FROM `Gallery` g WHERE g.`idGallery`=r.`gallery` LIMIT 1),"
                + "(SELECT g.`title_jp` FROM `Gallery` g WHERE g.`idGallery`=r.`gallery` LIMIT 1)"
                + " FROM `ReadingStats` r", null);
            readEntries(c, out, true);
            return out;
        } catch (Throwable t) {
            log("queryAll with titles failed, retrying without titles", t);
        } finally {
            closeQuietly(c);
        }
        out.clear();
        c = null;
        try {
            c = db.rawQuery("SELECT `gallery`,`read_count`,`total_ms`,`first_read`,`last_read`,`max_page` FROM `ReadingStats`", null);
            readEntries(c, out, false);
        } catch (Throwable t) {
            log("queryAll failed", t);
            out.clear();
        } finally {
            closeQuietly(c);
        }
        return out;
    }

    private static void readEntries(Cursor c, List<Entry> out, boolean withTitles) {
        if (c == null || !c.moveToFirst()) return;
        do {
            String title = null;
            if (withTitles) {
                title = firstNonEmpty(c.isNull(6) ? null : c.getString(6), c.isNull(7) ? null : c.getString(7),
                    c.isNull(8) ? null : c.getString(8), c.isNull(9) ? null : c.getString(9));
            }
            out.add(new Entry((int) c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5), title));
        } while (c.moveToNext());
    }

    /** @return {rows, sum(read_count), sum(total_ms)}; zeros when the table does not exist. */
    static long[] totals() {
        Cursor c = null;
        try {
            SQLiteDatabase db = database();
            if (db == null || !db.isOpen() || !tableExists(db)) return new long[3];
            c = db.rawQuery("SELECT COUNT(*), IFNULL(SUM(`read_count`),0), IFNULL(SUM(`total_ms`),0) FROM `ReadingStats`", null);
            if (c == null || !c.moveToFirst()) return new long[3];
            return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        } catch (Throwable t) {
            return new long[3];
        } finally {
            closeQuietly(c);
        }
    }

    private static void updateViewSummary(Preference pref) {
        try {
            Context ctx = pref.getContext();
            if (ctx == null) return;
            boolean zh = isZh(ctx);
            long[] t = totals();
            String s;
            if (t[0] <= 0) {
                s = zh ? "\u8fd8\u6ca1\u6709\u9605\u8bfb\u7edf\u8ba1" : "No reading stats yet";
            } else {
                s = zh
                    ? "\u5171 " + t[0] + " \u672c\uff0c\u7d2f\u8ba1 " + formatDuration(t[2], true) + " / " + t[1] + " \u6b21"
                    : t[0] + " galleries, " + formatDuration(t[2], false) + " in total / " + formatCount(t[1], false);
            }
            if (!isEnabled(ctx)) s += zh ? "\uff08\u8bb0\u5f55\u5f00\u5173\u5df2\u5173\u95ed\uff09" : " (recording is off)";
            pref.setSummary(s);
        } catch (Throwable t) {
            log("updateViewSummary failed", t);
        }
    }

    /** Refreshes the summaries of both stats preferences (view + clear) of the same screen. */
    private static void refreshSummaries(Preference pref) {
        try {
            if (pref == null) return;
            Preference view = null;
            Preference clear = null;
            if (pref.getPreferenceManager() != null) {
                view = pref.getPreferenceManager().findPreference(PREF_VIEW);
                clear = pref.getPreferenceManager().findPreference(PREF_CLEAR);
            }
            if (view == null && PREF_VIEW.equals(pref.getKey())) view = pref;
            if (clear == null && PREF_CLEAR.equals(pref.getKey())) clear = pref;
            if (view != null) updateViewSummary(view);
            if (clear != null) updateClearSummary(clear);
        } catch (Throwable t) {
            log("refreshSummaries failed", t);
        }
    }

    private static String sortLabel(int mode, boolean zh) {
        if (mode == SORT_TIME) return zh ? "\u6392\u5e8f\uff1a\u65f6\u957f" : "Sort: time";
        if (mode == SORT_COUNT) return zh ? "\u6392\u5e8f\uff1a\u6b21\u6570" : "Sort: count";
        return zh ? "\u6392\u5e8f\uff1a\u6700\u8fd1" : "Sort: recent";
    }

    /** Settings "view_reading_stats": one line per gallery; tap a line for its details. */
    static void showAllStats(final Context ctx, final Preference pref) {
        try {
            if (ctx == null) return;
            if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return;
            final boolean zh = isZh(ctx);
            final boolean enabled = isEnabled(ctx);
            final List<Entry> entries = queryAll();
            if (pref != null) refreshSummaries(pref);
            if (entries.isEmpty()) {
                String msg = zh ? "\u8fd8\u6ca1\u6709\u4efb\u4f55\u9605\u8bfb\u7edf\u8ba1\u3002\u6253\u5f00\u9605\u8bfb\u9875\u770b\u6f2b\u753b\u540e\uff0c\u8fd9\u91cc\u4f1a\u6309\u4f5c\u54c1\u5217\u51fa\u9605\u8bfb\u6b21\u6570\u548c\u65f6\u957f\u3002"
                    : "No reading stats yet. Galleries you read will be listed here with their count and time.";
                if (!enabled) {
                    msg += zh ? "\n\n\u8bbe\u7f6e\u91cc\u300c\u8bb0\u5f55\u9605\u8bfb\u65f6\u957f\u4e0e\u6b21\u6570\u300d\u5f53\u524d\u5173\u95ed\uff0c\u6253\u5f00\u540e\u624d\u4f1a\u5f00\u59cb\u8bb0\u5f55\u3002"
                        : "\n\n\"Record reading time and count\" is off in Settings; turn it on to start recording.";
                }
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle(zh ? "\u9605\u8bfb\u7edf\u8ba1" : "Reading stats")
                    .setMessage(msg)
                    .setPositiveButton(zh ? "\u5173\u95ed" : "Close", null)
                    .show();
                return;
            }
            sortEntries(entries, sSortMode);
            final EntryAdapter adapter = new EntryAdapter(ctx, entries, zh);
            String title = zh ? "\u9605\u8bfb\u7edf\u8ba1\uff08" + entries.size() + " \u672c\uff09" : "Reading stats (" + entries.size() + ")";
            if (!enabled) title += zh ? " \u00b7 \u8bb0\u5f55\u5df2\u5173\u95ed" : " \u00b7 recording off";
            final AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Entry e = adapter.getItem(which);
                        if (e != null) showEntryDetail(ctx, e, pref);
                    }
                })
                .setNeutralButton(sortLabel(sSortMode, zh), null)
                .setPositiveButton(zh ? "\u5173\u95ed" : "Close", null)
                .create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    try {
                        final Button sort = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                        if (sort == null) return;
                        // Replaces the default listener so the dialog stays open while re-sorting.
                        sort.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sSortMode = (sSortMode + 1) % 3;
                                sortEntries(entries, sSortMode);
                                adapter.notifyDataSetChanged();
                                sort.setText(sortLabel(sSortMode, zh));
                                ListView lv = dialog.getListView();
                                if (lv != null) lv.setSelection(0);
                            }
                        });
                    } catch (Throwable t) {
                        log("sort button setup failed", t);
                    }
                }
            });
            dialog.show();
        } catch (Throwable t) {
            log("showAllStats failed", t);
        }
    }

    /** Per-gallery details from the Settings list. Back returns to the list. */
    private static void showEntryDetail(final Context ctx, final Entry e, final Preference pref) {
        try {
            if (ctx == null || e == null) return;
            if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return;
            final boolean zh = isZh(ctx);
            StringBuilder sb = new StringBuilder();
            sb.append(zh ? "\u7f16\u53f7\uff1a#" : "ID: #").append(e.gallery).append('\n');
            sb.append(zh ? "\u9605\u8bfb\u6b21\u6570\uff1a" : "Times read: ").append(e.count).append('\n');
            sb.append(zh ? "\u7d2f\u8ba1\u65f6\u957f\uff1a" : "Total time: ").append(formatDuration(e.totalMs, zh)).append('\n');
            sb.append(zh ? "\u9996\u6b21\u9605\u8bfb\uff1a" : "First read: ").append(formatDate(e.firstRead)).append('\n');
            sb.append(zh ? "\u4e0a\u6b21\u9605\u8bfb\uff1a" : "Last read: ").append(formatDate(e.lastRead)).append('\n');
            sb.append(zh ? "\u6700\u8fdc\u5230\u8fbe\uff1a\u7b2c " : "Furthest page: ").append(e.maxPage).append(zh ? " \u9875" : "");
            new MaterialAlertDialogBuilder(ctx)
                .setTitle(e.displayTitle())
                .setMessage(sb.toString())
                .setPositiveButton(zh ? "\u6253\u5f00\u6f2b\u753b" : "Open gallery", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        openGallery(ctx, e.gallery);
                    }
                })
                .setNeutralButton(zh ? "\u8fd4\u56de\u5217\u8868" : "Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        showAllStats(ctx, pref);
                    }
                })
                .setNegativeButton(zh ? "\u6e05\u9664\u672c\u4f5c\u7edf\u8ba1" : "Clear for this gallery", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        confirmClear(ctx, e.gallery, pref, new Runnable() {
                            @Override
                            public void run() {
                                showAllStats(ctx, pref);
                            }
                        });
                    }
                })
                .show();
        } catch (Throwable t) {
            log("showEntryDetail failed", t);
        }
    }

    /** Same path as an nhentai.net/g/<id>/ link: GalleryActivity loads the gallery by id. */
    private static void openGallery(Context ctx, int id) {
        try {
            if (ctx == null || id <= 0) return;
            Intent intent = new Intent(ctx, GalleryActivity.class);
            intent.setData(Uri.parse(Utility.getBaseUrl() + "g/" + id + "/"));
            if (!(ctx instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Throwable t) {
            log("openGallery failed", t);
        }
    }

    private static final class EntryAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<Entry> entries;
        private final boolean zh;

        EntryAdapter(Context ctx, List<Entry> entries, boolean zh) {
            this.ctx = ctx;
            this.entries = entries;
            this.zh = zh;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Entry getItem(int position) {
            return position >= 0 && position < entries.size() ? entries.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            Entry e = getItem(position);
            return e == null ? position : e.gallery;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView sub;
            if (convertView instanceof LinearLayout && ((LinearLayout) convertView).getChildCount() == 2) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
                sub = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.VERTICAL);
                int h = dp(24);
                int v = dp(10);
                row.setPadding(h, v, h, v);
                title = new TextView(ctx);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                sub = new TextView(ctx);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                sub.setAlpha(0.7f);
                row.addView(title);
                row.addView(sub);
            }
            Entry e = getItem(position);
            if (e != null) {
                title.setText(e.displayTitle());
                StringBuilder sb = new StringBuilder();
                if (e.title != null && !e.title.trim().isEmpty()) sb.append('#').append(e.gallery).append(" \u00b7 ");
                sb.append(formatCount(e.count, zh)).append(" \u00b7 ").append(formatDuration(e.totalMs, zh))
                    .append(" \u00b7 ").append(zh ? "\u4e0a\u6b21 " : "last ").append(formatDate(e.lastRead));
                sub.setText(sb.toString());
            }
            return row;
        }

        private int dp(int value) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics());
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
