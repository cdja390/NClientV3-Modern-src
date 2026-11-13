package com.maxwai.nclientv3;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.maxwai.nclientv3.adapters.ListAdapter;
import com.maxwai.nclientv3.components.activities.BaseActivity;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.source.GalleryHistory;
import com.maxwai.nclientv3.utility.BottomNavRouter;
import com.maxwai.nclientv3.utility.GlassBar;
import com.maxwai.nclientv3.utility.HistoryClearHelper;
import com.maxwai.nclientv3.utility.SearchImeHelper;
import com.maxwai.nclientv3.utility.Utility;

import java.util.Objects;

public class HistoryActivity extends BaseActivity {
    private ListAdapter adapter;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);
        setContentView(R.layout.activity_history);
        BottomNavRouter.install(this, R.id.history);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.history);
        recycler = findViewById(R.id.recycler);
        masterLayout = findViewById(R.id.master_layout);
        adapter = new ListAdapter(this);
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        recycler.setAdapter(adapter);
        filterHistory("");
    }

    private void syncEmpty() {
        if (adapter == null) return;
        View emptyView = findViewById(R.id.history_empty);
        if (emptyView == null) return;
        emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavRouter.sync(this, R.id.history);
        GlassBar.refresh(this);
        if (BottomNavRouter.hasHistoryChanged(this) && adapter != null) {
            filterHistory(query);
        } else {
            adapter.resetStatuses();
            adapter.notifyDataSetChanged();
        }
    }

    public void filterHistory(String query) {
        this.query = query == null ? "" : query;
        adapter.restartDataset(GalleryHistory.rows(this, this.query));
        syncEmpty();
    }

    public void onHistoryCleared() {
        filterHistory(query);
        BottomNavRouter.noteHistory(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            BottomNavRouter.backHome(this);
            return true;
        } else if (item.getItemId() == R.id.cancelAll) {
            HistoryClearHelper.onClearClicked(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected int getPortraitColumnCount() {
        return Global.getColPortHistory();
    }

    @Override
    protected int getLandscapeColumnCount() {
        return Global.getColLandHistory();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.history, menu);
        SearchImeHelper.installHistory(this, menu.findItem(R.id.search));
        Utility.tintMenu(this, menu);
        return true;
    }
}
