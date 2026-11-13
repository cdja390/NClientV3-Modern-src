package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.TypedValue;

import com.maxwai.nclientv3.R;

public class ThemeColors {
    /** Preset key of the wallpaper-based (Material You) colours, Android 12+. */
    public static final String DYNAMIC = "dynamic";

    public static void apply(Resources.Theme theme, Context context) {
        SharedPreferences prefs = context.getSharedPreferences("Settings", 0);
        String color = prefs.getString("theme_color", "purple");
        if ("purple".equals(color)) {
            return;
        }
        Resources res = context.getResources();
        String[] keys = res.getStringArray(R.array.theme_color_keys);
        TypedArray styles = res.obtainTypedArray(R.array.theme_color_styles);
        try {
            for (int i = 0; i < keys.length; i++) {
                if (color.equals(keys[i])) {
                    int styleId = styles.getResourceId(i, 0);
                    if (styleId != 0) {
                        theme.applyStyle(styleId, true);
                        // 4.6.0: the dynamic preset also recolours surfaces/backgrounds, except in
                        // pure-black mode and on themes that pin their surfaces (the reader).
                        if (DYNAMIC.equals(color) && dynamicSurfacesAllowed(theme, prefs, context)) {
                            theme.applyStyle(R.style.ThemeOverlay_NClient_Dynamic_Surface, true);
                        }
                    }
                    break;
                }
            }
        } finally {
            styles.recycle();
        }
    }

    private static boolean dynamicSurfacesAllowed(Resources.Theme theme, SharedPreferences prefs, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        if (prefs.getBoolean(context.getString(R.string.preference_key_black_theme), false)) return false;
        if ("black".equals(prefs.getString(context.getString(R.string.preference_key_theme_select), ""))) return false;
        TypedValue tv = new TypedValue();
        return !(theme.resolveAttribute(R.attr.nclientFixedSurfaces, tv, true)
            && tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data != 0);
    }
}
