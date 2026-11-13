package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tag-aware local search for the Favorites and Downloads pages.
 * Rule: the query is split on whitespace; an item matches only if EVERY word
 * (case-insensitive contains) is found in one of its titles or in one of its
 * tag names (English name or Chinese name_zh). Empty query matches everything.
 * History is intentionally not touched.
 */
public class LocalTagFilterHelper {
    private static final String TITLE_AND_TAG_CLAUSE =
            "(title_eng LIKE ? OR title_jp LIKE ? OR title_pretty LIKE ? OR EXISTS ("
                    + "SELECT 1 FROM GalleryTags gt INNER JOIN Tags t ON t.idTag=gt.id_tag "
                    + "WHERE gt.id_gallery=Gallery.idGallery AND (t.name LIKE ? OR t.name_zh LIKE ?)))";
    private static final int ARGS_PER_WORD = 5;
    private static final long ZH_CACHE_MS = 30000L;

    private static String cachedZhQuery = null;
    private static List<Set<Integer>> cachedZhIds = null;
    private static long cachedZhTime = 0L;

    // ---------------------------------------------------------------- words

    public static String[] words(CharSequence query) {
        if (query == null) return new String[0];
        String q = query.toString().trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return new String[0];
        String[] parts = q.split("\\s+");
        ArrayList<String> out = new ArrayList<String>(parts.length);
        for (String p : parts) {
            if (p != null && !p.isEmpty() && !out.contains(p)) out.add(p);
        }
        return out.toArray(new String[0]);
    }

    // ------------------------------------------------------------ favorites

    /** WHERE clause for Favorite INNER JOIN Gallery; null when the query is empty (= all). */
    public static String favoriteWhere(CharSequence query) {
        String[] w = words(query);
        if (w.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(TITLE_AND_TAG_CLAUSE);
        }
        return sb.toString();
    }

    /** Bound args matching favoriteWhere; null when the query is empty. */
    public static String[] favoriteArgs(CharSequence query) {
        String[] w = words(query);
        if (w.length == 0) return null;
        String[] args = new String[w.length * ARGS_PER_WORD];
        for (int i = 0; i < w.length; i++) {
            String p = "%" + w[i] + "%";
            for (int j = 0; j < ARGS_PER_WORD; j++) args[i * ARGS_PER_WORD + j] = p;
        }
        return args;
    }

    // ------------------------------------------------------------ downloads

    /** LocalGallery match: title + metadata titles + metadata tags. */
    public static boolean matchesLocal(Object localGallery, String query) {
        String[] w = words(query);
        if (w.length == 0) return true;
        if (localGallery == null) return false;
        ArrayList<String> titles = new ArrayList<String>();
        addString(titles, call(localGallery, "getTitle"));
        Object tags = null;
        try {
            Object has = call(localGallery, "hasGalleryData");
            if (Boolean.TRUE.equals(has)) {
                Object data = call(localGallery, "getGalleryData");
                if (data != null) {
                    addTitles(titles, data);
                    tags = call(data, "getTags");
                }
            }
        } catch (Throwable ignored) {
        }
        return matchesAll(w, titles, tags);
    }

    /** In-progress downloader match: path title + gallery titles + gallery tags. */
    public static boolean matchesDownloader(Object downloader, String query) {
        String[] w = words(query);
        if (w.length == 0) return true;
        if (downloader == null) return false;
        ArrayList<String> titles = new ArrayList<String>();
        addString(titles, call(downloader, "getPathTitle"));
        Object tags = null;
        try {
            Object gallery = call(downloader, "getGallery");
            if (gallery != null) {
                addTitles(titles, gallery);
                tags = call(gallery, "getTags");
            }
        } catch (Throwable ignored) {
        }
        return matchesAll(w, titles, tags);
    }

    private static boolean matchesAll(String[] w, List<String> titles, Object tagList) {
        List<Object> tags = tagsOf(tagList);
        List<Set<Integer>> zhIds = null;
        for (int i = 0; i < w.length; i++) {
            String word = w[i];
            boolean hit = false;
            for (String t : titles) {
                if (t.contains(word)) { hit = true; break; }
            }
            if (!hit && !tags.isEmpty()) {
                for (Object tag : tags) {
                    String name = lower(call(tag, "getName"));
                    String zh = lower(call(tag, "getNameZh"));
                    if ((name != null && name.contains(word)) || (zh != null && zh.contains(word))) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    // Metadata JSON has no Chinese names: look them up in the Tags table by id.
                    if (zhIds == null) zhIds = zhTagIds(w);
                    Set<Integer> ids = (zhIds != null && i < zhIds.size()) ? zhIds.get(i) : null;
                    if (ids != null && !ids.isEmpty()) {
                        for (Object tag : tags) {
                            Object id = call(tag, "getId");
                            if (id instanceof Integer && ids.contains((Integer) id)) { hit = true; break; }
                        }
                    }
                }
            }
            if (!hit) return false;
        }
        return true;
    }

    /** For each word, ids of tags whose name_zh contains it. Cached per query for a short time. */
    private static synchronized List<Set<Integer>> zhTagIds(String[] w) {
        String key = join(w);
        long now = System.currentTimeMillis();
        if (key.equals(cachedZhQuery) && cachedZhIds != null && now - cachedZhTime < ZH_CACHE_MS) {
            return cachedZhIds;
        }
        List<Set<Integer>> result = new ArrayList<Set<Integer>>(w.length);
        SQLiteDatabase db = database();
        for (String word : w) {
            Set<Integer> ids = new HashSet<Integer>();
            if (db != null) {
                Cursor c = null;
                try {
                    c = db.rawQuery("SELECT idTag FROM Tags WHERE name_zh LIKE ?", new String[]{"%" + word + "%"});
                    while (c.moveToNext()) ids.add(c.getInt(0));
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) try { c.close(); } catch (Throwable ignored) { }
                }
            }
            result.add(ids);
        }
        cachedZhQuery = key;
        cachedZhIds = result;
        cachedZhTime = now;
        return result;
    }

    // -------------------------------------------------------------- helpers

    private static SQLiteDatabase database() {
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

    private static void addTitles(List<String> titles, Object holder) {
        try {
            Class<?> tt = Class.forName("com.maxwai.nclientv3.api.enums.TitleType");
            Object[] values = tt.getEnumConstants();
            if (values == null) return;
            Method m = holder.getClass().getMethod("getTitle", tt);
            for (Object v : values) {
                try { addString(titles, m.invoke(holder, v)); } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<Object> tagsOf(Object tagList) {
        ArrayList<Object> out = new ArrayList<Object>();
        if (tagList == null) return out;
        Object all = call(tagList, "getAllTagsList");
        if (all instanceof Collection) {
            for (Object o : (Collection<?>) all) if (o != null) out.add(o);
        }
        return out;
    }

    private static void addString(List<String> list, Object o) {
        String s = lower(o);
        if (s != null && !s.isEmpty()) list.add(s);
    }

    private static String lower(Object o) {
        return o == null ? null : o.toString().toLowerCase(Locale.US);
    }

    private static String join(String[] w) {
        StringBuilder sb = new StringBuilder();
        for (String s : w) sb.append(s).append('\u0001');
        return sb.toString();
    }

    private static Object call(Object target, String name) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(name);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
