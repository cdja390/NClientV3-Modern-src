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

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Cuts one thumbnail out of an e-hentai preview sprite sheet. */
public final class EhSpriteCrop extends BitmapTransformation {
    private static final String ID = "com.maxwai.nclientv3.ehentai.EhSpriteCrop";
    private final int x, y, w, h;

    public EhSpriteCrop(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap src, int outWidth, int outHeight) {
        // The sheet may be served at a different scale than the CSS pixels in the page.
        float scale = src.getHeight() < y + h && y + h > 0 ? src.getHeight() / (float) (y + h) : 1f;
        int left = Math.round(x * scale), top = Math.round(y * scale);
        int width = Math.round(w * scale), height = Math.round(h * scale);
        if (left >= src.getWidth() || top >= src.getHeight()) return src;
        width = Math.max(1, Math.min(width, src.getWidth() - left));
        height = Math.max(1, Math.min(height, src.getHeight() - top));
        return Bitmap.createBitmap(src, left, top, width, height);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EhSpriteCrop)) return false;
        EhSpriteCrop c = (EhSpriteCrop) o;
        return c.x == x && c.y == y && c.w == w && c.h == h;
    }

    @Override
    public int hashCode() {
        return ID.hashCode() + 31 * (x * 7919 + y * 104729 + w * 31 + h);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID.getBytes(StandardCharsets.UTF_8));
        messageDigest.update(ByteBuffer.allocate(16).putInt(x).putInt(y).putInt(w).putInt(h).array());
    }
}
