package com.maxwai.nclientv3;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.adapters.FavoriteAdapter;
import com.maxwai.nclientv3.adapters.ListAdapter;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.components.activities.BaseActivity;
import com.maxwai.nclientv3.components.views.PageSwitcher;
import com.maxwai.nclientv3.ehentai.EhFavoritesParser;
import com.maxwai.nclientv3.ehentai.EhAuth;
import com.maxwai.nclientv3.ehentai.EhClient;
import com.maxwai.nclientv3.ehentai.EhFeed;
import com.maxwai.nclientv3.ehentai.EhLoginActivity;
import com.maxwai.nclientv3.ehentai.EhUrls;
import com.maxwai.nclientv3.ehentai.EhUi;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.settings.FavoriteSyncManager;
import com.maxwai.nclientv3.utility.BottomNavRouter;
import com.maxwai.nclientv3.utility.FavoriteSyncHelper;
import com.maxwai.nclientv3.utility.GlassBar;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.SafeMode;
import com.maxwai.nclientv3.utility.SearchImeHelper;
import com.maxwai.nclientv3.utility.Utility;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.maxwai.nclientv3.source.FavoriteSources;
import com.maxwai.nclientv3.source.GalleryDownloads;
import com.maxwai.nclientv3.source.GallerySource;
import com.maxwai.nclientv3.source.SourceDialogs;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FavoriteActivity extends BaseActivity {
    public static final String EXTRA_ONLINE = "favorite_online";
    public static final String EXTRA_ONLINE_QUERY = "favorite_online_query";
    private static final String FOLDER_KEY = "eh_favcat_view";
    private static final int ENTRY_PER_PAGE = 24;
    private FavoriteAdapter adapter = null;
    private ListAdapter onlineAdapter;
    private final ExecutorService onlineIo = Executors.newSingleThreadExecutor();
    private boolean online;
    private GallerySource onlineSource;
    private boolean onlineLoggedIn;
    private volatile int onlineGeneration;
    private int onlinePage = 1, onlineLast = 1, onlineFolder = -1;
    private boolean onlineBusy;
    private String[] folderNames = new String[EhFavoritesParser.SLOT_COUNT];
    private List<GenericGallery> onlineRows = Collections.emptyList();
    private String localQuery = "", onlineQuery = "";
    private Snackbar onlineError;
    private boolean sortByTitle = false;
    private PageSwitcher pageSwitcher;
    private SearchView searchView;
    private MenuItem syncItem;
    private boolean syncRunning = false;
    /**
     * Held for the lifetime of the Activity rather than created per sync, so it can be attached in
     * {@link #onCreate} - picking up a sync that an earlier instance of this screen started - and
     * detached in {@link #onDestroy} instead of being retained by the sync thread until the run
     * ends. All three callbacks arrive on the sync thread.
     */
    private final FavoriteSyncManager.Listener syncListener = new FavoriteSyncManager.Listener() {
        @Override
        public void onProgress(boolean writing, int completed, int total) {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                ActionBar actionBar = getSupportActionBar();
                if (!online && actionBar != null) actionBar.setSubtitle(FavoriteSyncManager
                    .phaseText(FavoriteActivity.this, writing, completed, total));
            });
        }

        @Override
        public void onComplete(FavoriteSyncManager.Result result) {
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) finishSync(result);
            });
        }

        @Override
        public void onFailure() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                resetSyncUi();
                Toast.makeText(FavoriteActivity.this,
                    R.string.favorite_sync_failed, Toast.LENGTH_LONG).show();
            });
        }
    };

    public static int getEntryPerPage() {
        return Global.isInfiniteScrollFavorite() ? Integer.MAX_VALUE : ENTRY_PER_PAGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);

        setContentView(R.layout.activity_main); // drawer host, as in the Modern publish build
        BottomNavRouter.install(this, R.id.favorite_manager);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.favorites_title);
        pageSwitcher = findViewById(R.id.page_switcher);
        recycler = findViewById(R.id.recycler);
        masterLayout = findViewById(R.id.master_layout);
        refresher = findViewById(R.id.refresher);
        adapter = new FavoriteAdapter(this);
        onlineAdapter = new ListAdapter(this);
        online = savedInstanceState == null ? getIntent().getBooleanExtra(EXTRA_ONLINE, false)
            : savedInstanceState.getBoolean(EXTRA_ONLINE);
        Login.initLogin(this);
        if (savedInstanceState != null) {
            localQuery = savedInstanceState.getString("local_query", "");
            onlineQuery = savedInstanceState.getString("online_query", "");
            sortByTitle = savedInstanceState.getBoolean("local_sort_by_title");
        } else {
            onlineQuery = Objects.requireNonNullElse(getIntent().getStringExtra(EXTRA_ONLINE_QUERY), "");
        }
        onlineFolder = Math.max(-1, Math.min(EhFavoritesParser.SLOT_COUNT - 1,
            getSharedPreferences("Settings", MODE_PRIVATE).getInt(FOLDER_KEY, -1)));
        findViewById(R.id.favorites_login).setOnClickListener(v -> loginToOnlineSource());

        refresher.setOnRefreshListener(() -> {
            if (online) loadOnline(Global.isInfiniteScrollFavorite() ? 1 : onlinePage, false);
            else loadLocal(getActualPage());
        });
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        pageSwitcher.displayPages(1, 1);
        pageSwitcher.setChanger(new PageSwitcher.DefaultPageChanger() {
            @Override
            public void pageChanged() {
                if (online) loadOnline(getActualPage(), false);
                else {
                    loadLocal(getActualPage());
                    recycler.scrollToPosition(0);
                }
            }
        });
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (!online || !onlineLoggedIn || onlineBusy || dy <= 0 || !Global.isInfiniteScrollFavorite()
                    || onlinePage >= onlineLast || !(view.getLayoutManager() instanceof GridLayoutManager)) return;
                GridLayoutManager layout = (GridLayoutManager) view.getLayoutManager();
                if (layout.findLastVisibleItemPosition() >= onlineAdapter.getItemCount() - layout.getSpanCount() - 1)
                    loadOnline(onlinePage + 1, true);
            }
        });
        TabLayout tabs = findViewById(R.id.favorite_tabs);
        tabs.setVisibility(View.VISIBLE);
        tabs.addTab(tabs.newTab().setText(R.string.favorites_local_tab));
        tabs.addTab(tabs.newTab().setText(R.string.favorites_online_tab), online);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition() == 1); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        showTab(online);

        // Subscribe unconditionally: a sync started by an earlier instance of this screen may
        // still be running, and only a subscriber gets its completion - and with it the reload
        // that makes the newly synced favorites appear.
        FavoriteSyncManager.addListener(syncListener);
        if (FavoriteSyncManager.isRunning()) syncRunning = true;
    }

    @Override
    protected void onDestroy() {
        FavoriteSyncManager.removeListener(syncListener);
        onlineGeneration++;
        onlineIo.shutdownNow();
        adapter.close();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_ONLINE, online);
        outState.putString("local_query", localQuery);
        outState.putString("online_query", onlineQuery);
        outState.putBoolean("local_sort_by_title", sortByTitle);
    }

    public int getActualPage() {
        return pageSwitcher.getActualPage();
    }

    @Override
    protected int getLandscapeColumnCount() {
        return Global.getColLandFavorite();
    }

    @Override
    protected int getPortraitColumnCount() {
        return Global.getColPortFavorite();
    }

    private void loadLocal(int page) {
        refresher.setRefreshing(true);
        adapter.load(localQuery, sortByTitle, page);
    }

    public void onLocalFavoritesLoaded(int page, int last) {
        if (online) return;
        refresher.setRefreshing(syncRunning);
        pageSwitcher.displayPages(last, page);
        TextView empty = findViewById(R.id.browse_empty);
        empty.setText(R.string.no_entry_found);
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void showTab(boolean showOnline) {
        onlineGeneration++;
        adapter.cancelLoad();
        online = showOnline;
        onlineBusy = false;
        pageSwitcher.displayPages(1, 1);
        recycler.setAdapter(online ? onlineAdapter : adapter);
        Objects.requireNonNull(getSupportActionBar()).setSubtitle(null);
        hideOnlineError();
        if (online) {
            updateOnlineSource(true);
        } else {
            findViewById(R.id.favorite_filters).setVisibility(View.GONE);
            findViewById(R.id.favorites_login).setVisibility(View.GONE);
            findViewById(R.id.browse_empty).setVisibility(View.GONE);
            refresher.setEnabled(true);
            loadLocal(1);
        }
        invalidateOptionsMenu();
    }

    private void updateOnlineSource(boolean entering) {
        GallerySource selected = FavoriteSources.selected(this);
        boolean loggedIn = FavoriteSources.isLoggedIn(this, selected);
        boolean changed = onlineSource != selected || loggedIn != onlineLoggedIn;
        if (onlineSource != null && onlineSource != selected) {
            onlineQuery = "";
            folderNames = new String[EhFavoritesParser.SLOT_COUNT];
        }
        onlineSource = selected;
        onlineLoggedIn = loggedIn;
        bindOnlineFilters();
        if (!changed && !entering) {
            if (!onlineBusy && onlineLoggedIn) loadOnline(onlinePage, false);
            return;
        }
        onlineGeneration++;
        onlineBusy = false;
        hideOnlineError();
        onlinePage = onlineLast = 1;
        onlineRows = Collections.emptyList();
        onlineAdapter.restartDataset(onlineRows);
        pageSwitcher.displayPages(1, 1);
        refresher.setEnabled(loggedIn);
        refresher.setRefreshing(false);
        findViewById(R.id.favorites_login).setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        findViewById(R.id.browse_empty).setVisibility(View.GONE);
        if (loggedIn) loadOnline(1, false);
        invalidateOptionsMenu();
    }

    private void bindOnlineFilters() {
        EnumSet<GallerySource> sources = FavoriteSources.available(this);
        ChipGroup group = findViewById(R.id.favorite_sources);
        group.removeAllViews();
        if (sources.size() > 1) {
            for (GallerySource source : sources) {
                Chip chip = new Chip(this);
                chip.setText(SourceDialogs.label(source));
                chip.setCheckable(true);
                chip.setChecked(source == onlineSource);
                chip.setOnClickListener(v -> {
                    if (source == onlineSource) {
                        chip.setChecked(true);
                        return;
                    }
                    FavoriteSources.select(this, source);
                    updateOnlineSource(false);
                });
                group.addView(chip);
            }
        }
        if (onlineLoggedIn && onlineSource != GallerySource.NHENTAI) {
            Chip folders = new Chip(this);
            folders.setText(getString(R.string.eh_fav_current, folderLabel(onlineFolder)));
            folders.setOnClickListener(v -> pickFolder());
            group.addView(folders);
        }
        findViewById(R.id.favorite_filters).setVisibility(group.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private String folderLabel(int folder) {
        if (folder < 0) return getString(R.string.eh_fav_all);
        String label = folderNames[folder];
        return label == null || label.isEmpty() ? getString(R.string.eh_fav_folder_default, folder) : label;
    }

    private void pickFolder() {
        String[] labels = new String[EhFavoritesParser.SLOT_COUNT + 1];
        for (int i = 0; i < labels.length; i++) labels[i] = folderLabel(i - 1);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.eh_fav_folder)
            .setSingleChoiceItems(labels, onlineFolder + 1, (dialog, which) -> {
                dialog.dismiss();
                onlineFolder = which - 1;
                getSharedPreferences("Settings", MODE_PRIVATE).edit().putInt(FOLDER_KEY, onlineFolder).apply();
                bindOnlineFilters();
                loadOnline(1, false);
            })
            .setNegativeButton(R.string.cancel, null).show();
    }

    private void loginToOnlineSource() {
        if (onlineSource == GallerySource.EXHENTAI && EhAuth.isLoggedIn(this)) {
            if (onlineBusy) return;
            int request = ++onlineGeneration;
            onlineBusy = true;
            refresher.setRefreshing(true);
            onlineIo.execute(() -> {
                String access;
                try {
                    access = EhClient.fetchIgneous(this);
                } catch (Exception e) {
                    access = null;
                }
                String result = access;
                runOnUiThread(() -> {
                    if (!isCurrentOnline(request)) return;
                    onlineBusy = false;
                    refresher.setRefreshing(false);
                    if (result == null) {
                        new MaterialAlertDialogBuilder(this).setMessage(R.string.eh_exhentai_unavailable)
                            .setPositiveButton(R.string.ok, null).show();
                    } else {
                        EhAuth.storeIgneous(this, result);
                        updateOnlineSource(false);
                    }
                });
            });
        } else startActivity(new Intent(this, onlineSource == GallerySource.NHENTAI ? LoginActivity.class : EhLoginActivity.class));
    }

    private void loadOnline(int page, boolean append) {
        if (!onlineLoggedIn) return;
        int request = ++onlineGeneration;
        onlineBusy = true;
        refresher.setRefreshing(true);
        hideOnlineError();
        findViewById(R.id.browse_empty).setVisibility(View.GONE);
        String query = onlineQuery;
        if (onlineSource == GallerySource.NHENTAI) {
            InspectorV3.favoriteInspector(this, query, page, new InspectorV3.DefaultInspectorResponse() {
                @Override
                public void dispatch(InspectorV3 inspector, Runnable callback) {
                    runOnUiThread(() -> { if (isCurrentOnline(request)) callback.run(); });
                }

                @Override
                public void onSuccess(List<GenericGallery> rows) {
                    // The inspector's page count is delivered together with its rows.
                    showOnlinePage(request, page, resultInspector.getPageCount(), rows, append);
                }

                private InspectorV3 resultInspector;

                @Override
                public boolean shouldStart(InspectorV3 inspector) {
                    resultInspector = inspector;
                    return true;
                }

                @Override
                public void onFailure(Exception failure) {
                    showOnlineFailure(request, page, append, failure);
                }
            }).start();
            return;
        }
        boolean ex = onlineSource == GallerySource.EXHENTAI;
        int folder = onlineFolder;
        onlineIo.execute(() -> {
            if (request != onlineGeneration) return;
            try {
                String[][] folders = new String[1][];
                EhFeed.Result result = EhFeed.favorites(this, ex, folder, query, page, names -> folders[0] = names);
                runOnUiThread(() -> {
                    if (!isCurrentOnline(request)) return;
                    if (folders[0] != null) folderNames = folders[0];
                    showOnlinePage(request, page, result.pageCount(page), EhFeed.wrap(result.items), append);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> showOnlineFailure(request, page, append, failure));
            }
        });
    }

    private boolean isCurrentOnline(int request) {
        return request == onlineGeneration && online && !isFinishing() && !isDestroyed();
    }

    private void showOnlinePage(int request, int page, int last, List<GenericGallery> rows, boolean append) {
        if (!isCurrentOnline(request)) return;
        onlineBusy = false;
        refresher.setRefreshing(false);
        onlinePage = page;
        onlineLast = Math.max(1, last);
        onlineRows = rows;
        if (append) onlineAdapter.addGalleries(rows);
        else {
            onlineAdapter.resetStatuses();
            onlineAdapter.restartDataset(rows);
            if (!rows.isEmpty()) recycler.scrollToPosition(0);
        }
        pageSwitcher.displayPages(onlineLast, onlinePage);
        if (Global.isInfiniteScrollFavorite()) pageSwitcher.setVisibility(View.GONE);
        TextView empty = findViewById(R.id.browse_empty);
        empty.setText(R.string.no_entry_found);
        empty.setVisibility(onlineAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        bindOnlineFilters();
    }

    private void showOnlineFailure(int request, int page, boolean append, Exception failure) {
        if (!isCurrentOnline(request)) return;
        LogUtility.e("Online favorites request failed", failure);
        onlineBusy = false;
        refresher.setRefreshing(false);
        if (!FavoriteSources.isLoggedIn(this, onlineSource)) {
            updateOnlineSource(false);
            return;
        }
        String message = onlineSource == GallerySource.NHENTAI ? getString(R.string.unable_to_connect_to_the_site)
            : EhUi.describe(this, failure);
        onlineError = Snackbar.make(masterLayout, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.retry, v -> loadOnline(page, append));
        onlineError.show();
    }

    private void hideOnlineError() {
        if (onlineError != null) onlineError.dismiss();
        onlineError = null;
    }

    private void openRandomOnline() {
        int request = onlineGeneration;
        InspectorV3.randomInspector(this, new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> rows) {
                runOnUiThread(() -> {
                    if (!isCurrentOnline(request)) return;
                    if (rows.size() == 1 && rows.get(0) instanceof Gallery) {
                        startActivity(new Intent(FavoriteActivity.this, GalleryActivity.class)
                            .putExtra(getPackageName() + ".GALLERY", rows.get(0)));
                    } else Toast.makeText(FavoriteActivity.this, R.string.no_entry_found, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(Exception failure) {
                super.onFailure(failure);
                runOnUiThread(() -> {
                    if (isCurrentOnline(request))
                        Toast.makeText(FavoriteActivity.this, R.string.unable_to_connect_to_the_site, Toast.LENGTH_SHORT).show();
                });
            }
        }, true).start();
    }

    @Override
    protected void onResume() {
        BottomNavRouter.sync(this, R.id.favorite_manager);
        GlassBar.refresh(this);
        Login.initLogin(this);
        if (online) updateOnlineSource(false);
        else loadLocal(getActualPage());
        invalidateOptionsMenu();
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.sources).setVisible(false);
        menu.findItem(R.id.download_page).setVisible(!online || onlineLoggedIn);
        menu.findItem(R.id.sort_by_name).setVisible(!online)
            .setTitle(sortByTitle ? R.string.sort_by_latest : R.string.sort_by_title);
        menu.findItem(R.id.by_popular).setVisible(false);
        menu.findItem(R.id.only_language).setVisible(false);
        menu.findItem(R.id.add_bookmark).setVisible(false);
        menu.findItem(R.id.open_browser).setVisible(online && onlineLoggedIn);
        MenuItem searchItem = menu.findItem(R.id.search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
            | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        syncItem = menu.findItem(R.id.sync_favorites);
        syncItem.setVisible(!online);
        syncItem.setEnabled(!syncRunning && !FavoriteSyncManager.isRunning());
        MenuItem randomItem = menu.findItem(R.id.random_favorite);
        randomItem.setVisible(!online || (onlineLoggedIn && onlineSource == GallerySource.NHENTAI));
        randomItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
        searchItem.setVisible(!online || onlineLoggedIn);
        Objects.requireNonNull(searchView).setQuery(online ? onlineQuery : localQuery, false);
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (online) {
                    onlineQuery = query.trim();
                    loadOnline(1, false);
                }
                SearchImeHelper.onSubmitActivity(FavoriteActivity.this);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!online && !localQuery.equals(newText)) {
                    localQuery = newText;
                    loadLocal(1);
                    recycler.scrollToPosition(0);
                }
                return true;
            }
        });
        SearchImeHelper.install(this, searchItem);
        Utility.tintMenu(this, menu);
        MenuItem safeModeItem = menu.findItem(R.id.safe_mode);
        if (safeModeItem != null) safeModeItem.setChecked(SafeMode.isOn(this));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.safe_mode) {
            item.setChecked(SafeMode.toggle(this));
            if (adapter != null) adapter.notifyDataSetChanged();
            onlineAdapter.notifyDataSetChanged();
            return true;
        } else if (item.getItemId() == R.id.sync_favorites) {
            confirmFavoriteSync();
        } else if (item.getItemId() == R.id.download_page) {
            showDialogDownloadAll();
        } else if (item.getItemId() == R.id.sort_by_name) {
            sortByTitle = !sortByTitle;
            loadLocal(1);
            recycler.scrollToPosition(0);
            item.setTitle(sortByTitle ? R.string.sort_by_latest : R.string.sort_by_title);
        } else if (item.getItemId() == R.id.random_favorite) {
            if (online) openRandomOnline();
            else startActivity(new Intent(this, RandomFavoriteActivity.class));
        } else if (item.getItemId() == R.id.open_browser && online && onlineSource != null) {
            String url = onlineSource == GallerySource.NHENTAI ? Utility.getBaseUrl() + "favorites/"
                : EhUrls.favoritesUrl(onlineSource == GallerySource.EXHENTAI, onlineFolder, onlineQuery, null);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } else if (item.getItemId() == android.R.id.home) {
            BottomNavRouter.backHome(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmFavoriteSync() {
        FavoriteSyncHelper.showSyncDialog(this);
    }

    private void syncFavorites() {
        syncFavorites(0);
    }

    public void syncFavorites(int maxPages) {
        if (!Login.canAccessAuthenticatedApi(this)) {
            Toast.makeText(this, R.string.favorite_sync_login_required, Toast.LENGTH_LONG).show();
            return;
        }
        // A sync may already be in flight, started either by this screen or before it existed.
        // Either way this instance is subscribed, so show what it is doing rather than leaving
        // the tap looking like it did nothing.
        if (syncRunning || FavoriteSyncManager.isRunning()) {
            showSyncProgressDialog();
            return;
        }
        requestNotificationPermission();

        syncRunning = true;
        if (syncItem != null) syncItem.setEnabled(false);
        refresher.setRefreshing(true);
        // Losing the race with a sync started elsewhere is not a failure: this screen is already
        // subscribed to that run, so keep the syncing UI and let its callbacks drive it.
        if (!FavoriteSyncManager.sync(this, maxPages)) showSyncProgressDialog();
    }

    private void finishSync(FavoriteSyncManager.Result result) {
        resetSyncUi();
        if (!online) loadLocal(getActualPage());
        Toast.makeText(this, FavoriteSyncManager.describe(this, result), Toast.LENGTH_SHORT).show();
    }

    /**
     * Live view of a sync that is already in flight. Polls the manager's snapshot rather than
     * subscribing, so there is no listener to unregister if the dialog or Activity goes away
     * mid-sync, and a dialog opened at any point still shows the current phase.
     */
    public void showSyncProgressDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_sync_progress, null);
        TextView phase = content.findViewById(R.id.phase);
        ProgressBar bar = content.findViewById(R.id.progress);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.favorite_sync_notification_title)
            .setView(content)
            .setPositiveButton(R.string.hide, null)
            .show();

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing() || isFinishing() || isDestroyed()) return;
                if (!FavoriteSyncManager.isRunning()) {
                    phase.setText(R.string.favorite_sync_finished);
                    bar.setIndeterminate(false);
                    bar.setProgress(bar.getMax());
                    return;
                }
                FavoriteSyncManager.Progress p = FavoriteSyncManager.getProgress();
                phase.setText(FavoriteSyncManager.describeProgress(FavoriteActivity.this));
                if (p.total > 0) {
                    bar.setIndeterminate(false);
                    bar.setMax(p.total);
                    bar.setProgress(p.completed);
                } else {
                    bar.setIndeterminate(true);
                }
                handler.postDelayed(this, 500);
            }
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));
        tick.run();
    }

    /**
     * The manifest declares POST_NOTIFICATIONS but nothing ever asked for it, so on Android 13+
     * every notification the app posts was being dropped silently by NotificationSettings.
     * Asked here rather than at startup so the prompt arrives with an obvious reason attached.
     * The sync runs either way; only its progress notification depends on the answer.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
    }

    private void resetSyncUi() {
        syncRunning = false;
        if (syncItem != null) syncItem.setEnabled(true);
        if (!online) refresher.setRefreshing(false);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setSubtitle(null);
    }

    private void showDialogDownloadAll() {
        if (online ? onlineBusy : refresher.isRefreshing()) return;
        List<GenericGallery> rows = online ? onlineRows : adapter.displayedRows();
        if (rows.isEmpty()) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder
            .setTitle(R.string.download_all_galleries_in_this_page)
            .setIcon(R.drawable.ic_file)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, (dialog, which) -> GalleryDownloads.enqueue(this, rows));
        builder.show();
    }
}
