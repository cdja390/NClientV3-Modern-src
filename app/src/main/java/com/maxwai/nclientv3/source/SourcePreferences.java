/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2; settings backup already exports all Settings keys. */
package com.maxwai.nclientv3.source;

import android.content.Context;
import android.content.SharedPreferences;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.ehentai.EhFeed;
import com.maxwai.nclientv3.ehentai.EhRepository;
import com.maxwai.nclientv3.ehentai.EhSettings;
import com.maxwai.nclientv3.settings.Global;

import java.util.Collection;
import java.util.EnumSet;

public final class SourcePreferences {
    public static final String ENABLED = "enabled_sources";
    public static final String FAVORITE = "favorite_source";
    private static final String EH_BROWSE_POPULAR = "eh_browse_popular";

    private SourcePreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
    }

    public static SortType browseSort(Context context, Collection<GallerySource> sources) {
        if (SourceCapabilities.isNhOnly(sources)) return Global.getSortType();
        return prefs(context).getBoolean(EH_BROWSE_POPULAR, false)
            ? SortType.POPULAR_DAILY : SortType.RECENT_ALL_TIME;
    }

    public static void saveEhBrowseSort(Context context, SortType sort) {
        prefs(context).edit().putBoolean(EH_BROWSE_POPULAR, sort == SortType.POPULAR_DAILY).apply();
    }

    public static synchronized EnumSet<GallerySource> enabled(Context context) {
        if (context == null) return EnumSet.of(GallerySource.NHENTAI);
        SharedPreferences settings = prefs(context);
        String configured = settings.getString(ENABLED, null);
        boolean wantsEx = EhSettings.wantsExhentai(context);
        EnumSet<GallerySource> sources = SourcePolicy.migrate(configured,
            settings.getString("gallery_sources", null),
            settings.getString(context.getString(R.string.preference_key_site_mirror), ""),
            wantsEx);
        String normalized = SourcePolicy.serialize(sources);
        if (!normalized.equals(configured)) {
            settings.edit().putString(ENABLED, normalized).apply();
            if (configured != null) resetCaches();
        }
        boolean ex = sources.contains(GallerySource.EXHENTAI);
        if ((ex || sources.contains(GallerySource.EHENTAI)) && ex != wantsEx) {
            EhSettings.setUseExhentai(context, ex);
            remapFavorite(settings, ex);
            resetCaches();
        }
        return sources;
    }

    /** UI cannot save an empty selection. Existing imported empty state remains recoverable. */
    public static synchronized boolean save(Context context, Collection<GallerySource> sources) {
        if (sources.isEmpty()) return false;
        boolean ex = ehSource(context) == GallerySource.EXHENTAI;
        prefs(context).edit().putString(ENABLED, SourcePolicy.serialize(SourcePolicy.withEhHost(sources, ex))).apply();
        resetCaches();
        return true;
    }

    public static synchronized GallerySource ehSource(Context context) {
        enabled(context);
        return EhSettings.wantsExhentai(context) ? GallerySource.EXHENTAI : GallerySource.EHENTAI;
    }

    public static synchronized void setEhHost(Context context, boolean ex) {
        EnumSet<GallerySource> sources = enabled(context);
        SharedPreferences settings = prefs(context);
        EhSettings.setUseExhentai(context, ex);
        settings.edit().putString(ENABLED, SourcePolicy.serialize(SourcePolicy.withEhHost(sources, ex))).apply();
        remapFavorite(settings, ex);
        resetCaches();
    }

    private static void remapFavorite(SharedPreferences settings, boolean ex) {
        String favorite = settings.getString(FAVORITE, null);
        if (GallerySource.EHENTAI.name().equals(favorite) || GallerySource.EXHENTAI.name().equals(favorite)) {
            settings.edit().putString(FAVORITE, (ex ? GallerySource.EXHENTAI : GallerySource.EHENTAI).name()).apply();
        }
    }

    public static void resetCaches() {
        EhFeed.resetChains();
        EhRepository.clearCaches();
    }
}
