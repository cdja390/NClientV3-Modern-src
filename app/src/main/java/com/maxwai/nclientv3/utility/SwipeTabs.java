package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.recyclerview.widget.RecyclerView;
import com.maxwai.nclientv3.FavoriteActivity;
import com.maxwai.nclientv3.HistoryActivity;
import com.maxwai.nclientv3.LocalActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.views.PageSwitcher;

/**
 * Modern launcher-style interactive swipe handler:
 * - 1:1 real-time finger tracking translation with subtle depth scaling (1.0 -> 0.985)
 * - Boundary resistance damping (0.28x) when swiping beyond edge tabs
 * - Smooth decelerated spring rebound on release if under threshold
 * - Telemetry forwarded to BottomNavRouter for synchronized liquid glass activation
 */
public final class SwipeTabs {
    private static final int TAG_TOUCH = 0x70ffff03;

    private SwipeTabs() {}

    public static void attach(Activity activity) {
        if (activity == null) return;
        View v = activity.findViewById(R.id.recycler);
        if (!(v instanceof RecyclerView)) return;
        RecyclerView rv = (RecyclerView) v;
        if (rv.getTag(TAG_TOUCH) != null) return;
        rv.setTag(TAG_TOUCH, Boolean.TRUE);
        rv.addOnItemTouchListener(new Touch(activity));
    }

    public static boolean isTabSwipeMode(Context ctx) {
        if (ctx == null) return true;
        SharedPreferences sp = ctx.getSharedPreferences("Settings", 0);
        String mode = sp.getString("swipe_mode", null);
        if (mode != null) {
            return "tabs".equals(mode);
        }
        return true;
    }

    public static int rank(Class<?> c) {
        if (c == MainActivity.class) return 0;
        if (c == LocalActivity.class) return 1;
        if (c == FavoriteActivity.class) return 2;
        if (c == HistoryActivity.class) return 3;
        return -1;
    }

    public static Class<?> cls(int r) {
        switch (r) {
            case 0: return MainActivity.class;
            case 1: return LocalActivity.class;
            case 2: return FavoriteActivity.class;
            case 3: return HistoryActivity.class;
            default: return MainActivity.class;
        }
    }

    static PageSwitcher getVisiblePageSwitcher(Activity activity) {
        View v = activity.findViewById(R.id.page_switcher);
        if (v instanceof PageSwitcher && v.getVisibility() == View.VISIBLE) {
            return (PageSwitcher) v;
        }
        return null;
    }

    public static void go(Activity activity, int dir) {
        if (activity == null || dir == 0) return;
        if (isTabSwipeMode(activity) && BottomNavRouter.isPrimaryTab(activity)) {
            int r = rank(activity.getClass()) + dir;
            if (r >= 0 && r <= 3) {
                BottomNavRouter.switchTo(activity, cls(r));
            }
            return;
        }
        PageSwitcher ps = getVisiblePageSwitcher(activity);
        if (ps != null && ps.canTurn(dir)) {
            ps.turn(dir);
            LiquidGlass.requestCapture(ps);
            View nav = activity.findViewById(R.id.bottom_nav);
            if (nav != null) {
                LiquidGlass.requestCapture(nav);
            }
        }
    }

    static final class Touch implements RecyclerView.OnItemTouchListener {
        private final Activity activity;
        private final float density;
        private float downRawX;
        private float downRawY;
        private boolean decided;
        private boolean intercepting;
        private boolean isAnimating;
        private VelocityTracker vt;

        Touch(Activity activity) {
            this.activity = activity;
            this.density = activity.getResources().getDisplayMetrics().density;
        }

        private void track(MotionEvent e) {
            if (vt == null) vt = VelocityTracker.obtain();
            MotionEvent raw = MotionEvent.obtain(e);
            raw.setLocation(e.getRawX(), e.getRawY());
            vt.addMovement(raw);
            raw.recycle();
        }

        private void releaseVt() {
            if (vt != null) {
                vt.recycle();
                vt = null;
            }
        }

        private boolean canHandleHorizontal(int dir) {
            if (isTabSwipeMode(activity) && BottomNavRouter.isPrimaryTab(activity)) {
                int r = rank(activity.getClass());
                return r >= 0 && (r + dir >= 0 && r + dir <= 3);
            }
            PageSwitcher ps = getVisiblePageSwitcher(activity);
            return ps != null && ps.canTurn(dir);
        }

        private void animateSnapBack(final View rv) {
            if (rv == null) return;
            isAnimating = true;
            rv.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            resetViewState(rv);
                            isAnimating = false;
                            BottomNavRouter.onContentSwipeEnd(activity, false);
                        }
                    })
                    .start();
        }

        private void resetViewState(View v) {
            if (v != null) {
                v.setTranslationX(0f);
                v.setAlpha(1.0f);
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
            if (isAnimating) return true;
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                releaseVt();
                track(e);
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                decided = false;
                intercepting = false;
                return false;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                track(e);
                if (decided) return intercepting;

                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                float adx = Math.abs(dx);
                float ady = Math.abs(dy);
                float slop = 14f * density;

                if (ady > slop && ady > adx * 0.85f) {
                    decided = true;
                    intercepting = false;
                    return false;
                }

                if (adx >= slop && adx > ady * 1.15f) {
                    if (dx > 0f && downRawX < 28f * density
                            && activity.findViewById(R.id.drawer_layout) != null) {
                        decided = true;
                        intercepting = false;
                        return false;
                    }
                    decided = true;
                    intercepting = true;
                    ViewParent p = rv.getParent();
                    if (p != null) {
                        p.requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                return false;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                releaseVt();
                decided = false;
                intercepting = false;
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {
            if (isAnimating) return;
            int action = e.getActionMasked();
            track(e);

            if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - downRawX;
                int dir = dx < 0f ? 1 : -1;
                boolean canGo = canHandleHorizontal(dir);
                float width = rv.getWidth() > 0 ? (float) rv.getWidth() : 1080f;

                if (canGo) {
                    float maxDamp = 48f * density;
                    float damped = (float) (Math.signum(dx) * Math.min(maxDamp, Math.abs(dx) * 0.35f));
                    rv.setTranslationX(damped);
                    float progress = Math.min(1.0f, Math.abs(damped) / maxDamp);
                    rv.setAlpha(1.0f - progress * 0.05f);
                    float scale = 1.0f - progress * 0.012f;
                    rv.setScaleX(scale);
                    rv.setScaleY(scale);

                    if (isTabSwipeMode(activity) && BottomNavRouter.isPrimaryTab(activity)) {
                        int r = rank(activity.getClass());
                        float frac = r - dx / width;
                        boolean moving = Math.abs(dx) / width > 0.008f;
                        BottomNavRouter.onContentSwipe(activity, frac, moving);
                    }
                } else {
                    float damped = (float) (Math.signum(dx) * Math.min(24f * density, Math.abs(dx) * 0.16f));
                    rv.setTranslationX(damped);
                    rv.setAlpha(1.0f);
                    rv.setScaleX(1.0f);
                    rv.setScaleY(1.0f);

                    if (isTabSwipeMode(activity) && BottomNavRouter.isPrimaryTab(activity)) {
                        int r = rank(activity.getClass());
                        float frac = r - damped / width;
                        BottomNavRouter.onContentSwipe(activity, frac, Math.abs(damped) > 12f * density);
                    }
                }
                return;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                float vx = 0f;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                }
                releaseVt();
                decided = false;
                intercepting = false;

                if (action == MotionEvent.ACTION_CANCEL) {
                    animateSnapBack(rv);
                    return;
                }

                float adx = Math.abs(dx);
                float ady = Math.abs(dy);
                int dir = dx < 0f ? 1 : -1;
                boolean reversed = (dir > 0 && vx > 350f * density)
                        || (dir < 0 && vx < -350f * density);
                boolean farEnough = adx >= 60f * density && adx > ady * 1.1f;
                boolean fastFling = adx >= 20f * density && Math.abs(vx) >= 420f * density;

                if (!reversed && (farEnough || fastFling) && canHandleHorizontal(dir)) {
                    BottomNavRouter.onContentSwipeEnd(activity, true);
                    resetViewState(rv);
                    BottomNavRouter.performSnapHaptic(rv);
                    go(activity, dir);
                } else {
                    animateSnapBack(rv);
                }
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
    }
}
