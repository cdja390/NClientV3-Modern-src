package com.maxwai.nclientv3.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;

import org.junit.Test;

import java.util.Collections;

public class InspectorBrowseSortTest {
    @Test
    public void popularHomeUsesNhSearchAndCanReturnToRecentBrowse() {
        InspectorV3 browse = InspectorV3.searchInspector(null, "", Collections.emptyList(), 2,
            SortType.POPULAR_DAILY, new Ranges(), null);
        assertTrue(browse.isUnfilteredBrowse());
        assertEquals(ApiRequestType.BYALL, browse.getRequestType());
        assertTrue(browse.getUrl().endsWith("/api/v2/search?query=-nclientv3&page=2&sort=popular-today"));
        browse.setSortType(SortType.RECENT_ALL_TIME);
        assertTrue(browse.getUrl().endsWith("/api/v2/galleries?page=2"));
    }

    @Test
    public void legacyPopularBookmarkKeepsItsSortAndBrowseSemantics() {
        SortType sort = SortType.findFromAddition("popular-today");
        InspectorV3 bookmark = InspectorV3.searchInspector(null, "-nclientv3", Collections.emptyList(),
            1, sort, null, null);
        assertEquals(SortType.POPULAR_DAILY, sort);
        assertEquals(SortType.POPULAR_WEEKLY, SortType.findFromAddition("popular-week"));
        assertEquals(SortType.POPULAR_MONTH, SortType.findFromAddition("popular-month"));
        assertTrue(bookmark.isUnfilteredBrowse());
        assertTrue(bookmark.getUrl().endsWith("/api/v2/search?query=-nclientv3&page=1&sort=popular-today"));
    }

    @Test
    public void keywordTagsAndRequestedRatingDisableThePopularBrowse() {
        InspectorV3 keyword = InspectorV3.searchInspector(null, "keyword", Collections.emptyList(),
            1, SortType.POPULAR_DAILY, null, null);
        assertFalse(keyword.isUnfilteredBrowse());
        assertTrue(keyword.getUrl().endsWith("/api/v2/search?query=keyword&page=1&sort=popular-today"));
        Tag tag = new Tag("filter", 0, 1, TagType.TAG, TagStatus.ACCEPTED);
        assertFalse(InspectorV3.searchInspector(null, "", Collections.singleton(tag), 1,
            SortType.POPULAR_DAILY, null, null).isUnfilteredBrowse());
        Ranges ranges = new Ranges();
        ranges.setMinRating(4);
        assertFalse(InspectorV3.searchInspector(null, "", Collections.emptyList(), 1,
            SortType.POPULAR_DAILY, ranges, null).isUnfilteredBrowse());
    }
}
