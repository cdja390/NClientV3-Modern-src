package com.maxwai.nclientv3.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.maxwai.nclientv3.api.components.GalleryData;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.files.GalleryFolder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * P1 identity and list-row codec.
 * android.os.Parcel is not executed: unit tests set isReturnDefaultValues, so Parcel.obtain()
 * is null. T27 and T28 cover the field order GalleryListParcel writes. T29 covers the kind byte
 * that keeps a classic INSPECTOR section on the old reader.
 */
public class SourceKeyTest {

    @Test
    public void stableKeyIgnoresTokenAndSeparatesSources() {
        SourceKey eh = new SourceKey(GallerySource.EHENTAI, 123, "aaaaaaaaaa");
        SourceKey ehOtherToken = new SourceKey(GallerySource.EHENTAI, 123, "bbbbbbbbbb");
        SourceKey ex = new SourceKey(GallerySource.EXHENTAI, 123, "aaaaaaaaaa");
        SourceKey nh = new SourceKey(GallerySource.NHENTAI, 123, null);
        assertEquals(eh, ehOtherToken);
        assertEquals(eh.hashCode(), ehOtherToken.hashCode());
        assertEquals("EHENTAI:123", eh.stableKey());
        assertNotEquals(eh, ex);
        assertNotEquals(eh.stableKey(), nh.stableKey());
        assertFalse(GalleryIdentity.sameIdentity(GallerySource.NHENTAI, 123, GallerySource.EHENTAI, 123));
        assertTrue(GalleryIdentity.sameIdentity(GallerySource.EHENTAI, 123, GallerySource.EHENTAI, 123));
    }

    @Test
    public void numericIdIsNeverNegative() {
        assertEquals(0, GalleryIdentity.numericId(0));
        assertEquals(0, GalleryIdentity.numericId(-5));
        assertEquals(42, GalleryIdentity.numericId(42));
        assertEquals(Integer.MAX_VALUE, GalleryIdentity.numericId(Integer.MAX_VALUE + 1L));
        assertEquals(GallerySource.EHENTAI, GalleryIdentity.ehSource(false));
        assertEquals(GallerySource.EXHENTAI, GalleryIdentity.ehSource(true));
    }

    @Test
    public void sourceKeyFieldsRoundTripIncludingExAndNullToken() {
        Memory memory = new Memory();
        SourceKey ex = new SourceKey(GallerySource.EXHENTAI, 99L, "abcDEF0123");
        ex.write(memory);
        SourceKey read = SourceKey.read(memory);
        assertEquals(GallerySource.EXHENTAI, read.source);
        assertEquals(99L, read.id);
        assertEquals("abcDEF0123", read.token);
        assertEquals(ex, read);

        Memory bare = new Memory();
        new SourceKey(GallerySource.NHENTAI, 7, null).write(bare);
        assertNull(SourceKey.read(bare).token);
    }

    @Test
    public void unknownSourceOrdinalIsRejected() {
        Memory memory = new Memory();
        memory.writeByte(9);
        memory.writeLong(1);
        memory.writeString("tok");
        try {
            SourceKey.read(memory);
            fail("ordinal 9");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("9"));
        }
    }

    @Test
    public void mixedRowsKeepBothSourcesWhenNumericIdsMatch() {
        RowCodec.ListedRow nh = new RowCodec.ListedRow(
            new SourceKey(GallerySource.NHENTAI, 123, null), "nh-title", "https://nh/t", 10, 0, null, 55);
        RowCodec.ListedRow eh = new RowCodec.ListedRow(
            new SourceKey(GallerySource.EHENTAI, 123, "0123456789"), "eh-title", "https://eh/t", 20, 1,
            "chinese", 0);
        Memory memory = new Memory();
        RowCodec.writeRows(memory, Arrays.asList(nh, eh));
        List<RowCodec.ListedRow> rows = RowCodec.readRows(memory);
        assertEquals(2, rows.size());
        assertEquals(GallerySource.NHENTAI, rows.get(0).key.source);
        assertEquals(123L, rows.get(0).key.id);
        assertEquals(55, rows.get(0).mediaId);
        assertEquals("nh-title", rows.get(0).title);
        assertEquals(GallerySource.EHENTAI, rows.get(1).key.source);
        assertEquals("0123456789", rows.get(1).key.token);
        assertEquals("chinese", rows.get(1).rawLanguage);
        assertEquals(20, rows.get(1).pages);
        assertNotEquals(rows.get(0).key.stableKey(), rows.get(1).key.stableKey());
    }

    @Test
    public void classicInspectorKindStaysOnTheOldReader() {
        assertEquals(RowCodec.SectionKind.CLASSIC, RowCodec.sectionKind(0));
        assertEquals(RowCodec.SectionKind.CLASSIC, RowCodec.sectionKind(1));
        assertEquals(RowCodec.SectionKind.CLASSIC, RowCodec.sectionKind(2));
        assertEquals(RowCodec.SectionKind.MIXED, RowCodec.sectionKind(RowCodec.KIND_MIXED));
        assertEquals(RowCodec.SectionKind.UNKNOWN, RowCodec.sectionKind(4));
        assertEquals(RowCodec.SectionKind.UNKNOWN, RowCodec.sectionKind(-1));
        assertEquals(3, GenericGallery.Type.values().length);
        assertEquals(GenericGallery.Type.values().length, RowCodec.CLASSIC_TYPE_COUNT);
    }

    @Test
    public void statusAndNhentaiTablesDoNotCrossSources() {
        assertTrue(GalleryIdentity.persistsInNhentaiTables(GallerySource.NHENTAI));
        assertFalse(GalleryIdentity.persistsInNhentaiTables(GallerySource.EHENTAI));
        assertFalse(GalleryIdentity.persistsInNhentaiTables(GallerySource.EXHENTAI));
        GenericGallery nh = new BareGallery(123);
        assertTrue(GalleryIdentity.persistsInNhentaiTables(nh));
        assertEquals("NHENTAI:123", GalleryIdentity.of(nh).stableKey());
        assertFalse(GalleryIdentity.sameIdentity(
            GalleryIdentity.of(nh).source, GalleryIdentity.of(nh).id, GallerySource.EHENTAI, 123));
    }

    /** Nhentai-shaped row. Methods that need Android are not called. */
    private static final class BareGallery extends GenericGallery {
        private final int id;

        BareGallery(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public Type getType() {
            return Type.SIMPLE;
        }

        @Override
        public int getPageCount() {
            return 1;
        }

        @Override
        public boolean isValid() {
            return id > 0;
        }

        @Override
        public String getTitle() {
            return "t";
        }

        @Override
        public Size getMaxSize() {
            return null;
        }

        @Override
        public Size getMinSize() {
            return null;
        }

        @Override
        public GalleryFolder getGalleryFolder() {
            return null;
        }

        @Override
        public boolean hasGalleryData() {
            return false;
        }

        @Override
        public GalleryData getGalleryData() {
            return null;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
        }
    }

    private static final class Memory implements RowCodec.FieldWriter, RowCodec.FieldReader {
        private final ArrayList<Object> data = new ArrayList<>();
        private int cursor;

        @Override
        public void writeByte(int value) {
            data.add(value);
        }

        @Override
        public void writeInt(int value) {
            data.add(value);
        }

        @Override
        public void writeLong(long value) {
            data.add(value);
        }

        @Override
        public void writeString(String value) {
            data.add(value);
        }

        @Override
        public int readByte() {
            return (Integer) data.get(cursor++);
        }

        @Override
        public int readInt() {
            return (Integer) data.get(cursor++);
        }

        @Override
        public long readLong() {
            return (Long) data.get(cursor++);
        }

        @Override
        public String readString() {
            return (String) data.get(cursor++);
        }
    }
}
