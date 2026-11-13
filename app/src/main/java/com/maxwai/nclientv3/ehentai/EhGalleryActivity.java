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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** e-hentai gallery detail: metadata, tags, preview thumbnails, read. */
public class EhGalleryActivity extends GeneralActivity {
    protected final ExecutorService io = Executors.newSingleThreadExecutor();
    protected EhGalleryKey key;
    @Nullable
    protected EhGalleryDetail detail;
    private int loadedPreviewPages;
    private boolean loadingPreviews;
    private EhPreviewAdapter previews;
    private ProgressBar progress;
    private View scroll;
    private LinearLayout messageBox;
    private TextView message;
    private MaterialButton morePreviews;
    protected MaterialButton readButton, downloadButton, favoriteButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eh_gallery);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        key = EhIntents.key(getIntent());
        if (key == null) {
            finish();
            return;
        }
        setTitle(getIntent().getStringExtra(EhIntents.EXTRA_TITLE) == null
            ? getString(R.string.eh_title) : getIntent().getStringExtra(EhIntents.EXTRA_TITLE));
        progress = findViewById(R.id.eh_progress);
        scroll = findViewById(R.id.eh_scroll);
        messageBox = findViewById(R.id.eh_message_box);
        message = findViewById(R.id.eh_message);
        morePreviews = findViewById(R.id.eh_more_previews);
        readButton = findViewById(R.id.eh_read);
        downloadButton = findViewById(R.id.eh_download);
        favoriteButton = findViewById(R.id.eh_favorite);
        findViewById(R.id.eh_message_action).setOnClickListener(v -> load(true));
        RecyclerView rv = findViewById(R.id.eh_previews);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        previews = new EhPreviewAdapter(index -> openReader(index));
        rv.setAdapter(previews);
        morePreviews.setOnClickListener(v -> loadMorePreviews());
        readButton.setOnClickListener(v -> openReader(startPage()));
        downloadButton.setVisibility(View.GONE);
        favoriteButton.setVisibility(View.GONE);
        String thumb = getIntent().getStringExtra(EhIntents.EXTRA_THUMB);
        if (thumb != null) EhUi.loadThumb(findViewById(R.id.eh_cover), thumb);
        load(false);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** First page to open with the Read button (resume support is added by later phases). */
    protected int startPage() {
        return 1;
    }

    protected void openReader(int page) {
        if (detail == null) return;
        startActivity(EhIntents.reader(this, detail, page));
    }

    private void load(boolean refresh) {
        progress.setVisibility(View.VISIBLE);
        messageBox.setVisibility(View.GONE);
        if (refresh) EhRepository.forget(key);
        io.execute(() -> {
            try {
                EhGalleryDetail d = EhRepository.loadGallery(this, key, 0, refresh);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    bind(d);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    if (detail == null) {
                        scroll.setVisibility(View.GONE);
                        message.setText(EhUi.describe(this, e));
                        messageBox.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    protected void bind(@NonNull EhGalleryDetail d) {
        detail = d;
        scroll.setVisibility(View.VISIBLE);
        setTitle(d.title);
        ((TextView) findViewById(R.id.eh_title)).setText(d.title);
        TextView jpn = findViewById(R.id.eh_title_jpn);
        jpn.setVisibility(d.titleJpn == null ? View.GONE : View.VISIBLE);
        jpn.setText(d.titleJpn);
        EhUi.bindCategory(findViewById(R.id.eh_category), d.category);
        ImageView cover = findViewById(R.id.eh_cover);
        EhUi.loadThumb(cover, d.thumb);
        StringBuilder info = new StringBuilder();
        line(info, R.string.eh_info_pages, String.valueOf(d.pages));
        if (d.language != null) line(info, R.string.eh_info_language, d.language);
        if (d.fileSize != null) line(info, R.string.eh_info_size, d.fileSize);
        if (d.posted != null) line(info, R.string.eh_info_posted, d.posted);
        if (d.uploader != null) line(info, R.string.eh_info_uploader, d.uploader);
        if (d.rating >= 0) line(info, R.string.eh_info_rating, EhUi.rating(d.rating)
            + (d.ratingCount > 0 ? " (" + d.ratingCount + ")" : ""));
        if (d.favoritedCount >= 0) line(info, R.string.eh_info_favorited, String.valueOf(d.favoritedCount));
        ((TextView) findViewById(R.id.eh_info)).setText(info);
        bindTags(d.tags);
        previews.clear();
        previews.add(d.previews);
        loadedPreviewPages = 1;
        updateMorePreviews();
        readButton.setEnabled(d.pages > 0);
        onDetailBound(d);
    }

    /** Hook for later phases (favorite state, download state, history). */
    protected void onDetailBound(@NonNull EhGalleryDetail d) {
    }

    private void line(StringBuilder b, int label, String value) {
        if (b.length() > 0) b.append('\n');
        b.append(getString(label)).append(": ").append(value);
    }

    private void bindTags(@NonNull Map<String, List<String>> tags) {
        LinearLayout box = findViewById(R.id.eh_tags);
        box.removeAllViews();
        for (Map.Entry<String, List<String>> e : tags.entrySet()) {
            TextView ns = new TextView(this);
            ns.setText(e.getKey().isEmpty() ? getString(R.string.eh_tags_misc) : e.getKey());
            ns.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            ns.setPadding(0, Math.round(6 * getResources().getDisplayMetrics().density), 0, 0);
            box.addView(ns);
            ChipGroup group = new ChipGroup(this);
            group.setChipSpacingVertical(0);
            for (String tag : e.getValue()) {
                Chip chip = new Chip(this);
                chip.setText(tag);
                chip.setEnsureMinTouchTargetSize(false);
                final String q = tagQuery(e.getKey(), tag);
                chip.setOnClickListener(v -> startActivity(EhIntents.search(this, q)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
                group.addView(chip);
            }
            box.addView(group);
        }
    }

    /** e-hentai tag search syntax: namespace:"tag name$". */
    @NonNull
    static String tagQuery(@NonNull String namespace, @NonNull String tag) {
        String t = tag.contains(" ") ? "\"" + tag + "$\"" : tag + "$";
        return namespace.isEmpty() ? t : namespace + ":" + t;
    }

    private void updateMorePreviews() {
        boolean more = detail != null && loadedPreviewPages < detail.previewPages;
        morePreviews.setVisibility(more ? View.VISIBLE : View.GONE);
        morePreviews.setEnabled(!loadingPreviews);
    }

    private void loadMorePreviews() {
        if (detail == null || loadingPreviews) return;
        loadingPreviews = true;
        updateMorePreviews();
        final int page = loadedPreviewPages;
        io.execute(() -> {
            try {
                EhGalleryDetail d = EhRepository.loadGallery(this, key, page, false);
                runOnUiThread(() -> {
                    loadingPreviews = false;
                    previews.add(d.previews);
                    loadedPreviewPages = page + 1;
                    updateMorePreviews();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingPreviews = false;
                    updateMorePreviews();
                    com.google.android.material.snackbar.Snackbar.make(morePreviews, EhUi.describe(this, e),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Opens the page in a browser, not in this app (which also handles e-hentai links). */
    protected void openInBrowser(@NonNull String url) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        view.setSelector(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        try {
            startActivity(view);
        } catch (Exception e) {
            try {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), null));
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eh_gallery, menu);
        Utility.tintMenu(this, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.eh_action_share) {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, key.url());
            startActivity(Intent.createChooser(share, getString(R.string.eh_share)));
            return true;
        }
        if (id == R.id.eh_action_browser) {
            openInBrowser(key.url());
            return true;
        }
        if (id == R.id.eh_action_refresh) {
            load(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
