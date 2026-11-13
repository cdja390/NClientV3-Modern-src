package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EhUrlsTest {

    @Test
    public void findsGalleryLinksInSharedText() {
        EhGalleryKey k = EhGalleryKey.findIn("look https://e-hentai.org/g/4325674/08c0da7832/ nice");
        assertEquals(4325674L, k.gid);
        assertEquals("08c0da7832", k.token);
        assertFalse(k.exhentai);
        EhGalleryKey ex = EhGalleryKey.findIn("https://exhentai.org/g/123/ABCDEF0123/?p=2");
        assertTrue(ex.exhentai);
        assertEquals("abcdef0123", ex.token);
        assertNull(EhGalleryKey.findIn("https://e-hentai.org/g/123/"));
        assertNull(EhGalleryKey.findIn("https://nhentai.net/g/123/"));
        assertNull(EhGalleryKey.findIn(null));
    }

    @Test
    public void searchUrlEncodesAndFilters() {
        assertEquals("https://e-hentai.org/", EhUrls.searchUrl(false, null, 0, 0, null));
        assertEquals("https://e-hentai.org/?f_search=language%3Achinese+full+color",
            EhUrls.searchUrl(false, "language:chinese full color", 0, 0, null));
        // Show only Doujinshi (0x2) => hide every other category.
        assertEquals("https://exhentai.org/?f_cats=1021&f_search=x&next=4324782",
            EhUrls.searchUrl(true, "x", EhCategory.DOUJINSHI.bit, 0, "4324782"));
        assertEquals("https://e-hentai.org/?advsearch=1&f_srdd=4",
            EhUrls.searchUrl(false, "", EhCategory.ALL, 4, null));
    }

    @Test
    public void galleryAndFavoriteUrls() {
        EhGalleryKey k = new EhGalleryKey(1, "0123456789", false);
        assertEquals("https://e-hentai.org/g/1/0123456789/", EhUrls.galleryUrl(k, 0));
        assertEquals("https://e-hentai.org/g/1/0123456789/?p=3", EhUrls.galleryUrl(k, 3));
        assertEquals("https://e-hentai.org/gallerypopups.php?gid=1&t=0123456789&act=addfav", EhUrls.favoritePopupUrl(k));
        assertEquals("https://e-hentai.org/favorites.php?favcat=all", EhUrls.favoritesUrl(false, -1, null, null));
        assertEquals("https://exhentai.org/favorites.php?favcat=2&next=9-8", EhUrls.favoritesUrl(true, 2, "", "9-8"));
    }

    @Test
    public void hosts() {
        assertTrue(EhUrls.isEhHost("e-hentai.org"));
        assertTrue(EhUrls.isEhHost("forums.e-hentai.org"));
        assertTrue(EhUrls.isEhHost("EXHENTAI.ORG"));
        assertFalse(EhUrls.isEhHost("nhentai.net"));
        assertFalse(EhUrls.isEhHost("evil-e-hentai.org.example.com"));
    }

    @Test
    public void categoryMask() {
        assertEquals(0, EhCategory.excludeMaskForShown(0));
        assertEquals(0, EhCategory.excludeMaskForShown(EhCategory.ALL));
        assertEquals(EhCategory.ALL & ~0x6, EhCategory.excludeMaskForShown(0x6));
        assertEquals(EhCategory.NON_H, EhCategory.fromLabel("Non-H"));
        assertEquals(EhCategory.ARTIST_CG, EhCategory.fromCssClasses("cs ct4"));
    }
}
