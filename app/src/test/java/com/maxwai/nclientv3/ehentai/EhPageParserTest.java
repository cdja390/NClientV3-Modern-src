package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class EhPageParserTest {

    @Test
    public void viewerPage() throws Exception {
        EhPageParser.Result r = EhPageParser.parse(Fixtures.load("viewer.html"));
        assertTrue(r.imageUrl, r.imageUrl.startsWith("https://sfogugr.yzgtutunpeki.hath.network:17013/h/"));
        assertTrue(r.imageUrl.endsWith("/_001.webp"));
        assertEquals("40297-498486", r.reloadKey);
        assertEquals("https://e-hentai.org/fullimg/4325674/1/gfukqynaomu/_001.jpg", r.originalUrl);
        assertEquals("https://e-hentai.org/s/3eb42fe48b/4325674-1?nl=40297-498486",
            EhUrls.withReloadKey("https://e-hentai.org/s/3eb42fe48b/4325674-1", r.reloadKey));
    }

    @Test
    public void quotaImageIsReported() {
        try {
            EhPageParser.parse("<img id=\"img\" src=\"https://ehgt.org/g/509.gif\" />");
            fail();
        } catch (EhException e) {
            assertEquals(EhException.Kind.QUOTA_EXCEEDED, e.kind);
        }
    }

    @Test
    public void pageWithoutImageIsAParseError() {
        try {
            EhPageParser.parse("<html><body>nothing</body></html>");
            fail();
        } catch (EhException e) {
            assertEquals(EhException.Kind.PARSE, e.kind);
        }
    }
}
