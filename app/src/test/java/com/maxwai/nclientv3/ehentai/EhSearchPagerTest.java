package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** T15-T26: production query mapping and cursor cache with no live network. */
public class EhSearchPagerTest {
    private final List<String> calls = new ArrayList<>();
    private int totalPages = 20;

    @Before
    public void reset() {
        EhFeed.resetChains();
    }

    private static Tag tag(String name, TagType type, TagStatus status) {
        return new Tag(name, 0, 0, type, status);
    }

    @Test
    public void keepsFifteenTagsAndOnlyRemovesDuplicateTerms() {
        List<Tag> tags = new ArrayList<>();
        StringBuilder expected = new StringBuilder("query");
        for (int i = 0; i < 15; i++) {
            tags.add(tag("tag" + i, TagType.TAG, TagStatus.ACCEPTED));
            expected.append(" \"tag").append(i).append("$\"");
        }
        tags.add(tags.get(0));
        assertEquals(expected.toString(), EhFeed.buildQuery("query", tags));
    }

    @Test
    public void keepsQuotesAndAvoidedPrefixAndDropsPlaceholder() {
        assertEquals("-artist:\"a\"b$\"", EhFeed.buildQuery("-nclientv3",
            Collections.singletonList(tag("a\"b", TagType.ARTIST, TagStatus.AVOIDED))));
    }

    @Test
    public void categoryTagsOverrideGlobalMaskAndUnknownCategoryMeansAll() {
        List<Tag> tags = Arrays.asList(tag("Manga", TagType.CATEGORY, TagStatus.ACCEPTED),
            tag("Doujinshi", TagType.CATEGORY, TagStatus.AVOIDED));
        assertEquals(EhCategory.MANGA.bit | EhCategory.DOUJINSHI.bit,
            EhFeed.shownCategories(tags, EhCategory.COSPLAY.bit));
        assertEquals(0, EhFeed.shownCategories(Collections.singletonList(tag("unknown", TagType.CATEGORY, TagStatus.ACCEPTED)), 16));
        assertEquals(16, EhFeed.shownCategories(Collections.singletonList(tag("artist", TagType.ARTIST, TagStatus.ACCEPTED)), 16));
        assertTrue(EhFeed.searchUrl(false, null, tags, null, 64, 0, null).contains("f_cats=1017"));
    }

    @Test
    public void privateCategoryMatchesUpstreamExclusionMask() {
        List<Tag> tags = Collections.singletonList(tag("Private", TagType.CATEGORY, TagStatus.ACCEPTED));
        assertEquals("https://e-hentai.org/?f_cats=1023",
            EhFeed.searchUrl(false, null, tags, null, 0, 0, null));
        assertEquals(1019, EhCategory.excludeMaskForShown(EhCategory.REQUEST_PRIVATE | EhCategory.MANGA.bit));
    }

    @Test
    public void rangesOverrideRatingWhileMissingFiltersInheritGlobalValues() {
        Ranges ranges = new Ranges();
        assertTrue(ranges.isDefault());
        assertEquals("https://e-hentai.org/?f_cats=1021&advsearch=1&f_srdd=4",
            EhFeed.searchUrl(false, null, null, ranges, 2, 4, null));
        ranges.setFromPage(12);
        ranges.setToPage(60);
        assertEquals("https://e-hentai.org/?f_cats=1021&advsearch=1&f_srdd=4&f_spf=12&f_spt=60",
            EhFeed.searchUrl(false, null, null, ranges, 2, 4, null));
        ranges.setMinRating(3);
        assertEquals("https://exhentai.org/?f_cats=1021&advsearch=1&f_srdd=3&f_spf=12&f_spt=60&next=cursor",
            EhFeed.searchUrl(true, null, null, ranges, 2, 4, "cursor"));
        ranges.setMinRating(0);
        assertFalse(EhFeed.searchUrl(false, null, null, ranges, 0, 5, null).contains("f_srdd"));
        assertEquals("pages:>=12 pages:<=60", ranges.toQuery());
    }

    @Test
    public void ratingOnlyRequestIsNotDefaultAndDoesNotEnterNhQuery() {
        Ranges ranges = new Ranges();
        ranges.setMinRating(4);
        assertFalse(ranges.isDefault());
        assertEquals("", ranges.toQuery());
        assertEquals(4, ranges.getMinRating());
    }

    private EhListParser.Page fetch(String url) {
        calls.add(url);
        int at = url.indexOf("&next=");
        int page = at < 0 ? 1 : Integer.parseInt(url.substring(at + 6));
        EhGalleryInfo info = new EhGalleryInfo(new EhGalleryKey(page, "0123456789", false));
        return new EhListParser.Page(Collections.singletonList(info),
            page < totalPages ? Integer.toString(page + 1) : null, false);
    }

    private EhFeed.Result page(String query, int page) throws Exception {
        return EhFeed.page(cursor -> cursor == null ? query : query + "&next=" + cursor, page, this::fetch);
    }

    @Test
    public void cachesWalkedPagesAndRefreshResetsOneChain() throws Exception {
        page("q", 1);
        page("q", 2);
        page("q", 3);
        page("q", 2);
        assertEquals(Arrays.asList("q", "q&next=2", "q&next=3"), calls);
        page("q", 1);
        page("q", 2);
        assertEquals(5, calls.size());
    }

    @Test
    public void fourPageCacheEvictsLeastRecentlyUsedPage() throws Exception {
        for (int i = 1; i <= 5; i++) page("q", i);
        page("q", 2); // Touch page 2: page 3 becomes least recently used.
        assertEquals(5, calls.size());
        page("q", 6);
        page("q", 3);
        assertEquals(7, calls.size());
        assertEquals("q&next=3", calls.get(6));
    }

    @Test
    public void evictedPageKeepsRememberedNextCursorLikeUpstream() throws Exception {
        for (int i = 1; i <= 6; i++) page("q", i);
        totalPages = 2;
        assertTrue(page("q", 2).hasNext);
    }

    @Test
    public void twentyFourQueryChainsAreLeastRecentlyUsed() throws Exception {
        for (int i = 0; i < 24; i++) page("q" + i, 2);
        page("q0", 2); // Keep q0; adding q24 evicts q1.
        page("q24", 2);
        int before = calls.size();
        page("q0", 2);
        assertEquals(before, calls.size());
        page("q1", 2);
        assertEquals(before + 2, calls.size());
    }

    @Test
    public void maxWalkCapsOnlyUncachedJumpNotTotalPageNumber() throws Exception {
        assertTrue(page("jump", 10).items.isEmpty());
        assertEquals(Collections.singletonList("jump"), calls);
        for (int i = 1; i <= 12; i++) assertEquals(i, page("sequential", i).items.get(0).key.gid);
        assertEquals(12, page("sequential", 12).items.get(0).key.gid);
    }

    @Test
    public void lastCursorStopsFurtherRequests() throws Exception {
        totalPages = 2;
        assertTrue(page("q", 1).hasNext);
        assertFalse(page("q", 2).hasNext);
        assertTrue(page("q", 3).items.isEmpty());
        assertEquals(Arrays.asList("q", "q&next=2"), calls);
    }
}
