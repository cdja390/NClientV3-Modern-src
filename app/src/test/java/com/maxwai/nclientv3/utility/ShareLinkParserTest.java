package com.maxwai.nclientv3.utility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Routing rules for text shared into the app (ShareReceiverActivity). */
public class ShareLinkParserTest {

    private static final ShareLinkParser.HostCheck HOSTS =
        host -> host != null && Utility.belongsToSite(host, "nhentai.net");

    private static String parse(String s) {
        return ShareLinkParser.parse(s, HOSTS).toString();
    }

    @Test
    public void emptyTextIsEmpty() {
        assertEquals("EMPTY:", parse(null));
        assertEquals("EMPTY:", parse("   \n"));
    }

    @Test
    public void galleryLinkOpensGallery() {
        assertEquals("GALLERY:177013", parse("https://nhentai.net/g/177013/"));
        assertEquals("GALLERY:177013", parse("https://nhentai.net/g/177013/12/"));
        assertEquals("GALLERY:123456", parse("看这个 https://nhentai.net/g/123456/ 不错"));
        assertEquals("GALLERY:123456", parse("看这个https://nhentai.net/g/123456不错"));
        assertEquals("GALLERY:42", parse("(https://NHENTAI.net/g/42)."));
    }

    @Test
    public void otherSiteLinksKeepTheirUrl() {
        assertEquals("SITE_URL:https://nhentai.net/tag/full-color/",
            parse("https://nhentai.net/tag/full-color/"));
        assertEquals("SITE_URL:https://nhentai.net/search/?q=abc&page=2",
            parse("https://nhentai.net/search/?q=abc&page=2"));
    }

    @Test
    public void invalidPageIsDropped() {
        assertEquals("SITE_URL:https://nhentai.net/search/?q=abc",
            parse("https://nhentai.net/search/?q=abc&page=abc"));
        assertEquals("SITE_URL:https://nhentai.net/search/?q=x",
            parse("https://nhentai.net/search/?page=0&q=x"));
        assertEquals("SITE_URL:https://nhentai.net/search/",
            parse("https://nhentai.net/search/?page="));
    }

    @Test
    public void numbersOpenById() {
        assertEquals("ID:177013", parse("177013"));
        assertEquals("ID:177013", parse(" #177013 "));
    }

    @Test
    public void otherTextIsSearch() {
        assertEquals("SEARCH:full color", parse("full color"));
        assertEquals("SEARCH:see https://evil-nhentai.net/g/1/",
            parse("see https://evil-nhentai.net/g/1/"));
        assertEquals("SEARCH:1234567890123", parse("1234567890123"));
    }
}
