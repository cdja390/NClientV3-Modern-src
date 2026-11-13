/*
 * Copyright 2026 NClientV3-Modern contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Added for NClientV3 Modern 4.7.2. Field order shared by the Android parcel adapter
 * and the JVM tests. android.os.Parcel itself is not usable in unit tests here
 * (testOptions.unitTests.isReturnDefaultValues). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Primitive field stream for a mixed gallery list. Not Android's Parcel wire format.
 */
public final class RowCodec {
    /**
     * Gallery-section tag written by InspectorV3 when the list contains a non-nhentai row.
     * GenericGallery.Type ordinals are 0..2 (COMPLETE, LOCAL, SIMPLE). 3 is free.
     */
    public static final int KIND_MIXED = 3;
    /** Count of {@code GenericGallery.Type} constants. Kept here so tests need not load that class. */
    public static final int CLASSIC_TYPE_COUNT = 3;

    private RowCodec() {
    }

    public enum SectionKind {
        CLASSIC,
        MIXED,
        UNKNOWN
    }

    @NonNull
    public static SectionKind sectionKind(int kind) {
        if (kind == KIND_MIXED) return SectionKind.MIXED;
        if (kind >= 0 && kind < CLASSIC_TYPE_COUNT) return SectionKind.CLASSIC;
        return SectionKind.UNKNOWN;
    }

    public interface FieldWriter {
        void writeByte(int value);

        void writeInt(int value);

        void writeLong(long value);

        void writeString(@Nullable String value);
    }

    public interface FieldReader {
        int readByte();

        int readInt();

        long readLong();

        @Nullable
        String readString();
    }

    /** One list row: identity plus the fields the list actually draws. */
    public static final class ListedRow {
        @NonNull
        public final SourceKey key;
        @NonNull
        public final String title;
        @Nullable
        public final String thumb;
        public final int pages;
        public final int languageOrdinal;
        @Nullable
        public final String rawLanguage;
        public final int mediaId;

        public ListedRow(@NonNull SourceKey key, @NonNull String title, @Nullable String thumb,
                         int pages, int languageOrdinal, @Nullable String rawLanguage, int mediaId) {
            this.key = key;
            this.title = title;
            this.thumb = thumb;
            this.pages = pages;
            this.languageOrdinal = languageOrdinal;
            this.rawLanguage = rawLanguage;
            this.mediaId = mediaId;
        }

        public void write(@NonNull FieldWriter out) {
            key.write(out);
            out.writeString(title);
            out.writeString(thumb);
            out.writeInt(pages);
            out.writeByte(languageOrdinal);
            out.writeString(rawLanguage);
            out.writeInt(mediaId);
        }

        @NonNull
        public static ListedRow read(@NonNull FieldReader in) {
            SourceKey key = SourceKey.read(in);
            String title = in.readString();
            return new ListedRow(key, title == null ? "" : title, in.readString(), in.readInt(),
                in.readByte(), in.readString(), in.readInt());
        }
    }

    public static void writeRows(@NonNull FieldWriter out, @NonNull List<ListedRow> rows) {
        out.writeInt(rows.size());
        for (ListedRow row : rows) row.write(out);
    }

    @NonNull
    public static List<ListedRow> readRows(@NonNull FieldReader in) {
        int count = in.readInt();
        if (count < 0) count = 0;
        ArrayList<ListedRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) rows.add(ListedRow.read(in));
        return rows;
    }
}
