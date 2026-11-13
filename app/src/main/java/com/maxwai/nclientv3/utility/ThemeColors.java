package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;

import com.maxwai.nclientv3.R;

public class ThemeColors {
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
                    }
                    break;
                }
            }
        } finally {
            styles.recycle();
        }
    }
}
