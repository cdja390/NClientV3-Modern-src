/* Copyright 2026 yosefario-dev and NClientV3 contributors (SourceManager).
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Adapted from b2ee7ed. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import android.content.Context;

import com.maxwai.nclientv3.ehentai.EhAuth;
import com.maxwai.nclientv3.settings.Login;

import java.util.EnumSet;
import java.util.function.Predicate;

public final class FavoriteSources {
    private FavoriteSources() {
    }

    public static EnumSet<GallerySource> available(Context context) {
        EnumSet<GallerySource> enabled = SourcePreferences.enabled(context);
        return enabled.isEmpty() ? EnumSet.of(GallerySource.NHENTAI) : enabled;
    }

    public static GallerySource selected(Context context) {
        String preferred = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .getString(SourcePreferences.FAVORITE, "");
        return choose(available(context), preferred, source -> isLoggedIn(context, source));
    }

    public static void select(Context context, GallerySource source) {
        if (available(context).contains(source)) {
            context.getSharedPreferences("Settings", Context.MODE_PRIVATE).edit()
                .putString(SourcePreferences.FAVORITE, source.name()).apply();
        }
    }

    public static boolean isLoggedIn(Context context, GallerySource source) {
        switch (source) {
            case NHENTAI: return Login.canAccessAuthenticatedApi(context);
            case EXHENTAI: return EhAuth.hasExhentaiAccess(context);
            default: return EhAuth.isLoggedIn(context);
        }
    }

    static GallerySource choose(EnumSet<GallerySource> enabled, String preferred, Predicate<GallerySource> loggedIn) {
        for (GallerySource source : enabled) if (source.name().equals(preferred)) return source;
        for (GallerySource source : enabled) if (loggedIn.test(source)) return source;
        return enabled.isEmpty() ? GallerySource.NHENTAI : enabled.iterator().next();
    }
}
