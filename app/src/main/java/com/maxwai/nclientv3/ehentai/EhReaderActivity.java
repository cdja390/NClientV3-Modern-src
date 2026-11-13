/* Copyright 2026 NClientV3-Modern contributors.
 * Licensed under the Apache License, Version 2.0. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.ehentai;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.GlideX;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.status.StatusManager;
import com.maxwai.nclientv3.components.widgets.CustomLinearLayoutManager;
import com.maxwai.nclientv3.github.chrisbanes.photoview.PhotoView;
import com.maxwai.nclientv3.settings.DefaultDialogs;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.AutoPageTurner;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.ReadingSessionHelper;
import com.maxwai.nclientv3.utility.ReadingStatsHelper;
import com.maxwai.nclientv3.utility.Utility;
import com.maxwai.nclientv3.utility.VolumeDirectionHelper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Source-aware reader with independent EH image loading and the shared reader controls. */
public class EhReaderActivity extends GeneralActivity implements AutoPageTurner.ReaderHost {
    private static final String MODE_KEY = "zoomScrollType";
    private static final int HORIZONTAL = 0, VERTICAL = 1, WEBTOON = 2;
    private static final long STATS_INTERVAL_MS = 30000L;
    private static final float MAX_SCALE = 8f;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<PageHolder> attached = new HashSet<>();
    private final Set<PageHolder> bound = new HashSet<>();
    private EhGalleryKey key;
    private EhGalleryInfo info;
    private SharedPreferences preferences;
    private ViewPager2 pager;
    private RecyclerView webtoon;
    private CustomLinearLayoutManager webtoonLayout;
    private Toolbar toolbar;
    private TextView pageLabel;
    private SeekBar seek;
    private PageAdapter pageAdapter, webtoonAdapter;
    private EhReadingSession statistics;
    private int mode, furthestTurn, furthestSeen;
    private int preloadDistance;
    private boolean positioning, webtoonGesture, seeking, rtl, statsRecording, volumeUp, volumeDown;
    private boolean consumeAutoTurnGesture;
    private volatile int current;
    @Nullable
    private File offlineDir;
    private boolean checkedOffline;
    private boolean barsVisible = true;
    private long lastPauseElapsed, lastPauseWall;
    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            if (!statsRecording) return;
            flushStatistics(false);
            handler.postDelayed(this, STATS_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eh_reader);
        key = EhIntents.key(getIntent());
        if (key == null) {
            finish();
            return;
        }
        info = new EhGalleryInfo(key);
        String title = getIntent().getStringExtra(EhIntents.EXTRA_TITLE);
        info.title = title == null ? "" : title;
        info.thumb = getIntent().getStringExtra(EhIntents.EXTRA_THUMB);
        info.pages = Math.max(0, getIntent().getIntExtra(EhIntents.EXTRA_PAGES, 0));
        int start = getIntent().getIntExtra(EhIntents.EXTRA_START, 1);
        if (savedInstanceState != null) {
            start = savedInstanceState.getInt("page", start);
            furthestTurn = savedInstanceState.getInt("furthest_turn", 0);
            furthestSeen = savedInstanceState.getInt("furthest_seen", 0);
        }

        preferences = getSharedPreferences("Settings", MODE_PRIVATE);
        String rtlKey = getString(R.string.preference_key_use_rtl);
        if (!preferences.contains(rtlKey) && EhSettings.hasReaderDirection(this))
            Global.updateReadingDirection(this, EhSettings.readerRtl(this));
        rtl = preferences.getBoolean(rtlKey, false);
        int storedMode = preferences.getInt(MODE_KEY, HORIZONTAL);
        mode = storedMode >= HORIZONTAL && storedMode <= WEBTOON ? storedMode : HORIZONTAL;
        int offscreen = Math.min(20, Global.getOffscreenLimit());
        preloadDistance = Math.min(3, offscreen);
        if (Global.isLockScreen()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setInsets();
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(info.title);
        }
        pageLabel = findViewById(R.id.eh_reader_page);
        pageLabel.setOnClickListener(v -> choosePage());
        seek = findViewById(R.id.eh_reader_seek);
        pager = findViewById(R.id.eh_pager);
        pager.setOffscreenPageLimit(Math.max(1, offscreen));
        webtoon = findViewById(R.id.eh_webtoon);
        webtoonLayout = new CustomLinearLayoutManager(this);
        webtoon.setLayoutManager(webtoonLayout);
        webtoon.setItemAnimator(null);
        pageAdapter = new PageAdapter(false);
        webtoonAdapter = new PageAdapter(true);
        bindPaging();
        statistics = new EhReadingSession(savedInstanceState == null, (count, ms, wall, maxPage) ->
            EhDatabase.get(this).addReadingStats(info, count, ms, wall, maxPage));

        if (info.pages > 0) setPageCount(info.pages, start);
        else loadPageCount(start);
    }

    private void setInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.eh_reader_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int gestureBottom = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom;
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, gestureBottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void bindPaging() {
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (!positioning && mode != WEBTOON && position == pager.getCurrentItem()) pageChanged(position + 1);
            }
        });
        webtoon.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView view, int state) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) webtoonGesture = true;
                if (state == RecyclerView.SCROLL_STATE_IDLE) webtoonGesture = false;
            }

            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (mode != WEBTOON || !webtoonGesture || positioning || dy == 0) return;
                int index = webtoonLayout.findFirstVisibleItemPosition();
                if (dy > 0 && !view.canScrollVertically(1)
                    && webtoonLayout.findLastVisibleItemPosition() == info.pages - 1) index = info.pages - 1;
                if (index != RecyclerView.NO_POSITION) pageChanged(index + 1);
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (seeking) pageLabel.setText(getString(R.string.eh_reader_page, progress + 1, info.pages));
                else goToPage(progress + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) { seeking = true; }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                goToPage(bar.getProgress() + 1);
                updateLabel();
            }
        });
    }

    private void loadPageCount(int start) {
        seek.setEnabled(false);
        pageLabel.setText(getString(R.string.eh_reader_page, 0, 0));
        io.execute(() -> {
            try {
                EhGalleryDetail detail = EhRepository.loadGallery(this, key, 0, false);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (info.title.isEmpty()) {
                        info.title = detail.title;
                        if (getSupportActionBar() != null) getSupportActionBar().setTitle(detail.title);
                    }
                    setPageCount(detail.pages, start);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) pageLabel.setText(EhUi.describe(this, e));
                });
            }
        });
    }

    private void setPageCount(int pages, int start) {
        info.pages = Math.max(0, pages);
        current = EhPageNavigation.clamp(start, info.pages);
        furthestSeen = Math.max(furthestSeen, current);
        seek.setMax(Math.max(0, info.pages - 1));
        seek.setEnabled(info.pages > 1);
        applyMode();
        updateLabel();
        rememberPage();
    }

    private void applyMode() {
        positioning = true;
        try {
            webtoonGesture = false;
            webtoon.stopScroll();
            if (mode == WEBTOON) {
                pager.setAdapter(null);
                pager.setVisibility(View.GONE);
                webtoon.setVisibility(View.VISIBLE);
                if (webtoon.getAdapter() == null) webtoon.setAdapter(webtoonAdapter);
            } else {
                webtoon.setAdapter(null);
                webtoon.setVisibility(View.GONE);
                pager.setVisibility(View.VISIBLE);
                pager.setOrientation(mode == VERTICAL ? ViewPager2.ORIENTATION_VERTICAL : ViewPager2.ORIENTATION_HORIZONTAL);
                pager.setLayoutDirection(rtl && mode == HORIZONTAL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
                if (pager.getAdapter() == null) pager.setAdapter(pageAdapter);
            }
            seek.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            if (current > 0) positionView(current, false);
        } finally {
            positioning = false;
        }
        loadNearby();
    }

    private void positionView(int page, boolean smooth) {
        if (mode == WEBTOON) {
            webtoonGesture = false;
            webtoon.stopScroll();
            webtoonLayout.scrollToPositionWithOffset(page - 1, 0);
        } else {
            pager.setCurrentItem(page - 1, smooth);
        }
    }

    private boolean goToPage(int requested) {
        int page = EhPageNavigation.clamp(requested, info.pages);
        if (page == 0 || page == current) return false;
        int previous = current;
        pageChanged(page);
        positionView(page, Math.abs(previous - page) == 1);
        return true;
    }

    private void pageChanged(int page) {
        if (page == current || page <= 0 || page > info.pages) return;
        current = page;
        furthestTurn = Math.max(furthestTurn, page);
        furthestSeen = Math.max(furthestSeen, page);
        updateLabel();
        rememberPage();
        loadNearby();
    }

    private void loadNearby() {
        for (PageHolder holder : attached) holder.loadIfNear();
    }

    private void updateLabel() {
        pageLabel.setText(getString(R.string.eh_reader_page, current, info.pages));
        if (!seeking) seek.setProgress(Math.max(0, current - 1));
    }

    private void rememberPage() {
        int page = current;
        if (page > 0) database(() -> EhDatabase.get(this).saveProgress(info, page));
    }

    private void choosePage() {
        if (info.pages <= 1) return;
        DefaultDialogs.pageChangerDialog(new DefaultDialogs.Builder(this)
            .setActual(current).setMin(1).setMax(info.pages).setTitle(R.string.change_page)
            .setDialogs(new DefaultDialogs.CustomDialogResults() {
                @Override
                public void positive(int page) { goToPage(page); }
            }));
    }

    private void database(Runnable action) {
        EhDatabase.READING_IO.execute(() -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                LogUtility.e("EH reader state write failed", e);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statistics == null) return;
        statsRecording = preferences.getBoolean(ReadingStatsHelper.PREF_ENABLED, false);
        long elapsed = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        int page = current;
        database(() -> EhDatabase.get(this).recordVisit(info));
        if (statsRecording) {
            database(() -> statistics.resume(true, elapsed, wall, page));
            handler.postDelayed(statsTick, STATS_INTERVAL_MS);
        }
    }

    private void flushStatistics(boolean pause) {
        long elapsed = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        int page = furthestSeen;
        if (pause) {
            lastPauseElapsed = elapsed;
            lastPauseWall = wall;
            database(() -> statistics.pause(elapsed, wall, page));
        } else database(() -> statistics.flush(elapsed, wall, page));
    }

    @Override
    protected void onPause() {
        AutoPageTurner.onPause(this);
        consumeAutoTurnGesture = volumeUp = volumeDown = false;
        handler.removeCallbacks(statsTick);
        if (statsRecording) flushStatistics(true);
        statsRecording = false;
        if (key != null) applyAutomaticStatus();
        super.onPause();
    }

    private void applyAutomaticStatus() {
        if (!preferences.getBoolean(ReadingSessionHelper.PREF_ENABLED, false) || furthestTurn == 0) return;
        final int page = furthestTurn;
        furthestTurn = 0;
        final int total = info.pages;
        final boolean overwrite = preferences.getBoolean(ReadingSessionHelper.PREF_OVERWRITE, false);
        database(() -> {
            EhDatabase db = EhDatabase.get(this);
            String target = ReadingSessionHelper.nextStatus(db.readingStatus(key), page, total, overwrite,
                name -> StatusManager.getByName(name) != null);
            if (target == null) return;
            db.setReadingStatus(key, target);
            if (page >= total && !isFinishing())
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.reading_status_auto_marked, target),
                    Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", current);
        outState.putInt("furthest_turn", furthestTurn);
        outState.putInt("furthest_seen", furthestSeen);
    }

    @Override
    protected void onDestroy() {
        AutoPageTurner.onDestroy(this);
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        for (PageHolder holder : bound) holder.clear();
        bound.clear();
        attached.clear();
        if (lastPauseWall != 0) {
            int page = furthestSeen;
            database(() -> statistics.flush(lastPauseElapsed, lastPauseWall, page));
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN)
            consumeAutoTurnGesture = AutoPageTurner.onDispatchTouchEvent(this, event);
        if (consumeAutoTurnGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) consumeAutoTurnGesture = false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean up = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
        if (!Global.volumeOverride() || (!up && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN))
            return super.onKeyDown(keyCode, event);
        boolean plusNext = preferences.getBoolean(VolumeDirectionHelper.PREF_KEY_VOLUME_SIDE, true);
        if (up) volumeUp = true;
        else volumeDown = true;
        if (event.getRepeatCount() == 0) {
            if (volumeUp && volumeDown) VolumeDirectionHelper.setVolumeSide(this, !plusNext);
            else goToPage(current + EhPageNavigation.volumeDelta(up, plusNext, rtl));
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Global.volumeOverride() && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUp = false;
            else volumeDown = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean autoTurnNextPage() { return goToPage(current + 1); }

    @Override
    public int getZoomCurrentPage() { return current; }

    @Override
    public int getZoomTotalPages() { return info == null ? 0 : info.pages; }

    @Override
    public void hideBars() { setBars(false); }

    @Override
    public void showBars() { setBars(true); }

    private void setBars(boolean visible) {
        barsVisible = visible;
        toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), toolbar);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars());
        else controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eh_reader, menu);
        menu.findItem(R.id.eh_action_rtl).setChecked(rtl);
        AutoPageTurner.setupMenu(this, menu);
        VolumeDirectionHelper.setupMenu(this, menu);
        Utility.tintMenu(this, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (AutoPageTurner.onMenuItemSelected(this, item) || VolumeDirectionHelper.onMenuItemSelected(this, item)) return true;
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.eh_action_scroll_type) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.change_scroll_type)
                .setSingleChoiceItems(R.array.scroll_type, mode, (dialog, choice) -> {
                    dialog.dismiss();
                    if (mode == choice) return;
                    mode = choice;
                    preferences.edit().putInt(MODE_KEY, mode).apply();
                    applyMode();
                }).setNegativeButton(R.string.cancel, null).show();
            return true;
        }
        if (id == R.id.eh_action_rtl) {
            rtl = !rtl;
            Global.updateReadingDirection(this, rtl);
            item.setChecked(rtl);
            applyMode();
            return true;
        }
        if (id == R.id.eh_action_reload_page) {
            for (PageHolder holder : attached) {
                if (holder.index == current) holder.retry();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleTap(float fraction) {
        int delta = EhPageNavigation.edgeDelta(fraction, Global.isButtonChangePage(), rtl);
        if (delta == 0) setBars(!barsVisible);
        else goToPage(current + delta);
    }

    @Nullable
    private File localFile(int page) {
        if (!checkedOffline) {
            checkedOffline = true;
            offlineDir = EhDownloads.existingDir(this, key);
        }
        return EhDownloads.findPage(offlineDir, page);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        private final boolean continuous;

        PageAdapter(boolean continuous) {
            this.continuous = continuous;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.eh_item_reader_page, parent, false),
                continuous);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.bind(position + 1);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull PageHolder holder) {
            attached.add(holder);
            holder.loadIfNear();
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull PageHolder holder) {
            attached.remove(holder);
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder holder) {
            attached.remove(holder);
            bound.remove(holder);
            holder.clear();
            holder.index = 0;
        }

        @Override
        public int getItemCount() { return info.pages; }
    }

    private enum PageState { WAITING, LOADING, READY, ERROR }

    private final class PageHolder extends RecyclerView.ViewHolder {
        final PhotoView image;
        final ProgressBar progress;
        final TextView number, error;
        final boolean continuous;
        volatile int index;
        volatile int revision;
        PageState state;
        boolean retried, useReload;

        PageHolder(@NonNull View v, boolean continuous) {
            super(v);
            this.continuous = continuous;
            image = v.findViewById(R.id.eh_page_image);
            progress = v.findViewById(R.id.eh_page_progress);
            number = v.findViewById(R.id.eh_page_number);
            error = v.findViewById(R.id.eh_page_error);
            if (continuous) {
                v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                image.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                image.setAdjustViewBounds(true);
            }
            image.setMaximumScale(MAX_SCALE);
            image.setAllowParentInterceptOnEdge(true);
            image.setOnViewTapListener((view, x, y) -> handleTap(x / Math.max(1, view.getWidth())));
            error.setOnClickListener(view -> retry());
        }

        void clear() {
            revision++;
            RequestManager requests = GlideX.with(image);
            if (requests != null) requests.clear(image);
        }

        void bind(int index) {
            clear();
            bound.add(this);
            this.index = index;
            retried = useReload = false;
            showWaiting();
            loadIfNear();
        }

        void showWaiting() {
            state = PageState.WAITING;
            image.setImageDrawable(null);
            if (continuous) image.setMinimumHeight((int) (240 * getResources().getDisplayMetrics().density));
            progress.setVisibility(View.VISIBLE);
            number.setVisibility(View.VISIBLE);
            number.setText(String.valueOf(index));
            error.setVisibility(View.GONE);
        }

        void retry() {
            clear();
            retried = true;
            useReload = true;
            showWaiting();
            loadIfNear();
        }

        boolean matches(int page, int version) {
            return index == page && revision == version && !isDestroyed() && !isFinishing();
        }

        int distance(int page) {
            return Math.abs(page - current);
        }

        void loadIfNear() {
            int page = index;
            if (state != PageState.WAITING || !attached.contains(this) || page <= 0 || distance(page) > preloadDistance
                || io.isShutdown()) return;
            int version = ++revision;
            boolean reload = useReload;
            state = PageState.LOADING;
            io.execute(() -> {
                if (index != page || revision != version) return;
                if (distance(page) > preloadDistance) {
                    runOnUiThread(() -> {
                        if (matches(page, version)) {
                            state = PageState.WAITING;
                            loadIfNear();
                        }
                    });
                    return;
                }
                try {
                    File file = reload ? null : localFile(page);
                    Object model = file;
                    if (model == null) {
                        EhPageParser.Result resolved = EhRepository.resolveImage(EhReaderActivity.this, key, page, reload);
                        model = EhUi.model(EhReaderActivity.this, resolved.imageUrl);
                    }
                    Object loaded = model;
                    runOnUiThread(() -> {
                        if (matches(page, version)) display(loaded, page, version);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (matches(page, version)) showError(EhUi.describe(EhReaderActivity.this, e));
                    });
                }
            });
        }

        void showError(String message) {
            state = PageState.ERROR;
            progress.setVisibility(View.GONE);
            number.setVisibility(View.GONE);
            error.setVisibility(View.VISIBLE);
            error.setText(getString(R.string.eh_reader_page_error, message));
            if (continuous) {
                error.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, itemView.getWidth()), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                image.setMinimumHeight(Math.max(image.getMinimumHeight(), error.getMeasuredHeight()));
            }
        }

        void display(@NonNull Object model, int page, int version) {
            RequestManager requests = GlideX.with(image);
            if (requests == null) {
                state = PageState.WAITING;
                return;
            }
            com.bumptech.glide.RequestBuilder<Drawable> request = requests.load(model);
            if (continuous && webtoon.getWidth() > 0) request = request.override(webtoon.getWidth(), Target.SIZE_ORIGINAL);
            request.listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object m, @NonNull Target<Drawable> target,
                                            boolean first) {
                    image.post(() -> {
                        if (!matches(page, version)) return;
                        if (!retried) retry();
                        else showError(getString(R.string.eh_error_image));
                    });
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable draw, @NonNull Object m, Target<Drawable> target,
                                               @NonNull DataSource source, boolean first) {
                    if (!matches(page, version)) return true;
                    state = PageState.READY;
                    progress.setVisibility(View.GONE);
                    number.setVisibility(View.GONE);
                    if (continuous) image.setMinimumHeight(0);
                    else image.post(() -> applyDefaultZoom(page, version));
                    return false;
                }
            }).into(image);
        }

        void applyDefaultZoom(int page, int version) {
            if (!matches(page, version) || image.getDrawable() == null || image.getWidth() == 0 || image.getHeight() == 0) return;
            float scale = Global.getDefaultZoom();
            Drawable drawable = image.getDrawable();
            int w = drawable.getIntrinsicWidth(), h = drawable.getIntrinsicHeight();
            if (w > 0 && h > 2L * w)
                scale = Math.max(scale, (float) Math.floor((double) image.getWidth() * h / ((double) image.getHeight() * w)));
            image.setScale(Math.max(1f, Math.min(MAX_SCALE, scale)), false);
        }
    }
}
