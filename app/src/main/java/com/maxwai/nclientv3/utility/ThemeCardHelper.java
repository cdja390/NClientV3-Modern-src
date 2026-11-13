package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;

public final class ThemeCardHelper {

    public static class Palette {
        public final String key;
        public final String title;
        public final int primary;
        public final int primaryContainer;
        public final int tertiaryContainer;

        /** 4.6.0: preset name as a string resource, resolved when the card is bound. */
        public final int titleRes;

        public Palette(String key, int titleRes, int primary, int primaryContainer, int tertiaryContainer) {
            this(key, null, titleRes, primary, primaryContainer, tertiaryContainer);
        }

        public Palette(String key, String title, int primary, int primaryContainer, int tertiaryContainer) {
            this(key, title, 0, primary, primaryContainer, tertiaryContainer);
        }

        private Palette(String key, String title, int titleRes, int primary, int primaryContainer, int tertiaryContainer) {
            this.key = key;
            this.title = title;
            this.titleRes = titleRes;
            this.primary = primary;
            this.primaryContainer = primaryContainer;
            this.tertiaryContainer = tertiaryContainer;
        }

        /** True for the wallpaper-based preset; its card gets a small sparkle mark. */
        public boolean isDynamic() {
            return ThemeColors.DYNAMIC.equals(key);
        }
    }

    /**
     * 4.6.0: the "Dynamic" preset card, previewing the current wallpaper palette. Only offered on
     * Android 12+, where the system exposes those colours.
     */
    @Nullable
    static Palette dynamicPalette(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        return new Palette(ThemeColors.DYNAMIC, ctx.getString(R.string.theme_color_dynamic),
            ctx.getColor(android.R.color.system_accent1_600),
            ctx.getColor(android.R.color.system_accent1_100),
            ctx.getColor(android.R.color.system_accent3_100));
    }

    public static final Palette[] PALETTES = new Palette[] {
        new Palette("purple", R.string.theme_color_ink, 0xFF3156D6, 0xFFDCE3FF, 0xFFC9D4FF),
        new Palette("blue", R.string.theme_color_blue, 0xFF2F5BFF, 0xFFE4E9FF, 0xFFC9D4FF),
        new Palette("indigo", R.string.theme_color_indigo, 0xFF4F46E5, 0xFFE7E5FF, 0xFFD9D6FF),
        new Palette("cyan", R.string.theme_color_cyan, 0xFF0891B2, 0xFFD5F4FA, 0xFFBAE6FD),
        new Palette("teal", R.string.theme_color_teal, 0xFF0F766E, 0xFFD5F5F2, 0xFF99F6E4),
        new Palette("green", R.string.theme_color_green, 0xFF16803C, 0xFFDCF5E4, 0xFFBBF7D0),
        new Palette("lime", R.string.theme_color_lime, 0xFF4D7C0F, 0xFFE8F6D0, 0xFFD9F99D),
        new Palette("amber", R.string.theme_color_amber, 0xFFB45309, 0xFFFEF0D6, 0xFFFDE68A),
        new Palette("orange", R.string.theme_color_orange, 0xFFEA580C, 0xFFFFE8D6, 0xFFFED7AA),
        new Palette("red", R.string.theme_color_red, 0xFFDC2626, 0xFFFEE2E2, 0xFFFECACA),
        new Palette("rose", R.string.theme_color_rose, 0xFFE11D48, 0xFFFFE4E9, 0xFFFECDD3),
        new Palette("pink", R.string.theme_color_pink, 0xFFDB2777, 0xFFFCE7F1, 0xFFFBCFE8),
        new Palette("brown", R.string.theme_color_stone, 0xFF78716C, 0xFFF5F0ED, 0xFFE7E5E4),
        new Palette("ocean", R.string.theme_color_ocean, 0xFF0369A1, 0xFFE0F2FE, 0xFFBAE6FD),
        new Palette("mono", R.string.theme_color_mono, 0xFF18181B, 0xFFF4F4F5, 0xFFE4E4E7)
    };

    public static class ColorCardDrawable extends Drawable {
        private final Palette palette;
        private final boolean isSelected;
        private final int surfaceContainer;
        private final float dp;

        private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintCheck = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF cardRect = new RectF();
        private final RectF arcRect = new RectF();
        private final Path checkPath = new Path();

        public ColorCardDrawable(Context ctx, Palette palette, boolean isSelected, int surfaceContainer) {
            this.palette = palette;
            this.isSelected = isSelected;
            this.surfaceContainer = surfaceContainer;
            this.dp = ctx.getResources().getDisplayMetrics().density;

            paintFill.setStyle(Paint.Style.FILL);
            paintStroke.setStyle(Paint.Style.STROKE);
            paintCheck.setStyle(Paint.Style.STROKE);
            paintCheck.setStrokeCap(Paint.Cap.ROUND);
            paintCheck.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            float cx = b.centerX();
            float cy = b.centerY();

            // 1. Card base: 20dp corner radius round rect filled with surfaceContainer
            float corner = 20f * dp;
            cardRect.set(b);
            paintFill.setColor(surfaceContainer);
            canvas.drawRoundRect(cardRect, corner, corner, paintFill);

            // 2. Center two-tone arcs: 48dp diameter (24dp radius)
            float rArc = 24f * dp;
            arcRect.set(cx - rArc, cy - rArc, cx + rArc, cy + rArc);

            // Top half arc: primaryContainer (180 to 360 deg)
            paintFill.setColor(palette.primaryContainer);
            canvas.drawArc(arcRect, 180f, 180f, true, paintFill);

            // Bottom half arc: tertiaryContainer (0 to 180 deg)
            paintFill.setColor(palette.tertiaryContainer);
            canvas.drawArc(arcRect, 0f, 180f, true, paintFill);

            // 3. Center dot / Selected state
            if (!isSelected) {
                // Center dot: 20dp diameter (10dp radius)
                paintFill.setColor(palette.primary);
                canvas.drawCircle(cx, cy, 10f * dp, paintFill);
            } else {
                // Outer concentric border ring: 54dp diameter (27dp radius), 2dp stroke width
                paintStroke.setColor(palette.primary);
                paintStroke.setStrokeWidth(2f * dp);
                canvas.drawCircle(cx, cy, 27f * dp, paintStroke);

                // Center circle: 24dp diameter (12dp radius)
                paintFill.setColor(palette.primary);
                canvas.drawCircle(cx, cy, 12f * dp, paintFill);

                // Checkmark: 16dp icon centered, white stroke
                checkPath.reset();
                checkPath.moveTo(cx - 4.5f * dp, cy + 0.2f * dp);
                checkPath.lineTo(cx - 1.2f * dp, cy + 3.6f * dp);
                checkPath.lineTo(cx + 4.8f * dp, cy - 3.2f * dp);

                paintCheck.setColor(0xFFFFFFFF);
                paintCheck.setStrokeWidth(2.2f * dp);
                canvas.drawPath(checkPath, paintCheck);
            }

            // 4.6.0: four-point sparkle in the top-right corner marks the dynamic preset.
            if (palette.isDynamic()) {
                float sx = b.right - 14f * dp;
                float sy = b.top + 14f * dp;
                float r = 6f * dp;
                float w = 1.6f * dp;
                checkPath.reset();
                checkPath.moveTo(sx, sy - r);
                checkPath.lineTo(sx + w, sy - w);
                checkPath.lineTo(sx + r, sy);
                checkPath.lineTo(sx + w, sy + w);
                checkPath.lineTo(sx, sy + r);
                checkPath.lineTo(sx - w, sy + w);
                checkPath.lineTo(sx - r, sy);
                checkPath.lineTo(sx - w, sy - w);
                checkPath.close();
                paintFill.setColor(palette.primary);
                canvas.drawPath(checkPath, paintFill);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paintFill.setAlpha(alpha);
            paintStroke.setAlpha(alpha);
            paintCheck.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paintFill.setColorFilter(colorFilter);
            paintStroke.setColorFilter(colorFilter);
            paintCheck.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static int resolveSurfaceContainer(Context ctx) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(R.attr.colorSurfaceContainer, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        }
        // Fallback based on night mode
        int nightMode = ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            return 0xFF1C1D22;
        } else {
            return 0xFFF4F4F5;
        }
    }

    public static void bindSukiSuCards(ViewGroup colorGroup, String selectedKey, final View.OnClickListener clickListener) {
        if (colorGroup == null) return;
        final Context ctx = colorGroup.getContext();
        final float dp = ctx.getResources().getDisplayMetrics().density;
        final int surfaceColor = resolveSurfaceContainer(ctx);

        colorGroup.removeAllViews();

        java.util.List<Palette> palettes = new java.util.ArrayList<>(java.util.Arrays.asList(PALETTES));
        Palette dynamic = dynamicPalette(ctx);
        if (dynamic != null) palettes.add(dynamic);

        for (int i = 0; i < palettes.size(); i++) {
            final Palette p = palettes.get(i);
            final boolean isSelected = p.key.equals(selectedKey);

            View card = new View(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Math.round(72f * dp),
                Math.round(72f * dp)
            );
            lp.rightMargin = Math.round(12f * dp);
            card.setLayoutParams(lp);

            card.setTag(p.key);
            String title = p.titleRes != 0 ? ctx.getString(p.titleRes) : p.title;
            card.setContentDescription(title);
            card.setTooltipText(title);
            card.setClickable(true);
            card.setFocusable(true);
            card.setBackground(new ColorCardDrawable(ctx, p, isSelected, surfaceColor));

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (clickListener != null) {
                        clickListener.onClick(v);
                    }
                }
            });

            colorGroup.addView(card);

            if (isSelected) {
                final View selView = card;
                colorGroup.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewParent parent = selView.getParent();
                        if (parent != null) {
                            ViewParent scroll = parent.getParent();
                            if (scroll instanceof HorizontalScrollView) {
                                ((HorizontalScrollView) scroll).smoothScrollTo(
                                    Math.max(0, selView.getLeft() - Math.round(16f * dp)),
                                    0
                                );
                            }
                        }
                    }
                });
            }
        }
    }

    public static void setupSectionCollapse(final View headerView, final View modeContainer, final View colorContainer, final ImageView arrowView) {
        if (headerView == null || modeContainer == null || colorContainer == null || arrowView == null) return;

        final Context ctx = headerView.getContext();
        final SharedPreferences prefs = ctx.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean isExpanded = prefs.getBoolean("theme_section_expanded", true);

        // Apply initial state
        modeContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        colorContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        arrowView.setRotation(isExpanded ? 180f : 0f);
        headerView.setBackgroundResource(isExpanded ? R.drawable.bg_menu_top : R.drawable.bg_menu_preview);

        headerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                boolean current = prefs.getBoolean("theme_section_expanded", true);
                boolean next = !current;
                prefs.edit().putBoolean("theme_section_expanded", next).apply();

                arrowView.animate()
                    .rotation(next ? 180f : 0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

                modeContainer.setVisibility(next ? View.VISIBLE : View.GONE);
                colorContainer.setVisibility(next ? View.VISIBLE : View.GONE);
                headerView.setBackgroundResource(next ? R.drawable.bg_menu_top : R.drawable.bg_menu_preview);
            }
        });
    }
}
