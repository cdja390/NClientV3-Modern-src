package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import com.maxwai.nclientv3.R;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.status.Status;
import com.maxwai.nclientv3.components.status.StatusManager;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.WeakHashMap;

/**
 * Auto-mark reading status: optionally sets a gallery's reading status while it is read
 * (off by default, settings auto_mark_reading_status / auto_mark_overwrite_manual).
 *
 * Hooks (ZoomActivity.smali):
 *  - onCreate, right after the initial setPageText(startPage): onSessionStart(activity, startPage)
 *    The start page (including a resume jump) is NOT a page turn.
 *  - setPageText(I), after DownloadWhileReadingHelper.onPageChanged: onPageChanged(activity, gallery, page)
 *  - onPause, after AutoPageTurner.onPause: onLeave(activity, gallery) -> judge once, write only on change.
 *
 * Rules (setting "auto_mark_reading_status", default OFF):
 *  - status only moves up: None / Plan to read -> Reading -> Completed; Completed is never changed.
 *  - Reading needs at least one real page change in this session (opening alone never marks).
 *  - Completed (user decision 2026-11-12): the furthest page reached by a page change in this
 *    session is the last page. Dragging the seek bar to the last page counts. The start/resume
 *    page itself does not count, so a resume that lands on the last page does not complete it,
 *    and a single-page gallery is never auto-completed (it cannot change page).
 *  - On Hold / Dropped / custom statuses are never changed, unless "auto_mark_overwrite_manual"
 *    (default OFF) is on, in which case they may only be moved to Completed.
 *  - Default status names are matched in all shipped locales (zh-CN, en, zh-TW, ...). If the target
 *    status cannot be found (renamed/deleted) nothing is written and nothing is recreated.
 *  - Local galleries with id <= 0 or without metadata (hasGalleryData() == false) are skipped.
 *  - Everything is inside try/catch; failures only log and never affect reading or downloads.
 */
public class ReadingSessionHelper {

    public static final String PREF_SETTINGS = "Settings";
    public static final String PREF_ENABLED = "auto_mark_reading_status";
    public static final String PREF_OVERWRITE = "auto_mark_overwrite_manual";
    private static final String TAG = "NClientAutoMark";

    private static final int CAT_NONE = 0;
    private static final int CAT_PLAN = 1;
    private static final int CAT_READING = 2;
    private static final int CAT_COMPLETED = 3;
    private static final int CAT_MANUAL = 4;

    // default_status_1 (Reading) in every shipped values-*/strings.xml; zh-CN and en first.
    private static final String[] READING_NAMES = {
        "\u6b63\u5728\u9605\u8bfb", "Reading", "\u95b1\u8b80\u4e2d",
        "\u0642\u064a\u062f \u0627\u0644\u0642\u0631\u0627\u0621\u0629", "Lesen", "Leyendo", "En cours", "Leggendo", "\u8aad\u66f8\u4e2d",
        "Lendo", "\u0427\u0438\u0442\u0430\u0435\u0442\u0441\u044f", "Okunuyor", "\u0427\u0438\u0442\u0430\u043d\u043d\u044f"
    };
    // default_status_2 (Completed)
    private static final String[] COMPLETED_NAMES = {
        "\u5df2\u5b8c\u6210", "Completed",
        "\u062a\u0645", "Komplett", "Completado", "Termin\u00e9", "Completati", "\u5b8c\u4e86\u6e08",
        "Conclu\u00eddo", "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u043e", "Tamamland\u0131", "\u0412\u0438\u043a\u043e\u043d\u0430\u043d\u043e"
    };
    // default_status_5 (Plan to read)
    private static final String[] PLAN_NAMES = {
        "\u8ba1\u5212\u9605\u8bfb", "Plan to read", "\u8a08\u5283\u95b1\u8b80",
        "\u0645\u064f\u062e\u0637\u0651\u064e\u0637\u0629 \u0642\u0631\u0627\u0621\u062a\u0647", "Geplant", "Planeando leer", "\u00c0 lire", "Da leggere", "\u8a08\u753b\u4e2d",
        "Planeja ler", "\u041f\u043b\u0430\u043d\u0438\u0440\u0443\u0435\u0442\u0441\u044f \u043f\u0440\u043e\u0447\u0438\u0442\u0430\u0442\u044c", "Planlan\u0131yor", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u043f\u0440\u043e\u0447\u0438\u0442\u0430\u043d\u043d\u044f"
    };

    private static final class Session {
        int startPage;
        int lastPage;
        int turns;
        int maxTurnPage;
    }

    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<Activity, Session>();

    /** Called once from ZoomActivity.onCreate after the initial setPageText. page is 1-based. */
    public static void onSessionStart(Activity activity, int page) {
        try {
            if (activity == null) return;
            Session s = new Session();
            s.startPage = page;
            s.lastPage = page;
            s.turns = 0;
            s.maxTurnPage = 0;
            synchronized (SESSIONS) {
                SESSIONS.put(activity, s);
            }
        } catch (Throwable t) {
            log("onSessionStart failed", t);
        }
    }

    /** Called from ZoomActivity.setPageText after DownloadWhileReadingHelper. page is 1-based. */
    public static void onPageChanged(Activity activity, GenericGallery gallery, int page) {
        try {
            if (activity == null) return;
            Session s;
            synchronized (SESSIONS) {
                s = SESSIONS.get(activity);
            }
            // Calls before onSessionStart (e.g. the initial jump inside onCreate) are ignored.
            if (s == null) return;
            if (page == s.lastPage) return;
            s.lastPage = page;
            s.turns++;
            if (page > s.maxTurnPage) s.maxTurnPage = page;
        } catch (Throwable t) {
            log("onPageChanged failed", t);
        }
    }

    /** Called from ZoomActivity.onPause after AutoPageTurner.onPause. Judges once per pause. */
    public static void onLeave(Activity activity, GenericGallery gallery) {
        try {
            if (activity == null || gallery == null) return;
            Session s;
            synchronized (SESSIONS) {
                s = SESSIONS.get(activity);
            }
            if (s == null) return;
            SharedPreferences sp = activity.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
            if (!sp.getBoolean(PREF_ENABLED, false)) return;

            int id = gallery.getId();
            if (id <= 0) {
                Log.i(TAG, "skip: gallery id " + id);
                return;
            }
            if (!gallery.hasGalleryData()) {
                Log.i(TAG, "skip: gallery " + id + " has no metadata");
                return;
            }
            int total = gallery.getPageCount();
            if (total <= 0) return;
            if (s.turns < 1) return;

            boolean finished = s.maxTurnPage >= total;

            Status current = Queries.StatusMangaTable.getStatus(id);
            String currentName = current == null ? null : current.name;
            String next = nextStatus(currentName, s.maxTurnPage, total, sp.getBoolean(PREF_OVERWRITE, false),
                name -> StatusManager.getByName(name) != null);
            Status target = next == null ? null : StatusManager.getByName(next);
            if (target == null || target.name == null) {
                Log.i(TAG, "skip: target status not found (renamed or deleted)");
                return;
            }
            if (currentName != null && currentName.equals(target.name)) return;

            Queries.StatusMangaTable.insert(gallery, target);
            Log.i(TAG, "gallery " + id + ": " + currentName + " -> " + target.name
                + " (turns=" + s.turns + ", max=" + s.maxTurnPage + "/" + total + ")");

            if (finished) {
                try {
                    String msg = activity.getString(R.string.reading_status_auto_marked, target.name);
                    Toast.makeText(activity.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            log("onLeave failed", t);
        }
    }

    /** Chooses an existing status only after a real page change. */
    public static String nextStatus(String current, int furthestTurn, int pages, boolean overwrite,
                                    Predicate<String> exists) {
        if (furthestTurn <= 0 || pages <= 1) return null;
        int category = classify(current);
        if (category == CAT_COMPLETED) return null;
        String[] targetNames;
        if (furthestTurn >= pages) {
            if (category == CAT_MANUAL && !overwrite) return null;
            targetNames = COMPLETED_NAMES;
        } else {
            if (category != CAT_NONE && category != CAT_PLAN) return null;
            targetNames = READING_NAMES;
        }
        for (String name : targetNames) if (exists.test(name)) return name;
        return null;
    }

    private static int classify(String name) {
        if (name == null || name.length() == 0 || "None".equals(name)) return CAT_NONE;
        if (contains(COMPLETED_NAMES, name)) return CAT_COMPLETED;
        if (contains(READING_NAMES, name)) return CAT_READING;
        if (contains(PLAN_NAMES, name)) return CAT_PLAN;
        return CAT_MANUAL;
    }

    private static boolean contains(String[] names, String name) {
        for (String n : names) {
            if (n.equals(name)) return true;
        }
        return false;
    }

    private static boolean isZh(Activity activity) {
        try {
            Locale locale;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                locale = activity.getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = activity.getResources().getConfiguration().locale;
            }
            if (locale == null) return false;
            String lang = locale.getLanguage();
            return lang != null && lang.toLowerCase(Locale.US).startsWith("zh");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void log(String msg, Throwable t) {
        try {
            Log.w(TAG, msg, t);
        } catch (Throwable ignored) {
        }
    }
}
