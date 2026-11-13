/* Copyright 2026 yosefario-dev and NClientV3 contributors (source preferences).
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Adapted from b2ee7ed for legacy migration and user-approved defaults in 4.7.2. */
package com.maxwai.nclientv3.source;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.StringJoiner;

public final class SourcePolicy {
    private SourcePolicy() {
    }

    public static EnumSet<GallerySource> parse(String value) {
        EnumSet<GallerySource> result = EnumSet.noneOf(GallerySource.class);
        if (value == null) return result;
        for (String name : value.split(",")) {
            try {
                result.add(GallerySource.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // Unknown names from a newer installation do not invent a source.
            }
        }
        return result;
    }

    public static String serialize(Collection<GallerySource> sources) {
        StringJoiner joined = new StringJoiner(",");
        for (GallerySource source : GallerySource.values()) {
            if (sources.contains(source)) joined.add(source.name());
        }
        return joined.toString();
    }

    public static EnumSet<GallerySource> migrate(String configured, String legacy, String mirror, boolean ex) {
        if (configured != null) {
            EnumSet<GallerySource> sources = parse(configured);
            if (sources.contains(GallerySource.EHENTAI) && sources.contains(GallerySource.EXHENTAI)) {
                return withEhHost(sources, ex);
            }
            return sources;
        }
        GallerySource eh = ex ? GallerySource.EXHENTAI : GallerySource.EHENTAI;
        if (legacy != null) {
            if ("eh".equals(legacy)) return EnumSet.of(eh);
            if ("both".equals(legacy)) return EnumSet.of(GallerySource.NHENTAI, eh);
            return EnumSet.of(GallerySource.NHENTAI);
        }
        String host = mirror == null ? "" : mirror.toLowerCase(Locale.ROOT);
        if (host.contains("exhentai")) return EnumSet.of(GallerySource.EXHENTAI);
        if (host.contains("e-hentai") || host.contains("ehentai")) return EnumSet.of(GallerySource.EHENTAI);
        return EnumSet.of(GallerySource.NHENTAI); // User-approved default, including empty mirror.
    }

    public static EnumSet<GallerySource> withEhHost(Collection<GallerySource> sources, boolean ex) {
        EnumSet<GallerySource> result = EnumSet.noneOf(GallerySource.class);
        if (sources.contains(GallerySource.NHENTAI)) result.add(GallerySource.NHENTAI);
        if (sources.contains(GallerySource.EHENTAI) || sources.contains(GallerySource.EXHENTAI)) {
            result.add(ex ? GallerySource.EXHENTAI : GallerySource.EHENTAI);
        }
        return result;
    }
}
