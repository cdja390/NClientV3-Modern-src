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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point of the e-hentai source: front page / search, popular, and (with later phases)
 * favorites, history and downloads. Pages are loaded one at a time, only when the list is
 * scrolled near its end, so browsing stays within e-hentai's page-load limits.
 */
public class EhBrowseActivity extends GeneralActivity {
    enum Section {FRONT, POPULAR}

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
        messageAction.setOnClickListener(v -> reload(true));

        query = getIntent().getStringExtra(EhIntents.EXTRA_QUERY);
        buildSections();
        for (Section s : sections) tabs.addTab(tabs.newTab().setText(sectionTitle(s)));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                section = sections.get(tab.getPosition());
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
        reload(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTitle();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    protected void buildSections() {
        sections.add(Section.FRONT);
        sections.add(Section.POPULAR);
    }

    @NonNull
    private String sectionTitle(@NonNull Section s) {
        switch (s) {
            case POPULAR: return getString(R.string.eh_section_popular);
            default: return getString(R.string.eh_section_front);
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

    /** Sections backed by ehentai.db load synchronously; returns false for online sections. */
    protected boolean loadLocalSection() {
        return false;
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
                EhListParser.Page page = EhRepository.loadList(this, url);
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    loading = false;
                    refresh.setRefreshing(false);
                    if (cursor == null) adapter.setItems(page.items);
                    else adapter.addItems(page.items);
                    nextCursor = page.nextCursor;
                    endReached = page.nextCursor == null;
                    if (adapter.isEmpty()) showMessage(getString(R.string.eh_no_results), false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    loading = false;
                    refresh.setRefreshing(false);
                    if (adapter.isEmpty()) showMessage(EhUi.describe(this, e), true);
                    else com.google.android.material.snackbar.Snackbar
                        .make(recycler, EhUi.describe(this, e), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.eh_retry, v -> loadMore()).show();
                });
            }
        });
    }

    protected void showMessage(@NonNull String text, boolean retry) {
        message.setText(text);
        messageAction.setVisibility(retry ? View.VISIBLE : View.GONE);
        messageBox.setVisibility(View.VISIBLE);
    }

    protected void showList(@NonNull List<? extends EhGalleryInfo> list, @NonNull String emptyText) {
        refresh.setRefreshing(false);
        adapter.setItems(list);
        endReached = true;
        if (list.isEmpty()) showMessage(emptyText, false);
        else messageBox.setVisibility(View.GONE);
    }

    protected boolean onItemLongPress(@NonNull EhGalleryInfo info) {
        return false;
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
        if (id == R.id.eh_action_switch_source) {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
