package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import com.maxwai.nclientv3.R;

/**
 * Pure-GPU Glass Renderer for PageSwitcher and compatibility bridge for LiquidHostLayout.
 * - BottomNav delegates directly to LiquidHostLayout (0 CPU blur, 0 bitmap readback)
 * - PageSwitcher uses API 31+ RenderEffect GPU blur capturing sibling refresher (0 CPU box blur)
 */
public final class LiquidGlass {
    private static final int TAG_CTRL = 0x70ffff01;

    private LiquidGlass() {}

    public static void attach(View view) {
        if (view == null) return;
        if (view.getId() == R.id.bottom_nav) {
            view.setElevation(0f);
            view.setBackground(null);
            ViewParent parent = view.getParent();
            if (parent instanceof LiquidHostLayout) {
                ((LiquidHostLayout) parent).syncState();
            }
            return;
        }

        // Attach GPU glass pane to PageSwitcher
        Object tag = view.getTag(TAG_CTRL);
        if (tag instanceof PageSwitcherPane) {
            ((PageSwitcherPane) tag).syncTheme();
        } else {
            PageSwitcherPane pane = new PageSwitcherPane(view);
            view.setTag(TAG_CTRL, pane);
            view.setBackground(pane);
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    int w = v.getWidth();
                    int h = v.getHeight();
                    if (w > 0 && h > 0) {
                        outline.setRoundRect(0, 0, w, h, Math.min(w, h) * 0.5f);
                    }
                }
            });
            if (Build.VERSION.SDK_INT >= 28) {
                view.setOutlineAmbientShadowColor(0x1F000000);
                view.setOutlineSpotShadowColor(0x28000000);
            }
            pane.syncTheme();
        }
    }

    public static void setPillState(
            View view, float slot, float scaleX, float scaleY, float pressProgress) {
        if (view == null) return;
        ViewParent parent = view.getParent();
        if (parent instanceof LiquidHostLayout) {
            ((LiquidHostLayout) parent).invalidate();
        }
    }

    public static void requestCapture(View view) {
        if (view == null) return;
        view.invalidate();
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            ((View) parent).invalidate();
        }
    }

    public static Activity findActivity(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    public static int resolveThemeColor(Context ctx, int attrId, int fallback) {
        if (ctx == null || attrId == 0) return fallback;
        TypedValue tv = new TypedValue();
        Resources.Theme theme = ctx.getTheme();
        if (theme != null && theme.resolveAttribute(attrId, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.resourceId != 0) {
                try {
                    return ctx.getColor(tv.resourceId);
                } catch (Throwable ignored) {
                }
            }
        }
        return fallback;
    }

    public static int withAlpha(int color, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int luma(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (54 * r + 183 * g + 19 * b) >> 8;
    }

    /**
     * GPU-accelerated Glass Drawable for PageSwitcher
     */
    static final class PageSwitcherPane extends Drawable {
        private final View owner;
        private final float density;

        private boolean isDark = true;
        private int surfaceColor = 0xFF141218;
        private int bgColor = 0xFF141218;
        private int surfaceContainerHighColor = 0xFF2B2930;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF boundsF = new RectF();
        private final RectF strokeF = new RectF();
        private final Path clipPath = new Path();

        private Object blurNodeObj;
        private static boolean isRecordingSibling;

        PageSwitcherPane(View owner) {
            this.owner = owner;
            this.density = owner.getResources().getDisplayMetrics().density;
            outerRimPaint.setStyle(Paint.Style.STROKE);
            outerRimPaint.setStrokeWidth(0.6f * density);
            innerRimPaint.setStyle(Paint.Style.STROKE);
            innerRimPaint.setStrokeWidth(0.9f * density);
        }

        void syncTheme() {
            Context ctx = owner.getContext();
            if (ctx == null) return;
            surfaceColor = resolveThemeColor(ctx, R.attr.colorSurface, 0xFF141218) | 0xFF000000;
            bgColor = resolveThemeColor(ctx, android.R.attr.colorBackground, surfaceColor) | 0xFF000000;
            surfaceContainerHighColor = resolveThemeColor(
                    ctx, R.attr.colorSurfaceContainerHigh, 0xFF2B2930) | 0xFF000000;
            isDark = luma(surfaceColor) < 132;

            outerRimPaint.setColor(isDark ? 0x29FFFFFF : 0x0D000000);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) return;

            float r = Math.min(w, h) * 0.5f;
            boundsF.set(b);
            clipPath.rewind();
            clipPath.addRoundRect(boundsF, r, r, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(clipPath);

            boolean isLiquid = GlassBar.isLiquidEnabled(owner.getContext());
            boolean hw = canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 31;

            if (isLiquid && hw && !isRecordingSibling) {
                View parent = (View) owner.getParent();
                View refresher = (parent != null) ? parent.findViewById(R.id.refresher) : null;
                if (refresher != null && refresher.getWidth() > 0 && refresher.getHeight() > 0) {
                    int halfW = Math.max(1, (int) Math.ceil(w * 0.5f));
                    int halfH = Math.max(1, (int) Math.ceil(h * 0.5f));
                    if (blurNodeObj == null) {
                        blurNodeObj = new RenderNode("PageSwitcherBlur");
                        ((RenderNode) blurNodeObj).setRenderEffect(
                                RenderEffect.createBlurEffect(3.2f * density, 3.2f * density, Shader.TileMode.CLAMP));
                    }
                    ((RenderNode) blurNodeObj).setPosition(0, 0, halfW, halfH);

                    int relX = owner.getLeft() - refresher.getLeft();
                    int relY = owner.getTop() - refresher.getTop();

                    isRecordingSibling = true;
                    Canvas bRec = ((RenderNode) blurNodeObj).beginRecording(halfW, halfH);
                    bRec.drawColor(bgColor);
                    bRec.scale(0.5f, 0.5f);
                    bRec.translate(-relX, -relY);
                    refresher.draw(bRec);
                    ((RenderNode) blurNodeObj).endRecording();
                    isRecordingSibling = false;

                    canvas.save();
                    canvas.scale(2.0f, 2.0f);
                    canvas.drawRenderNode((RenderNode) blurNodeObj);
                    canvas.restore();
                } else {
                    fillPaint.setColor(withAlpha(surfaceContainerHighColor, isDark ? 0xBD : 0xCC));
                    canvas.drawRect(boundsF, fillPaint);
                }
            } else {
                int solidColor = isLiquid
                        ? withAlpha(surfaceContainerHighColor, isDark ? 0xBD : 0xCC)
                        : withAlpha(surfaceContainerHighColor, 0xF5);
                fillPaint.setColor(solidColor);
                canvas.drawRect(boundsF, fillPaint);
            }

            // Warm glaze
            if (isLiquid) {
                int glaze = isDark ? withAlpha(surfaceContainerHighColor, 87) : 0x47FFFBFB;
                fillPaint.setColor(glaze);
                canvas.drawRect(boundsF, fillPaint);
            }

            // Outer 0.6dp rim
            float outInset = 0.3f * density;
            strokeF.set(b.left + outInset, b.top + outInset, b.right - outInset, b.bottom - outInset);
            canvas.drawRoundRect(strokeF, r - outInset, r - outInset, outerRimPaint);

            // Inner 0.9dp specular gradient
            float inInset = 0.6f * density + 0.45f * density;
            strokeF.set(b.left + inInset, b.top + inInset, b.right - inInset, b.bottom - inInset);
            int topCol = isDark ? 0x47FFFFFF : 0xE0FFFFFF;
            int botCol = isDark ? 0x14FFFFFF : 0x70FFFFFF;
            innerRimPaint.setShader(new LinearGradient(
                    0f, b.top, 0f, b.bottom, topCol, botCol, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(strokeF, r - inInset, r - inInset, innerRimPaint);

            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
