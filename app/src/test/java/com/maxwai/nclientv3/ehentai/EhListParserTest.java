package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class EhListParserTest {

    private static void checkFirstTwo(EhListParser.Page page) {
        assertEquals(25, page.items.size());
        assertEquals("4324782", page.nextCursor);
        EhGalleryInfo first = page.items.get(0);
        assertEquals(4325690L, first.key.gid);
        assertEquals("7c4cfd2995", first.key.token);
        assertFalse(first.key.exhentai);
        assertEquals(EhCategory.MISC, first.category);
        assertEquals(328, first.pages);
        assertEquals(2.0f, first.rating, 0.001f);
        assertTrue(first.title.startsWith("[Jared999d] Monster Eater Origin 5"));
        assertNotNull(first.thumb);
        assertTrue(first.thumb, first.thumb.startsWith("https://ehgt.org/"));
        assertEquals("2026-11-13 04:15", first.posted);
        EhGalleryInfo second = page.items.get(1);
        assertEquals(4325674L, second.key.gid);
        assertEquals(EhCategory.DOUJINSHI, second.category);
        assertEquals(29, second.pages);
        assertEquals(2.5f, second.rating, 0.001f);
        // Lazy-loaded thumbnails use data-src, never the 1x1 placeholder.
        assertFalse(second.thumb.startsWith("data:"));
    }

    @Test
    public void compactMode() throws Exception {
        EhListParser.Page page = EhListParser.parse(Fixtures.load("list_compact.html"), false);
        checkFirstTwo(page);
        assertEquals("chinese", page.items.get(0).language);
        assertEquals("fXn1m8", page.items.get(0).uploader);
    }

    @Test
    public void minimalMode() throws Exception {
        checkFirstTwo(EhListParser.parse(Fixtures.load("list_minimal.html"), false));
    }

    @Test
    public void extendedMode() throws Exception {
        checkFirstTwo(EhListParser.parse(Fixtures.load("list_extended.html"), false));
    }

    @Test
    public void thumbnailMode() throws Exception {
        checkFirstTwo(EhListParser.parse(Fixtures.load("list_thumbnail.html"), false));
    }

    @Test
    public void lastPageHasNoCursor() throws Exception {
        EhListParser.Page page = EhListParser.parse(
            "<table class=\"ptt\"><tr><td>1</td></tr></table><table class=\"itg gltc\"></table>"
                + "<span id=\"unext\">Next &gt;</span>", false);
        assertNull(page.nextCursor);
        assertTrue(page.noHits);
    }

    @Test
    public void favoritesCursorKeepsTimestamp() throws Exception {
        EhListParser.Page page = EhListParser.parse(
            "<a id=\"unext\" href=\"https://e-hentai.org/favorites.php?favcat=all&amp;next=2961893-1718000000\">&gt;</a>", false);
        assertEquals("2961893-1718000000", page.nextCursor);
    }

    @Test
    public void bannedPageThrows() {
        try {
            EhListParser.parse("Your IP address has been temporarily banned for excessive pageloads. The ban expires in 59 minutes", false);
            fail();
        } catch (EhException e) {
            assertEquals(EhException.Kind.IP_BANNED, e.kind);
        }
    }

    @Test
    public void ratingSprite() {
        assertEquals(5f, EhListParser.ratingFromStyle("background-position:0px -1px;opacity:1"), 0.001f);
        assertEquals(4.5f, EhListParser.ratingFromStyle("background-position:0px -21px"), 0.001f);
        assertEquals(0.5f, EhListParser.ratingFromStyle("background-position:-64px -21px"), 0.001f);
        assertEquals(-1f, EhListParser.ratingFromStyle("opacity:1"), 0.001f);
    }

    @Test
    public void favoriteBorderColours() {
        assertEquals(0, EhListParser.favoriteSlotFromStyle("border-color:#000;background-color:rgba(0,0,0,.1)"));
        assertEquals(3, EhListParser.favoriteSlotFromStyle("border-color:#dd0"));
        assertEquals(9, EhListParser.favoriteSlotFromStyle("border-color:#ee88ee"));
        assertEquals(-1, EhListParser.favoriteSlotFromStyle(""));
    }
}
