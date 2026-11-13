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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.R;

import java.util.ArrayList;
import java.util.List;

/** Gallery cards for the e-hentai lists. */
class EhListAdapter extends RecyclerView.Adapter<EhListAdapter.Holder> {
    interface Listener {
        void onOpen(@NonNull EhGalleryInfo info);

        /** Long press; return true when handled. */
        boolean onLongPress(@NonNull EhGalleryInfo info);
    }

    private final List<EhGalleryInfo> items = new ArrayList<>();
    private final Listener listener;
    @Nullable
    private ExtraText extra;

    interface ExtraText {
        @Nullable
        String extraFor(@NonNull EhGalleryInfo info);
    }

    EhListAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void setExtraText(@Nullable ExtraText extra) {
        this.extra = extra;
    }

    void setItems(@NonNull List<? extends EhGalleryInfo> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    void addItems(@NonNull List<? extends EhGalleryInfo> list) {
        int start = items.size();
        for (EhGalleryInfo g : list) {
            boolean dup = false;
            for (EhGalleryInfo old : items) {
                if (old.key.gid == g.key.gid) {
                    dup = true;
                    break;
                }
            }
            if (!dup) items.add(g);
        }
        notifyItemRangeInserted(start, items.size() - start);
    }

    void remove(@NonNull EhGalleryInfo info) {
        int i = items.indexOf(info);
        if (i >= 0) {
            items.remove(i);
            notifyItemRemoved(i);
        }
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.eh_item_gallery, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        EhGalleryInfo g = items.get(position);
        h.title.setText(g.title);
        EhUi.bindCategory(h.category, g.category);
        h.rating.setText(EhUi.rating(g.rating));
        StringBuilder meta = new StringBuilder();
        if (g.pages > 0) meta.append(h.itemView.getContext().getString(R.string.eh_pages_count, g.pages));
        if (g.language != null) append(meta, g.language);
        if (g.posted != null) append(meta, g.posted);
        if (g.uploader != null) append(meta, g.uploader);
        String more = extra == null ? null : extra.extraFor(g);
        if (more != null) meta.append('\n').append(more);
        h.meta.setText(meta);
        EhUi.loadThumb(h.thumb, g.thumb);
        h.itemView.setOnClickListener(v -> listener.onOpen(g));
        h.itemView.setOnLongClickListener(v -> listener.onLongPress(g));
    }

    private static void append(StringBuilder b, String s) {
        if (b.length() > 0) b.append(" · ");
        b.append(s);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, category, rating, meta;

        Holder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.eh_thumb);
            title = v.findViewById(R.id.eh_title);
            category = v.findViewById(R.id.eh_category);
            rating = v.findViewById(R.id.eh_rating);
            meta = v.findViewById(R.id.eh_meta);
        }
    }
}
