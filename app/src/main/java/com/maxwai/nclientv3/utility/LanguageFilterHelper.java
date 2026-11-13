package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/** Shows the browse-page language filter on search-result pages and reloads the query. */
public class LanguageFilterHelper {
    private static final int[] LANGUAGE_TAG_IDS = new int[]{0x2fc3, 0x18ca, 0x750b};

    public static void ensureSearchLanguage(Activity activity, Menu menu) {
        if (activity == null || menu == null) return;
        try {
            Object mode = getField(activity, "modeType");
            if (mode == null || !"SEARCH".equals(String.valueOf(mode))) return;
            int id = classFor("com.maxwai.nclientv3.R$id").getField("only_language").getInt(null);
            MenuItem item = menu.findItem(id);
            if (item == null) return;
            item.setVisible(true);
            Method show = activity.getClass().getDeclaredMethod("showLanguageIcon", MenuItem.class);
            show.setAccessible(true);
            show.invoke(activity, item);
        } catch (Throwable ignored) {
        }
    }

    public static boolean reloadSearch(Activity activity) {
        if (activity == null) return false;
        try {
            Object inspector = getField(activity, "inspector");
            if (inspector == null) return false;
            Object query = getField(inspector, "query");
            Object ranges = getField(inspector, "ranges");
            Object tags = getField(inspector, "tags");
            Object response = getField(activity, "resetDataset");
            Object customObj = getField(inspector, "custom");
            boolean custom = customObj instanceof Boolean && ((Boolean) customObj).booleanValue();

            Collection<Object> passedTags = null;
            if (custom && tags instanceof Collection) {
                passedTags = new HashSet<Object>();
                for (Object tag : (Collection<?>) tags) {
                    if (!isLanguageTag(tag)) passedTags.add(tag);
                }
            }

            Class<?> global = classFor("com.maxwai.nclientv3.settings.Global");
            Object sortType = global.getMethod("getSortType").invoke(null);
            Class<?> inspectorCl = inspector.getClass();
            Method search = null;
            for (Method m : inspectorCl.getMethods()) {
                if ("searchInspector".equals(m.getName()) && m.getParameterTypes().length == 7) {
                    search = m;
                    break;
                }
            }
            if (search == null) return false;
            Object neu = search.invoke(null, activity, query, passedTags, Integer.valueOf(1), sortType, ranges, response);
            setField(activity, "inspector", neu);
            inspectorCl.getMethod("start").invoke(neu);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isLanguageTag(Object tag) {
        if (tag == null) return false;
        try {
            int id = ((Integer) tag.getClass().getMethod("getId").invoke(tag)).intValue();
            for (int langId : LANGUAGE_TAG_IDS) {
                if (id == langId) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Class<?> classFor(String name) throws Exception {
        return Class.forName(name);
    }

    private static Object getField(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
