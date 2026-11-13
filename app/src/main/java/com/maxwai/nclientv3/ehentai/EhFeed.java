/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (merged source feed design)
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
 * Added for NClientV3 Modern 4.7.1. The merge follows InspectorV3.mergeAcross, EHentaiPager and
 * EHentaiSource.buildSearchQuery from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0), rewritten on top of this app's
 * e-hentai client (EhRepository / EhListParser). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.source.GalleryIdentity;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main-list source selection and the e-hentai half of the merged nhentai + e-hentai feed.
 * <p>
 * Like yosefario-dev/NClientV3: page N of the main list (home, search, tag) asks every enabled
 * source for its page N and interleaves the results round-robin (nh, eh, nh, eh, ...), dropping
 * duplicates; the page count is the larger of the two. e-hentai pages by cursor (next=gid), so
 * page N is reached by walking the cursor chain from page 1, at most {@link #MAX_WALK} requests.
 */
public final class EhFeed {
    /** Settings key (SharedPreferences "Settings"); values {@link #MODE_NH}, {@link #MODE_EH}, {@link #MODE_BOTH}. */
    public static final String KEY_SOURCES = "gallery_sources";
    public static final String MODE_NH = "nh";
    public static final String MODE_EH = "eh";
    public static final String MODE_BOTH = "both";
    static final int MAX_WALK = 8;
    /** Query nhentai's "popular" home page is rewritten to (InspectorV3.tryByAllPopular). */
    private static final String NH_POPULAR_PLACEHOLDER = "-nclientv3";

    public enum Mode {NH, EH, BOTH}

    private EhFeed() {
    }

    @NonNull
    public static Mode mode(@Nullable Context context) {
        if (context == null) return Mode.NH;
        SharedPreferences p = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        return parseMode(p.getString(KEY_SOURCES, MODE_NH));
    }

    @NonNull
    static Mode parseMode(@Nullable String value) {
        if (MODE_EH.equals(value)) return Mode.EH;
        if (MODE_BOTH.equals(value)) return Mode.BOTH;
        return Mode.NH;
    }

    /** Only the browsable lists are merged; favorites, random and single galleries stay nhentai. */
    public static boolean isMergeable(@Nullable ApiRequestType type) {
        return type == ApiRequestType.BYALL || type == ApiRequestType.BYSEARCH || type == ApiRequestType.BYTAG;
    }

    @NonNull
    public static Mode modeFor(@Nullable Context context, @Nullable ApiRequestType type) {
        return isMergeable(type) ? mode(context) : Mode.NH;
    }

    // ---- query ----

    /** e-hentai namespace for an nhentai tag type; "" = no namespace, null = not searchable. */
    @Nullable
    static String namespace(@Nullable TagType type) {
        if (type == null) return null;
        if (type.equals(TagType.PARODY)) return "parody";
        if (type.equals(TagType.CHARACTER)) return "character";
        if (type.equals(TagType.ARTIST)) return "artist";
        if (type.equals(TagType.GROUP)) return "group";
        if (type.equals(TagType.LANGUAGE)) return "language";
        if (type.equals(TagType.TAG)) return "";
        return null;
    }

    /** {@code ns:"name$"}, prefixed with '-' for an avoided tag; null when it cannot be expressed. */
    @Nullable
    static String tagTerm(@Nullable String name, @Nullable String namespace, boolean avoided) {
        if (name == null || namespace == null) return null;
        String value = name.trim();
        if (value.isEmpty()) return null;
        String core = (namespace.isEmpty() ? "" : namespace + ":") + "\"" + value + "$\"";
        return avoided ? "-" + core : core;
    }

    /** Free text and all distinct tag terms, matching yosefario buildSearchQuery. */
    @NonNull
    public static String buildQuery(@Nullable String query, @Nullable Collection<Tag> tags) {
        StringBuilder b = new StringBuilder();
        if (query != null && !NH_POPULAR_PLACEHOLDER.equals(query.trim())) append(b, query);
        if (tags != null) {
            for (Tag tag : tags) {
                if (tag == null) continue;
                String term = tagTerm(tag.getName(), namespace(tag.getType()), tag.getStatus() == TagStatus.AVOIDED);
                if (term != null && b.indexOf(term) < 0) {
                    append(b, term);
                }
            }
        }
        return b.toString().trim();
    }

    private static void append(StringBuilder b, @Nullable String term) {
        if (term == null || term.trim().isEmpty()) return;
        if (b.length() > 0) b.append(' ');
        b.append(term.trim());
    }

    // ---- paging ----

    /** One parsed e-hentai list page; in tests a fake stands in for the network. */
    interface Fetcher {
        @NonNull
        EhListParser.Page fetch(@NonNull String url) throws IOException;
    }

    interface UrlBuilder {
        @NonNull
        String url(@Nullable String cursor);
    }

    public static final class Result {
        @NonNull
        public final List<EhGalleryInfo> items;
        public final boolean hasNext;

        Result(@NonNull List<EhGalleryInfo> items, boolean hasNext) {
            this.items = items;
            this.hasNext = hasNext;
        }

        /** Page count to report for 1-based {@code page}: one more while e-hentai has a next page. */
        public int pageCount(int page) {
            int p = Math.max(1, page);
            return !items.isEmpty() && hasNext ? p + 1 : p;
        }
    }

    /** Page {@code page} (1-based) of the list whose first page is {@code urls.url(null)}. */
    @NonNull
    static Result page(@NonNull UrlBuilder urls, int page, @NonNull Fetcher fetcher) throws IOException {
        return EhPager.page(urls, page, fetcher);
    }

    public static void resetChains() {
        EhPager.reset();
    }

    /** CATEGORY tags override global categories, including an unrecognized explicit category. */
    static int shownCategories(@Nullable Collection<Tag> tags, int globalShown) {
        int shown = 0;
        boolean specified = false;
        if (tags != null) {
            for (Tag tag : tags) {
                if (tag != null && TagType.CATEGORY.equals(tag.getType())) {
                    specified = true;
                    shown |= tag.getName() != null && "private".equalsIgnoreCase(tag.getName().trim())
                        ? EhCategory.REQUEST_PRIVATE : EhCategory.fromLabel(tag.getName()).bit;
                }
            }
        }
        return specified ? shown : globalShown;
    }

    static String searchUrl(boolean ex, @Nullable String query, @Nullable Collection<Tag> tags,
                            @Nullable Ranges ranges, int globalShown, int globalRating, @Nullable String cursor) {
        int rating = ranges != null && ranges.getMinRating() != Ranges.UNDEFINED
            ? ranges.getMinRating() : globalRating;
        int minPages = ranges == null ? Ranges.UNDEFINED : ranges.getFromPage();
        int maxPages = ranges == null ? Ranges.UNDEFINED : ranges.getToPage();
        return EhUrls.searchUrl(ex, buildQuery(query, tags), shownCategories(tags, globalShown),
            rating, minPages, maxPages, cursor);
    }

    /** Loads page {@code page} of the e-hentai list matching the main list's query and tags. */
    @NonNull
    public static Result load(@NonNull Context context, @Nullable String query, @Nullable Collection<Tag> tags,
                              int page) throws IOException {
        return load(context, query, tags, null, page);
    }

    @NonNull
    public static Result load(@NonNull Context context, @Nullable String query, @Nullable Collection<Tag> tags,
                              @Nullable Ranges ranges, int page) throws IOException {
        final boolean ex = EhSettings.useExhentai(context);
        final String search = buildQuery(query, tags);
        final int shown = EhSettings.shownCategories(context);
        final int minRating = EhSettings.minRating(context);
        final Context app = context.getApplicationContext();
        Result r = page(cursor -> searchUrl(ex, query, tags, ranges, shown, minRating, cursor), page,
            url -> EhRepository.loadList(app, url));
        LogUtility.d("EhFeed: page " + page + " '" + search + "' -> " + r.items.size() + " galleries");
        return r;
    }

    // ---- merge ----

    /** Round-robin interleave (a0, b0, a1, b1, ...) keeping the tail of the longer list. */
    @NonNull
    public static <T> List<T> interleave(@Nullable List<? extends T> a, @Nullable List<? extends T> b) {
        int na = a == null ? 0 : a.size();
        int nb = b == null ? 0 : b.size();
        List<T> out = new ArrayList<>(na + nb);
        for (int i = 0; i < Math.max(na, nb); i++) {
            if (i < na) out.add(a.get(i));
            if (i < nb) out.add(b.get(i));
        }
        return out;
    }

    /** The merged page: nhentai items and e-hentai items interleaved, duplicates dropped. */
    @NonNull
    public static ArrayList<GenericGallery> merge(@Nullable List<GenericGallery> nh, @NonNull List<EhGalleryInfo> eh) {
        List<GenericGallery> ehItems = new ArrayList<>(eh.size());
        for (EhGalleryInfo info : eh) ehItems.add(new EhSimpleGallery(info));
        List<GenericGallery> mixed = interleave(nh, ehItems);
        ArrayList<GenericGallery> out = new ArrayList<>(mixed.size());
        Set<String> seen = new HashSet<>();
        for (GenericGallery g : mixed) {
            if (g == null) continue;
            String id = GalleryIdentity.of(g).stableKey();
            if (seen.add(id)) out.add(g);
        }
        return out;
    }

    @NonNull
    public static ArrayList<GenericGallery> wrap(@NonNull List<EhGalleryInfo> eh) {
        return merge(null, eh);
    }
}
