package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

public class SafeMode {
    private static int applied = -1;
    private static final int TAG_PEEK = 0x70ffff04;

    public static boolean isOn(Context ctx) {
        if (ctx == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences("Settings", 0);
        return sp.getBoolean("safe_mode", false);
    }

    public static boolean consumeChange(Context ctx) {
        boolean on = isOn(ctx);
        int onInt = on ? 1 : 0;
        if (onInt == applied) {
            return false;
        }
        applied = onInt;
        return true;
    }

    public static boolean toggle(Context ctx) {
        if (ctx == null) return false;
        boolean next = !isOn(ctx);
        SharedPreferences sp = ctx.getSharedPreferences("Settings", 0);
        sp.edit().putBoolean("safe_mode", next).apply();
        applied = next ? 1 : 0;
        return next;
    }

    public static void apply(final ImageView iv) {
        if (iv == null) return;
        boolean on = isOn(iv.getContext());
        if (on) {
            blur(iv);
            setupPeek(iv);
        } else {
            unblur(iv);
            iv.setOnTouchListener(null);
            iv.setTag(TAG_PEEK, null);
        }
    }

    private static void blur(ImageView iv) {
        if (iv == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            RenderEffect blurEffect = RenderEffect.createBlurEffect(70.0f, 70.0f, Shader.TileMode.CLAMP);
            iv.setRenderEffect(blurEffect);
            iv.setForeground(new ColorDrawable(0x991C1B1E));
        } else {
            iv.setColorFilter(0xB31C1B1E, PorterDuff.Mode.SRC_ATOP);
        }
    }

    private static void unblur(ImageView iv) {
        if (iv == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            iv.setRenderEffect(null);
            iv.setForeground(null);
        } else {
            iv.clearColorFilter();
        }
    }

    private static View findClickableParent(View child) {
        ViewParent p = child.getParent();
        while (p instanceof View) {
            View pv = (View) p;
            if (pv.hasOnClickListeners()) {
                return pv;
            }
            p = pv.getParent();
        }
        return null;
    }

    private static void setupPeek(final ImageView iv) {
        if (iv == null) return;
        if (iv.getTag(TAG_PEEK) != null) return;
        iv.setTag(TAG_PEEK, Boolean.TRUE);

        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] isPeeking = new boolean[]{false};
        final Runnable peekRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isOn(iv.getContext())) return;
                isPeeking[0] = true;
                iv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                ViewParent parent = iv.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                View pv = findClickableParent(iv);
                if (pv != null) {
                    pv.setPressed(false);
                }
                unblur(iv);
            }
        };

        iv.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isOn(iv.getContext())) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    isPeeking[0] = false;
                    View pv = findClickableParent(iv);
                    if (pv != null) {
                        pv.setPressed(true);
                    }
                    handler.postDelayed(peekRunnable, 260);
                    return true;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float slop = 16f * iv.getResources().getDisplayMetrics().density;
                    if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) {
                        handler.removeCallbacks(peekRunnable);
                        View pv = findClickableParent(iv);
                        if (pv != null) {
                            pv.setPressed(false);
                        }
                        if (isPeeking[0]) {
                            isPeeking[0] = false;
                            ViewParent parent = iv.getParent();
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(false);
                            }
                            blur(iv);
                        }
                    }
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(peekRunnable);
                    View pv = findClickableParent(iv);
                    if (pv != null) {
                        pv.setPressed(false);
                    }
                    if (isPeeking[0]) {
                        isPeeking[0] = false;
                        ViewParent parent = iv.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                        blur(iv);
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    handler.removeCallbacks(peekRunnable);
                    View pv = findClickableParent(iv);
                    if (pv != null) {
                        pv.setPressed(false);
                    }
                    if (isPeeking[0]) {
                        isPeeking[0] = false;
                        ViewParent parent = iv.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                        blur(iv);
                        return true;
                    } else {
                        // Normal short tap: trigger click on parent
                        if (pv != null) {
                            pv.performClick();
                        } else {
                            iv.performClick();
                        }
                        return true;
                    }
                }
                return true;
            }
        });
    }
}
