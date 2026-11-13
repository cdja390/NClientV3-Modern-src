package com.maxwai.nclientv3.adapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.FavoriteActivity;
import com.maxwai.nclientv3.GalleryActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.ehentai.EhDatabase;
import com.maxwai.nclientv3.ehentai.EhSimpleGallery;
import com.maxwai.nclientv3.ehentai.EhUi;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.source.GalleryIdentity;
import com.maxwai.nclientv3.source.SourceDialogs;
import com.maxwai.nclientv3.source.SourceKey;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;
import com.maxwai.nclientv3.utility.LocalTagFilterHelper;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.SafeMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoriteAdapter extends RecyclerView.Adapter<GenericAdapter.ViewHolder> {
    private final FavoriteActivity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<SourceKey, Integer> statuses = new HashMap<>();
    private final Set<Integer> loadingGalleryIds = Collections.synchronizedSet(new HashSet<>());
    private List<Row> rows = Collections.emptyList();
    private GenericGallery[] galleries = new GenericGallery[0];
    private Cursor cursor;
    private volatile int generation;

    private static final class Row {
        final int nhPosition;
        final EhDatabase.Entry eh;

        Row(int nhPosition, EhDatabase.Entry eh) {
            this.nhPosition = nhPosition;
            this.eh = eh;
        }
    }

    public FavoriteAdapter(FavoriteActivity activity) {
        this.activity = activity;
    }

    @NonNull
    @Override
    public GenericAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GenericAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.entry_layout, parent, false));
    }

    @Nullable
    private GenericGallery galleryFromPosition(int position) {
        if (position < 0 || position >= rows.size()) return null;
        if (galleries[position] != null) return galleries[position];
        EhDatabase.Entry eh = rows.get(position).eh;
        if (eh != null) {
            return galleries[position] = new EhSimpleGallery(eh);
        }
        cursor.moveToPosition(rows.get(position).nhPosition);
        String pageData = cursor.getString(
            cursor.getColumnIndex(Queries.GalleryTable.PAGES));
        if (pageData != null && pageData.startsWith(Queries.GalleryTable.FAVORITE_SUMMARY_PREFIX)) {
            try {
                GenericGallery summary = SimpleGallery.fromV2ListItem(activity, new JSONObject(
                    pageData.substring(Queries.GalleryTable.FAVORITE_SUMMARY_PREFIX.length())));
                galleries[position] = summary;
                return summary;
            } catch (JSONException e) {
                LogUtility.e("Unable to read cached favorite summary", e);
                return null;
            }
        }
        if (pageData != null && pageData.contains(";") && !pageData.contains("/")) {
            GenericGallery summary = SimpleGallery.fromLegacyFavoriteCursor(cursor);
            galleries[position] = summary;
            return summary;
        }
        Gallery g = Queries.GalleryTable.cursorToGallery(activity, cursor);
        galleries[position] = g;
        if (g.getGalleryData().hasUpdatedInfo()) { // TODO: to be removed in next major version
            if (g.getGalleryData().isDeleted()) {
                LogUtility.w("Deleting Gallery " + g.getTitle() + " with id " + g.getId() + " since not available anymore");
                Queries.GalleryTable.delete(g.getId());
            } else {
                Queries.GalleryTable.insert(g);
            }
        }
        return g;
    }

    @Override
    public void onBindViewHolder(@NonNull final GenericAdapter.ViewHolder holder, int position) {
        final GenericGallery ent = galleryFromPosition(holder.getBindingAdapterPosition());
        if (ent == null) return;
        if (ent instanceof EhSimpleGallery)
            EhUi.loadThumb(holder.imgView, ((EhSimpleGallery) ent).info.thumb);
        else if (ent instanceof Gallery)
            ImageDownloadUtility.loadImage(activity, ((Gallery) ent).getThumbnail(), holder.imgView);
        else
            ImageDownloadUtility.loadImage(activity, ((SimpleGallery) ent).getThumbnail(), holder.imgView);
        SafeMode.apply(holder.imgView);
        SourceKey key = GalleryIdentity.of(ent);
        holder.pages.setText(activity.getString(R.string.source_badge_pages,
            activity.getString(SourceDialogs.badge(key.source)), ent.getPageCount()));
        holder.title.setText(ent.getTitle());
        holder.flag.setText(Global.getLanguageFlag(ent instanceof Gallery
            ? ((Gallery) ent).getLanguage() : ((SimpleGallery) ent).getLanguage()));
        holder.title.setOnClickListener(v -> {
            Layout layout = holder.title.getLayout();
            if (layout.getEllipsisCount(layout.getLineCount() - 1) > 0)
                holder.title.setMaxLines(7);
            else if (holder.title.getMaxLines() == 7) holder.title.setMaxLines(3);
            else holder.layout.performClick();
        });
        holder.layout.setOnClickListener(v -> {
            if (ent.isValid()) openGallery(ent);
        });
        holder.layout.setOnLongClickListener(v -> {
            holder.title.animate().alpha(holder.title.getAlpha() == 0f ? 1f : 0f).setDuration(100).start();
            holder.flag.animate().alpha(holder.flag.getAlpha() == 0f ? 1f : 0f).setDuration(100).start();
            holder.pages.animate().alpha(holder.pages.getAlpha() == 0f ? 1f : 0f).setDuration(100).start();
            return true;
        });
        holder.title.setBackgroundColor(statuses.computeIfAbsent(key, ignored -> ListAdapter.statusColor(activity, key)));
    }

    private void openGallery(GenericGallery ent) {
        if (ent instanceof EhSimpleGallery) {
            activity.startActivity(((EhSimpleGallery) ent).open(activity));
            return;
        }
        if (ent instanceof Gallery) {
            startGallery((Gallery) ent);
            return;
        }
        if (!loadingGalleryIds.add(ent.getId())) return;
        InspectorV3.galleryInspector(activity, ent.getId(), new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> results) {
                loadingGalleryIds.remove(ent.getId());
                if (results == null || results.size() != 1 || !(results.get(0) instanceof Gallery)) {
                    showLoadFailure();
                    return;
                }
                Gallery gallery = (Gallery) results.get(0);
                if (Queries.FavoriteTable.isFavorite(gallery.getId())) Queries.FavoriteTable.addFavorite(gallery);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    replaceGallery(gallery);
                    startGallery(gallery);
                });
            }

            @Override
            public void onFailure(Exception e) {
                loadingGalleryIds.remove(ent.getId());
                LogUtility.e("Unable to load favorite gallery detail", e);
                showLoadFailure();
            }
        }).start();
    }

    private void showLoadFailure() {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Toast.makeText(activity,
                R.string.unable_to_connect_to_the_site, Toast.LENGTH_SHORT).show();
        });
    }

    private void replaceGallery(Gallery gallery) {
        if (galleries == null) return;
        for (int i = 0; i < galleries.length; i++) {
            if (galleries[i] != null && rows.get(i).eh == null && galleries[i].getId() == gallery.getId()) {
                galleries[i] = gallery;
                notifyItemChanged(i);
                return;
            }
        }
    }

    private void startGallery(Gallery ent) {
        Intent intent = new Intent(activity, GalleryActivity.class);
        LogUtility.d(ent + "");
        intent.putExtra(activity.getPackageName() + ".GALLERY", ent);
        intent.putExtra(activity.getPackageName() + ".UNKNOWN", true);
        activity.startActivity(intent);
    }

    public void load(String query, boolean byTitle, int requestedPage) {
        int request = ++generation;
        int perPage = FavoriteActivity.getEntryPerPage();
        io.execute(() -> {
            if (request != generation) return;
            Cursor nh = null;
            try {
                List<EhDatabase.Entry> eh = EhDatabase.get(activity).favorites();
                String[] words = LocalTagFilterHelper.words(query);
                eh.removeIf(row -> !matches(row.title, words));
                if (byTitle) eh.sort(Comparator.comparing(row -> row.title));
                int total = Queries.FavoriteTable.countFavorite(query) + eh.size();
                int last = Math.max(1, (int) ((total + (long) perPage - 1) / perPage));
                int page = Math.max(1, Math.min(last, requestedPage));
                int offset = (page - 1) * perPage;
                List<Row> next;
                if (eh.isEmpty()) {
                    nh = Queries.FavoriteTable.getAllFavoriteGalleriesCursor(query, byTitle, perPage, offset);
                    next = new ArrayList<>(nh.getCount());
                    for (int i = 0; i < nh.getCount(); i++) next.add(new Row(i, null));
                } else {
                    nh = Queries.FavoriteTable.getAllFavoriteGalleriesCursor(query, byTitle, Integer.MAX_VALUE, 0);
                    next = mergePage(nh, eh, byTitle, offset, perPage);
                }
                Cursor loaded = nh;
                activity.runOnUiThread(() -> publish(request, loaded, next, page, last));
            } catch (RuntimeException e) {
                if (nh != null) nh.close();
                LogUtility.e("Unable to read local favorites", e);
                activity.runOnUiThread(() -> {
                    if (request == generation && !activity.isDestroyed() && !activity.isFinishing()) {
                        activity.onLocalFavoritesLoaded(1, 1);
                        Toast.makeText(activity, R.string.failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private static boolean matches(String title, String[] words) {
        String lower = title.toLowerCase(Locale.US);
        for (String word : words) if (!lower.contains(word)) return false;
        return true;
    }

    private static List<Row> mergePage(Cursor nh, List<EhDatabase.Entry> eh, boolean byTitle, int offset, int limit) {
        List<Row> result = new ArrayList<>();
        int time = nh.getColumnIndexOrThrow(Queries.FavoriteTable.TIME);
        boolean hasNh = nh.moveToFirst();
        int e = 0, position = 0;
        while ((hasNh || e < eh.size()) && result.size() < limit) {
            Row row;
            if (hasNh && (e == eh.size() || nhFirst(nh, time, eh.get(e), byTitle))) {
                row = new Row(nh.getPosition(), null);
                hasNh = nh.moveToNext();
            } else {
                row = new Row(-1, eh.get(e++));
            }
            if (position++ >= offset) result.add(row);
        }
        return result;
    }

    private static boolean nhFirst(Cursor nh, int timeColumn, EhDatabase.Entry eh, boolean byTitle) {
        if (!byTitle) return nh.getLong(timeColumn) >= eh.time;
        String title = Queries.FavoriteTable.sortTitle(nh);
        return title == null || title.compareTo(eh.title) <= 0;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void publish(int request, Cursor loaded, List<Row> next, int page, int last) {
        if (request != generation || activity.isFinishing() || activity.isDestroyed()) {
            loaded.close();
            return;
        }
        if (cursor != null) cursor.close();
        cursor = loaded;
        rows = next;
        galleries = new GenericGallery[rows.size()];
        statuses.clear();
        notifyDataSetChanged();
        activity.onLocalFavoritesLoaded(page, last);
    }

    public void cancelLoad() {
        generation++;
    }

    public void close() {
        cancelLoad();
        io.shutdown();
        if (cursor != null) cursor.close();
        cursor = null;
        rows = Collections.emptyList();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public List<GenericGallery> displayedRows() {
        List<GenericGallery> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            GenericGallery gallery = galleryFromPosition(i);
            if (gallery != null) result.add(gallery);
        }
        return result;
    }
}
