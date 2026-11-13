package com.maxwai.nclientv3.source;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class GalleryDownloadsTest {
    @Test
    public void sameNumericIdGoesToEachQueueWithItsOriginalToken() {
        SourceKey nh = new SourceKey(GallerySource.NHENTAI, 123, null);
        SourceKey eh = new SourceKey(GallerySource.EHENTAI, 123, "0123456789");
        SourceKey ex = new SourceKey(GallerySource.EXHENTAI, 123, "a123456789");
        SourceKey incomplete = new SourceKey(GallerySource.EHENTAI, 234, null);
        List<SourceKey> nhQueue = new ArrayList<>();
        List<SourceKey> ehQueue = new ArrayList<>();
        GalleryDownloads.route(Arrays.asList(nh, eh, ex, nh, eh, incomplete), Function.identity(),
            nhQueue::add, ehQueue::add);
        assertEquals(Collections.singletonList(nh), nhQueue);
        assertEquals(Arrays.asList(eh, ex), ehQueue);
        assertEquals("0123456789", ehQueue.get(0).token);
        assertEquals("a123456789", ehQueue.get(1).token);
    }
}
