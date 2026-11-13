package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import com.maxwai.nclientv3.R;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

public class AutoPageTurner {
    public interface ReaderHost {
        boolean autoTurnNextPage();
        void hideBars();
        void showBars();
        int getZoomCurrentPage();
        int getZoomTotalPages();
    }

    public static final int MENU_ID_AUTO_TURN = 0x70ffff08;
    private static final int PILL_TEXT_VIEW_ID = 0x70ffff09;
    private static final String PREF_NAME = "AutoPageTurner";
    private static final String PREF_KEY_INTERVAL = "interval";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sFlipRunnable;
    private static boolean sIsRunning = false;
    private static int sIntervalSec = 5;
    private static long sStartTime = 0L;
    private static WeakReference<Activity> sCurrentActivity;
    private static View sPillView;
    private static boolean sKeptScreenOn;

    private static boolean owns(Activity activity) {
        return sCurrentActivity != null && sCurrentActivity.get() == activity;
    }

    public static int getIntervalSec() {
        return sIntervalSec;
    }

    public static void setupMenu(Activity activity, Menu menu) {
        if (menu == null) return;
        MenuItem item = menu.findItem(MENU_ID_AUTO_TURN);
        if (item == null) {
            item = menu.add(0, MENU_ID_AUTO_TURN, 999, R.string.auto_turn);
        }
        if (item != null) {
            if (sIsRunning && owns(activity)) {
                item.setTitle(activity.getString(R.string.auto_turn_running, sIntervalSec));
            } else {
                item.setTitle(R.string.auto_turn);
            }
        }
    }

    public static boolean onMenuItemSelected(Activity activity, MenuItem item) {
        if (item != null && item.getItemId() == MENU_ID_AUTO_TURN) {
            showSpeedDialog(activity);
            return true;
        }
        return false;
    }

    public static void showSpeedDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedInterval = sp.getInt(PREF_KEY_INTERVAL, 5);
        if (!sIsRunning && savedInterval > 0) {
            sIntervalSec = savedInterval;
        }

        final String[] items = new String[] {
            activity.getString(sIsRunning ? R.string.auto_turn_off_running : R.string.auto_turn_off),
            activity.getString(R.string.auto_turn_seconds, 3),
            activity.getString(R.string.auto_turn_seconds_recommended, 5),
            activity.getString(R.string.auto_turn_seconds, 8),
            activity.getString(R.string.auto_turn_seconds, 10),
            activity.getString(R.string.auto_turn_seconds, 15),
            activity.getString(R.string.auto_turn_custom)
        };

        final int[] intervals = new int[] { 0, 3, 5, 8, 10, 15, -1 };

        int checkedItem = 0;
        if (sIsRunning) {
            for (int i = 1; i < intervals.length - 1; i++) {
                if (intervals[i] == sIntervalSec) {
                    checkedItem = i;
                    break;
                }
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(R.string.auto_turn_dialog_title);
        builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which >= 0 && which < intervals.length) {
                    int selected = intervals[which];
                    if (selected == 0) {
                        stop(activity, activity.getString(R.string.auto_turn_closed));
                    } else if (selected == -1) {
                        showCustomIntervalDialog(activity);
                    } else {
                        start(activity, selected);
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    public static void showCustomIntervalDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.auto_turn_custom_hint);
        int current = sIntervalSec > 0 ? sIntervalSec : 5;
        input.setText(String.valueOf(current));
        input.setSelection(input.getText().length());

        FrameLayout container = new FrameLayout(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int marginH = dp(activity, 24);
        int marginV = dp(activity, 8);
        lp.setMargins(marginH, marginV, marginH, marginV);
        input.setLayoutParams(lp);
        container.addView(input);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(R.string.auto_turn_custom_title);
        builder.setView(container);
        builder.setPositiveButton(R.string.auto_turn_start, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String text = input.getText().toString().trim();
                int sec = 5;
                try {
                    sec = Integer.parseInt(text);
                } catch (Exception ignored) {}
                if (sec < 1) sec = 1;
                if (sec > 60) sec = 60;
                start(activity, sec);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    public static void start(final Activity activity, int intervalSec) {
        if (!(activity instanceof ReaderHost) || activity.isFinishing()) return;
        ReaderHost reader = (ReaderHost) activity;
        if (reader.getZoomTotalPages() <= 0) return;
        if (intervalSec <= 0) intervalSec = 5;

        stop(null, null);

        sIntervalSec = intervalSec;
        sIsRunning = true;
        sStartTime = SystemClock.uptimeMillis();
        sCurrentActivity = new WeakReference<>(activity);

        try {
            SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putInt(PREF_KEY_INTERVAL, intervalSec).apply();
        } catch (Throwable ignored) {}

        try {
            sKeptScreenOn = (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignored) {}

        showPill(activity, intervalSec);

        reader.hideBars();

        Toast.makeText(activity, activity.getString(R.string.auto_turn_started, intervalSec), Toast.LENGTH_SHORT).show();

        sFlipRunnable = new Runnable() {
            @Override
            public void run() {
                Activity act = sCurrentActivity != null ? sCurrentActivity.get() : null;
                if (act == null || act.isFinishing() || !sIsRunning) {
                    stop(act, null);
                    return;
                }
                if (act instanceof ReaderHost) {
                    ReaderHost host = (ReaderHost) act;
                    boolean turned = host.autoTurnNextPage();
                    if (turned) {
                        try {
                            if (sPillView != null) {
                                sPillView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            }
                        } catch (Throwable ignored) {}
                        updatePillText(act);
                        if (host.getZoomCurrentPage() >= host.getZoomTotalPages())
                            stop(act, act.getString(R.string.auto_turn_last_page));
                        else sHandler.postDelayed(this, sIntervalSec * 1000L);
                    } else {
                        stop(act, act.getString(R.string.auto_turn_last_page));
                    }
                }
            }
        };

        sHandler.postDelayed(sFlipRunnable, intervalSec * 1000L);
    }

    public static void stop(Activity activity, String reason) {
        if (!sIsRunning && sPillView == null) return;
        if (activity != null && !owns(activity)) return;
        sIsRunning = false;

        if (sFlipRunnable != null) {
            sHandler.removeCallbacks(sFlipRunnable);
            sFlipRunnable = null;
        }

        Activity act = activity;
        if (act == null && sCurrentActivity != null) {
            act = sCurrentActivity.get();
        }

        // Always clean up pill view unconditionally to prevent Activity/Window leak
        removePill(act);

        if (act != null) {
            try {
                if (!sKeptScreenOn) act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } catch (Throwable ignored) {}

            if (!act.isFinishing()) {
                if (act instanceof ReaderHost) ((ReaderHost) act).showBars();

                if (reason != null && !reason.isEmpty()) {
                    Toast.makeText(act, reason, Toast.LENGTH_SHORT).show();
                }
            }
        }

        sCurrentActivity = null;
    }

    public static boolean onDispatchTouchEvent(Activity activity, MotionEvent ev) {
        if (!sIsRunning || !owns(activity)) {
            return false;
        }
        if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (SystemClock.uptimeMillis() - sStartTime < 500) {
                return false;
            }
            stop(activity, activity.getString(R.string.auto_turn_stopped));
            return true;
        }
        return false;
    }

    public static void onPause(Activity activity) {
        if (sIsRunning && owns(activity)) {
            stop(activity, activity.getString(R.string.auto_turn_paused));
        }
    }

    public static void onDestroy(Activity activity) {
        stop(activity, null);
    }

    private static void showPill(final Activity activity, int intervalSec) {
        removePill(activity);

        FrameLayout decorView = (FrameLayout) activity.getWindow().getDecorView();

        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);

        int padH = dp(activity, 16);
        int padV = dp(activity, 10);
        pill.setPadding(padH, padV, padH, padV);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(0xE61E1E1E);
        gd.setCornerRadius(dp(activity, 22));
        gd.setStroke(dp(activity, 1), 0x33FFFFFF);
        pill.setBackground(gd);
        pill.setElevation(dp(activity, 8));

        TextView icon = new TextView(activity);
        icon.setText("▶ ");
        icon.setTextColor(0xFF4CAF50);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.addView(icon);

        TextView tv = new TextView(activity);
        tv.setId(PILL_TEXT_VIEW_ID);
        tv.setText(getPillLabel(activity, intervalSec));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(tv);

        TextView tvStop = new TextView(activity);
        tvStop.setText("  ✕");
        tvStop.setTextColor(0x99FFFFFF);
        tvStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.addView(tvStop);

        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop(activity, activity.getString(R.string.auto_turn_stopped));
            }
        });

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = dp(activity, 52);
        pill.setLayoutParams(flp);

        pill.setAlpha(0f);
        decorView.addView(pill);
        pill.animate().alpha(1f).setDuration(250).start();

        sPillView = pill;
    }

    private static void updatePillText(Activity activity) {
        if (sPillView == null) return;
        TextView tv = sPillView.findViewById(PILL_TEXT_VIEW_ID);
        if (tv != null) {
            tv.setText(getPillLabel(activity, sIntervalSec));
        }
    }

    private static String getPillLabel(Activity activity, int intervalSec) {
        if (activity instanceof ReaderHost) {
            ReaderHost host = (ReaderHost) activity;
            int cur = host.getZoomCurrentPage();
            int tot = host.getZoomTotalPages();
            if (tot > 0) {
                return activity.getString(R.string.auto_turn_pill_progress, intervalSec, cur, tot);
            }
        }
        return activity.getString(R.string.auto_turn_pill, intervalSec);
    }

    private static void removePill(Activity activity) {
        if (sPillView != null) {
            final View pill = sPillView;
            sPillView = null;
            try {
                if (activity != null && !activity.isFinishing()) {
                    pill.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            ViewParent p = pill.getParent();
                            if (p instanceof ViewGroup) {
                                ((ViewGroup) p).removeView(pill);
                            }
                        }
                    }).start();
                } else {
                    ViewParent p = pill.getParent();
                    if (p instanceof ViewGroup) {
                        ((ViewGroup) p).removeView(pill);
                    }
                }
            } catch (Throwable t) {
                try {
                    ViewParent p = pill.getParent();
                    if (p instanceof ViewGroup) {
                        ((ViewGroup) p).removeView(pill);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val, ctx.getResources().getDisplayMetrics());
    }
}
