package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.maxwai.nclientv3.R;
import java.lang.reflect.Method;

/**
 * Geometry & Inset Enforcer for Nc-ag Pure-GPU Floating Bottom Bar & Page Switcher.
 * Sets authoritative 64dp bar height, 24dp horizontal margins, 10dp bottom margin,
 * 44dp PageSwitcher, and dynamic list bottom padding to avoid occlusion.
 */
public final class BarSize {
    private BarSize() {}

    public static void apply(Activity activity) {
        if (activity == null) return;
        float d = activity.getResources().getDisplayMetrics().density;

        final int barH = 64;
        final int barBottom = 10;
        final int side = 24;
        final int padDp = 4;
        final int iconDp = 22;
        final int labelSp = 11;
        final int switchH = 44;
        View nav = activity.findViewById(R.id.bottom_nav);
        // 4.6.0: with the bar hidden by the user the page switcher and list sit near the bottom.
        final boolean userHidden = BottomNavRouter.isHiddenByUser(nav);
        final int switchBottom = userHidden ? 16 : 86; // 10 + 64 + 12
        final int listPadWithSwitcher = userHidden ? 76 : 148;
        final int listPadWithoutSwitcher = userHidden ? 24 : 96;

        if (nav != null && nav.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) nav.getLayoutParams();
            int h = Math.round(barH * d);
            int bm = Math.round(barBottom * d);
            int sm = Math.round(side * d);
            int pad = Math.round(padDp * d);
            if (lp.height != h || lp.bottomMargin != bm || lp.leftMargin != sm || lp.rightMargin != sm) {
                lp.height = h;
                lp.bottomMargin = bm;
                lp.leftMargin = sm;
                lp.rightMargin = sm;
                nav.setMinimumHeight(h);
                nav.setLayoutParams(lp);
                nav.setPadding(pad, 0, pad, 0);
                nav.setElevation(0f);
                nav.setBackground(null);
                call(nav, "setItemIconSize", Math.round(iconDp * d));
                call(nav, "setItemPaddingTop", Math.round(2f * d));
                call(nav, "setItemPaddingBottom", Math.round(2f * d));
            }
        }

        View ps = activity.findViewById(R.id.page_switcher);
        boolean hasSwitcher = (ps != null);
        if (hasSwitcher && ps.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) ps.getLayoutParams();
            int h = Math.round(switchH * d);
            int bm = Math.round(switchBottom * d);
            int sm = Math.round(side * d);
            if (lp.height != h || lp.bottomMargin != bm || lp.leftMargin != sm || lp.rightMargin != sm) {
                lp.height = h;
                lp.bottomMargin = bm;
                lp.leftMargin = sm;
                lp.rightMargin = sm;
                ps.setLayoutParams(lp);
            }
            View index = ps.findViewById(R.id.page_index);
            if (index instanceof TextView) {
                ((TextView) index).setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            }
        }

        View rv = activity.findViewById(R.id.recycler);
        if (rv != null) {
            int padBottom = Math.round((hasSwitcher ? listPadWithSwitcher : listPadWithoutSwitcher) * d);
            if (rv.getPaddingBottom() != padBottom) {
                rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(), rv.getPaddingRight(), padBottom);
            }
        }
    }

    private static void call(View view, String name, int value) {
        try {
            Method method = view.getClass().getMethod(name, int.class);
            method.invoke(view, Integer.valueOf(value));
        } catch (Throwable ignored) {
        }
    }
}
