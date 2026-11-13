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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.utility.LogUtility;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * e-hentai data in its own database file (ehentai.db), so the main Entries.db schema and its
 * helper are left untouched. Tables: reading history (with resume page and reading time),
 * local favorites, and downloads.
 */
public final class EhDatabase extends SQLiteOpenHelper {
    static final ExecutorService READING_IO = Executors.newSingleThreadExecutor();
    public static final String NAME = "ehentai.db";
    private static final int VERSION = 3;

    public static final String HISTORY = "history";
    public static final String FAVORITES = "favorites";
    public static final String DOWNLOADS = "downloads";
    private static final String STATUS = "reading_status";
    private static final String LEGACY_DIRS = "legacy_download_dirs";
    private static final String STATS = "reading_stats";
    static final String[] TABLES = {HISTORY, FAVORITES, DOWNLOADS, STATUS, LEGACY_DIRS, STATS};
    private static final String LEGACY_STATS_SQL = "INSERT OR IGNORE INTO " + STATS
        + " (gid,token,ex,title,thumb,category,pages,read_count,total_ms,first_read,stats_last_read,max_page) "
        + "SELECT gid,token,ex,title,thumb,category,pages,read_count,read_seconds*1000,last_read,last_read,last_page "
        + "FROM " + HISTORY + " WHERE (read_count>0 OR read_seconds>0)";

    public static final int STATE_QUEUED = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_PAUSED = 4;

    private static final String GALLERY_COLUMNS =
        "gid INTEGER NOT NULL, token TEXT NOT NULL, ex INTEGER NOT NULL DEFAULT 0, title TEXT, "
            + "thumb TEXT, category INTEGER NOT NULL DEFAULT 0, pages INTEGER NOT NULL DEFAULT 0";
    private static final String BY_SOURCE = "ex=? AND gid=?";
    private static final String BY_DOWNLOAD = BY_SOURCE + " AND token=?";
    private static final String IDENTITY = ", PRIMARY KEY (ex, gid))";
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
        "gid", "token", "ex", "title", "thumb", "category", "pages"));
    private static final Map<String, Set<String>> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put(HISTORY, with("last_page", "read_count", "read_seconds", "last_read"));
        COLUMNS.put(FAVORITES, with("added"));
        COLUMNS.put(DOWNLOADS, with("state", "done", "added"));
        COLUMNS.put(STATUS, new HashSet<>(Arrays.asList("gid", "ex", "token", "status")));
        COLUMNS.put(LEGACY_DIRS, new HashSet<>(Arrays.asList("gid", "ex", "token")));
        COLUMNS.put(STATS, with("read_count", "total_ms", "first_read", "stats_last_read", "max_page"));
    }

    private static Set<String> with(String... extra) {
        Set<String> s = new HashSet<>(COMMON);
        s.addAll(Arrays.asList(extra));
        return s;
    }

    private static volatile EhDatabase instance;

    private EhDatabase(@NonNull Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @NonNull
    public static EhDatabase get(@NonNull Context context) {
        EhDatabase db = instance;
        if (db == null) {
            synchronized (EhDatabase.class) {
                db = instance;
                if (db == null) instance = db = new EhDatabase(context);
            }
        }
        return db;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createLibrary(db);
        createStats(db);
    }

    private static void createLibrary(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + HISTORY + " (" + GALLERY_COLUMNS + ", last_page INTEGER NOT NULL DEFAULT 1, "
            + "read_count INTEGER NOT NULL DEFAULT 0, read_seconds INTEGER NOT NULL DEFAULT 0, "
            + "last_read INTEGER NOT NULL DEFAULT 0" + IDENTITY);
        db.execSQL("CREATE TABLE " + FAVORITES + " (" + GALLERY_COLUMNS + ", added INTEGER NOT NULL DEFAULT 0" + IDENTITY);
        db.execSQL("CREATE TABLE " + DOWNLOADS + " (" + GALLERY_COLUMNS + ", state INTEGER NOT NULL DEFAULT 0, "
            + "done INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL DEFAULT 0" + IDENTITY);
        db.execSQL("CREATE TABLE " + STATUS + " (gid INTEGER NOT NULL, ex INTEGER NOT NULL DEFAULT 0, "
            + "token TEXT NOT NULL, status TEXT NOT NULL" + IDENTITY);
        db.execSQL("CREATE TABLE " + LEGACY_DIRS + " (gid INTEGER NOT NULL, token TEXT NOT NULL, "
            + "ex INTEGER NOT NULL, PRIMARY KEY (gid, token))");
    }

    private static void createStats(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + STATS + " (" + GALLERY_COLUMNS + ", read_count INTEGER NOT NULL DEFAULT 0, "
            + "total_ms INTEGER NOT NULL DEFAULT 0, first_read INTEGER NOT NULL DEFAULT 0, "
            + "stats_last_read INTEGER NOT NULL DEFAULT 0, max_page INTEGER NOT NULL DEFAULT 0" + IDENTITY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            String[] oldTables = {HISTORY, FAVORITES, DOWNLOADS};
            for (String table : oldTables) db.execSQL("ALTER TABLE " + table + " RENAME TO " + table + "_v1");
            createLibrary(db);
            for (String table : oldTables) {
                db.execSQL("INSERT INTO " + table + " SELECT * FROM " + table + "_v1");
                db.execSQL("DROP TABLE " + table + "_v1");
            }
            db.execSQL("INSERT INTO " + LEGACY_DIRS + " (gid, token, ex) SELECT gid, token, ex FROM " + DOWNLOADS);
        }
        if (oldVersion < 3) {
            createStats(db);
            db.execSQL(LEGACY_STATS_SQL);
        }
    }

    /** Row of one of the tables, with the extra columns that table has. */
    public static final class Entry extends EhGalleryInfo {
        public int lastPage = 1;
        public long readCount;
        public long totalMs;
        public long firstRead;
        public int maxPage;
        public long time;
        public int state;
        public int done;

        Entry(@NonNull EhGalleryKey key) {
            super(key);
        }
    }

    @NonNull
    private static ContentValues galleryValues(@NonNull EhGalleryInfo info) {
        ContentValues v = new ContentValues();
        v.put("gid", info.key.gid);
        v.put("token", info.key.token);
        v.put("ex", info.key.exhentai ? 1 : 0);
        v.put("title", info.title);
        v.put("thumb", info.thumb);
        v.put("category", info.category.bit);
        v.put("pages", info.pages);
        return v;
    }

    @NonNull
    private static Entry read(@NonNull Cursor c) {
        Entry e = new Entry(new EhGalleryKey(c.getLong(c.getColumnIndexOrThrow("gid")),
            c.getString(c.getColumnIndexOrThrow("token")), c.getInt(c.getColumnIndexOrThrow("ex")) != 0));
        String title = c.getString(c.getColumnIndexOrThrow("title"));
        e.title = title == null ? "" : title;
        e.thumb = c.getString(c.getColumnIndexOrThrow("thumb"));
        e.category = EhCategory.fromBit(c.getInt(c.getColumnIndexOrThrow("category")));
        e.pages = c.getInt(c.getColumnIndexOrThrow("pages"));
        int i;
        if ((i = c.getColumnIndex("last_page")) >= 0) e.lastPage = c.getInt(i);
        if ((i = c.getColumnIndex("read_count")) >= 0) e.readCount = c.getLong(i);
        if ((i = c.getColumnIndex("total_ms")) >= 0) e.totalMs = c.getLong(i);
        if ((i = c.getColumnIndex("first_read")) >= 0) e.firstRead = c.getLong(i);
        if ((i = c.getColumnIndex("max_page")) >= 0) e.maxPage = c.getInt(i);
        if ((i = c.getColumnIndex("last_read")) >= 0) e.time = c.getLong(i);
        if ((i = c.getColumnIndex("stats_last_read")) >= 0) e.time = c.getLong(i);
        if ((i = c.getColumnIndex("added")) >= 0) e.time = c.getLong(i);
        if ((i = c.getColumnIndex("state")) >= 0) e.state = c.getInt(i);
        if ((i = c.getColumnIndex("done")) >= 0) e.done = c.getInt(i);
        return e;
    }

    @NonNull
    private List<Entry> list(@NonNull String table, @NonNull String order) {
        return list(table, null, order, 0);
    }

    private List<Entry> list(String table, @Nullable String where, String order, int limit) {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(table, null, where, null, null, null, order,
            limit > 0 ? Integer.toString(limit) : null)) {
            while (c.moveToNext()) out.add(read(c));
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
        }
        return out;
    }

    @Nullable
    private Entry one(@NonNull String table, @NonNull EhGalleryKey key) {
        boolean download = DOWNLOADS.equals(table);
        try (Cursor c = getReadableDatabase().query(table, null, download ? BY_DOWNLOAD : BY_SOURCE,
            download ? downloadArgs(key) : sourceArgs(key),
            null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
            return null;
        }
    }

    private static String[] sourceArgs(EhGalleryKey key) {
        return new String[]{key.exhentai ? "1" : "0", Long.toString(key.gid)};
    }

    private static String[] downloadArgs(EhGalleryKey key) {
        return new String[]{key.exhentai ? "1" : "0", Long.toString(key.gid), key.token};
    }

    /** Updates the gallery columns of an existing row, or inserts one with {@code extra}. */
    private void upsert(@NonNull String table, @NonNull EhGalleryInfo info, @NonNull ContentValues extra) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = galleryValues(info);
        if (info.title == null || info.title.isEmpty()) v.remove("title");
        if (info.thumb == null) v.remove("thumb");
        if (info.pages <= 0) v.remove("pages");
        if (info.category == EhCategory.UNKNOWN) v.remove("category");
        v.putAll(extra);
        db.beginTransaction();
        try {
            if (db.update(table, v, BY_SOURCE, sourceArgs(info.key)) == 0) {
                ContentValues all = galleryValues(info);
                all.putAll(extra);
                db.insertOrThrow(table, null, all);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---- history ----

    public void recordVisit(@NonNull EhGalleryInfo info) {
        try {
            ContentValues extra = new ContentValues();
            extra.put("last_read", System.currentTimeMillis());
            upsert(HISTORY, info, extra);
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db write failed", e);
        }
    }

    public void saveProgress(@NonNull EhGalleryInfo info, int page) {
        try {
            ContentValues extra = new ContentValues();
            extra.put("last_page", Math.max(1, page));
            upsert(HISTORY, info, extra);
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db write failed", e);
        }
    }

    /** Last page read (1-based), or 0 when the gallery was never opened. */
    public int lastPage(@NonNull EhGalleryKey key) {
        Entry e = one(HISTORY, key);
        return e == null ? 0 : e.lastPage;
    }

    @NonNull
    public List<Entry> history() {
        return history(0);
    }

    @NonNull
    public List<Entry> history(int limit) {
        return list(HISTORY, "last_read>0", "last_read DESC, ex, gid", limit);
    }

    public int historyCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + HISTORY + " WHERE last_read>0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public long historySignature() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), MAX(last_read), SUM(gid), SUM(ex) FROM "
            + HISTORY + " WHERE last_read>0", null)) {
            return c.moveToFirst() ? ((c.getLong(0) * 31 + c.getLong(1)) * 31 + c.getLong(2)) * 31 + c.getLong(3) : 0;
        }
    }

    public void removeHistory(@NonNull EhGalleryKey key) {
        hideHistory(BY_SOURCE, sourceArgs(key));
    }

    public void clearHistory() {
        hideHistory("last_read>0", null);
    }

    private void hideHistory(String where, @Nullable String[] args) {
        ContentValues values = new ContentValues();
        values.put("last_read", 0);
        getWritableDatabase().update(HISTORY, values, where, args);
    }

    public void addReadingStats(EhGalleryInfo info, long count, long elapsedMs, long now, int maxPage) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsert(STATS, info, new ContentValues());
            db.execSQL("UPDATE " + STATS + " SET read_count=read_count+?, total_ms=total_ms+?, "
                    + "first_read=CASE WHEN first_read=0 THEN ? ELSE first_read END, "
                    + "stats_last_read=MAX(stats_last_read, ?), max_page=MAX(max_page, ?) WHERE " + BY_SOURCE,
                new Object[]{count, elapsedMs, now, now, maxPage, info.key.exhentai ? 1 : 0, info.key.gid});
            db.execSQL("UPDATE " + HISTORY + " SET read_count=read_count+?, "
                    + "read_seconds=(SELECT total_ms/1000 FROM " + STATS + " WHERE " + BY_SOURCE + ") WHERE " + BY_SOURCE,
                new Object[]{count, info.key.exhentai ? 1 : 0, info.key.gid, info.key.exhentai ? 1 : 0, info.key.gid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Nullable
    public Entry readingStats(EhGalleryKey key) {
        return one(STATS, key);
    }

    @NonNull
    public List<Entry> readingStats() {
        return list(STATS, "stats_last_read DESC");
    }

    public void clearReadingStats(@Nullable EhGalleryKey key) {
        SQLiteDatabase db = getWritableDatabase();
        String where = key == null ? null : BY_SOURCE;
        String[] args = key == null ? null : sourceArgs(key);
        db.beginTransaction();
        try {
            db.delete(STATS, where, args);
            ContentValues legacy = new ContentValues();
            legacy.put("read_count", 0);
            legacy.put("read_seconds", 0);
            db.update(HISTORY, legacy, where, args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public long[] readingTotals() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(read_count),0), "
            + "COALESCE(SUM(total_ms),0) FROM " + STATS, null)) {
            if (c.moveToFirst()) return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        }
        return new long[3];
    }

    /** {galleries read, total pages of those galleries, total reading milliseconds}. */
    @NonNull
    public long[] stats() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(pages),0), "
            + "COALESCE(SUM(total_ms),0) FROM " + STATS, null)) {
            if (c.moveToFirst()) return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
        }
        return new long[]{0, 0, 0};
    }

    // ---- local favorites ----

    public boolean isFavorite(@NonNull EhGalleryKey key) {
        return one(FAVORITES, key) != null;
    }

    public void setFavorite(@NonNull EhGalleryInfo info, boolean favorite) {
        if (!favorite) {
            getWritableDatabase().delete(FAVORITES, BY_SOURCE, sourceArgs(info.key));
            return;
        }
        ContentValues extra = new ContentValues();
        extra.put("added", System.currentTimeMillis());
        upsert(FAVORITES, info, extra);
    }

    public void refreshFavorite(@NonNull EhGalleryInfo info) {
        ContentValues values = galleryValues(info);
        if (info.title.isEmpty()) values.remove("title");
        if (info.thumb == null) values.remove("thumb");
        if (info.pages <= 0) values.remove("pages");
        if (info.category == EhCategory.UNKNOWN) values.remove("category");
        getWritableDatabase().update(FAVORITES, values, BY_SOURCE, sourceArgs(info.key));
    }

    @NonNull
    public List<Entry> favorites() {
        return list(FAVORITES, "added DESC");
    }

    // ---- downloads ----

    public void putDownload(@NonNull EhGalleryInfo info, int state) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues extra = new ContentValues();
            extra.put("state", state);
            if (download(info.key) == null) {
                extra.put("added", System.currentTimeMillis());
                extra.put("done", 0);
            }
            upsert(DOWNLOADS, info, extra);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setDownloadProgress(@NonNull EhGalleryKey key, int state, int done) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        if (done >= 0) v.put("done", done);
        getWritableDatabase().update(DOWNLOADS, v, BY_DOWNLOAD, downloadArgs(key));
    }

    @Nullable
    public Entry download(@NonNull EhGalleryKey key) {
        return one(DOWNLOADS, key);
    }

    @NonNull
    public List<Entry> downloads() {
        return list(DOWNLOADS, "added DESC");
    }

    public void removeDownload(@NonNull EhGalleryKey key) {
        getWritableDatabase().delete(DOWNLOADS, BY_DOWNLOAD, downloadArgs(key));
    }

    boolean ownsLegacyDownloadDir(@NonNull EhGalleryKey key) {
        try (Cursor c = getReadableDatabase().query(LEGACY_DIRS, new String[]{"gid"}, BY_DOWNLOAD,
            downloadArgs(key), null, null, null)) {
            return c.moveToFirst();
        }
    }

    @Nullable
    public String readingStatus(@NonNull EhGalleryKey key) {
        try (Cursor c = getReadableDatabase().query(STATUS, new String[]{"status"}, BY_SOURCE,
            sourceArgs(key), null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public void setReadingStatus(@NonNull EhGalleryKey key, @Nullable String status) {
        SQLiteDatabase db = getWritableDatabase();
        if (status == null) {
            db.delete(STATUS, BY_SOURCE, sourceArgs(key));
            return;
        }
        ContentValues values = new ContentValues();
        values.put("gid", key.gid);
        values.put("ex", key.exhentai ? 1 : 0);
        values.put("token", key.token);
        values.put("status", status);
        db.insertWithOnConflict(STATUS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---- backup ----

    @NonNull
    Map<String, List<Map<String, Object>>> dump() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        for (String table : TABLES) {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Cursor c = db.query(table, null, null, null, null, null, null)) {
                while (c.moveToNext()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        switch (c.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                row.put(c.getColumnName(i), c.getLong(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                row.put(c.getColumnName(i), c.getDouble(i));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                row.put(c.getColumnName(i), c.getString(i));
                                break;
                            default:
                                row.put(c.getColumnName(i), null);
                        }
                    }
                    rows.add(row);
                }
            }
            out.put(table, rows);
        }
        return out;
    }

    /** Merges backed-up rows by source and id. Returns rows written. */
    int restore(@NonNull Map<String, List<Map<String, Object>>> tables) {
        SQLiteDatabase db = getWritableDatabase();
        int written = 0;
        db.beginTransaction();
        try {
            for (Map.Entry<String, List<Map<String, Object>>> t : tables.entrySet()) {
                Set<String> allowed = COLUMNS.get(t.getKey());
                if (allowed == null) continue;
                for (Map<String, Object> row : t.getValue()) {
                    ContentValues v = new ContentValues();
                    for (Map.Entry<String, Object> c : row.entrySet()) {
                        if (!allowed.contains(c.getKey())) continue;
                        Object o = c.getValue();
                        if (o == null) v.putNull(c.getKey());
                        else if (o instanceof Long) v.put(c.getKey(), (Long) o);
                        else if (o instanceof Double) v.put(c.getKey(), (Double) o);
                        else v.put(c.getKey(), o.toString());
                    }
                    if (!v.containsKey("gid") || v.getAsString("token") == null) continue;
                    if (v.getAsInteger("ex") == null) v.put("ex", 0);
                    int conflict = LEGACY_DIRS.equals(t.getKey()) ? SQLiteDatabase.CONFLICT_IGNORE
                        : SQLiteDatabase.CONFLICT_REPLACE;
                    if (db.insertWithOnConflict(t.getKey(), null, v, conflict) != -1) {
                        written++;
                        if (DOWNLOADS.equals(t.getKey()) && !tables.containsKey(LEGACY_DIRS)) {
                            ContentValues legacy = new ContentValues();
                            legacy.put("gid", v.getAsLong("gid"));
                            legacy.put("token", v.getAsString("token"));
                            legacy.put("ex", v.containsKey("ex") ? v.getAsInteger("ex") : 0);
                            db.insertWithOnConflict(LEGACY_DIRS, null, legacy, SQLiteDatabase.CONFLICT_IGNORE);
                        }
                        if (HISTORY.equals(t.getKey()) && !tables.containsKey(STATS)) {
                            db.execSQL(LEGACY_STATS_SQL + " AND " + BY_SOURCE,
                                new Object[]{v.getAsInteger("ex"), v.getAsLong("gid")});
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return written;
    }

    /** Writes the backup entry body (the caller opens and closes the zip entry). */
    public static void exportBackup(@NonNull Context context, @NonNull OutputStream out) throws IOException {
        String json;
        try {
            json = EhBackup.encode(get(context).dump());
        } catch (RuntimeException e) {
            // Never fail the whole backup because of the e-hentai part.
            LogUtility.e("ehentai.db export failed", e);
            json = EhBackup.encode(new LinkedHashMap<>());
        }
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads a backup entry body (does not close {@code in}). */
    public static void importBackup(@NonNull Context context, @NonNull InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16 * 1024];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        Map<String, List<Map<String, Object>>> tables = EhBackup.decode(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        try {
            int rows = get(context).restore(tables);
            LogUtility.i("ehentai.db: restored " + rows + " rows");
        } catch (RuntimeException e) {
            throw new IOException("ehentai.db import failed", e);
        }
    }
}
