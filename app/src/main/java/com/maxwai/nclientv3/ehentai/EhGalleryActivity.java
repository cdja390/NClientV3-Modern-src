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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.status.Status;
import com.maxwai.nclientv3.components.status.StatusManager;
import com.maxwai.nclientv3.utility.Utility;
import com.maxwai.nclientv3.utility.ReadingStatsHelper;
import com.maxwai.nclientv3.utility.ResumeHelper;

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
    private int resumePage;
    private boolean localFavorite;
    @Nullable
    private EhDatabase.Entry downloadRow;
    private boolean favoriteBusy;
    private String readingStatus;
    private boolean resumeChecked;
    private boolean localStateLoaded;
    private boolean detailReady;

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
        resumeChecked = savedInstanceState != null && savedInstanceState.getBoolean("eh_resume_checked");
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
        downloadButton.setOnClickListener(v -> onDownloadClicked());
        favoriteButton.setOnClickListener(v -> onFavoriteClicked());
        String thumb = getIntent().getStringExtra(EhIntents.EXTRA_THUMB);
        if (thumb != null) EhUi.loadThumb(findViewById(R.id.eh_cover), thumb);
        load(false);
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalState();
    }

    /** History, local favorite and download state from ehentai.db. */
    private void refreshLocalState() {
        if (key == null || io.isShutdown()) return;
        EhDatabase.READING_IO.execute(() -> {
            EhDatabase db = EhDatabase.get(this);
            int last = db.lastPage(key);
            boolean fav = db.isFavorite(key);
            EhDatabase.Entry dl = db.download(key);
            String status = db.readingStatus(key);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                resumePage = last;
                localFavorite = fav;
                downloadRow = dl;
                localStateLoaded = true;
                applyReadingStatus(status);
                updateButtons();
                maybeResume();
            });
        });
    }

    private void applyReadingStatus(@Nullable String name) {
        readingStatus = name;
        Status status = StatusManager.getByName(name);
        findViewById(R.id.eh_title).setBackgroundColor(status == null ? 0 : status.color);
    }

    private void chooseReadingStatus() {
        List<String> names = StatusManager.getNames();
        names.add(0, getString(R.string.remove_status));
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_status_title)
            .setSingleChoiceItems(names.toArray(new String[0]), Math.max(0, names.indexOf(readingStatus)), (dialog, which) -> {
                dialog.dismiss();
                String chosen = which == 0 ? null : names.get(which);
                EhDatabase.READING_IO.execute(() -> EhDatabase.get(this).setReadingStatus(key, chosen));
                applyReadingStatus(chosen);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void updateButtons() {
        readButton.setText(resumePage > 1 ? getString(R.string.eh_continue_page, resumePage) : getString(R.string.eh_read));
        if (EhAuth.isLoggedIn(this)) {
            String name = detail == null ? null : detail.favoriteName;
            favoriteButton.setText(name == null ? getString(R.string.eh_favorite) : name);
        } else {
            favoriteButton.setText(localFavorite ? R.string.eh_local_favorite_saved : R.string.eh_favorite);
        }
        EhDatabase.Entry d = downloadRow;
        if (d == null) downloadButton.setText(R.string.eh_download);
        else if (d.state == EhDatabase.STATE_DONE) downloadButton.setText(R.string.eh_downloaded);
        else if (d.state == EhDatabase.STATE_QUEUED || d.state == EhDatabase.STATE_RUNNING)
            downloadButton.setText(getString(R.string.eh_download_state_running, d.done, Math.max(d.pages, d.done)));
        else downloadButton.setText(R.string.eh_download_resume);
        downloadButton.setEnabled(detail != null || d != null);
        invalidateOptionsMenu();
    }

    /** First page to open with the Read button: where the last session stopped. */
    protected int startPage() {
        return Math.max(1, resumePage);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("eh_resume_checked", resumeChecked);
    }

    private void maybeResume() {
        if (resumeChecked || !localStateLoaded || !detailReady || !hasWindowFocus() || isFinishing()) return;
        resumeChecked = true;
        if (resumePage <= 1) return;
        var settings = getSharedPreferences("Settings", 0);
        if (settings.contains(ResumeHelper.PREF_AUTO_JUMP)) {
            if (settings.getBoolean(ResumeHelper.PREF_AUTO_JUMP, false)) openReader(resumePage);
            return;
        }
        android.widget.CheckBox remember = new android.widget.CheckBox(this);
        remember.setText(R.string.resume_dont_ask);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resume_dialog_title)
            .setMessage(getString(R.string.resume_dialog_message, resumePage))
            .setView(remember)
            .setPositiveButton(R.string.resume_yes, (d, which) -> {
                if (remember.isChecked()) settings.edit().putBoolean(ResumeHelper.PREF_AUTO_JUMP, true).apply();
                openReader(resumePage);
            })
            .setNegativeButton(R.string.resume_no, (d, which) -> {
                if (remember.isChecked()) settings.edit().putBoolean(ResumeHelper.PREF_AUTO_JUMP, false).apply();
            })
            .show();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) maybeResume();
    }

    protected void openReader(int page) {
        EhGalleryInfo info = detail != null ? detail : downloadRow;
        if (info == null) return;
        resumeChecked = true;
        startActivity(EhIntents.reader(this, info, page));
    }

    private void onDownloadClicked() {
        EhDatabase.Entry d = downloadRow;
        if (d == null) {
            if (detail == null) return;
            final EhGalleryInfo info = detail;
            io.execute(() -> {
                EhDownloads.enqueue(this, info);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.eh_download_queued, Toast.LENGTH_SHORT).show();
                    refreshLocalState();
                });
            });
            return;
        }
        if (d.state == EhDatabase.STATE_DONE) {
            EhDownloads.confirmDelete(this, d, () -> {
                downloadRow = null;
                updateButtons();
            });
            return;
        }
        if (d.state == EhDatabase.STATE_QUEUED || d.state == EhDatabase.STATE_RUNNING) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(d.title)
                .setItems(new CharSequence[]{getString(R.string.eh_download_pause), getString(R.string.eh_download_delete)},
                    (dialog, which) -> {
                        if (which == 0) io.execute(() -> {
                            EhDownloads.pauseAll(this);
                            refreshLocalState();
                        });
                        else {
                            io.execute(() -> EhDownloads.pauseAll(this));
                            EhDownloads.confirmDelete(this, d, () -> downloadRow = null);
                        }
                    })
                .show();
            return;
        }
        io.execute(() -> {
            EhDownloads.resume(this, d);
            refreshLocalState();
        });
    }

    private void onFavoriteClicked() {
        if (!EhAuth.isLoggedIn(this) || (key.exhentai && !EhAuth.hasExhentaiAccess(this))) {
            toggleLocalFavorite();
            return;
        }
        if (favoriteBusy) return;
        favoriteBusy = true;
        io.execute(() -> {
            try {
                EhRepository.FavoriteState st = EhRepository.loadFavoriteState(this, key);
                runOnUiThread(() -> {
                    favoriteBusy = false;
                    showFavoriteDialog(st);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    favoriteBusy = false;
                    Toast.makeText(this, EhUi.describe(this, e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFavoriteDialog(@NonNull EhRepository.FavoriteState st) {
        int n = EhFavoritesParser.SLOT_COUNT;
        String[] labels = new String[st.favorited ? n + 1 : n];
        for (int i = 0; i < n; i++) {
            String name = st.folders[i];
            labels[i] = name == null || name.isEmpty() ? getString(R.string.eh_fav_folder_default, i) : name;
        }
        if (st.favorited) labels[n] = getString(R.string.eh_fav_remove);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_favorite)
            .setItems(labels, (d, which) -> {
                final int slot = which >= n ? -1 : which;
                final String label = slot < 0 ? null : labels[slot];
                io.execute(() -> {
                    try {
                        EhRepository.setOnlineFavorite(this, key, slot, st.favorited);
                        runOnUiThread(() -> {
                            if (detail != null) detail.favoriteName = label;
                            updateButtons();
                            Toast.makeText(this, slot < 0 ? getString(R.string.eh_fav_removed)
                                : getString(R.string.eh_fav_added, label), Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, EhUi.describe(this, e), Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void toggleLocalFavorite() {
        EhGalleryInfo info = detail != null ? detail : downloadRow;
        if (info == null) return;
        final boolean now = !localFavorite;
        localFavorite = now;
        updateButtons();
        EhDatabase.READING_IO.execute(() -> EhDatabase.get(this).setFavorite(info, now));
        Toast.makeText(this, now ? R.string.eh_local_favorite_added : R.string.eh_local_favorite_removed,
            Toast.LENGTH_SHORT).show();
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
                EhDatabase.Entry offline = EhDatabase.get(this).download(key);
                boolean hasOffline = offline != null && offline.state == EhDatabase.STATE_DONE
                    && EhDownloads.localPage(this, offline.key, 1) != null;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    if (detail == null && hasOffline) {
                        bindOffline(offline, EhUi.describe(this, e));
                    } else if (detail == null) {
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

    protected void onDetailBound(@NonNull EhGalleryDetail d) {
        updateButtons();
        recordVisit(d);
        io.execute(() -> EhDatabase.get(this).refreshFavorite(d));
        detailReady = true;
        maybeResume();
    }

    /** Offline view of a downloaded gallery when the site cannot be reached. */
    private void bindOffline(@NonNull EhDatabase.Entry e, @NonNull String why) {
        downloadRow = e;
        scroll.setVisibility(View.VISIBLE);
        setTitle(e.title);
        ((TextView) findViewById(R.id.eh_title)).setText(e.title);
        findViewById(R.id.eh_title_jpn).setVisibility(View.GONE);
        EhUi.bindCategory(findViewById(R.id.eh_category), e.category);
        java.io.File cover = EhDownloads.localPage(this, e.key, 1);
        com.bumptech.glide.RequestManager g = com.maxwai.nclientv3.components.GlideX.with(findViewById(R.id.eh_cover));
        if (cover != null && g != null) g.load(cover).into((ImageView) findViewById(R.id.eh_cover));
        ((TextView) findViewById(R.id.eh_info)).setText(getString(R.string.eh_offline_info, e.pages, why));
        findViewById(R.id.eh_previews_title).setVisibility(View.GONE);
        morePreviews.setVisibility(View.GONE);
        readButton.setEnabled(e.pages > 0);
        updateButtons();
        recordVisit(e);
        detailReady = true;
        maybeResume();
    }

    private void recordVisit(EhGalleryInfo info) {
        EhDatabase.READING_IO.execute(() -> EhDatabase.get(this).recordVisit(info));
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
                chip.setOnClickListener(v -> startActivity(EhIntents.tag(this, getIntent(), e.getKey(), tag)));
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem local = menu.findItem(R.id.eh_action_local_favorite);
        if (local != null) local.setChecked(localFavorite);
        return super.onPrepareOptionsMenu(menu);
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
        if (id == R.id.eh_action_local_favorite) {
            toggleLocalFavorite();
            return true;
        }
        if (id == R.id.eh_action_status) {
            chooseReadingStatus();
            return true;
        }
        if (id == R.id.eh_action_reading_stats) {
            ReadingStatsHelper.showEhStats(this, key);
            return true;
        }
        if (id == R.id.eh_action_refresh) {
            load(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
