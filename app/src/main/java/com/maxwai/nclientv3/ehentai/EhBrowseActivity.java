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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.utility.Utility;
import com.maxwai.nclientv3.source.GallerySource;
import com.maxwai.nclientv3.source.SourcePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point of the e-hentai source: front page / search, popular, online favorites, local
 * favorites, reading history and downloads. Pages are loaded one at a time, only when the list is
 * scrolled near its end, so browsing stays within e-hentai's page-load limits.
 */
public class EhBrowseActivity extends GeneralActivity {
    enum Section {FRONT, POPULAR, FAVORITES, SAVED, HISTORY, DOWNLOADS}


    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Section> sections = new ArrayList<>();
    private Section section = Section.FRONT;
    private EhListAdapter adapter;
    private SwipeRefreshLayout refresh;
    private RecyclerView recycler;
    private LinearLayout messageBox;
    private TextView message;
    private MaterialButton messageAction;
    private TextView filterBar;
    private TabLayout tabs;
    @Nullable
    private String query;
    @Nullable
    private String nextCursor;
    private boolean loading;
    private boolean endReached;
    private int generation;
    /** Online favorites folder, -1 for all. */
    private int favcat = -1;
    @NonNull
    private String[] folderNames = new String[EhFavoritesParser.SLOT_COUNT];
    @Nullable
    private String authSnapshot;
    private final java.text.DateFormat dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eh_browse);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        refresh = findViewById(R.id.eh_refresh);
        recycler = findViewById(R.id.eh_recycler);
        messageBox = findViewById(R.id.eh_message_box);
        message = findViewById(R.id.eh_message);
        messageAction = findViewById(R.id.eh_message_action);
        filterBar = findViewById(R.id.eh_filter_bar);
        tabs = findViewById(R.id.eh_tabs);

        adapter = new EhListAdapter(new EhListAdapter.Listener() {
            @Override
            public void onOpen(@NonNull EhGalleryInfo info) {
                startActivity(EhIntents.gallery(EhBrowseActivity.this, info));
            }

            @Override
            public boolean onLongPress(@NonNull EhGalleryInfo info) {
                return onItemLongPress(info);
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || endReached || nextCursor == null) return;
                if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 6) loadMore();
            }
        });
        refresh.setOnRefreshListener(() -> reload(true));
        adapter.setExtraText(this::extraText);

        query = getIntent().getStringExtra(EhIntents.EXTRA_QUERY);
        buildSections();
        for (Section s : sections) tabs.addTab(tabs.newTab().setText(sectionTitle(s)));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                section = sections.get(tab.getPosition());
                invalidateOptionsMenu();
                reload(false);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                recycler.scrollToPosition(0);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!TextUtils.isEmpty(query) && section == Section.FRONT) {
                    query = null;
                    reload(false);
                } else {
                    finish();
                }
            }
        });
        authSnapshot = authState();
        reload(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTitle();
        String now = authState();
        if (!now.equals(authSnapshot)) {
            authSnapshot = now;
            invalidateOptionsMenu();
            reload(false);
        } else if (isLocal(section)) {
            reload(false);
        }
    }

    @NonNull
    private String authState() {
        return EhAuth.isLoggedIn(this) + ":" + EhAuth.hasExhentaiAccess(this) + ":" + EhSettings.useExhentai(this);
    }

    private static boolean isLocal(@NonNull Section s) {
        return s == Section.SAVED || s == Section.HISTORY || s == Section.DOWNLOADS;
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    protected void buildSections() {
        sections.add(Section.FRONT);
        sections.add(Section.POPULAR);
        sections.add(Section.FAVORITES);
        sections.add(Section.SAVED);
        sections.add(Section.HISTORY);
        sections.add(Section.DOWNLOADS);
    }

    @NonNull
    private String sectionTitle(@NonNull Section s) {
        switch (s) {
            case POPULAR: return getString(R.string.eh_section_popular);
            case FAVORITES: return getString(R.string.eh_section_favorites);
            case SAVED: return getString(R.string.eh_section_saved);
            case HISTORY: return getString(R.string.eh_section_history);
            case DOWNLOADS: return getString(R.string.eh_section_downloads);
            default: return getString(R.string.eh_section_front);
        }
    }

    @Nullable
    private String extraText(@NonNull EhGalleryInfo info) {
        if (!(info instanceof EhDatabase.Entry)) return null;
        EhDatabase.Entry e = (EhDatabase.Entry) info;
        switch (section) {
            case HISTORY:
                return getString(R.string.eh_history_line, e.lastPage, Math.max(e.pages, e.lastPage),
                    Math.max(1, e.readSeconds / 60), dateFormat.format(new java.util.Date(e.time)));
            case DOWNLOADS:
                return EhDownloads.describe(this, e);
            default:
                return null;
        }
    }

    private void updateTitle() {
        boolean ex = EhSettings.useExhentai(this);
        setTitle(ex ? R.string.eh_title_ex : R.string.eh_title);
        if (getSupportActionBar() != null)
            getSupportActionBar().setSubtitle(TextUtils.isEmpty(query) ? null : query);
        int shown = EhSettings.shownCategories(this);
        int minRating = EhSettings.minRating(this);
        if (section == Section.FRONT && ((shown != 0 && shown != EhCategory.ALL) || minRating >= 2)) {
            filterBar.setVisibility(View.VISIBLE);
            filterBar.setText(describeFilter(shown, minRating));
        } else if (section == Section.FAVORITES && EhAuth.isLoggedIn(this)) {
            filterBar.setVisibility(View.VISIBLE);
            filterBar.setText(getString(R.string.eh_fav_current, folderLabel(favcat)));
        } else {
            filterBar.setVisibility(View.GONE);
        }
    }

    @NonNull
    private String describeFilter(int shown, int minRating) {
        StringBuilder b = new StringBuilder();
        if (shown != 0 && shown != EhCategory.ALL) {
            for (EhCategory c : EhCategory.values()) {
                if (c == EhCategory.UNKNOWN || (shown & c.bit) == 0) continue;
                if (b.length() > 0) b.append(", ");
                b.append(EhUi.categoryLabel(this, c));
            }
        }
        if (minRating >= 2) {
            if (b.length() > 0) b.append(" · ");
            b.append(getString(R.string.eh_filter_min_rating_value, minRating));
        }
        return getString(R.string.eh_filter_active, b.toString());
    }

    /** First page of the current section. */
    private void reload(boolean userRefresh) {
        generation++;
        nextCursor = null;
        endReached = false;
        loading = false;
        if (!userRefresh) adapter.setItems(new ArrayList<>());
        updateTitle();
        messageBox.setVisibility(View.GONE);
        if (!loadLocalSection()) loadPage(null);
    }

    /** Sections backed by ehentai.db; returns false for online sections. */
    protected boolean loadLocalSection() {
        if (section == Section.FAVORITES && !EhAuth.isLoggedIn(this)) {
            refresh.setRefreshing(false);
            endReached = true;
            showMessage(getString(R.string.eh_favorites_sign_in), getString(R.string.eh_account_sign_in), this::openLogin);
            return true;
        }
        if (!isLocal(section)) return false;
        final Section s = section;
        final int gen = generation;
        endReached = true;
        refresh.setRefreshing(true);
        io.execute(() -> {
            EhDatabase db = EhDatabase.get(this);
            List<EhDatabase.Entry> list = s == Section.HISTORY ? db.history()
                : s == Section.SAVED ? db.favorites() : db.downloads();
            runOnUiThread(() -> {
                if (gen != generation || isFinishing()) return;
                int empty = s == Section.HISTORY ? R.string.eh_history_empty
                    : s == Section.SAVED ? R.string.eh_saved_empty : R.string.eh_downloads_empty;
                showList(list, getString(empty));
            });
        });
        return true;
    }

    private void openLogin() {
        startActivity(new Intent(this, EhLoginActivity.class));
    }

    @NonNull
    private String folderLabel(int slot) {
        if (slot < 0) return getString(R.string.eh_fav_all);
        String n = slot < folderNames.length ? folderNames[slot] : null;
        return n == null || n.isEmpty() ? getString(R.string.eh_fav_folder_default, slot) : n;
    }

    private void loadMore() {
        if (nextCursor != null) loadPage(nextCursor);
    }

    @Nullable
    protected String urlFor(@NonNull Section s, @Nullable String cursor) {
        boolean ex = EhSettings.useExhentai(this);
        switch (s) {
            case POPULAR:
                return cursor == null ? EhUrls.popularUrl(ex) : null;
            case FAVORITES:
                return EhUrls.favoritesUrl(ex, favcat, null, cursor);
            default:
                return EhUrls.searchUrl(ex, query, EhSettings.shownCategories(this), EhSettings.minRating(this), cursor);
        }
    }

    private void loadPage(@Nullable String cursor) {
        String url = urlFor(section, cursor);
        if (url == null) {
            endReached = true;
            return;
        }
        final int gen = generation;
        loading = true;
        refresh.setRefreshing(true);
        io.execute(() -> {
            try {
                EhListParser.Page page;
                String[] folders = null;
                if (section == Section.FAVORITES) {
                    EhRepository.FavoritesPage fp = EhRepository.loadFavorites(this, url);
                    page = fp.page;
                    folders = fp.folders;
                } else {
                    page = EhRepository.loadList(this, url);
                }
                final String[] newFolders = folders;
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    if (newFolders != null) {
                        folderNames = newFolders;
                        updateTitle();
                    }
                    loading = false;
                    refresh.setRefreshing(false);
                    if (cursor == null) adapter.setItems(page.items);
                    else adapter.addItems(page.items);
                    nextCursor = page.nextCursor;
                    endReached = page.nextCursor == null;
                    if (adapter.isEmpty()) showMessage(getString(R.string.eh_no_results), null, null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    loading = false;
                    refresh.setRefreshing(false);
                    if (adapter.isEmpty()) showMessage(EhUi.describe(this, e), getString(R.string.eh_retry), () -> reload(true));
                    else com.google.android.material.snackbar.Snackbar
                        .make(recycler, EhUi.describe(this, e), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.eh_retry, v -> loadMore()).show();
                });
            }
        });
    }

    protected void showMessage(@NonNull String text, @Nullable String action, @Nullable Runnable onAction) {
        message.setText(text);
        messageAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) messageAction.setText(action);
        messageAction.setOnClickListener(v -> {
            if (onAction != null) onAction.run();
        });
        messageBox.setVisibility(View.VISIBLE);
    }

    protected void showList(@NonNull List<? extends EhGalleryInfo> list, @NonNull String emptyText) {
        refresh.setRefreshing(false);
        adapter.setItems(list);
        endReached = true;
        if (list.isEmpty()) showMessage(emptyText, null, null);
        else messageBox.setVisibility(View.GONE);
    }

    protected boolean onItemLongPress(@NonNull EhGalleryInfo info) {
        if (section != Section.HISTORY && section != Section.SAVED && section != Section.DOWNLOADS) return false;
        final Section s = section;
        int text = s == Section.HISTORY ? R.string.eh_history_remove
            : s == Section.SAVED ? R.string.eh_local_favorite_remove : R.string.eh_download_delete;
        new MaterialAlertDialogBuilder(this)
            .setTitle(info.title)
            .setItems(new CharSequence[]{getString(text)}, (d, w) -> {
                if (s == Section.DOWNLOADS) {
                    EhDownloads.confirmDelete(this, info, () -> adapter.remove(info));
                    return;
                }
                io.execute(() -> {
                        if (s == Section.HISTORY) EhDatabase.get(this).removeHistory(info.key);
                    else EhDatabase.get(this).setFavorite(info, false);
                });
                adapter.remove(info);
            })
            .show();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eh_browse, menu);
        Utility.tintMenu(this, menu);
        MenuItem searchItem = menu.findItem(R.id.eh_action_search);
        SearchView sv = (SearchView) searchItem.getActionView();
        if (sv != null) {
            sv.setQueryHint(getString(R.string.eh_search_hint));
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String q) {
                    search(q);
                    searchItem.collapseActionView();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean loggedIn = EhAuth.isLoggedIn(this);
        MenuItem account = menu.findItem(R.id.eh_action_account);
        account.setTitle(loggedIn ? getString(R.string.eh_account_sign_out) : getString(R.string.eh_account_sign_in));
        MenuItem ex = menu.findItem(R.id.eh_action_exhentai);
        ex.setVisible(loggedIn);
        ex.setChecked(SourcePreferences.ehSource(this) == GallerySource.EXHENTAI);
        menu.findItem(R.id.eh_action_fav_folder).setVisible(section == Section.FAVORITES && loggedIn);
        menu.findItem(R.id.eh_action_clear_history).setVisible(section == Section.HISTORY);
        menu.findItem(R.id.eh_action_filter).setVisible(section == Section.FRONT);
        return super.onPrepareOptionsMenu(menu);
    }

    /** Runs a search on the front-page section. */
    protected void search(@Nullable String q) {
        query = q == null || q.trim().isEmpty() ? null : q.trim();
        if (section != Section.FRONT) {
            TabLayout.Tab tab = tabs.getTabAt(sections.indexOf(Section.FRONT));
            if (tab != null) {
                tab.select();
                return;
            }
        }
        reload(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (id == R.id.eh_action_filter) {
            showFilterDialog();
            return true;
        }
        if (id == R.id.eh_action_account) {
            if (!EhAuth.isLoggedIn(this)) openLogin();
            else new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_account_sign_out)
                .setMessage(R.string.eh_account_sign_out_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    EhAuth.logout(this);
                    EhLoginActivity.clearWebCookies();
                    authSnapshot = authState();
                    invalidateOptionsMenu();
                    reload(false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        }
        if (id == R.id.eh_action_exhentai) {
            boolean want = SourcePreferences.ehSource(this) != GallerySource.EXHENTAI;
            if (want && !EhAuth.hasExhentaiAccess(this)) {
                checkExhentaiAccess();
                return true;
            }
            SourcePreferences.setEhHost(this, want);
            authSnapshot = authState();
            invalidateOptionsMenu();
            reload(false);
            return true;
        }
        if (id == R.id.eh_action_fav_folder) {
            showFolderDialog();
            return true;
        }
        if (id == R.id.eh_action_clear_history) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_history_clear)
                .setMessage(R.string.eh_history_clear_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> io.execute(() -> {
                    EhDatabase.get(this).clearHistory();
                    runOnUiThread(() -> reload(false));
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        }
        if (id == R.id.eh_action_stats) {
            showStats();
            return true;
        }
        if (id == R.id.eh_action_switch_source) {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** ExHentai needs an igneous cookie; ask the site for one before switching. */
    private void checkExhentaiAccess() {
        refresh.setRefreshing(true);
        io.execute(() -> {
            String igneous = null;
            try {
                igneous = EhClient.fetchIgneous(this);
            } catch (Exception ignore) {
                // Reported below as no access.
            }
            final boolean ok = igneous != null;
            if (ok) EhAuth.storeIgneous(this, igneous);
            runOnUiThread(() -> {
                refresh.setRefreshing(false);
                if (!ok) {
                    new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.eh_exhentai_unavailable)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                    return;
                }
                SourcePreferences.setEhHost(this, true);
                authSnapshot = authState();
                invalidateOptionsMenu();
                reload(false);
            });
        });
    }

    private void showFolderDialog() {
        String[] labels = new String[EhFavoritesParser.SLOT_COUNT + 1];
        labels[0] = getString(R.string.eh_fav_all);
        for (int i = 0; i < EhFavoritesParser.SLOT_COUNT; i++) labels[i + 1] = folderLabel(i);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_fav_folder)
            .setSingleChoiceItems(labels, favcat + 1, (d, which) -> {
                favcat = which - 1;
                d.dismiss();
                reload(false);
            })
            .show();
    }

    private void showStats() {
        io.execute(() -> {
            long[] st = EhDatabase.get(this).stats();
            int saved = EhDatabase.get(this).favorites().size();
            int downloads = EhDatabase.get(this).downloads().size();
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_stats)
                .setMessage(getString(R.string.eh_stats_body, st[0], st[1], st[2] / 60, saved, downloads))
                .setPositiveButton(android.R.string.ok, null)
                .show());
        });
    }

    private void showFilterDialog() {
        final EhCategory[] cats = new EhCategory[10];
        String[] labels = new String[10];
        boolean[] checked = new boolean[10];
        int shown = EhSettings.shownCategories(this);
        int i = 0;
        for (EhCategory c : EhCategory.values()) {
            if (c == EhCategory.UNKNOWN) continue;
            cats[i] = c;
            labels[i] = EhUi.categoryLabel(this, c);
            checked[i] = shown == 0 || (shown & c.bit) != 0;
            i++;
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_filter_categories)
            .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                int bits = 0;
                for (int k = 0; k < cats.length; k++) if (checked[k]) bits |= cats[k].bit;
                EhSettings.setShownCategories(this, bits == EhCategory.ALL ? 0 : bits);
                showRatingDialog();
            })
            .setNeutralButton(R.string.eh_filter_reset, (d, w) -> {
                EhSettings.setShownCategories(this, 0);
                EhSettings.setMinRating(this, 0);
                reload(false);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showRatingDialog() {
        String[] options = {
            getString(R.string.eh_filter_any_rating),
            getString(R.string.eh_filter_min_rating_value, 2),
            getString(R.string.eh_filter_min_rating_value, 3),
            getString(R.string.eh_filter_min_rating_value, 4),
            getString(R.string.eh_filter_min_rating_value, 5),
        };
        int current = Math.max(0, EhSettings.minRating(this) - 1);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_filter_min_rating)
            .setSingleChoiceItems(options, current, (d, which) -> {
                EhSettings.setMinRating(this, which == 0 ? 0 : which + 1);
                d.dismiss();
                reload(false);
            })
            .setOnCancelListener(d -> reload(false))
            .show();
    }
}
