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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads lists, galleries and single pages, with small in-memory caches so that reading a
 * gallery does not re-request preview pages it already has. Blocking; call off the UI thread.
 */
public final class EhRepository {
    private static final int MAX_GALLERIES = 8;
    /** "gid:previewPage" -> parsed page; gid -> previews per page and page count. */
    private static final Map<String, EhGalleryDetail> PREVIEW_PAGES = lru(48);
    private static final Map<Long, int[]> SHAPE = lru(MAX_GALLERIES * 4);
    /** "gid:index" -> resolved viewer result. */
    private static final Map<String, EhPageParser.Result> IMAGES = lru(400);

    private EhRepository() {
    }

    private static <K, V> Map<K, V> lru(final int max) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }

    @NonNull
    public static EhListParser.Page loadList(@NonNull Context context, @NonNull String url) throws IOException {
        boolean ex = url.contains("exhentai.org");
        return EhListParser.parse(EhClient.getHtml(context, url), ex);
    }

    /** Gallery page {@code previewPage} (0-based), cached. */
    @NonNull
    public static EhGalleryDetail loadGallery(@NonNull Context context, @NonNull EhGalleryKey key, int previewPage,
                                              boolean refresh) throws IOException {
        String cacheKey = key.gid + ":" + previewPage;
        if (!refresh) {
            synchronized (PREVIEW_PAGES) {
                EhGalleryDetail cached = PREVIEW_PAGES.get(cacheKey);
                if (cached != null) return cached;
            }
        }
        EhGalleryDetail d = EhGalleryParser.parse(EhClient.getHtml(context, EhUrls.galleryUrl(key, previewPage)), key);
        synchronized (PREVIEW_PAGES) {
            PREVIEW_PAGES.put(cacheKey, d);
            if (previewPage == 0 || !SHAPE.containsKey(key.gid))
                SHAPE.put(key.gid, new int[]{previewPage == 0 ? d.previewsPerPage() : guessPerPage(d, previewPage), d.pages});
        }
        return d;
    }

    private static int guessPerPage(EhGalleryDetail d, int previewPage) {
        if (d.previews.isEmpty() || previewPage == 0) return d.previewsPerPage();
        int firstIndex = d.previews.get(0).index;
        int per = (firstIndex - 1) / previewPage;
        return per > 0 ? per : d.previewsPerPage();
    }

    /** Viewer page URL for 1-based {@code index}, loading the needed preview page if necessary. */
    @NonNull
    public static String pageUrl(@NonNull Context context, @NonNull EhGalleryKey key, int index) throws IOException {
        int perPage;
        synchronized (PREVIEW_PAGES) {
            int[] shape = SHAPE.get(key.gid);
            perPage = shape == null ? -1 : shape[0];
        }
        if (perPage <= 0) perPage = loadGallery(context, key, 0, false).previewsPerPage();
        int previewPage = (index - 1) / perPage;
        EhGalleryDetail d = loadGallery(context, key, previewPage, false);
        String url = find(d.previews, index);
        if (url != null) return url;
        // The guess was off (account with a different preview size): walk from page 0 shape.
        perPage = loadGallery(context, key, 0, false).previewsPerPage();
        d = loadGallery(context, key, (index - 1) / perPage, false);
        url = find(d.previews, index);
        if (url == null) throw new EhException(EhException.Kind.PARSE, "page " + index + " not found");
        return url;
    }

    @Nullable
    private static String find(List<EhGalleryDetail.EhPreview> previews, int index) {
        for (EhGalleryDetail.EhPreview p : previews) if (p.index == index) return p.pageUrl;
        return null;
    }

    /**
     * Resolves the image of page {@code index}. With {@code reload} the cached result is skipped
     * and the server's reload key is used, which picks a different image server.
     */
    @NonNull
    public static EhPageParser.Result resolveImage(@NonNull Context context, @NonNull EhGalleryKey key, int index,
                                                   boolean reload) throws IOException {
        String cacheKey = key.gid + ":" + index;
        EhPageParser.Result previous;
        synchronized (IMAGES) {
            previous = IMAGES.get(cacheKey);
            if (!reload && previous != null) return previous;
        }
        String pageUrl = pageUrl(context, key, index);
        if (reload && previous != null && previous.reloadKey != null)
            pageUrl = EhUrls.withReloadKey(pageUrl, previous.reloadKey);
        EhPageParser.Result r = EhPageParser.parse(EhClient.getHtml(context, pageUrl));
        synchronized (IMAGES) {
            IMAGES.put(cacheKey, r);
        }
        return r;
    }

    public static void clearCaches() {
        synchronized (PREVIEW_PAGES) {
            PREVIEW_PAGES.clear();
            SHAPE.clear();
        }
        synchronized (IMAGES) {
            IMAGES.clear();
        }
    }

    public static void forget(@NonNull EhGalleryKey key) {
        synchronized (PREVIEW_PAGES) {
            PREVIEW_PAGES.keySet().removeIf(k -> k.startsWith(key.gid + ":"));
            SHAPE.remove(key.gid);
        }
    }

    /** One favorites.php page together with the account's folder names. */
    public static final class FavoritesPage {
        @NonNull
        public final EhListParser.Page page;
        @NonNull
        public final String[] folders;

        FavoritesPage(@NonNull EhListParser.Page page, @NonNull String[] folders) {
            this.page = page;
            this.folders = folders;
        }
    }

    @NonNull
    public static FavoritesPage loadFavorites(@NonNull Context context, @NonNull String url) throws IOException {
        String html = EhClient.getHtml(context, url);
        return new FavoritesPage(EhListParser.parse(html, url.contains("exhentai.org")), EhFavoritesParser.folderNames(html));
    }

    /** Folder names offered by the add-to-favorites popup and whether the gallery is in one. */
    public static final class FavoriteState {
        @NonNull
        public final String[] folders;
        public final boolean favorited;

        FavoriteState(@NonNull String[] folders, boolean favorited) {
            this.folders = folders;
            this.favorited = favorited;
        }
    }

    @NonNull
    public static FavoriteState loadFavoriteState(@NonNull Context context, @NonNull EhGalleryKey key) throws IOException {
        String html = EhClient.getHtml(context, EhUrls.favoritePopupUrl(key));
        return new FavoriteState(EhFavoritesParser.folderNames(html), EhFavoritesParser.popupHasFavorite(html));
    }

    /** Puts the gallery in folder {@code slot} (0-9), or removes it from favorites when slot < 0. */
    public static void setOnlineFavorite(@NonNull Context context, @NonNull EhGalleryKey key, int slot,
                                         boolean alreadyFavorited) throws IOException {
        okhttp3.FormBody body = new okhttp3.FormBody.Builder()
            .add("favcat", slot < 0 ? "favdel" : Integer.toString(slot))
            .add("favnote", "")
            .add("apply", alreadyFavorited ? "Apply Changes" : "Add to Favorites")
            .add("update", "1")
            .build();
        EhClient.postForm(context, EhUrls.favoritePopupUrl(key), body);
        forget(key);
    }
}
