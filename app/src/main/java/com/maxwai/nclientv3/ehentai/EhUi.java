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

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.GlideX;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** Small UI helpers shared by the e-hentai screens. */
public final class EhUi {
    private EhUi() {
    }

    @NonNull
    public static String categoryLabel(@NonNull Context c, @NonNull EhCategory cat) {
        switch (cat) {
            case MISC: return c.getString(R.string.eh_cat_misc);
            case DOUJINSHI: return c.getString(R.string.eh_cat_doujinshi);
            case MANGA: return c.getString(R.string.eh_cat_manga);
            case ARTIST_CG: return c.getString(R.string.eh_cat_artist_cg);
            case GAME_CG: return c.getString(R.string.eh_cat_game_cg);
            case IMAGE_SET: return c.getString(R.string.eh_cat_image_set);
            case COSPLAY: return c.getString(R.string.eh_cat_cosplay);
            case ASIAN_PORN: return c.getString(R.string.eh_cat_asian_porn);
            case NON_H: return c.getString(R.string.eh_cat_non_h);
            case WESTERN: return c.getString(R.string.eh_cat_western);
            default: return c.getString(R.string.eh_cat_unknown);
        }
    }

    public static void bindCategory(@NonNull TextView chip, @NonNull EhCategory cat) {
        chip.setText(categoryLabel(chip.getContext(), cat));
        chip.setBackgroundTintList(ColorStateList.valueOf(cat.color));
    }

    @NonNull
    public static String describe(@NonNull Context c, @Nullable Throwable t) {
        if (t instanceof EhException) {
            EhException e = (EhException) t;
            switch (e.kind) {
                case QUOTA_EXCEEDED: return c.getString(R.string.eh_error_quota);
                case IP_BANNED: return c.getString(R.string.eh_error_banned);
                case SAD_PANDA: return c.getString(R.string.eh_error_sad_panda);
                case RATE_LIMITED: return c.getString(R.string.eh_error_rate_limited);
                case GALLERY_GONE: return c.getString(R.string.eh_error_gone);
                case PARSE: return c.getString(R.string.eh_error_parse);
                default: return c.getString(R.string.eh_error_http, e.httpCode);
            }
        }
        if (t instanceof UnknownHostException || t instanceof SocketTimeoutException)
            return c.getString(R.string.eh_error_network);
        if (t instanceof InterruptedIOException) return c.getString(R.string.eh_error_network);
        if (t instanceof IOException) return c.getString(R.string.eh_error_network);
        return t == null || t.getMessage() == null ? c.getString(R.string.eh_error_parse) : t.getMessage();
    }

    /** Glide model for an e-hentai image URL (adds cookies for exhentai thumbnails). */
    @NonNull
    public static Object model(@NonNull Context c, @NonNull String url) {
        String cookie = EhClient.cookieFor(c, url);
        LazyHeaders.Builder h = new LazyHeaders.Builder().addHeader("User-Agent", EhClient.userAgent(c));
        if (cookie != null) h.addHeader("Cookie", cookie);
        return new GlideUrl(url, h.build());
    }

    public static void loadThumb(@NonNull ImageView view, @Nullable String url) {
        RequestManager glide = GlideX.with(view);
        if (glide == null) return;
        if (url == null) {
            glide.clear(view);
            view.setImageDrawable(null);
            return;
        }
        glide.load(model(view.getContext(), url)).into(view);
    }

    @NonNull
    public static String rating(float rating) {
        return rating < 0 ? "" : String.format(Locale.US, "★ %.1f", rating);
    }
}
