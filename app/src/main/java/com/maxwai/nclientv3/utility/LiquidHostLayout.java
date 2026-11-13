package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maxwai.nclientv3.R;

/**
 * 120Hz Pure-GPU Liquid Glass Bottom Bar Compositor & Container (1:1 port of Nc-ag KernelFloatingBottomBar).
 * - Single-pass HWUI RenderNode recording of content layer (Glide HARDWARE bitmap safe, 0 CPU readback)
 * - Layer 1: 0.5x downscaled RenderEffect blur (3.2dp radius) + 2x upscale + warm milky / dark glaze
 * - Layer 2: 0.0px strictly stationary dual-layer tabs + 56dp resting pill
 * - Layer 3: Unclipped 74dp crystal magnifying droplet (1.145x outer bevel + 1.055x inner window,
 *            8dp BlurMaskFilter GPU-scaled shadow, cyan/orange caustic bevel, specular rim)
 */
public class LiquidHostLayout extends FrameLayout {

    private final float density;
    private View contentChild;
    private BottomNavigationView bottomNav;
    private BottomNavRouter router;

    private boolean isRecordingContent;
    private boolean isLiquidEnabled = true;

    // Theme palette
    private boolean isDark = true;
    private boolean isBlack = false;
    private int surfaceColor = 0xFF141218;
    private int bgColor = 0xFF141218;
    private int surfaceContainerHighColor = 0xFF2B2930;
    private int primaryColor = 0xFFD0BCFF;
    private int primaryContainerColor = 0xFF4F378B;
    private int onSurfaceVariantColor = 0xFFCAC4D0;
    private int inactiveTabColor = 0xD8CAC4D0;
    private int activeTabColor = 0xFFD0BCFF;
    private int restingPillBaseColor = 0xFF4F378B;

    // Optical brushes & shaders
    private final Paint barShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barGlazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barOuterRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barInnerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restingPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restingTransitionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropletShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropletBarRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topGlazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint causticBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crispOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tabTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient barInnerRimShader;
    private LinearGradient topGlazeShader;
    private LinearGradient causticBevelShader;
    private LinearGradient specularRimShader;
    private final Matrix shaderMatrix = new Matrix();

    // Reusable geometry paths & rects
    private final RectF barRectF = new RectF();
    private final RectF strokeRectF = new RectF();
    private final RectF pillRectF = new RectF();
    private final Path barPath = new Path();
    private final Path pillClipPath = new Path();
    private final Path outerCapsulePath = new Path();
    private final Path innerCapsulePath = new Path();

    // RenderNodes (API 29+)
    private Object contentNodeObj;
    private Object blurNodeObj;
    private Object inactiveTabsNodeObj;
    private Object activeTabsNodeObj;
    private float cachedBlurRadius = -1f;
    private int cachedHalfW = -1;
    private int cachedHalfH = -1;

    // Cached tabs info
    private int cachedMenuHash = 0;
    private int cachedTabsW = -1;
    private int cachedTabsH = -1;

    // Touch delegation
    private int touchSlop;

    public LiquidHostLayout(Context context) {
        this(context, null);
    }

    public LiquidHostLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidHostLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public LiquidHostLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.density = context.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        initOpticalPaints();
    }

    private void initOpticalPaints() {
        barShadowPaint.setColor(0xFF000000);
        barShadowPaint.setMaskFilter(new BlurMaskFilter(7f * density, BlurMaskFilter.Blur.NORMAL));

        dropletShadowPaint.setColor(0xFF000000);
        dropletShadowPaint.setMaskFilter(new BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL));

        barOuterRimPaint.setStyle(Paint.Style.STROKE);
        barOuterRimPaint.setStrokeWidth(0.6f * density);

        barInnerRimPaint.setStyle(Paint.Style.STROKE);
        barInnerRimPaint.setStrokeWidth(0.9f * density);

        dropletBarRimPaint.setStyle(Paint.Style.STROKE);
        dropletBarRimPaint.setStrokeWidth(0.6f * density);

        causticBevelPaint.setStyle(Paint.Style.STROKE);
        specularRimPaint.setStyle(Paint.Style.STROKE);
        specularRimPaint.setStrokeWidth(1.15f * density);

        crispOutlinePaint.setStyle(Paint.Style.STROKE);
        crispOutlinePaint.setStrokeWidth(0.65f * density);

        tabTextPaint.setTextSize(10.5f * density);
        tabTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tabTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        resolveChildren();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resolveChildren();
        syncState();
    }

    private void resolveChildren() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof BottomNavigationView || child.getId() == R.id.bottom_nav) {
                bottomNav = (BottomNavigationView) child;
            } else if (contentChild == null) {
                contentChild = child;
            }
        }
        if (bottomNav != null) {
            bottomNav.setElevation(0f);
            bottomNav.setBackground(null);
            bottomNav.setItemRippleColor(null);
            Object tag = bottomNav.getTag(R.id.liquid_slot);
            if (tag instanceof BottomNavRouter) {
                router = (BottomNavRouter) tag;
            }
        }
        Activity act = LiquidGlass.findActivity(getContext());
        if (act != null && SwipeTabs.rank(act.getClass()) < 0 && bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
    }

    public void setRouter(BottomNavRouter router) {
        this.router = router;
    }

    public boolean isHostActive() {
        if (bottomNav == null || bottomNav.getVisibility() != View.VISIBLE) return false;
        Activity act = LiquidGlass.findActivity(getContext());
        if (act != null && SwipeTabs.rank(act.getClass()) < 0) return false;
        return getWidth() > 10 && getHeight() > 10 && bottomNav.getWidth() > 10;
    }

    public boolean isLiquidActive() {
        return isLiquidEnabled && isHostActive();
    }

    public void syncState() {
        Context ctx = getContext();
        if (ctx == null) return;
        this.isLiquidEnabled = GlassBar.isLiquidEnabled(ctx);

        this.surfaceColor = LiquidGlass.resolveThemeColor(ctx, R.attr.colorSurface, 0xFF141218) | 0xFF000000;
        this.bgColor = LiquidGlass.resolveThemeColor(ctx, android.R.attr.colorBackground, surfaceColor) | 0xFF000000;
        this.surfaceContainerHighColor = LiquidGlass.resolveThemeColor(
                ctx, R.attr.colorSurfaceContainerHigh, 0xFF2B2930) | 0xFF000000;
        this.primaryColor = LiquidGlass.resolveThemeColor(ctx, R.attr.colorPrimary, 0xFFD0BCFF) | 0xFF000000;
        this.primaryContainerColor = LiquidGlass.resolveThemeColor(
                ctx, R.attr.colorPrimaryContainer, 0xFF4F378B) | 0xFF000000;
        this.onSurfaceVariantColor = LiquidGlass.resolveThemeColor(
                ctx, R.attr.colorOnSurfaceVariant, 0xFFCAC4D0) | 0xFF000000;

        this.isDark = LiquidGlass.luma(surfaceColor) < 132;
        this.isBlack = isDark && (surfaceColor == 0xFF000000 || bgColor == 0xFF000000);

        this.inactiveTabColor = LiquidGlass.withAlpha(onSurfaceVariantColor, isDark ? 0xD8 : 0xC0);
        this.activeTabColor = primaryColor;

        if (isDark) {
            int lumContainer = LiquidGlass.luma(primaryContainerColor);
            if (lumContainer > 70) {
                this.restingPillBaseColor = blendColor(primaryContainerColor, 0xFF000000, 0.45f);
            } else {
                this.restingPillBaseColor = primaryContainerColor;
            }
        } else {
            this.restingPillBaseColor = primaryContainerColor;
        }

        rebuildShaders();
        cachedTabsW = -1; // force snapshot rebuild
        invalidate();
    }

    public static int blendColor(int c0, int c1, float t) {
        if (t <= 0f) return c0;
        if (t >= 1f) return c1;
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a = Math.round(a0 + (a1 - a0) * t);
        int r = Math.round(r0 + (r1 - r0) * t);
        int g = Math.round(g0 + (g1 - g0) * t);
        int b = Math.round(b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void rebuildShaders() {
        float maxDropH = 74f * density;

        // Top/bottom glaze brush
        int[] topGlazeColors = isDark
                ? new int[] { 0x38FFFFFF, 0x0FFFFFFF, 0x00FFFFFF, 0x0DFFFFFF, 0x24FFFFFF }
                : new int[] { 0x57FFFFFF, 0x1AFFFFFF, 0x0FFFFFFF, 0x24FFF8F7, 0x3DEDE2E1 };
        float[] topGlazeStops = new float[] { 0.00f, 0.22f, 0.55f, 0.85f, 1.00f };
        topGlazeShader = new LinearGradient(
                0f, 0f, 0f, maxDropH, topGlazeColors, topGlazeStops, Shader.TileMode.CLAMP);
        topGlazePaint.setShader(topGlazeShader);

        // Caustic chromatic bevel brush (cyan top #8BE9FD / warm peach bottom #FFD8C8)
        int[] causticColors = isDark
                ? new int[] { 0x42BFE9FF, 0x1F8BE9FD, 0x05FFFFFF, 0x05FFFFFF, 0x1FFFD8C8, 0x38FFFFFF }
                : new int[] { 0x6BFFFFFF, 0x249BE7FF, 0x05000000, 0x06000000, 0x24FFC8AE, 0x61FFFFFF };
        float[] causticStops = new float[] { 0.00f, 0.14f, 0.36f, 0.66f, 0.88f, 1.00f };
        causticBevelShader = new LinearGradient(
                0f, 0f, 0f, maxDropH, causticColors, causticStops, Shader.TileMode.CLAMP);
        causticBevelPaint.setShader(causticBevelShader);

        // Specular rim brush
        int[] specularColors = isDark
                ? new int[] { 0x8FFFFFFF, 0x29A0E6FF, 0x00FFFFFF, 0x24FFD4BE, 0x66FFFFFF }
                : new int[] { 0xE6FFFFFF, 0x3DB5ECFF, 0x00FFFFFF, 0x33FFD9C6, 0xCCFFFFFF };
        float[] specularStops = new float[] { 0.00f, 0.20f, 0.50f, 0.82f, 1.00f };
        specularRimShader = new LinearGradient(
                0f, 0f, 0f, maxDropH, specularColors, specularStops, Shader.TileMode.CLAMP);
        specularRimPaint.setShader(specularRimShader);

        crispOutlinePaint.setColor(isDark ? 0x3DFFFFFF : 0x12000000);
        dropletBarRimPaint.setColor(isDark ? 0x24FFFFFF : 0x0B000000);
        barOuterRimPaint.setColor(isDark ? 0x29FFFFFF : 0x0D000000);
    }

    @Override
    public void onDescendantInvalidated(View child, View target) {
        super.onDescendantInvalidated(child, target);
        if (child != bottomNav && !isRecordingContent && isHostActive()) {
            invalidate();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isHostActive() && bottomNav != null) {
            float x = ev.getX();
            float y = ev.getY();
            int bL = bottomNav.getLeft();
            int bT = bottomNav.getTop();
            int bR = bottomNav.getRight();
            int bB = bottomNav.getBottom();
            float extraY = 6f * density;
            if (x >= bL && x <= bR && y >= (bT - extraY) && y <= (bB + extraY)) {
                Activity act = LiquidGlass.findActivity(getContext());
                if (act != null) {
                    View d = act.findViewById(R.id.drawer_layout);
                    if (d instanceof DrawerLayout && ((DrawerLayout) d).isDrawerOpen(0x800003)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isHostActive() && bottomNav != null && router != null) {
            float x = ev.getX();
            float y = ev.getY();
            int bL = bottomNav.getLeft();
            int bT = bottomNav.getTop();
            int bR = bottomNav.getRight();
            int bB = bottomNav.getBottom();
            float extraY = 6f * density;
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (x < bL || x > bR || y < (bT - extraY) || y > (bB + extraY)) {
                    return false;
                }
            }
            return router.handleBarTouch(ev, bL, bT, bR, bB);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (isRecordingContent) {
            return super.drawChild(canvas, child, drawingTime);
        }
        if (isHostActive() && child == bottomNav) {
            // Suppress default BottomNavigationView overdraw; rendered via our Pure-GPU crystal layers
            return false;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        if (!isHostActive() || bottomNav == null) {
            super.dispatchDraw(canvas);
            return;
        }

        int barL = bottomNav.getLeft();
        int barT = bottomNav.getTop();
        int barR = bottomNav.getRight();
        int barB = bottomNav.getBottom();
        int barW = barR - barL;
        int barH = barB - barT;

        if (barW <= 10 || barH <= 10) {
            super.dispatchDraw(canvas);
            return;
        }

        boolean hw = canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 29;

        // 1. Record / draw content child
        if (hw && contentChild != null) {
            if (contentNodeObj == null) {
                contentNodeObj = Api29Helper.createRenderNode("LiquidContent");
            }
            Api29Helper.setPosition(contentNodeObj, 0, 0, w, h);
            Canvas rec = Api29Helper.beginRecording(contentNodeObj, w, h);
            isRecordingContent = true;
            try {
                super.drawChild(rec, contentChild, getDrawingTime());
            } finally {
                isRecordingContent = false;
            }
            Api29Helper.endRecording(contentNodeObj);
            Api29Helper.drawRenderNode(canvas, contentNodeObj);
        } else if (contentChild != null) {
            super.drawChild(canvas, contentChild, getDrawingTime());
        }

        // 2. Prepare tab icons snapshot if needed
        syncTabsIfNeeded(barW, barH);

        // Retrieve live motion state from router
        float visualSlot = router != null ? router.visualSlot : 0f;
        float pressVal = router != null ? router.pressVal : 0f;
        float panelOffsetPx = router != null ? router.panelOffsetPx : 0f;

        // Apply panel rubberband offset
        if (Math.abs(panelOffsetPx) > 0.05f) {
            barL += Math.round(panelOffsetPx);
            barR += Math.round(panelOffsetPx);
        }

        float barPadPx = 4f * density;
        float innerW = Math.max(1f, barW - barPadPx * 2f);
        float baseH = Math.max(1f, barH - barPadPx * 2f);
        int tabCount = (bottomNav.getMenu() != null && bottomNav.getMenu().size() > 0)
                ? bottomNav.getMenu().size() : 4;
        float slotW = innerW / (float) tabCount;

        // 3. LAYER 1: 64dp Outer Capsule Bar
        float barRadius = barH * 0.5f;
        barRectF.set(barL, barT, barR, barB);
        barPath.rewind();
        barPath.addRoundRect(barRectF, barRadius, barRadius, Path.Direction.CW);

        // Outer soft drop shadow (elevation ~8dp)
        canvas.save();
        canvas.translate(0f, 2f * density);
        barShadowPaint.setAlpha(isDark ? 68 : 28);
        canvas.drawRoundRect(barRectF, barRadius, barRadius, barShadowPaint);
        canvas.restore();

        // Clip to 64dp capsule bar
        canvas.save();
        canvas.clipPath(barPath);

        if (isLiquidEnabled && Build.VERSION.SDK_INT >= 31 && hw && contentNodeObj != null) {
            int halfW = Math.max(1, (int) Math.ceil(barW * 0.5f));
            int halfH = Math.max(1, (int) Math.ceil(barH * 0.5f));
            if (blurNodeObj == null) {
                blurNodeObj = Api29Helper.createRenderNode("LiquidBarBlur");
            }
            Api29Helper.setPosition(blurNodeObj, 0, 0, halfW, halfH);
            boolean safeMode = SafeMode.isOn(getContext());
            float blurRadius = safeMode ? 6.4f * density : 3.2f * density;
            if (Math.abs(blurRadius - cachedBlurRadius) > 0.1f || cachedHalfW != halfW || cachedHalfH != halfH) {
                cachedBlurRadius = blurRadius;
                cachedHalfW = halfW;
                cachedHalfH = halfH;
                if (Build.VERSION.SDK_INT >= 33) {
                    Api33Helper.setBarLiquidEffect(blurNodeObj, blurRadius, halfW, halfH, barRadius * 0.5f, density);
                } else {
                    Api31Helper.setBlurEffect(blurNodeObj, blurRadius);
                }
            }
            Canvas bRec = Api29Helper.beginRecording(blurNodeObj, halfW, halfH);
            bRec.drawColor(bgColor);
            bRec.scale(0.5f, 0.5f);
            bRec.translate(-barL, -barT);
            Api29Helper.drawRenderNode(bRec, contentNodeObj);
            Api29Helper.endRecording(blurNodeObj);

            canvas.save();
            canvas.translate(barL, barT);
            canvas.scale(2.0f, 2.0f);
            Api29Helper.drawRenderNode(canvas, blurNodeObj);
            canvas.restore();
        } else {
            // Solid / translucent fallback
            int solidColor = isLiquidEnabled
                    ? LiquidGlass.withAlpha(surfaceContainerHighColor, isDark ? 0xD2 : 0xDC)
                    : LiquidGlass.withAlpha(surfaceContainerHighColor, 0xF5);
            barGlazePaint.setColor(solidColor);
            canvas.drawRect(barRectF, barGlazePaint);
        }

        // Warm milky / dark translucent glaze
        if (isLiquidEnabled) {
            int glazeColor = isDark
                    ? LiquidGlass.withAlpha(surfaceContainerHighColor, 87)
                    : 0x47FFFBFB; // 0xFFFFFBFB @ 0.28f
            barGlazePaint.setColor(glazeColor);
            canvas.drawRect(barRectF, barGlazePaint);
        }

        // Outer 0.6dp rim
        float outerInset = 0.3f * density;
        strokeRectF.set(barL + outerInset, barT + outerInset, barR - outerInset, barB - outerInset);
        canvas.drawRoundRect(strokeRectF, barRadius - outerInset, barRadius - outerInset, barOuterRimPaint);

        // Caustic chromatic rim (cyan top / warm peach bottom)
        if (causticBevelShader != null) {
            float rimInset = 0.5f * density;
            strokeRectF.set(barL + rimInset, barT + rimInset, barR - rimInset, barB - rimInset);
            shaderMatrix.setScale(1f, barH / (74f * density));
            shaderMatrix.postTranslate(0f, barT);
            causticBevelShader.setLocalMatrix(shaderMatrix);
            causticBevelPaint.setStrokeWidth(1.2f * density);
            canvas.drawRoundRect(strokeRectF, barRadius - rimInset, barRadius - rimInset, causticBevelPaint);
        }

        // Inner 0.9dp specular gradient rim
        float innerInset = 0.6f * density + 0.45f * density;
        strokeRectF.set(barL + innerInset, barT + innerInset, barR - innerInset, barB - innerInset);
        if (barInnerRimShader == null) {
            int topCol = isDark ? 0x47FFFFFF : 0xE0FFFFFF;
            int botCol = isDark ? 0x14FFFFFF : 0x70FFFFFF;
            barInnerRimShader = new LinearGradient(
                    0f, barT, 0f, barB, topCol, botCol, Shader.TileMode.CLAMP);
            barInnerRimPaint.setShader(barInnerRimShader);
        }
        canvas.drawRoundRect(strokeRectF, barRadius - innerInset, barRadius - innerInset, barInnerRimPaint);

        canvas.restore(); // restore Layer 1 clip

        // 4. LAYER 2: Stationary Tabs + 56dp Resting Pill
        float tabsLeft = barL + barPadPx;
        float tabsTop = barT + barPadPx;

        // 4a. Inactive tabs
        if (inactiveTabsNodeObj != null) {
            canvas.save();
            canvas.translate(tabsLeft, tabsTop);
            Api29Helper.drawRenderNode(canvas, inactiveTabsNodeObj);
            canvas.restore();
        }

        // 4b. Resting pill (when idleFactor > 0.01f)
        float pState = Math.max(0f, Math.min(1.2f, pressVal));
        float idleFactor = Math.max(0f, Math.min(1f, 1f - pState));

        if (idleFactor > 0.01f && slotW > 0f) {
            float pillLeft = tabsLeft + visualSlot * slotW;
            float pillRight = pillLeft + slotW;
            float pillTop = tabsTop;
            float pillBottom = tabsTop + baseH;
            float pillR = baseH * 0.5f;

            pillRectF.set(pillLeft, pillTop, pillRight, pillBottom);
            pillClipPath.rewind();
            pillClipPath.addRoundRect(pillRectF, pillR, pillR, Path.Direction.CW);

            int pillAlpha = Math.round(idleFactor * (isLiquidEnabled ? (isDark ? 0.72f : 0.66f) : 0.95f) * 255f);
            restingPillPaint.setColor(LiquidGlass.withAlpha(restingPillBaseColor, pillAlpha));
            canvas.drawRoundRect(pillRectF, pillR, pillR, restingPillPaint);

            if (activeTabsNodeObj != null) {
                canvas.save();
                canvas.clipPath(pillClipPath);
                canvas.translate(tabsLeft, tabsTop);
                Api29Helper.drawRenderNode(canvas, activeTabsNodeObj);
                canvas.restore();
            }
        }

        // 5. LAYER 3: 74dp Unclipped Crystal Magnifying Droplet (when pressProgress > 0.005f)
        if (pState > 0.005f && hw && slotW > 0f) {
            drawCrystalDroplet(
                    canvas, pState, visualSlot, barL, barT, barR, barB,
                    tabsLeft, tabsTop, slotW, baseH, barRadius, hw);
        }
    }

    private void drawCrystalDroplet(
            Canvas canvas, float p, float visualSlot,
            int barL, int barT, int barR, int barB,
            float tabsLeft, float tabsTop, float slotW, float baseH, float barRadius, boolean hw) {

        float pCol = Math.max(0f, Math.min(1f, p));
        float centerX = tabsLeft + (visualSlot + 0.5f) * slotW;
        float centerY = barT + (barB - barT) * 0.5f;

        float maxDropletH = 74f * density;
        float maxDropletW = slotW * 1.16f;
        float curW = lerp(slotW, maxDropletW, p);
        float curH = lerp(baseH, maxDropletH, p);

        float edgeAllow = 4f * density;
        float dropL = Math.max(barL - edgeAllow, Math.min(barR - curW + edgeAllow, centerX - curW * 0.5f));
        float dropR = dropL + curW;
        float dropT = centerY - curH * 0.5f;
        float dropB = dropT + curH;
        float dropCx = (dropL + dropR) * 0.5f;
        float dropCy = centerY;
        float outerR = Math.min(curW, curH) * 0.5f;

        // 1. Hardware Gaussian drop shadow on fixed geometry (GPU scaled)
        if (pCol > 0.03f) {
            canvas.save();
            canvas.translate(dropCx - maxDropletW * 0.5f, dropCy - maxDropletH * 0.5f + 2f * density);
            float sx = Math.max(0.1f, curW / maxDropletW);
            float sy = Math.max(0.1f, curH / maxDropletH);
            canvas.scale(sx, sy, maxDropletW * 0.5f, maxDropletH * 0.5f);
            dropletShadowPaint.setAlpha(Math.round((isDark ? 88f : 36f) * pCol));
            canvas.drawRoundRect(
                    0f, 0f, maxDropletW, maxDropletH,
                    maxDropletH * 0.5f, maxDropletH * 0.5f, dropletShadowPaint);
            canvas.restore();
        }

        // 2. Analytic RoundRect Capsule Paths
        outerCapsulePath.rewind();
        outerCapsulePath.addRoundRect(dropL, dropT, dropR, dropB, outerR, outerR, Path.Direction.CW);

        float bevelPx = 4.2f * density * pCol;
        float innerH = Math.max(2f, curH - bevelPx * 2f);
        float innerR = Math.min(curW - bevelPx * 2f, innerH) * 0.5f;
        innerCapsulePath.rewind();
        innerCapsulePath.addRoundRect(
                dropL + bevelPx, dropT + bevelPx,
                Math.max(dropL + bevelPx + 2f, dropR - bevelPx), dropB - bevelPx,
                innerR, innerR, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(outerCapsulePath);

        // 3. Meniscus Bevel Magnification (1.145x) & Window Magnification (1.055x) / AGSL Barrel Distortion (API 33+)
        if (isLiquidEnabled && contentNodeObj != null && hw) {
            boolean drawnAgsl = false;
            if (Build.VERSION.SDK_INT >= 33) {
                drawnAgsl = Api33Helper.drawDistortedDroplet(
                        canvas, contentNodeObj, dropCx, dropCy, curW, curH,
                        pCol, bgColor, getWidth(), getHeight());
            }
            if (!drawnAgsl) {
                // Outer 1.145x lens (Android 12 and below fallback)
                canvas.drawColor(bgColor);
                canvas.save();
                float outerZoom = lerp(1.0f, 1.145f, pCol);
                canvas.scale(outerZoom, outerZoom, dropCx, dropCy);
                Api29Helper.drawRenderNode(canvas, contentNodeObj);
                canvas.restore();

                // Inner 1.055x clear crystal window
                canvas.save();
                canvas.clipPath(innerCapsulePath);
                canvas.drawColor(bgColor);
                float innerZoom = lerp(1.0f, 1.055f, pCol);
                canvas.scale(innerZoom, innerZoom, dropCx, dropCy);
                Api29Helper.drawRenderNode(canvas, contentNodeObj);
                canvas.restore();
            }
        } else {
            canvas.drawColor(LiquidGlass.withAlpha(restingPillBaseColor, 0xFA));
        }

        // Subtle resting-pill blend during spring transition (<15ms)
        if (pCol < 0.99f) {
            int transAlpha = Math.round((1f - pCol) * 0.58f * 255f);
            restingTransitionPaint.setColor(LiquidGlass.withAlpha(restingPillBaseColor, transAlpha));
            canvas.drawRect(dropL, dropT, dropR, dropB, restingTransitionPaint);
        }

        // Outer bar rim continuity through crystal dome
        canvas.drawRoundRect(barL, barT, barR, barB, barRadius, barRadius, dropletBarRimPaint);

        // Meniscus glaze shader
        if (topGlazeShader != null) {
            shaderMatrix.setScale(1f, curH / maxDropletH);
            shaderMatrix.postTranslate(0f, dropT);
            topGlazeShader.setLocalMatrix(shaderMatrix);
            topGlazePaint.setAlpha(Math.round(pCol * 255f));
            canvas.drawRoundRect(dropL, dropT, dropR, dropB, outerR, outerR, topGlazePaint);
        }

        // 4. Stationary Active Tab Icons at exact scale = 1.0f -> 0.0px movement on scrub!
        if (activeTabsNodeObj != null) {
            canvas.save();
            canvas.translate(tabsLeft, tabsTop);
            Api29Helper.drawRenderNode(canvas, activeTabsNodeObj);
            canvas.restore();
        }

        // 5. Chromatic caustic meniscus shimmer + Fresnel specular rim
        if (bevelPx > 0.5f && causticBevelShader != null) {
            shaderMatrix.setScale(1f, curH / maxDropletH);
            shaderMatrix.postTranslate(0f, dropT);
            causticBevelShader.setLocalMatrix(shaderMatrix);
            causticBevelPaint.setStrokeWidth(bevelPx);
            causticBevelPaint.setAlpha(Math.round(pCol * 255f));
            float halfBevel = bevelPx * 0.5f;
            strokeRectF.set(dropL + halfBevel, dropT + halfBevel, dropR - halfBevel, dropB - halfBevel);
            float bRadius = Math.max(1f, outerR - halfBevel);
            canvas.drawRoundRect(strokeRectF, bRadius, bRadius, causticBevelPaint);
        }

        if (specularRimShader != null) {
            shaderMatrix.setScale(1f, curH / maxDropletH);
            shaderMatrix.postTranslate(0f, dropT);
            specularRimShader.setLocalMatrix(shaderMatrix);
            specularRimPaint.setAlpha(Math.round(pCol * 255f));
            float specInset = 0.65f * density;
            strokeRectF.set(dropL + specInset, dropT + specInset, dropR - specInset, dropB - specInset);
            float sRadius = Math.max(1f, outerR - specInset);
            canvas.drawRoundRect(strokeRectF, sRadius, sRadius, specularRimPaint);
        }

        crispOutlinePaint.setAlpha(Math.round((isDark ? 0.24f : 0.07f) * pCol * 255f));
        float crispInset = 0.325f * density;
        strokeRectF.set(dropL + crispInset, dropT + crispInset, dropR - crispInset, dropB - crispInset);
        float cRadius = Math.max(1f, outerR - crispInset);
        canvas.drawRoundRect(strokeRectF, cRadius, cRadius, crispOutlinePaint);

        canvas.restore(); // restore Layer 3 clip
    }

    private void syncTabsIfNeeded(int barW, int barH) {
        if (bottomNav == null || Build.VERSION.SDK_INT < 29) return;
        Menu menu = bottomNav.getMenu();
        if (menu == null || menu.size() <= 0) return;

        int menuHash = menu.size() * 31 + activeTabColor * 17 + inactiveTabColor;
        float barPadPx = 4f * density;
        int innerW = Math.max(1, Math.round(barW - barPadPx * 2f));
        int innerH = Math.max(1, Math.round(barH - barPadPx * 2f));

        if (cachedMenuHash == menuHash && cachedTabsW == innerW && cachedTabsH == innerH
                && inactiveTabsNodeObj != null && activeTabsNodeObj != null) {
            return;
        }

        cachedMenuHash = menuHash;
        cachedTabsW = innerW;
        cachedTabsH = innerH;

        if (inactiveTabsNodeObj == null) {
            inactiveTabsNodeObj = Api29Helper.createRenderNode("InactiveTabs");
        }
        if (activeTabsNodeObj == null) {
            activeTabsNodeObj = Api29Helper.createRenderNode("ActiveTabs");
        }

        Api29Helper.setPosition(inactiveTabsNodeObj, 0, 0, innerW, innerH);
        Api29Helper.setPosition(activeTabsNodeObj, 0, 0, innerW, innerH);

        Canvas inRec = Api29Helper.beginRecording(inactiveTabsNodeObj, innerW, innerH);
        Canvas actRec = Api29Helper.beginRecording(activeTabsNodeObj, innerW, innerH);

        int count = menu.size();
        float slotW = (float) innerW / (float) count;
        float iconPx = 22f * density;
        float gapPx = 2f * density;
        Paint.FontMetrics fm = tabTextPaint.getFontMetrics();
        float textH = fm.descent - fm.ascent;
        float blockH = iconPx + gapPx + textH;
        float topY = (innerH - blockH) * 0.5f;

        Resources res = getResources();

        for (int i = 0; i < count; i++) {
            MenuItem item = menu.getItem(i);
            float cx = (i + 0.5f) * slotW;
            float iconLeft = cx - iconPx * 0.5f;
            float iconTop = topY;
            float textBaseline = topY + iconPx + gapPx - fm.ascent;

            CharSequence title = item.getTitle();
            if (title == null) title = "";
            CharSequence elTitle = TextUtils.ellipsize(
                    title, tabTextPaint, Math.max(10f, slotW - 6f * density), TextUtils.TruncateAt.END);

            Drawable icon = item.getIcon();
            if (icon != null) {
                Drawable dIn = icon.getConstantState() != null ? icon.getConstantState().newDrawable(res).mutate() : icon;
                dIn.setBounds(Math.round(iconLeft), Math.round(iconTop), Math.round(iconLeft + iconPx), Math.round(iconTop + iconPx));
                dIn.setTint(inactiveTabColor);
                dIn.draw(inRec);

                Drawable dAct = icon.getConstantState() != null ? icon.getConstantState().newDrawable(res).mutate() : icon;
                dAct.setBounds(Math.round(iconLeft), Math.round(iconTop), Math.round(iconLeft + iconPx), Math.round(iconTop + iconPx));
                dAct.setTint(activeTabColor);
                dAct.draw(actRec);
            }

            tabTextPaint.setColor(inactiveTabColor);
            inRec.drawText(elTitle, 0, elTitle.length(), cx, textBaseline, tabTextPaint);

            tabTextPaint.setColor(activeTabColor);
            actRec.drawText(elTitle, 0, elTitle.length(), cx, textBaseline, tabTextPaint);
        }

        Api29Helper.endRecording(inactiveTabsNodeObj);
        Api29Helper.endRecording(activeTabsNodeObj);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // Isolate API 29 / 31 calls to prevent class-verifier crashes on older runtimes
    private static final class Api29Helper {
        static RenderNode createRenderNode(String name) {
            return new RenderNode(name);
        }

        static void setPosition(Object nodeObj, int left, int top, int right, int bottom) {
            ((RenderNode) nodeObj).setPosition(left, top, right, bottom);
        }

        static Canvas beginRecording(Object nodeObj, int w, int h) {
            return ((RenderNode) nodeObj).beginRecording(w, h);
        }

        static void endRecording(Object nodeObj) {
            ((RenderNode) nodeObj).endRecording();
        }

        static void drawRenderNode(Canvas canvas, Object nodeObj) {
            canvas.drawRenderNode((RenderNode) nodeObj);
        }
    }

    private static final class Api31Helper {
        static void setBlurEffect(Object nodeObj, float radius) {
            if (Build.VERSION.SDK_INT >= 31) {
                ((RenderNode) nodeObj).setRenderEffect(
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            }
        }
    }

    private static final class Api33Helper {
        private static RuntimeShader sShader;
        private static RuntimeShader sBarShader;
        private static RenderNode sDropletNode;

        static void setBarLiquidEffect(
                Object nodeObj, float blurRadius, float halfW, float halfH,
                float barRadius, float density) {
            try {
                if (sBarShader == null) {
                    String agsl =
                        "uniform shader contents;\n" +
                        "uniform vec4 uBarRect;\n" +
                        "uniform float uRadius;\n" +
                        "uniform float uBevelPx;\n" +
                        "uniform float uRefractPx;\n" +
                        "float sdRoundRect(vec2 p, vec2 b, float r) {\n" +
                        "    vec2 q = abs(p) - b + vec2(r);\n" +
                        "    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n" +
                        "}\n" +
                        "vec4 main(vec2 p) {\n" +
                        "    vec2 center = (uBarRect.xy + uBarRect.zw) * 0.5;\n" +
                        "    vec2 halfSize = (uBarRect.zw - uBarRect.xy) * 0.5;\n" +
                        "    vec2 localP = p - center;\n" +
                        "    float d = sdRoundRect(localP, halfSize, uRadius);\n" +
                        "    float innerDist = -d;\n" +
                        "    if (innerDist >= 0.0 && innerDist < uBevelPx) {\n" +
                        "        float t = 1.0 - (innerDist / uBevelPx);\n" +
                        "        float curve = t * t;\n" +
                        "        vec2 q = abs(localP) - halfSize + vec2(uRadius);\n" +
                        "        vec2 n = sign(localP) * normalize(max(q, vec2(0.001)));\n" +
                        "        vec2 offset = -n * curve * uRefractPx;\n" +
                        "        vec4 cR = contents.eval(p + offset * 1.25);\n" +
                        "        vec4 cG = contents.eval(p + offset * 1.00);\n" +
                        "        vec4 cB = contents.eval(p + offset * 0.75);\n" +
                        "        float rim = pow(t, 3.5) * 0.12;\n" +
                        "        return vec4(cR.r + rim, cG.g + rim, cB.b + rim, cG.a);\n" +
                        "    }\n" +
                        "    return contents.eval(p);\n" +
                        "}\n";
                    sBarShader = new RuntimeShader(agsl);
                }
                sBarShader.setFloatUniform("uBarRect", 0f, 0f, halfW, halfH);
                sBarShader.setFloatUniform("uRadius", barRadius);
                sBarShader.setFloatUniform("uBevelPx", 6.5f * density * 0.5f);
                sBarShader.setFloatUniform("uRefractPx", 3.2f * density * 0.5f);

                RenderEffect blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
                RenderEffect refractEffect = RenderEffect.createRuntimeShaderEffect(sBarShader, "contents");
                RenderEffect combined = RenderEffect.createChainEffect(refractEffect, blurEffect);
                ((RenderNode) nodeObj).setRenderEffect(combined);
            } catch (Throwable t) {
                Api31Helper.setBlurEffect(nodeObj, blurRadius);
            }
        }

        static boolean drawDistortedDroplet(
                Canvas canvas, Object contentNodeObj,
                float dropCx, float dropCy, float curW, float curH,
                float pCol, int bgColor, int totalW, int totalH) {
            if (Build.VERSION.SDK_INT < 33 || contentNodeObj == null) return false;
            try {
                if (sShader == null) {
                    String agsl =
                        "uniform shader contents;\n" +
                        "uniform vec2 uCenter;\n" +
                        "uniform vec2 uRadius;\n" +
                        "uniform float uDistortion;\n" +
                        "vec4 main(vec2 p) {\n" +
                        "    vec2 d = (p - uCenter) / max(uRadius, vec2(1.0));\n" +
                        "    float r2 = dot(d, d);\n" +
                        "    if (r2 < 1.0) {\n" +
                        "        float r = sqrt(r2);\n" +
                        "        float mag = 1.0 - uDistortion * 0.165 * (1.0 - r2);\n" +
                        "        float bevel = smoothstep(0.38, 0.85, r) * (1.0 - smoothstep(0.93, 1.0, r));\n" +
                        "        float disp = uDistortion * bevel * 0.055;\n" +
                        "        vec2 dir = p - uCenter;\n" +
                        "        vec4 cR = contents.eval(uCenter + dir * (mag - disp));\n" +
                        "        vec4 cG = contents.eval(uCenter + dir * mag);\n" +
                        "        vec4 cB = contents.eval(uCenter + dir * (mag + disp));\n" +
                        "        float caustic = bevel * 0.08;\n" +
                        "        return vec4(cR.r + caustic, cG.g + caustic * 0.9, cB.b + caustic * 1.2, cG.a);\n" +
                        "    }\n" +
                        "    return contents.eval(p);\n" +
                        "}\n";
                    sShader = new RuntimeShader(agsl);
                }
                if (sDropletNode == null) {
                    sDropletNode = new RenderNode("LiquidLensDroplet");
                }
                float rx = curW * 0.5f;
                float ry = curH * 0.5f;
                sShader.setFloatUniform("uCenter", dropCx, dropCy);
                sShader.setFloatUniform("uRadius", rx, ry);
                sShader.setFloatUniform("uDistortion", pCol);

                RenderEffect effect = RenderEffect.createRuntimeShaderEffect(sShader, "contents");
                sDropletNode.setRenderEffect(effect);
                sDropletNode.setPosition(0, 0, totalW, totalH);

                Canvas rec = sDropletNode.beginRecording(totalW, totalH);
                rec.drawColor(bgColor);
                Api29Helper.drawRenderNode(rec, contentNodeObj);
                sDropletNode.endRecording();

                canvas.drawRenderNode(sDropletNode);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
