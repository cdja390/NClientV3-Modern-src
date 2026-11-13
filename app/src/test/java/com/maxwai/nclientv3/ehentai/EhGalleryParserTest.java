package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

public class EhGalleryParserTest {

    @Test
    public void parsesMetadataTagsAndSprites() throws Exception {
        EhGalleryKey key = new EhGalleryKey(4325674, "08c0da7832", false);
        EhGalleryDetail d = EhGalleryParser.parse(Fixtures.load("gallery_p0.html"), key);
        assertTrue(d.title.startsWith("[Aureole (Kira Boshi)] Sousa no Gokyouryoku"));
        assertTrue(d.titleJpn.startsWith("[あーりある"));
        assertEquals(EhCategory.DOUJINSHI, d.category);
        assertEquals("quanbuzhineng123", d.uploader);
        assertEquals("2026-11-13 04:00", d.posted);
        assertEquals("Chinese", d.language);
        assertEquals("64.57 MiB", d.fileSize);
        assertEquals(29, d.pages);
        assertEquals(130, d.favoritedCount);
        assertEquals(2.33f, d.rating, 0.001f);
        assertEquals(18, d.ratingCount);
        assertEquals("https://ehgt.org/w/02/794/32601-ec49ohk4.webp", d.thumb);
        assertEquals(4325666L, d.parent.gid);
        assertEquals(-1, d.favoriteSlot);
        assertNull(d.favoriteName);

        List<String> language = d.tags.get("language");
        assertEquals(2, language.size());
        assertEquals("chinese", language.get(0));
        assertTrue(d.tags.get("parody").contains("zenless zone zero"));

        assertEquals(2, d.previewPages);
        assertEquals(20, d.previews.size());
        assertEquals(20, d.previewsPerPage());
        EhGalleryDetail.EhPreview p1 = d.previews.get(0);
        assertEquals(1, p1.index);
        assertEquals("https://e-hentai.org/s/3eb42fe48b/4325674-1", p1.pageUrl);
        assertTrue(p1.isSprite());
        assertEquals(0, p1.offsetX);
        assertEquals(200, p1.width);
        assertEquals(282, p1.height);
        EhGalleryDetail.EhPreview p2 = d.previews.get(1);
        assertEquals(2, p2.index);
        assertEquals(200, p2.offsetX);
    }

    @Test
    public void secondPreviewPageContinuesNumbering() throws Exception {
        EhGalleryKey key = new EhGalleryKey(4325690, "7c4cfd2995", false);
        EhGalleryDetail d = EhGalleryParser.parse(Fixtures.load("gallery_p1.html"), key);
        assertEquals(328, d.pages);
        assertEquals(20, d.previews.size());
        assertEquals(21, d.previews.get(0).index);
        assertEquals(17, d.previewPages);
    }

    @Test
    public void favoriteSlotFromIcon() throws Exception {
        String html = "<h1 id=\"gn\">t</h1><div id=\"gdf\"><div id=\"fav\"><div class=\"i\" "
            + "style=\"background-image:url(https://ehgt.org/g/fav.png); background-position:0px -59px; "
            + "margin-left:10px\" title=\"Reading\"></div></div><a id=\"favoritelink\" href=\"#\">Reading</a></div>";
        EhGalleryDetail d = EhGalleryParser.parse(html, new EhGalleryKey(1, "0123456789", false));
        assertEquals(3, d.favoriteSlot);
        assertEquals("Reading", d.favoriteName);
    }

    @Test
    public void favoriteSlotFromColour() throws Exception {
        String html = "<h1 id=\"gn\">t</h1><div id=\"gdf\"><div id=\"fav\" style=\"background-position:0px -21px;"
            + "background-color:rgba(208,208,0,0.5)\"></div><a id=\"favoritelink\" href=\"#\">Reading</a></div>";
        EhGalleryDetail d = EhGalleryParser.parse(html, new EhGalleryKey(1, "0123456789", false));
        assertEquals(3, d.favoriteSlot);
    }

    @Test
    public void removedGallery() {
        try {
            EhGalleryParser.parse("<html><body><div class=\"d\"><p>This gallery has been removed or is unavailable.</p></div></body></html>",
                new EhGalleryKey(1, "0123456789", false));
            fail();
        } catch (EhException e) {
            assertEquals(EhException.Kind.GALLERY_GONE, e.kind);
        }
    }

    @Test
    public void emptyExhentaiPageIsSadPanda() {
        try {
            EhGalleryParser.parse("", new EhGalleryKey(1, "0123456789", true));
            fail();
        } catch (EhException e) {
            assertEquals(EhException.Kind.SAD_PANDA, e.kind);
        }
    }
}
