package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewParent;
import com.maxwai.nclientv3.R;

public class GlassBar {
    public GlassBar() {}

    public static boolean isLiquidEnabled(Context ctx) {
        if (ctx == null) return true;
        SharedPreferences sp = ctx.getSharedPreferences("Settings", 0);
        return sp.getBoolean("liquid_glass", true);
    }

    public static int getBlurFps(Context ctx) {
        if (ctx == null) return 60;
        try {
            String val = ctx.getSharedPreferences("Settings", 0).getString("blur_fps", "60");
            return Integer.parseInt(val);
        } catch (Throwable ignored) {
            return 60;
        }
    }

    public static void refresh(Activity activity) {
        if (activity == null) return;
        BarSize.apply(activity);

        View nav = activity.findViewById(R.id.bottom_nav);
        if (nav != null) {
            attach(nav);
        }

        View ps = activity.findViewById(R.id.page_switcher);
        if (ps != null) {
            attach(ps);
        }
    }

    public static void attach(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setTranslationY(0f);
        view.setForeground(null);

        if (view.getId() == R.id.bottom_nav) {
            view.setElevation(0f);
            view.setBackground(null);
            ViewParent parent = view.getParent();
            if (parent instanceof LiquidHostLayout) {
                ((LiquidHostLayout) parent).syncState();
            }
            return;
        }

        LiquidGlass.attach(view);
    }
}
