package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.maxwai.nclientv3.utility.ReadingSessionHelper;

import org.junit.Test;

public class EhReadingSessionTest {
    private long count, total;
    private int maxPage;
    private boolean fail;

    private EhReadingSession session(boolean fresh) {
        return new EhReadingSession(fresh, (inc, elapsed, wall, page) -> {
            if (fail) throw new IllegalStateException("test write failure");
            count += inc;
            total += elapsed;
            maxPage = Math.max(maxPage, page);
        });
    }

    @Test
    public void offRecordsNothingAndRestorationDoesNotIncrementReadCount() {
        EhReadingSession off = session(true);
        off.resume(false, 0, 10000, 1);
        off.pause(40000, 80000, 2);
        assertEquals(0, count);
        assertEquals(0, total);
        EhReadingSession restored = session(false);
        restored.resume(true, 1, 12000, 8);
        restored.pause(101, 80000, 9);
        assertEquals(0, count);
        assertEquals(100, total);
        assertEquals(9, maxPage);
    }

    @Test
    public void foregroundMillisecondsAccumulateAcrossPausesWithoutCountingBackground() {
        EhReadingSession reader = session(true);
        reader.resume(true, 0, 15000, 1);
        reader.pause(350, 1000, 3);
        reader.resume(true, 1234, 500, 3);
        reader.pause(1684, 500, 6);
        reader.resume(true, 600000, 0, 6);
        reader.pause(600500, 100000, 4);
        reader.pause(900000, 200000, 4);
        assertEquals(1, count);
        assertEquals(1300, total);
        assertEquals(6, maxPage);
        session(true).resume(true, 900000, 200000, 1);
        assertEquals(2, count);
    }

    @Test
    public void failedWritesAreRetriedWithoutDiscardingTimeOrRecounting() {
        EhReadingSession reader = session(true);
        fail = true;
        assertThrows(IllegalStateException.class, () -> reader.resume(true, 0, 0, 1));
        assertThrows(IllegalStateException.class, () -> reader.flush(30000, 50000, 3));
        assertThrows(IllegalStateException.class, () -> reader.pause(60000, 80000, 4));
        fail = false;
        reader.flush(60000, 80000, 4);
        reader.flush(60000, 80000, 4);
        assertEquals(60000, total);
        assertEquals(1, count);
        assertEquals(4, maxPage);
    }

    @Test
    public void autoStatusOnlyPromotesAfterARealTurnAndRespectsManualStatus() {
        java.util.function.Predicate<String> installed = name -> "Reading".equals(name) || "Completed".equals(name);
        assertNull(ReadingSessionHelper.nextStatus(null, 0, 8, false, installed));
        assertNull(ReadingSessionHelper.nextStatus(null, 1, 1, false, installed));
        assertEquals("Reading", ReadingSessionHelper.nextStatus("Plan to read", 4, 8, false, installed));
        assertEquals("Completed", ReadingSessionHelper.nextStatus("Reading", 8, 8, false, installed));
        assertNull(ReadingSessionHelper.nextStatus("Completed", 4, 8, true, installed));
        assertNull(ReadingSessionHelper.nextStatus("On Hold", 8, 8, false, installed));
        assertNull(ReadingSessionHelper.nextStatus("Custom", 4, 8, true, installed));
        assertEquals("Completed", ReadingSessionHelper.nextStatus("Custom", 8, 8, true, installed));
        assertNull(ReadingSessionHelper.nextStatus("Reading", 8, 8, true, name -> false));
    }

    @Test
    public void tapAndVolumeDirectionUseTheSharedRtlAndButtonSettings() {
        assertEquals(-1, EhPageNavigation.edgeDelta(.1f, true, false));
        assertEquals(1, EhPageNavigation.edgeDelta(.1f, true, true));
        assertEquals(1, EhPageNavigation.edgeDelta(.9f, true, false));
        assertEquals(0, EhPageNavigation.edgeDelta(.9f, false, true));
        assertEquals(0, EhPageNavigation.edgeDelta(.5f, true, true));
        assertEquals(1, EhPageNavigation.volumeDelta(true, true, false));
        assertEquals(-1, EhPageNavigation.volumeDelta(true, true, true));
        assertEquals(1, EhPageNavigation.volumeDelta(false, false, false));
        assertEquals(1, EhPageNavigation.clamp(0, 12));
        assertEquals(12, EhPageNavigation.clamp(13, 12));
    }
}
