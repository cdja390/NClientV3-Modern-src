package com.maxwai.nclientv3.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.enums.SortType;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** T04-T14: fake sources exercise the production merge without Android or network calls. */
public class SourceMergerTest {
    private static final GallerySource NH = GallerySource.NHENTAI;
    private static final GallerySource EH = GallerySource.EHENTAI;
    private final List<GallerySource> failures = new ArrayList<>();

    private static SourceMerger<String> merger() {
        return new SourceMerger<>(s -> s);
    }

    private static MergeSource<String> rows(GallerySource source, int pages, String... rows) {
        return SourceMerger.source(source, sort -> new MergePage<>(Arrays.asList(rows), pages));
    }

    private static MergeSource<String> broken(GallerySource source, Exception error) {
        return SourceMerger.source(source, sort -> { throw error; });
    }

    private MergePage<String> merge(MergeSource<String> a, MergeSource<String> b) throws IOException {
        return merger().merge(Arrays.asList(a, b), SortType.POPULAR_ALL_TIME, 1,
            (source, error) -> failures.add(source));
    }

    @Test
    public void sortDowngradesBothRequestsWithoutChangingSavedPreference() throws IOException {
        List<SortType> actual = new ArrayList<>();
        List<MergeSource<String>> sources = Arrays.asList(
            SourceMerger.source(NH, sort -> { actual.add(sort); return new MergePage<>(Collections.emptyList(), 1); }),
            SourceMerger.source(EH, sort -> { actual.add(sort); return new MergePage<>(Collections.emptyList(), 1); }));
        SortType requested = SortType.POPULAR_ALL_TIME;
        merger().merge(sources, requested, 1, (source, error) -> fail());
        assertEquals(Arrays.asList(SortType.RECENT_ALL_TIME, SortType.RECENT_ALL_TIME), actual);
        assertEquals(SortType.POPULAR_ALL_TIME, requested);
        assertEquals(requested, SourceCapabilities.effectiveSort(Collections.singletonList(NH), requested));
    }

    @Test
    public void sortOptionsAreTheSourceIntersection() {
        assertEquals(Arrays.asList(SortType.values()), SourceCapabilities.sortOptions(Collections.singletonList(NH)));
        assertEquals(Collections.singletonList(SortType.RECENT_ALL_TIME), SourceCapabilities.sortOptions(Arrays.asList(NH, EH)));
        assertEquals(Collections.singletonList(SortType.RECENT_ALL_TIME), SourceCapabilities.sortOptions(Arrays.asList(NH, GallerySource.EXHENTAI)));
    }

    @Test
    public void roundRobinKeepsLongerTailAndEmptySourceDoesNotBlock() throws IOException {
        MergeSource<String> nh = rows(NH, 3, "n1", "n2", "n3");
        MergeSource<String> eh = rows(EH, 6, "e1", "e2", "e3", "e4", "e5");
        assertEquals(Arrays.asList("n1", "e1", "n2", "e2", "n3", "e3", "e4", "e5"), merge(nh, eh).items);
        assertEquals(Arrays.asList("e1", "e2", "e3", "e4", "e5"), merge(rows(NH, 1), eh).items);
        assertEquals(Arrays.asList("e1", "e2", "e3", "e4", "e5"), merge(eh, rows(NH, 1)).items);
    }

    @Test
    public void pageCountIsMaximumIncludingEmptySuccessfulPages() throws IOException {
        assertEquals(8, merge(rows(NH, 8, "n"), rows(EH, 3, "e")).pageCount);
        assertEquals(9, merge(rows(NH, 2, "n"), rows(EH, 9)).pageCount);
    }

    @Test
    public void nhFailureKeepsEh() throws IOException {
        assertEquals(Collections.singletonList("e"), merge(broken(NH, new IOException("nh")), rows(EH, 2, "e")).items);
        assertEquals(Collections.singletonList(NH), failures);
    }

    @Test
    public void ehFailureKeepsNh() throws IOException {
        assertEquals(Collections.singletonList("n"), merge(rows(NH, 4, "n"), broken(EH, new IOException("509"))).items);
        assertEquals(Collections.singletonList(EH), failures);
    }

    @Test
    public void allFailedRethrowsLastIOException() {
        IOException last = new IOException("last");
        try {
            merge(broken(NH, new IllegalStateException("first")), broken(EH, last));
            fail();
        } catch (IOException actual) {
            assertSame(last, actual);
            assertEquals(Arrays.asList(NH, EH), failures);
        }
    }

    @Test
    public void allFailedWrapsLastRuntimeException() {
        RuntimeException last = new IllegalStateException("last");
        try {
            merge(broken(NH, new IOException("first")), broken(EH, last));
            fail();
        } catch (IOException actual) {
            assertSame(last, actual.getCause());
        }
    }

    @Test
    public void invalidNhResponseDoesNotPreventEh() throws IOException {
        assertEquals(Collections.singletonList("e"), merge(broken(NH, new InspectorV3.InvalidResponseException()), rows(EH, 1, "e")).items);
    }

    @Test
    public void emptySuccessAndOtherFailureIsStillSuccess() throws IOException {
        assertTrue(merge(rows(NH, 1), broken(EH, new IOException("eh"))).items.isEmpty());
    }

    @Test
    public void dedupUsesSourceAndIdButNotToken() throws IOException {
        SourceKey nh = new SourceKey(NH, 123, null);
        SourceKey eh = new SourceKey(EH, 123, "first");
        SourceKey duplicate = new SourceKey(EH, 123, "refreshed");
        List<MergeSource<SourceKey>> sources = Arrays.asList(
            SourceMerger.source(NH, sort -> new MergePage<>(Arrays.asList(nh, nh), 1)),
            SourceMerger.source(EH, sort -> new MergePage<>(Arrays.asList(eh, duplicate), 1)));
        assertEquals(Arrays.asList(nh, eh), new SourceMerger<SourceKey>(SourceKey::stableKey)
            .merge(sources, SortType.RECENT_ALL_TIME, 1, (source, error) -> fail()).items);
    }

    @Test
    public void firstPageResetsSeenAndNewInspectorHasIndependentSeen() throws IOException {
        SourceMerger<String> current = merger();
        List<MergeSource<String>> sources = Arrays.asList(rows(NH, 2, "n"), rows(EH, 2, "e"));
        assertEquals(2, current.merge(sources, SortType.RECENT_ALL_TIME, 1, (source, error) -> fail()).items.size());
        assertTrue(current.merge(sources, SortType.RECENT_ALL_TIME, 2, (source, error) -> fail()).items.isEmpty());
        assertEquals(2, current.merge(sources, SortType.RECENT_ALL_TIME, 1, (source, error) -> fail()).items.size());
        assertEquals(2, merger().merge(sources, SortType.RECENT_ALL_TIME, 2, (source, error) -> fail()).items.size());
    }
}
