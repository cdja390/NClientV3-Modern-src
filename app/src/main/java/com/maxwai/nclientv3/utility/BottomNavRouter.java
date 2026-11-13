package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.maxwai.nclientv3.ehentai.EhDatabase;
import com.maxwai.nclientv3.FavoriteActivity;
import com.maxwai.nclientv3.HistoryActivity;
import com.maxwai.nclientv3.LocalActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.loginapi.User;
import com.maxwai.nclientv3.settings.Login;

/**
 * Pure-GPU 120Hz Floating Bottom Bar Controller & Zero-Reload Peer Tab Router (1:1 with Nc-ag KernelFloatingBottomBar).
 * - Permanent resident bottom_nav across tab switches:
 *   FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP + 1ms static transition
 * - Press-anywhere and glide physics: 900f/54f sub-stepped flight -> 1:1 finger lock -> 380f/0.88 + 500f/0.74 release settle
 * - PerformSubtleTabHaptic: SEGMENT_TICK (API 34+) / CLOCK_TICK (API 28-33) with +-0.54f hysteresis & multi-tab jump suppression
 * - Seamless Content swipe-to-tabs integration with SwipeTabs
 */
public class BottomNavRouter implements
        NavigationBarView.OnItemSelectedListener,
        NavigationBarView.OnItemReselectedListener {

    private static final int FLAGS_REUSE =
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            | Intent.FLAG_ACTIVITY_SINGLE_TOP;
    private static final int GRAVITY_START = 0x800003;

    // Cross-Activity spring handoff state
    private static float sHandoffSlot = Float.NaN;
    private static float sHandoffVel = 0f;
    private static float sHandoffPress = 0f;
    private static int sHandoffLastHapticSlot = -1;
    private static Class<?> sHandoffTarget = null;
    private static java.lang.ref.WeakReference<Activity> sCurrentActivity;

    static {
        try {
            Login.addUserListener(new Login.UserListener() {
                @Override
                public void onUserChanged(final User user) {
                    final Activity act = sCurrentActivity != null ? sCurrentActivity.get() : null;
                    if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Activity a = sCurrentActivity != null ? sCurrentActivity.get() : null;
                                if (a != null) {
                                    View nv = a.findViewById(R.id.nav_view);
                                    if (nv instanceof com.google.android.material.navigation.NavigationView) {
                                        updateNavHeaderLogin(a, (com.google.android.material.navigation.NavigationView) nv);
                                    }
                                }
                            }
                        });
                    }
                }
            });
        } catch (Throwable ignored) {}
    }
    private static long sHandoffUptime = 0L;

    final Activity activity;
    final BottomNavigationView bar;
    final float density;
    LiquidHostLayout host;
    int selected;
    boolean adjusting;

    // Live motion state for LiquidHostLayout (Layer 2 & Layer 3)
    float visualSlot;
    float targetSlot;
    float slotVel;
    float pressVal;
    float pressTarget;
    float pressVel;
    float panelOffsetPx;
    float panelTargetPx;
    float panelVel;

    // Touch gesture state
    private float startPointerX;
    private float latestPointerX;
    private int downSlot;
    private boolean isDragging;
    private boolean isBarControlling;
    private boolean isLockedToFinger;
    private int lastHapticTab;
    private int suppressHapticTarget = -1;
    private boolean contentSwiping;
    private int touchSlop;

    private boolean flightLift;
    private boolean springRunning;
    private boolean awaitFirstFrame;
    private long lastFrameNanos;

    // Per-Activity data fingerprints to avoid unnecessary reloads
    private boolean favInitialized;
    private long lastFavSig = Long.MIN_VALUE;
    private boolean histInitialized;
    private long lastHistSig = Long.MIN_VALUE;
    private boolean lastSafeMode;

    private final Runnable springFrame = new Runnable() {
        @Override
        public void run() {
            stepSpring();
        }
    };

    private final Runnable resetOwnTask = new Runnable() {
        @Override
        public void run() {
            stopSpringAndSnapOwn();
        }
    };

    private BottomNavRouter(Activity activity, BottomNavigationView bar, int selected) {
        this.activity = activity;
        this.bar = bar;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.selected = selected;
        int initSlot = slotOfItem(selected);
        this.visualSlot = initSlot;
        this.targetSlot = initSlot;
        this.lastHapticTab = initSlot;
        this.lastSafeMode = readSafeMode(activity);
    }

    private static boolean readSafeMode(Context ctx) {
        if (ctx == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences("Settings", 0);
        return sp.getBoolean("safe_mode", false);
    }

    public static boolean isPrimaryTab(Activity a) {
        if (a == null) return false;
        if (SwipeTabs.rank(a.getClass()) < 0) return false;
        View nav = a.findViewById(R.id.bottom_nav);
        return nav != null && (nav.getVisibility() == View.VISIBLE || isHiddenByUser(nav));
    }

    /** 4.6.0: "Hide bottom navigation bar" setting (key hide_bottom_nav, default off). */
    public static boolean isHiddenByUser(Context ctx) {
        return ctx != null && ctx.getSharedPreferences("Settings", 0).getBoolean("hide_bottom_nav", false);
    }

    /** True when the bar is gone only because of the setting, i.e. it would otherwise show. */
    public static boolean isHiddenByUser(View nav) {
        return nav != null && Boolean.TRUE.equals(nav.getTag(R.id.hidden_by_user));
    }

    /**
     * 4.6.0: shows or hides bottom_nav. {@code wanted} says whether the screen would show the bar;
     * the setting can still hide it. Keyed on bottom_nav only - never on page_switcher, whose id
     * the reader's page bar shares.
     */
    public static void applyUserVisibility(View nav, boolean wanted) {
        if (nav == null) return;
        boolean hide = wanted && isHiddenByUser(nav.getContext());
        nav.setTag(R.id.hidden_by_user, hide ? Boolean.TRUE : null);
        nav.setVisibility(wanted && !hide ? View.VISIBLE : View.GONE);
    }

    private static void refreshUserVisibility(Activity activity, View nav) {
        boolean wanted = nav.getVisibility() == View.VISIBLE || isHiddenByUser(nav);
        if (wanted) applyUserVisibility(nav, true);
        View nv = activity.findViewById(R.id.nav_view);
        if (nv instanceof com.google.android.material.navigation.NavigationView) {
            MenuItem browse = ((com.google.android.material.navigation.NavigationView) nv)
                .getMenu().findItem(R.id.nav_browse);
            if (browse != null) browse.setVisible(isHiddenByUser(nav));
        }
    }

    private static void applyStayTransition(Activity a) {
        if (a == null) return;
        a.overridePendingTransition(R.anim.nclient_slide_in_right, R.anim.nclient_slide_out_left);
    }

    public static void applyDirectionalTransition(Activity a, int fromSlot, int toSlot) {
        if (a == null) return;
        a.overridePendingTransition(R.anim.nclient_slide_in_right, R.anim.nclient_slide_out_left);
    }

    public static void openDrawer(Activity a) {
        if (a == null) return;
        View d = a.findViewById(R.id.drawer_layout);
        if (d instanceof DrawerLayout) {
            ((DrawerLayout) d).openDrawer(GRAVITY_START);
        }
    }

    public static void closeDrawer(Activity a) {
        if (a == null) return;
        View d = a.findViewById(R.id.drawer_layout);
        if (d instanceof DrawerLayout) {
            ((DrawerLayout) d).closeDrawers();
        }
    }

    private static void startTargetActivity(Activity ctx, String className) {
        try {
            Class<?> cls = Class.forName(className);
            ctx.startActivity(new Intent(ctx, cls));
        } catch (Throwable ignored) {}
    }

    public static void styleNavHeader(Activity activity, com.google.android.material.navigation.NavigationView navView) {
        if (navView == null || activity == null || navView.getHeaderCount() <= 0) return;
        View header = navView.getHeaderView(0);
        if (header == null) return;
        int surfaceContainer = LiquidGlass.resolveThemeColor(activity, R.attr.colorSurfaceContainerHigh, 0xFF2B2930) | 0xFF000000;
        int primary = LiquidGlass.resolveThemeColor(activity, R.attr.colorPrimary, 0xFFD0BCFF) | 0xFF000000;
        int primaryContainer = LiquidGlass.resolveThemeColor(activity, R.attr.colorPrimaryContainer, 0xFF4F378B) | 0xFF000000;
        boolean isDark = LiquidGlass.luma(surfaceContainer) < 132;
        int headerBg = isDark ? LiquidHostLayout.blendColor(surfaceContainer, primaryContainer, 0.28f) : primaryContainer;
        header.setBackgroundColor(headerBg);
        View iv = header.findViewById(R.id.imageView);
        if (iv == null) iv = header.findViewById(R.id.image);
        if (iv instanceof ImageView) {
            User user = Login.getUser();
            if (user == null) {
                ((ImageView) iv).setColorFilter(primary, PorterDuff.Mode.SRC_IN);
            } else {
                ((ImageView) iv).clearColorFilter();
            }
        }
    }

    private static boolean sIsUpdatingNavHeader = false;

    public static void updateNavHeaderLogin(final Activity activity, final com.google.android.material.navigation.NavigationView navView) {
        if (navView == null || activity == null) return;
        if (sIsUpdatingNavHeader) return;
        sIsUpdatingNavHeader = true;
        try {
            styleNavHeader(activity, navView);

            User user = Login.getUser();

            MenuItem loginItem = navView.getMenu().findItem(R.id.action_login);
            MenuItem logoutItem = navView.getMenu().findItem(R.id.action_logout);

            View headerView = navView.getHeaderCount() > 0 ? navView.getHeaderView(0) : null;
            ImageView iv = null;
            TextView tvUsername = null;
            if (headerView != null) {
                View v = headerView.findViewById(R.id.imageView);
                if (v == null) v = headerView.findViewById(R.id.image);
                if (v instanceof ImageView) iv = (ImageView) v;
                View tv = headerView.findViewById(R.id.textView3);
                if (tv instanceof TextView) tvUsername = (TextView) tv;
            }

            if (user != null) {
                if (loginItem != null) loginItem.setVisible(false);
                if (logoutItem != null) logoutItem.setVisible(true);
                if (tvUsername != null) tvUsername.setText(user.getUsername());
                if (iv != null) {
                    iv.clearColorFilter();
                    AvatarHelper.loadUserAvatar(activity, user, iv);
                }
            } else {
                if (loginItem != null) {
                    loginItem.setVisible(true);
                    loginItem.setTitle(R.string.login);
                }
                if (logoutItem != null) logoutItem.setVisible(false);
                if (tvUsername != null) tvUsername.setText(R.string.app_name);
                if (iv != null) {
                    iv.setImageResource(R.drawable.ic_logo);
                    int primary = LiquidGlass.resolveThemeColor(activity, R.attr.colorPrimary, 0xFFD0BCFF) | 0xFF000000;
                    iv.setColorFilter(primary, PorterDuff.Mode.SRC_IN);
                }
            }
        } finally {
            sIsUpdatingNavHeader = false;
        }
    }

    private static void showLogoutDialog(final Activity activity, final com.google.android.material.navigation.NavigationView navView) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.ic_exit_to_app)
            .setTitle(R.string.logout)
            .setMessage(R.string.are_you_sure)
            .setPositiveButton(R.string.yes, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    Login.logout();
                    updateNavHeaderLogin(activity, navView);
                }
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    public static void setupNavigationView(final Activity activity, final com.google.android.material.navigation.NavigationView navView) {
        if (navView == null || activity == null) return;
        updateNavHeaderLogin(activity, navView);
        if (activity instanceof MainActivity) return;
        navView.setNavigationItemSelectedListener(new com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                closeDrawer(activity);
                int id = item.getItemId();
                if (id == R.id.nav_browse) {
                    switchTo(activity, MainActivity.class);
                    return true;
                }
                if (id == R.id.downloaded) {
                    if (activity instanceof LocalActivity) return true;
                    switchTo(activity, LocalActivity.class);
                    return true;
                }
                if (id == R.id.favorite_manager) {
                    if (activity instanceof FavoriteActivity) return true;
                    switchTo(activity, FavoriteActivity.class);
                    return true;
                }
                if (id == R.id.history) {
                    if (activity instanceof HistoryActivity) return true;
                    switchTo(activity, HistoryActivity.class);
                    return true;
                }
                if (id == R.id.bookmarks) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.BookmarkActivity");
                    return true;
                }
                if (id == R.id.random) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.RandomActivity");
                    return true;
                }
                if (id == R.id.tag_manager) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.TagFilterActivity");
                    return true;
                }
                if (id == R.id.status_manager) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.StatusViewerActivity");
                    return true;
                }
                if (id == R.id.eh_source) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.ehentai.EhBrowseActivity");
                    return true;
                }
                if (id == R.id.action_settings) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.SettingsActivity");
                    return true;
                }
                if (id == R.id.action_login) {
                    startTargetActivity(activity, "com.maxwai.nclientv3.LoginActivity");
                    return true;
                }
                if (id == R.id.action_logout) {
                    showLogoutDialog(activity, navView);
                    return true;
                }
                return true;
            }
        });
    }

    static int slotOfItem(int itemId) {
        if (itemId == R.id.nav_browse) return 0;
        if (itemId == R.id.downloaded) return 1;
        if (itemId == R.id.favorite_manager) return 2;
        if (itemId == R.id.history) return 3;
        return 0;
    }

    static int itemOfSlot(int slot) {
        switch (slot) {
            case 0: return R.id.nav_browse;
            case 1: return R.id.downloaded;
            case 2: return R.id.favorite_manager;
            case 3: return R.id.history;
            default: return R.id.nav_browse;
        }
    }

    public static void install(final Activity activity, int selectedId) {
        if (activity == null) return;
        sCurrentActivity = new java.lang.ref.WeakReference<>(activity);
        View v = activity.findViewById(R.id.bottom_nav);
        if (!(v instanceof BottomNavigationView)) return;
        final BottomNavigationView bar = (BottomNavigationView) v;
        refreshUserVisibility(activity, bar);

        final BottomNavRouter router = new BottomNavRouter(activity, bar, selectedId);
        router.adjusting = true;
        bar.setItemActiveIndicatorEnabled(false);
        bar.setOnItemSelectedListener(router);
        bar.setOnItemReselectedListener(router);
        bar.setTag(R.id.liquid_slot, router);
        bar.setSelectedItemId(selectedId);
        router.adjusting = false;

        ViewParent parent = bar.getParent();
        if (parent instanceof LiquidHostLayout) {
            router.host = (LiquidHostLayout) parent;
            router.host.setRouter(router);
        }

        if (activity.getClass() != MainActivity.class && activity instanceof ComponentActivity) {
            ComponentActivity ca = (ComponentActivity) activity;
            ca.getOnBackPressedDispatcher().addCallback(ca, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    backHome(activity);
                }
            });
        }

        View d = activity.findViewById(R.id.drawer_layout);
        if (d instanceof DrawerLayout) {
            ((DrawerLayout) d).addDrawerListener(new DrawerLift(activity, bar, 0f));
        }

        View nv = activity.findViewById(R.id.nav_view);
        if (nv instanceof com.google.android.material.navigation.NavigationView) {
            setupNavigationView(activity, (com.google.android.material.navigation.NavigationView) nv);
        }

        BarSize.apply(activity);
        GlassBar.attach(bar);
        SwipeTabs.attach(activity);
        router.peekHandoffForInstall(selectedId);

        bar.post(new Runnable() {
            @Override
            public void run() {
                setupPermanentDrawerButton(activity);
                if (router.host != null) {
                    router.host.syncState();
                }
            }
        });
    }

    public static void sync(Activity activity, int selectedId) {
        if (activity == null) return;
        sCurrentActivity = new java.lang.ref.WeakReference<>(activity);
        setupPermanentDrawerButton(activity);
        View nv = activity.findViewById(R.id.nav_view);
        if (nv instanceof com.google.android.material.navigation.NavigationView) {
            updateNavHeaderLogin(activity, (com.google.android.material.navigation.NavigationView) nv);
        }

        View v = activity.findViewById(R.id.bottom_nav);
        if (!(v instanceof BottomNavigationView)) return;
        BottomNavigationView bar = (BottomNavigationView) v;
        refreshUserVisibility(activity, bar);
        BarSize.apply(activity);
        setupPermanentDrawerButton(activity);

        Object tag = bar.getTag(R.id.liquid_slot);
        if (!(tag instanceof BottomNavRouter)) {
            install(activity, selectedId);
            tag = bar.getTag(R.id.liquid_slot);
            if (!(tag instanceof BottomNavRouter)) return;
        }

        BottomNavRouter router = (BottomNavRouter) tag;
        bar.removeCallbacks(router.resetOwnTask);
        router.selected = selectedId;
        router.adjusting = true;
        bar.setItemActiveIndicatorEnabled(false);
        bar.setSelectedItemId(selectedId);
        router.adjusting = false;

        ViewParent parent = bar.getParent();
        if (parent instanceof LiquidHostLayout) {
            router.host = (LiquidHostLayout) parent;
            router.host.setRouter(router);
            router.host.syncState();
        }

        boolean curSafe = readSafeMode(activity);
        if (curSafe != router.lastSafeMode) {
            router.lastSafeMode = curSafe;
            View rv = activity.findViewById(R.id.recycler);
            if (rv instanceof RecyclerView) {
                RecyclerView.Adapter<?> ad = ((RecyclerView) rv).getAdapter();
                if (ad != null) {
                    ad.notifyDataSetChanged();
                }
            }
        }

        router.consumeHandoffInSync(selectedId);
    }

    public static boolean shouldReloadFavorites(Activity activity) {
        if (activity == null) return true;
        long sig = queryTableSignature(
                "SELECT COUNT(*), COALESCE(MAX(time),0), COALESCE(SUM(id_gallery),0) FROM Favorite");
        View v = activity.findViewById(R.id.bottom_nav);
        if (v instanceof BottomNavigationView) {
            Object tag = v.getTag(R.id.liquid_slot);
            if (tag instanceof BottomNavRouter) {
                BottomNavRouter r = (BottomNavRouter) tag;
                if (!r.favInitialized || sig == Long.MIN_VALUE || r.lastFavSig != sig) {
                    r.favInitialized = true;
                    r.lastFavSig = sig;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public static boolean hasHistoryChanged(Activity activity) {
        if (activity == null) return false;
        long sig = historySignature(activity);
        View v = activity.findViewById(R.id.bottom_nav);
        if (v instanceof BottomNavigationView) {
            Object tag = v.getTag(R.id.liquid_slot);
            if (tag instanceof BottomNavRouter) {
                BottomNavRouter r = (BottomNavRouter) tag;
                if (!r.histInitialized) {
                    r.histInitialized = true;
                    r.lastHistSig = sig;
                    return false;
                }
                if (sig != Long.MIN_VALUE && r.lastHistSig != sig) {
                    r.lastHistSig = sig;
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static void noteHistory(Activity activity) {
        if (activity == null) return;
        long sig = historySignature(activity);
        View v = activity.findViewById(R.id.bottom_nav);
        if (v instanceof BottomNavigationView) {
            Object tag = v.getTag(R.id.liquid_slot);
            if (tag instanceof BottomNavRouter) {
                BottomNavRouter r = (BottomNavRouter) tag;
                r.histInitialized = true;
                r.lastHistSig = sig;
            }
        }
    }

    private static long historySignature(Activity activity) {
        long nh = queryTableSignature(
                "SELECT COUNT(*), COALESCE(MAX(time),0), COALESCE(SUM(id),0) FROM History");
        try {
            return nh == Long.MIN_VALUE ? nh : 31 * nh + EhDatabase.get(activity).historySignature();
        } catch (RuntimeException e) {
            return nh;
        }
    }

    private static long queryTableSignature(String sql) {
        Cursor c = null;
        try {
            SQLiteDatabase db = Queries.db;
            if (db == null || !db.isOpen()) return Long.MIN_VALUE;
            c = db.rawQuery(sql, null);
            if (c != null && c.moveToFirst()) {
                long c0 = c.getLong(0);
                long c1 = c.getLong(1);
                long c2 = c.getLong(2);
                return (c0 * 31L + c1) * 31L + c2;
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored2) {
                }
            }
        }
        return Long.MIN_VALUE;
    }

    public static void setupPermanentDrawerButton(final Activity activity) {
        if (activity == null) return;
        View nav = activity.findViewById(R.id.bottom_nav);
        if (nav != null && nav.getVisibility() != View.VISIBLE && !isHiddenByUser(nav)) return;

        View tb = activity.findViewById(R.id.toolbar);
        if (!(tb instanceof Toolbar)) return;
        final Toolbar toolbar = (Toolbar) tb;

        if (activity.getClass() != MainActivity.class) {
            if (toolbar.getTag(R.id.liquid_slot) != null) return;
            toolbar.setTag(R.id.liquid_slot, Boolean.TRUE);
            if (activity instanceof AppCompatActivity) {
                ActionBar ab = ((AppCompatActivity) activity).getSupportActionBar();
                if (ab != null) {
                    ab.setDisplayHomeAsUpEnabled(false);
                    ab.setHomeButtonEnabled(false);
                }
            }
            final Runnable applyIcon = new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing()) return;
                    DrawerArrowDrawable icon = new DrawerArrowDrawable(activity);
                    toolbar.setNavigationIcon(icon);
                    toolbar.setNavigationContentDescription(R.string.navigation_drawer_open);
                    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openDrawer(activity);
                        }
                    });
                }
            };
            applyIcon.run();
            toolbar.post(applyIcon);
        }
    }

    private static void hidePeerUpArrow(Activity activity) {
        setupPermanentDrawerButton(activity);
    }

    public static void backHome(Activity activity) {
        if (activity == null) return;
        View d = activity.findViewById(R.id.drawer_layout);
        if (d instanceof DrawerLayout && ((DrawerLayout) d).isDrawerOpen(GRAVITY_START)) {
            ((DrawerLayout) d).closeDrawer(GRAVITY_START);
            return;
        }
        if (activity.getClass() == MainActivity.class) return;
        switchTo(activity, MainActivity.class);
    }

    public static void switchTo(Activity from, Class<?> target) {
        if (from == null || target == null) return;
        closeDrawer(from);
        if (from.getClass() == target) {
            View rv = from.findViewById(R.id.recycler);
            if (rv instanceof RecyclerView) {
                ((RecyclerView) rv).smoothScrollToPosition(0);
            }
            return;
        }

        int fromSlot = SwipeTabs.rank(from.getClass());
        int toSlot = SwipeTabs.rank(target);

        recordHandoffFrom(from, target);

        Intent intent = new Intent(from, target);
        intent.addFlags(FLAGS_REUSE);
        from.startActivity(intent);
        applyDirectionalTransition(from, fromSlot, toSlot);
    }

    private static void recordHandoffFrom(Activity from, Class<?> target) {
        sHandoffTarget = target;
        sHandoffUptime = SystemClock.uptimeMillis();
        View v = from.findViewById(R.id.bottom_nav);
        if (v instanceof BottomNavigationView) {
            Object tag = v.getTag(R.id.liquid_slot);
            if (tag instanceof BottomNavRouter) {
                BottomNavRouter r = (BottomNavRouter) tag;
                sHandoffSlot = r.visualSlot;
                sHandoffVel = r.slotVel;
                sHandoffPress = r.pressVal;
                sHandoffLastHapticSlot = r.lastHapticTab;
                r.springRunning = false;
                r.bar.removeCallbacks(r.springFrame);
                r.bar.removeCallbacks(r.resetOwnTask);
                r.bar.postDelayed(r.resetOwnTask, 180L);
                r.adjusting = true;
                r.bar.setSelectedItemId(r.selected);
                r.adjusting = false;
                return;
            }
        }
        int r0 = SwipeTabs.rank(from.getClass());
        if (r0 >= 0) {
            sHandoffSlot = r0;
            sHandoffVel = 0f;
            sHandoffPress = 0f;
            sHandoffLastHapticSlot = r0;
        }
    }

    void stopSpringAndSnapOwn() {
        springRunning = false;
        awaitFirstFrame = false;
        flightLift = false;
        isBarControlling = false;
        contentSwiping = false;
        bar.removeCallbacks(springFrame);
        int own = slotOfItem(selected);
        visualSlot = own;
        targetSlot = own;
        slotVel = 0f;
        pressVal = 0f;
        pressTarget = 0f;
        pressVel = 0f;
        panelOffsetPx = 0f;
        panelTargetPx = 0f;
        panelVel = 0f;
        lastHapticTab = own;
        suppressHapticTarget = -1;
        adjusting = true;
        bar.setSelectedItemId(selected);
        adjusting = false;
        invalidateHost();
    }

    private void peekHandoffForInstall(int selectedId) {
        int destSlot = slotOfItem(selectedId);
        targetSlot = destSlot;
        long age = SystemClock.uptimeMillis() - sHandoffUptime;
        if (!Float.isNaN(sHandoffSlot) && age >= 0L && age < 1500L) {
            visualSlot = sHandoffSlot;
            slotVel = sHandoffVel;
            pressVal = sHandoffPress;
            lastHapticTab = sHandoffLastHapticSlot >= 0 ? sHandoffLastHapticSlot : destSlot;
        } else {
            visualSlot = destSlot;
            slotVel = 0f;
            pressVal = 0f;
            lastHapticTab = destSlot;
        }
        invalidateHost();
    }

    private void consumeHandoffInSync(int selectedId) {
        int destSlot = slotOfItem(selectedId);
        targetSlot = destSlot;
        panelTargetPx = 0f;

        long age = SystemClock.uptimeMillis() - sHandoffUptime;
        boolean matchesTarget = (sHandoffTarget == null || sHandoffTarget == activity.getClass());
        if (matchesTarget && !Float.isNaN(sHandoffSlot) && age >= 0L && age < 1500L) {
            applyStayTransition(activity);
            float startSlot = sHandoffSlot;
            float startVel = sHandoffVel;
            float startPress = sHandoffPress;
            int prevHaptic = sHandoffLastHapticSlot;

            sHandoffSlot = Float.NaN;
            sHandoffVel = 0f;
            sHandoffPress = 0f;
            sHandoffLastHapticSlot = -1;
            sHandoffTarget = null;

            float dist = Math.abs(destSlot - startSlot);
            if (dist > 0.01f || Math.abs(startVel) > 0.05f || startPress > 0.02f) {
                visualSlot = startSlot;
                slotVel = startVel;
                flightLift = dist > 0.15f;
                pressVal = Math.max(startPress, flightLift ? 0.35f : 0f);
                pressTarget = 0f; // Settle droplet down upon arriving
                awaitFirstFrame = true;

                if (prevHaptic == destSlot) {
                    lastHapticTab = destSlot;
                    suppressHapticTarget = -1;
                } else {
                    lastHapticTab = prevHaptic >= 0 ? prevHaptic : destSlot;
                    suppressHapticTarget = destSlot;
                }

                invalidateHost();
                ensureSpringRunning();
                return;
            }
        }

        if (!springRunning) {
            visualSlot = destSlot;
            slotVel = 0f;
            pressVal = 0f;
            pressTarget = 0f;
            panelOffsetPx = 0f;
            lastHapticTab = destSlot;
            suppressHapticTarget = -1;
            invalidateHost();
        }
    }

    void ensureSpringRunning() {
        if (springRunning) return;
        springRunning = true;
        lastFrameNanos = System.nanoTime();
        bar.postOnAnimation(springFrame);
    }

    void stepSpring() {
        if (!springRunning || !bar.isAttachedToWindow()) {
            springRunning = false;
            return;
        }

        if (awaitFirstFrame) {
            awaitFirstFrame = false;
            lastFrameNanos = System.nanoTime();
            invalidateHost();
            bar.postOnAnimation(springFrame);
            return;
        }

        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) * 1e-9f;
        lastFrameNanos = now;
        if (dt <= 0.001f) dt = 0.008f;
        else if (dt > 0.025f) dt = 0.025f;

        // Release flight lift when capsule nears landing
        if (flightLift && Math.abs(targetSlot - visualSlot) < 0.18f) {
            flightLift = false;
            if (!isBarControlling && !contentSwiping) {
                pressTarget = 0f;
            }
        }

        int steps = (int) Math.ceil(dt / 0.004f);
        if (steps < 1) steps = 1;
        else if (steps > 6) steps = 6;
        float h = dt / (float) steps;

        // Springs:
        // High-stiffness tracking spring during flight: k=900, c=54 (zeta ~ 0.90)
        // Settle spring on release: k=380, c=34.3 (zeta ~ 0.88)
        // Droplet shape morph: k=500, c=33.1 (zeta ~ 0.74)
        float kSlot = (isBarControlling && !isLockedToFinger) ? 900f : 380f;
        float cSlot = (isBarControlling && !isLockedToFinger) ? 54.0f : 34.31f;
        final float kPress = 500f;
        final float cPress = 33.09f;
        final float kPanel = 420f;
        final float cPanel = 35.0f;

        for (int s = 0; s < steps; s++) {
            if (!(isBarControlling && isLockedToFinger) && !contentSwiping) {
                float aSlot = kSlot * (targetSlot - visualSlot) - cSlot * slotVel;
                slotVel += aSlot * h;
                visualSlot += slotVel * h;
                if (visualSlot < 0f) {
                    visualSlot = 0f;
                    if (slotVel < 0f) slotVel = 0f;
                } else if (visualSlot > 3f) {
                    visualSlot = 3f;
                    if (slotVel > 0f) slotVel = 0f;
                }
            }

            float aPress = kPress * (pressTarget - pressVal) - cPress * pressVel;
            pressVel += aPress * h;
            pressVal += pressVel * h;
            if (pressVal < 0f) pressVal = 0f;
            else if (pressVal > 1.2f) pressVal = 1.2f;

            float aPanel = kPanel * (panelTargetPx - panelOffsetPx) - cPanel * panelVel;
            panelVel += aPanel * h;
            panelOffsetPx += panelVel * h;
        }

        // Live Haptic check with +-0.54f hysteresis and multi-tab suppression
        checkSubtleHaptic(visualSlot);

        // Check if locked to finger while dragging
        if (isBarControlling && isDragging && !isLockedToFinger) {
            float dist = Math.abs(targetSlot - visualSlot);
            if (dist < 0.08f) {
                visualSlot = targetSlot;
                slotVel = 0f;
                isLockedToFinger = true;
                suppressHapticTarget = -1;
            }
        } else if (isBarControlling && !isDragging && !isLockedToFinger) {
            float dist = Math.abs(targetSlot - visualSlot);
            if (dist < 0.002f && Math.abs(slotVel) < 0.02f) {
                visualSlot = targetSlot;
                slotVel = 0f;
                isLockedToFinger = true;
            }
        }

        invalidateHost();

        boolean slotSettled = Math.abs(targetSlot - visualSlot) < 0.0025f && Math.abs(slotVel) < 0.012f;
        boolean pressSettled = Math.abs(pressTarget - pressVal) < 0.004f && Math.abs(pressVel) < 0.015f;
        boolean panelSettled = Math.abs(panelTargetPx - panelOffsetPx) < 0.12f && Math.abs(panelVel) < 0.8f;

        if (slotSettled && pressSettled && panelSettled && !isBarControlling && !contentSwiping) {
            visualSlot = targetSlot;
            slotVel = 0f;
            pressVal = pressTarget;
            pressVel = 0f;
            panelOffsetPx = panelTargetPx;
            panelVel = 0f;
            flightLift = false;
            invalidateHost();
            springRunning = false;
        } else {
            bar.postOnAnimation(springFrame);
        }
    }

    private void checkSubtleHaptic(float p) {
        if (suppressHapticTarget >= 0) {
            if (Math.abs(p - suppressHapticTarget) < 0.42f) {
                if (suppressHapticTarget != lastHapticTab) {
                    performSubtleTabHaptic(bar);
                    lastHapticTab = suppressHapticTarget;
                }
                suppressHapticTarget = -1;
            }
        } else {
            int candidate = lastHapticTab;
            if (p > candidate + 0.54f || p < candidate - 0.54f) {
                candidate = Math.max(0, Math.min(3, Math.round(p)));
            }
            if (candidate != lastHapticTab) {
                performSubtleTabHaptic(bar);
                lastHapticTab = candidate;
            }
        }
    }

    public static void performSubtleTabHaptic(View v) {
        if (v == null) return;
        int constant = (Build.VERSION.SDK_INT >= 34)
                ? HapticFeedbackConstants.SEGMENT_TICK
                : HapticFeedbackConstants.CLOCK_TICK;
        if (!v.performHapticFeedback(constant)) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    public static void performLiftHaptic(View v) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 27) {
            if (!v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        } else {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    public static void performSnapHaptic(View v) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 27) {
            if (!v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)) {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            }
        } else {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    private void invalidateHost() {
        if (host != null) {
            host.invalidate();
        } else {
            bar.invalidate();
        }
    }

    /**
     * Unified Press-Anywhere & Glide Touch handler invoked from LiquidHostLayout
     */
    boolean handleBarTouch(MotionEvent e, int barL, int barT, int barR, int barB) {
        int action = e.getActionMasked();
        float barPadPx = 4f * density;
        float innerW = Math.max(1f, (barR - barL) - barPadPx * 2f);
        float slotW = innerW / 4f;

        if (action == MotionEvent.ACTION_DOWN) {
            performLiftHaptic(bar);
            startPointerX = e.getX() - (barL + barPadPx);
            latestPointerX = startPointerX;
            isDragging = false;
            isBarControlling = true;
            flightLift = false;

            if (slotW > 0f) {
                downSlot = Math.max(0, Math.min(3, (int) (startPointerX / slotW)));
            } else {
                downSlot = slotOfItem(selected);
            }

            int ownSlot = slotOfItem(selected);
            isLockedToFinger = (downSlot == Math.round(visualSlot));
            suppressHapticTarget = (downSlot != lastHapticTab) ? downSlot : -1;

            targetSlot = downSlot;
            pressTarget = 1.0f; // Lift crystal droplet
            panelTargetPx = 0f;

            if (touchSlop <= 0) {
                touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            }

            ensureSpringRunning();
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (!isBarControlling) return false;
            latestPointerX = e.getX() - (barL + barPadPx);
            float dx = latestPointerX - startPointerX;

            if (!isDragging && Math.abs(dx) > touchSlop) {
                isDragging = true;
                ViewParent p = bar.getParent();
                if (p != null) {
                    p.requestDisallowInterceptTouchEvent(true);
                }
            }

            if (slotW > 0f) {
                float dragExcess = Math.max(0f, Math.abs(dx) - touchSlop);
                float fingerLockBlend = Math.max(0f, Math.min(1f, dragExcess / (slotW * 0.35f)));
                float relProgress = Math.max(0f, Math.min(3f, (float) downSlot + dx / slotW));
                float rawFinger = Math.max(0f, Math.min(3f, (latestPointerX / slotW) - 0.5f));
                float targetPos = isDragging ? lerp(relProgress, rawFinger, fingerLockBlend) : (float) downSlot;

                if (isDragging && isLockedToFinger) {
                    visualSlot = targetPos;
                    slotVel = 0f;
                    suppressHapticTarget = -1;
                } else {
                    targetSlot = targetPos;
                }
            }

            // Rubber-band micro-offset
            float normDx = dx / (90f * density);
            panelTargetPx = (float) Math.tanh(normDx) * (4.0f * density);

            ensureSpringRunning();
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!isBarControlling) return false;
            isBarControlling = false;
            pressTarget = 0f; // Release settle
            panelTargetPx = 0f;

            int ownSlot = slotOfItem(selected);

            if (action == MotionEvent.ACTION_CANCEL) {
                targetSlot = ownSlot;
                suppressHapticTarget = -1;
                lastHapticTab = ownSlot;
                ensureSpringRunning();
                return true;
            }

            performSnapHaptic(bar);
            int finalSlot = isDragging ? Math.max(0, Math.min(3, Math.round(visualSlot))) : downSlot;

            if (finalSlot == ownSlot) {
                targetSlot = ownSlot;
                ensureSpringRunning();
                if (!isDragging) {
                    View rv = activity.findViewById(R.id.recycler);
                    if (rv instanceof RecyclerView) {
                        ((RecyclerView) rv).smoothScrollToPosition(0);
                    }
                }
                return true;
            }

            if (!isDragging || Math.abs(finalSlot - visualSlot) > 0.52f) {
                if (finalSlot != lastHapticTab) {
                    suppressHapticTarget = finalSlot;
                }
            }

            switchTo(activity, SwipeTabs.cls(finalSlot));
            return true;
        }

        return false;
    }

    /**
     * Continuous content horizontal swipe telemetry forwarded from SwipeTabs.Touch
     */
    public static void onContentSwipe(Activity activity, float progress, boolean moving) {
        if (activity == null) return;
        View v = activity.findViewById(R.id.bottom_nav);
        if (!(v instanceof BottomNavigationView)) return;
        Object tag = v.getTag(R.id.liquid_slot);
        if (tag instanceof BottomNavRouter) {
            BottomNavRouter r = (BottomNavRouter) tag;
            r.contentSwiping = moving;
            r.visualSlot = Math.max(0f, Math.min(3f, progress));
            r.targetSlot = r.visualSlot;
            r.pressTarget = moving ? 1.0f : 0.0f;
            r.checkSubtleHaptic(r.visualSlot);
            r.ensureSpringRunning();
        }
    }

    public static void onContentSwipeEnd(Activity activity, boolean committed) {
        if (activity == null) return;
        View v = activity.findViewById(R.id.bottom_nav);
        if (!(v instanceof BottomNavigationView)) return;
        Object tag = v.getTag(R.id.liquid_slot);
        if (tag instanceof BottomNavRouter) {
            BottomNavRouter r = (BottomNavRouter) tag;
            r.contentSwiping = false;
            r.pressTarget = 0f;
            if (!committed) {
                r.targetSlot = slotOfItem(r.selected);
            }
            r.ensureSpringRunning();
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private boolean open(Class<?> target, int tappedSlot) {
        if (activity.getClass() == target) {
            targetSlot = tappedSlot;
            pressTarget = 0f;
            ensureSpringRunning();
            View rv = activity.findViewById(R.id.recycler);
            if (rv instanceof RecyclerView) {
                ((RecyclerView) rv).smoothScrollToPosition(0);
            }
            return true;
        }
        switchTo(activity, target);
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (adjusting || isBarControlling || isDragging) return true;
        performSnapHaptic(bar);
        int id = item.getItemId();
        if (id == R.id.nav_browse) return open(MainActivity.class, 0);
        if (id == R.id.downloaded) return open(LocalActivity.class, 1);
        if (id == R.id.favorite_manager) return open(FavoriteActivity.class, 2);
        if (id == R.id.history) return open(HistoryActivity.class, 3);
        return false;
    }

    @Override
    public void onNavigationItemReselected(MenuItem item) {
        onNavigationItemSelected(item);
    }

    /**
     * Preserved DrawerLift listener for DrawerLayout integration
     */
    static class DrawerLift implements DrawerLayout.DrawerListener {
        final Activity activity;
        final View bar;
        final float rest;
        private boolean headerUpdated = false;

        DrawerLift(Activity activity, View bar, float rest) {
            this.activity = activity;
            this.bar = bar;
            this.rest = rest;
        }

        DrawerLift(View bar, float rest) {
            this(null, bar, rest);
        }

        private void setLift(boolean show) {
            if (bar != null) {
                bar.setElevation(show ? rest : 0f);
            }
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            setLift(true);
            headerUpdated = false;
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            setLift(false);
            if (activity != null && drawerView instanceof com.google.android.material.navigation.NavigationView) {
                updateNavHeaderLogin(activity, (com.google.android.material.navigation.NavigationView) drawerView);
            }
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            setLift(slideOffset <= 0f);
            if (!headerUpdated && slideOffset > 0.05f) {
                headerUpdated = true;
                if (activity != null && drawerView instanceof com.google.android.material.navigation.NavigationView) {
                    updateNavHeaderLogin(activity, (com.google.android.material.navigation.NavigationView) drawerView);
                }
            }
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            if (newState == DrawerLayout.STATE_DRAGGING) {
                headerUpdated = false;
            }
        }
    }
}
