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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.GlideX;

import java.util.ArrayList;
import java.util.List;

/** Preview thumbnails on the gallery page (sprite sheets are cropped per thumbnail). */
class EhPreviewAdapter extends RecyclerView.Adapter<EhPreviewAdapter.Holder> {
    interface Listener {
        void onPreview(int index);
    }

    private final List<EhGalleryDetail.EhPreview> items = new ArrayList<>();
    private final Listener listener;

    EhPreviewAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void add(@NonNull List<EhGalleryDetail.EhPreview> list) {
        int start = items.size();
        for (EhGalleryDetail.EhPreview p : list) {
            boolean dup = false;
            for (EhGalleryDetail.EhPreview old : items) if (old.index == p.index) dup = true;
            if (!dup) items.add(p);
        }
        notifyItemRangeInserted(start, items.size() - start);
    }

    void clear() {
        int n = items.size();
        items.clear();
        notifyItemRangeRemoved(0, n);
    }

    int count() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.eh_item_preview, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        EhGalleryDetail.EhPreview p = items.get(position);
        h.number.setText(String.valueOf(p.index));
        h.itemView.setOnClickListener(v -> listener.onPreview(p.index));
        RequestManager glide = GlideX.with(h.image);
        if (glide == null) return;
        if (p.imageUrl == null) {
            glide.clear(h.image);
            return;
        }
        Object model = EhUi.model(h.image.getContext(), p.imageUrl);
        if (p.isSprite()) glide.load(model).transform(new EhSpriteCrop(p.offsetX, p.offsetY, p.width, p.height)).into(h.image);
        else glide.load(model).into(h.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView number;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.eh_preview_image);
            number = v.findViewById(R.id.eh_preview_number);
        }
    }
}
