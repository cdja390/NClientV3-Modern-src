package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.api.enums.TagType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 4.7.1 merged nhentai + e-hentai main list (yosefario-style). */
public class EhFeedTest {

    @Before
    public void reset() {
        EhFeed.resetChains();
    }

    @Test
    public void interleavesRoundRobinKeepingTheTail() {
        assertEquals(Arrays.asList("n1", "e1", "n2", "e2", "n3", "n4"),
            EhFeed.interleave(Arrays.asList("n1", "n2", "n3", "n4"), Arrays.asList("e1", "e2")));
        assertEquals(Arrays.asList("e1", "e2"), EhFeed.interleave(null, Arrays.asList("e1", "e2")));
        assertEquals(Collections.emptyList(), EhFeed.interleave(null, null));
    }

    @Test
    public void modesAndMergeableRequests() {
        assertEquals(EhFeed.Mode.NH, EhFeed.parseMode(null));
        assertEquals(EhFeed.Mode.NH, EhFeed.parseMode("nh"));
        assertEquals(EhFeed.Mode.EH, EhFeed.parseMode("eh"));
        assertEquals(EhFeed.Mode.BOTH, EhFeed.parseMode("both"));
        assertEquals(EhFeed.Mode.NH, EhFeed.parseMode("garbage"));
        assertTrue(EhFeed.isMergeable(ApiRequestType.BYALL));
        assertTrue(EhFeed.isMergeable(ApiRequestType.BYSEARCH));
        assertTrue(EhFeed.isMergeable(ApiRequestType.BYTAG));
        assertFalse(EhFeed.isMergeable(ApiRequestType.FAVORITE));
        assertFalse(EhFeed.isMergeable(ApiRequestType.BYSINGLE));
        assertFalse(EhFeed.isMergeable(ApiRequestType.RANDOM));
        assertEquals(EhFeed.Mode.NH, EhFeed.modeFor(null, ApiRequestType.BYALL));
    }

    @Test
    public void tagTermsUseEhentaiNamespaces() {
        assertEquals("artist:\"shindol$\"", EhFeed.tagTerm("shindol", EhFeed.namespace(TagType.ARTIST), false));
        assertEquals("-\"netorare$\"", EhFeed.tagTerm("netorare", EhFeed.namespace(TagType.TAG), true));
        assertEquals("language:\"chinese$\"", EhFeed.tagTerm("chinese", EhFeed.namespace(TagType.LANGUAGE), false));
        assertNull(EhFeed.namespace(TagType.CATEGORY));
        assertNull(EhFeed.tagTerm("x", null, false));
        assertNull(EhFeed.tagTerm("  ", "artist", false));
        assertEquals("group:\"ab$\"", EhFeed.tagTerm("a\"b", "group", false));
    }

    @Test
    public void popularPlaceholderIsNotSentToEhentai() {
        assertEquals("", EhFeed.buildQuery("-nclientv3", null));
        assertEquals("touhou", EhFeed.buildQuery("  touhou ", null));
    }

    private static EhListParser.Page page(String cursor, long... gids) {
        List<EhGalleryInfo> items = new ArrayList<>();
        for (long gid : gids) items.add(new EhGalleryInfo(new EhGalleryKey(gid, "0123456789", false)));
        return new EhListParser.Page(items, cursor, items.isEmpty());
    }

    @Test
    public void walksTheCursorChainToPageN() throws Exception {
        List<String> requested = new ArrayList<>();
        EhFeed.Fetcher fetcher = url -> {
            requested.add(url);
            if (url.equals("base")) return page("c1", 10, 9);
            if (url.equals("base&next=c1")) return page("c2", 8, 7);
            if (url.equals("base&next=c2")) return page(null, 6);
            throw new AssertionError(url);
        };
        EhFeed.UrlBuilder urls = c -> c == null ? "base" : "base&next=" + c;
        EhFeed.Result p3 = EhFeed.page(urls, 3, fetcher);
        assertEquals(1, p3.items.size());
        assertFalse(p3.hasNext);
        assertEquals(3, p3.pageCount(3));
        assertEquals(Arrays.asList("base", "base&next=c1", "base&next=c2"), requested);

        requested.clear();
        EhFeed.Result p2 = EhFeed.page(urls, 2, fetcher);
        assertEquals(2, p2.items.size());
        assertTrue(p2.hasNext);
        assertEquals(3, p2.pageCount(2));
        assertEquals(Collections.singletonList("base&next=c1"), requested);

        requested.clear();
        assertTrue(EhFeed.page(urls, 4, fetcher).items.isEmpty());
        assertTrue(requested.isEmpty());

        requested.clear();
        EhFeed.Result p1 = EhFeed.page(urls, 1, fetcher);
        assertEquals(2, p1.items.size());
        assertEquals(Collections.singletonList("base"), requested);
    }

    @Test
    public void emptyParsedPageDoesNotAdvertiseAnotherPage() {
        assertEquals(4, new EhFeed.Result(Collections.emptyList(), true).pageCount(4));
        assertEquals(1, new EhFeed.Result(Collections.emptyList(), true).pageCount(0));
        assertEquals(5, new EhFeed.Result(page(null, 1).items, true).pageCount(4));
    }

    @Test
    public void farPagesAreNotWalkedForever() throws Exception {
        EhFeed.Fetcher fetcher = url -> page("c", 1);
        EhFeed.UrlBuilder urls = c -> c == null ? "b" : "b&next=" + c;
        assertTrue(EhFeed.page(urls, EhFeed.MAX_WALK + 3, fetcher).items.isEmpty());
    }
}
