package com.maxwai.nclientv3.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Pure-Java parts of the Settings reading stats list (sorting, title fallback). */
public class ReadingStatsHelperTest {

    private static List<ReadingStatsHelper.Entry> sample() {
        List<ReadingStatsHelper.Entry> l = new ArrayList<ReadingStatsHelper.Entry>();
        l.add(new ReadingStatsHelper.Entry(1, 5, 1000L, 0, 300L, 3, "a"));
        l.add(new ReadingStatsHelper.Entry(2, 1, 9000L, 0, 100L, 3, "b"));
        l.add(new ReadingStatsHelper.Entry(3, 9, 500L, 0, 200L, 3, null));
        return l;
    }

    private static String ids(List<ReadingStatsHelper.Entry> l) {
        StringBuilder sb = new StringBuilder();
        for (ReadingStatsHelper.Entry e : l) sb.append(e.gallery);
        return sb.toString();
    }

    @Test
    public void sortModes() {
        List<ReadingStatsHelper.Entry> l = sample();
        ReadingStatsHelper.sortEntries(l, ReadingStatsHelper.SORT_LAST_READ);
        assertEquals("132", ids(l));
        ReadingStatsHelper.sortEntries(l, ReadingStatsHelper.SORT_TIME);
        assertEquals("213", ids(l));
        ReadingStatsHelper.sortEntries(l, ReadingStatsHelper.SORT_COUNT);
        assertEquals("312", ids(l));
    }

    @Test
    public void titleFallback() {
        assertEquals("#3", sample().get(2).displayTitle());
        assertEquals("x", ReadingStatsHelper.firstNonEmpty(null, " ", "x", "y"));
        assertNull(ReadingStatsHelper.firstNonEmpty(null, ""));
    }
}
