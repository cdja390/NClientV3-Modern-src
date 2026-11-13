package com.maxwai.nclientv3.source;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.EnumSet;

public class FavoriteSourcesTest {
    @Test
    public void savedAvailableSourceWinsEvenWhenLoggedOut() {
        assertEquals(GallerySource.EHENTAI, FavoriteSources.choose(
            EnumSet.of(GallerySource.NHENTAI, GallerySource.EHENTAI), "EHENTAI", source -> source == GallerySource.NHENTAI));
    }

    @Test
    public void unavailableSourceFallsBackToLoggedInThenFirst() {
        EnumSet<GallerySource> available = EnumSet.of(GallerySource.NHENTAI, GallerySource.EXHENTAI);
        assertEquals(GallerySource.EXHENTAI, FavoriteSources.choose(available, "EHENTAI", source -> source == GallerySource.EXHENTAI));
        assertEquals(GallerySource.NHENTAI, FavoriteSources.choose(available, "", source -> false));
    }
}
