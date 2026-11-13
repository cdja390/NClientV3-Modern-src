/*
 * Copyright 2026 NClientV3-Modern contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Added for NClientV3 Modern 4.7.0 (e-hentai source, no GeckoView). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
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
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.GlideX;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight reader for e-hentai galleries. Each page's image address is resolved from its
 * viewer page on demand (one request at a time, only for pages near the current one), so a
 * long gallery is never crawled in one go. A broken image is retried once through e-hentai's
 * reload key, which asks for a different image server.
 */
public class EhReaderActivity extends GeneralActivity {
    private static final int PRELOAD_DISTANCE = 2;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** ehentai.db writes, kept off the network queue. */
    private final ExecutorService dbIo = Executors.newSingleThreadExecutor();
    private long resumedAt;
    @Nullable
    private File offlineDir;
    private boolean offlineChecked;
    private EhGalleryKey key;
    private EhGalleryInfo info;
    private ViewPager2 pager;
    private Toolbar toolbar;
    private View bottomBar;
    private TextView pageLabel;
    private SeekBar seek;
    private PageAdapter adapter;
    private boolean barsVisible = true;
    private volatile int current;

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
        info.pages = getIntent().getIntExtra(EhIntents.EXTRA_PAGES, 0);
        int start = getIntent().getIntExtra(EhIntents.EXTRA_START, 1);
        if (savedInstanceState != null) start = savedInstanceState.getInt("page", start);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(info.title);
        }
        bottomBar = findViewById(R.id.eh_reader_bar);
        pageLabel = findViewById(R.id.eh_reader_page);
        seek = findViewById(R.id.eh_reader_seek);
        pager = findViewById(R.id.eh_pager);
        pager.setOffscreenPageLimit(1);
        applyDirection();
        adapter = new PageAdapter();
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                current = position + 1;
                updateLabel();
                onPageShown(current);
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) pageLabel.setText(getString(R.string.eh_reader_page, progress + 1, info.pages));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                pager.setCurrentItem(s.getProgress(), false);
            }
        });
        final int startPage = start;
        if (info.pages > 0) {
            setPageCount(info.pages, startPage);
        } else {
            io.execute(() -> {
                try {
                    EhGalleryDetail d = EhRepository.loadGallery(this, key, 0, false);
                    runOnUiThread(() -> {
                        info.pages = d.pages;
                        if (info.title.isEmpty()) {
                            info.title = d.title;
                            if (getSupportActionBar() != null) getSupportActionBar().setTitle(d.title);
                        }
                        setPageCount(d.pages, startPage);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> pageLabel.setText(EhUi.describe(this, e)));
                }
            });
        }
    }

    private void setPageCount(int pages, int startPage) {
        info.pages = pages;
        seek.setMax(Math.max(0, pages - 1));
        adapter.notifyDataSetChanged();
        int startIndex = Math.max(1, Math.min(startPage, Math.max(1, pages)));
        pager.setCurrentItem(startIndex - 1, false);
        current = startIndex;
        updateLabel();
        onPageShown(current);
    }

    private void updateLabel() {
        pageLabel.setText(getString(R.string.eh_reader_page, current, info.pages));
        seek.setProgress(current - 1);
    }

    /** Called whenever a page becomes the current one: remembers it for "continue reading". */
    protected void onPageShown(int index) {
        final long gid = key.gid;
        dbIo.execute(() -> EhDatabase.get(this).saveProgress(gid, index, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedAt = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (key == null) return;
        final long seconds = resumedAt == 0 ? 0 : (System.currentTimeMillis() - resumedAt) / 1000;
        resumedAt = 0;
        final long gid = key.gid;
        final int page = Math.max(1, current);
        if (!dbIo.isShutdown()) dbIo.execute(() -> EhDatabase.get(this).saveProgress(gid, page, seconds));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", current);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        dbIo.shutdown();
        super.onDestroy();
    }

    private void applyDirection() {
        pager.setLayoutDirection(EhSettings.readerRtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        seek.setLayoutDirection(EhSettings.readerRtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        toolbar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (barsVisible) c.show(WindowInsetsCompat.Type.systemBars());
        else c.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eh_reader, menu);
        menu.findItem(R.id.eh_action_rtl).setChecked(EhSettings.readerRtl(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.eh_action_rtl) {
            boolean rtl = !EhSettings.readerRtl(this);
            EhSettings.setReaderRtl(this, rtl);
            item.setChecked(rtl);
            int keep = current;
            applyDirection();
            pager.setCurrentItem(keep - 1, false);
            return true;
        }
        if (id == R.id.eh_action_reload_page) {
            adapter.reload(current);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Local file for a page when the gallery was downloaded, else null. */
    @Nullable
    protected File localFile(int index) {
        if (!offlineChecked) {
            offlineChecked = true;
            offlineDir = EhDownloads.existingDir(this, key);
        }
        return EhDownloads.findPage(offlineDir, index);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.eh_item_reader_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder h, int position) {
            h.bind(position + 1);
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder h) {
            RequestManager g = GlideX.with(h.image);
            if (g != null) g.clear(h.image);
            h.index = 0;
        }

        @Override
        public int getItemCount() {
            return info.pages;
        }

        void reload(int index) {
            notifyItemChanged(index - 1, Boolean.TRUE);
            RecyclerView rv = (RecyclerView) pager.getChildAt(0);
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(index - 1);
            if (vh instanceof PageHolder) ((PageHolder) vh).load(true);
        }
    }

    private final class PageHolder extends RecyclerView.ViewHolder {
        final PhotoView image;
        final ProgressBar progress;
        final TextView number, error;
        int index;
        boolean retried;

        PageHolder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.eh_page_image);
            progress = v.findViewById(R.id.eh_page_progress);
            number = v.findViewById(R.id.eh_page_number);
            error = v.findViewById(R.id.eh_page_error);
            image.setOnPhotoTapListener((view, x, y) -> toggleBars());
            image.setOnOutsidePhotoTapListener(view -> toggleBars());
            error.setOnClickListener(view -> load(true));
        }

        void bind(int index) {
            this.index = index;
            retried = false;
            load(false);
        }

        void showLoading() {
            image.setImageDrawable(null);
            progress.setVisibility(View.VISIBLE);
            number.setVisibility(View.VISIBLE);
            number.setText(String.valueOf(index));
            error.setVisibility(View.GONE);
        }

        void showError(@NonNull String text) {
            progress.setVisibility(View.GONE);
            number.setVisibility(View.GONE);
            error.setVisibility(View.VISIBLE);
            error.setText(getString(R.string.eh_reader_page_error, text));
        }

        void load(boolean reload) {
            final int wanted = index;
            if (wanted <= 0) return;
            showLoading();
            File local = localFile(wanted);
            if (local != null) {
                display(local, wanted);
                return;
            }
            io.execute(() -> {
                // Skip pages the user has already flicked past; they reload when shown again.
                if (index != wanted || Math.abs(wanted - current) > PRELOAD_DISTANCE) return;
                try {
                    EhPageParser.Result r = EhRepository.resolveImage(EhReaderActivity.this, key, wanted, reload);
                    runOnUiThread(() -> {
                        if (index == wanted && !isDestroyed()) display(EhUi.model(EhReaderActivity.this, r.imageUrl), wanted);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (index == wanted && !isDestroyed()) showError(EhUi.describe(EhReaderActivity.this, e));
                    });
                }
            });
        }

        void display(@NonNull Object model, int wanted) {
            RequestManager g = GlideX.with(image);
            if (g == null) return;
            g.load(model).listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object m, @NonNull Target<Drawable> target, boolean isFirstResource) {
                    if (index != wanted) return false;
                    if (!retried && !(model instanceof File)) {
                        retried = true;
                        image.post(() -> load(true));
                    } else {
                        image.post(() -> showError(getString(R.string.eh_error_image)));
                    }
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object m, Target<Drawable> target,
                                               @NonNull DataSource dataSource, boolean isFirstResource) {
                    progress.setVisibility(View.GONE);
                    number.setVisibility(View.GONE);
                    return false;
                }
            }).into(image);
        }
    }
}
