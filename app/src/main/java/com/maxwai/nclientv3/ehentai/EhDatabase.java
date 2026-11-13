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

/**
 * e-hentai data in its own database file (ehentai.db), so the main Entries.db schema and its
 * helper are left untouched. Tables: reading history (with resume page and reading time),
 * local favorites, and downloads.
 */
public final class EhDatabase extends SQLiteOpenHelper {
    public static final String NAME = "ehentai.db";
    private static final int VERSION = 1;

    public static final String HISTORY = "history";
    public static final String FAVORITES = "favorites";
    public static final String DOWNLOADS = "downloads";
    static final String[] TABLES = {HISTORY, FAVORITES, DOWNLOADS};

    public static final int STATE_QUEUED = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_PAUSED = 4;

    private static final String GALLERY_COLUMNS =
        "gid INTEGER PRIMARY KEY, token TEXT NOT NULL, ex INTEGER NOT NULL DEFAULT 0, title TEXT, "
            + "thumb TEXT, category INTEGER NOT NULL DEFAULT 0, pages INTEGER NOT NULL DEFAULT 0";
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
        "gid", "token", "ex", "title", "thumb", "category", "pages"));
    private static final Map<String, Set<String>> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put(HISTORY, with("last_page", "read_count", "read_seconds", "last_read"));
        COLUMNS.put(FAVORITES, with("added"));
        COLUMNS.put(DOWNLOADS, with("state", "done", "added"));
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
        db.execSQL("CREATE TABLE " + HISTORY + " (" + GALLERY_COLUMNS + ", last_page INTEGER NOT NULL DEFAULT 1, "
            + "read_count INTEGER NOT NULL DEFAULT 0, read_seconds INTEGER NOT NULL DEFAULT 0, "
            + "last_read INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE " + FAVORITES + " (" + GALLERY_COLUMNS + ", added INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE " + DOWNLOADS + " (" + GALLERY_COLUMNS + ", state INTEGER NOT NULL DEFAULT 0, "
            + "done INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema.
    }

    /** Row of one of the tables, with the extra columns that table has. */
    public static final class Entry extends EhGalleryInfo {
        public int lastPage = 1;
        public int readCount;
        public long readSeconds;
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
        if ((i = c.getColumnIndex("read_count")) >= 0) e.readCount = c.getInt(i);
        if ((i = c.getColumnIndex("read_seconds")) >= 0) e.readSeconds = c.getLong(i);
        if ((i = c.getColumnIndex("last_read")) >= 0) e.time = c.getLong(i);
        if ((i = c.getColumnIndex("added")) >= 0) e.time = c.getLong(i);
        if ((i = c.getColumnIndex("state")) >= 0) e.state = c.getInt(i);
        if ((i = c.getColumnIndex("done")) >= 0) e.done = c.getInt(i);
        return e;
    }

    @NonNull
    private List<Entry> list(@NonNull String table, @NonNull String order) {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(table, null, null, null, null, null, order, "1000")) {
            while (c.moveToNext()) out.add(read(c));
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
        }
        return out;
    }

    @Nullable
    private Entry one(@NonNull String table, long gid) {
        try (Cursor c = getReadableDatabase().query(table, null, "gid=?", new String[]{Long.toString(gid)},
            null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
            return null;
        }
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
        if (db.update(table, v, "gid=?", new String[]{Long.toString(info.key.gid)}) == 0) {
            ContentValues all = galleryValues(info);
            all.putAll(extra);
            db.insertWithOnConflict(table, null, all, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    // ---- history ----

    /** Records that a gallery was opened in the reader. */
    public void recordOpen(@NonNull EhGalleryInfo info) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues extra = new ContentValues();
            extra.put("last_read", System.currentTimeMillis());
            upsert(HISTORY, info, extra);
            db.execSQL("UPDATE " + HISTORY + " SET read_count = read_count + 1 WHERE gid = ?",
                new Object[]{info.key.gid});
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db write failed", e);
        }
    }

    public void saveProgress(long gid, int page, long addSeconds) {
        try {
            getWritableDatabase().execSQL("UPDATE " + HISTORY + " SET last_page = ?, last_read = ?, "
                    + "read_seconds = read_seconds + ? WHERE gid = ?",
                new Object[]{Math.max(1, page), System.currentTimeMillis(), Math.max(0, addSeconds), gid});
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db write failed", e);
        }
    }

    /** Last page read (1-based), or 0 when the gallery was never opened. */
    public int lastPage(long gid) {
        Entry e = one(HISTORY, gid);
        return e == null ? 0 : e.lastPage;
    }

    @NonNull
    public List<Entry> history() {
        return list(HISTORY, "last_read DESC");
    }

    public void removeHistory(long gid) {
        getWritableDatabase().delete(HISTORY, "gid=?", new String[]{Long.toString(gid)});
    }

    public void clearHistory() {
        getWritableDatabase().delete(HISTORY, null, null);
    }

    /** {galleries read, total pages of those galleries, total reading seconds}. */
    @NonNull
    public long[] stats() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(pages),0), "
            + "COALESCE(SUM(read_seconds),0) FROM " + HISTORY, null)) {
            if (c.moveToFirst()) return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        } catch (RuntimeException e) {
            LogUtility.e("ehentai.db read failed", e);
        }
        return new long[]{0, 0, 0};
    }

    // ---- local favorites ----

    public boolean isFavorite(long gid) {
        return one(FAVORITES, gid) != null;
    }

    public void setFavorite(@NonNull EhGalleryInfo info, boolean favorite) {
        if (!favorite) {
            getWritableDatabase().delete(FAVORITES, "gid=?", new String[]{Long.toString(info.key.gid)});
            return;
        }
        ContentValues extra = new ContentValues();
        extra.put("added", System.currentTimeMillis());
        upsert(FAVORITES, info, extra);
    }

    @NonNull
    public List<Entry> favorites() {
        return list(FAVORITES, "added DESC");
    }

    // ---- downloads ----

    public void putDownload(@NonNull EhGalleryInfo info, int state) {
        ContentValues extra = new ContentValues();
        extra.put("state", state);
        Entry old = one(DOWNLOADS, info.key.gid);
        if (old == null) extra.put("added", System.currentTimeMillis());
        upsert(DOWNLOADS, info, extra);
    }

    public void setDownloadProgress(long gid, int state, int done) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        if (done >= 0) v.put("done", done);
        getWritableDatabase().update(DOWNLOADS, v, "gid=?", new String[]{Long.toString(gid)});
    }

    @Nullable
    public Entry download(long gid) {
        return one(DOWNLOADS, gid);
    }

    @NonNull
    public List<Entry> downloads() {
        return list(DOWNLOADS, "added DESC");
    }

    public void removeDownload(long gid) {
        getWritableDatabase().delete(DOWNLOADS, "gid=?", new String[]{Long.toString(gid)});
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

    /** Merges backed-up rows (same gallery id replaces the local row). Returns rows written. */
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
                    if (db.insertWithOnConflict(t.getKey(), null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1) written++;
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
