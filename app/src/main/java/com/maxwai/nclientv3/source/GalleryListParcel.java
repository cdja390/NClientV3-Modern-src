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
 * Added for NClientV3 Modern 4.7.2. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.source;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.enums.Language;
import com.maxwai.nclientv3.ehentai.EhSimpleGallery;

import java.util.ArrayList;
import java.util.List;

/** Android adapter over {@link RowCodec}. Classic InspectorV3 parcels do not come through here. */
public final class GalleryListParcel {
    private GalleryListParcel() {
    }

    public static void writeMixed(@NonNull Parcel dest, @NonNull List<GenericGallery> galleries) {
        ArrayList<RowCodec.ListedRow> rows = new ArrayList<>(galleries.size());
        for (GenericGallery gallery : galleries) {
            if (gallery != null) rows.add(fromGallery(gallery));
        }
        RowCodec.writeRows(new ParcelFields(dest), rows);
    }

    @NonNull
    public static ArrayList<GenericGallery> readMixed(@NonNull Parcel source) {
        List<RowCodec.ListedRow> rows = RowCodec.readRows(new ParcelFields(source));
        ArrayList<GenericGallery> galleries = new ArrayList<>(rows.size());
        for (RowCodec.ListedRow row : rows) galleries.add(toGallery(row));
        return galleries;
    }

    @NonNull
    static RowCodec.ListedRow fromGallery(@NonNull GenericGallery gallery) {
        if (gallery instanceof EhSimpleGallery) {
            EhSimpleGallery eh = (EhSimpleGallery) gallery;
            String raw = eh.info.language;
            if (raw == null) raw = EhSimpleGallery.languageToken(eh.getLanguage());
            return new RowCodec.ListedRow(eh.sourceKey(), eh.getTitle(), eh.info.thumb, eh.getPageCount(),
                eh.getLanguage().ordinal(), raw, 0);
        }
        if (gallery instanceof SimpleGallery) {
            SimpleGallery simple = (SimpleGallery) gallery;
            String thumb = simple.getThumbnail() == null ? null : simple.getThumbnail().toString();
            return new RowCodec.ListedRow(
                new SourceKey(GallerySource.NHENTAI, simple.getId(), null),
                simple.getTitle(), thumb, simple.getPageCount(), simple.getLanguage().ordinal(),
                null, simple.getMediaId());
        }
        return new RowCodec.ListedRow(
            new SourceKey(GallerySource.NHENTAI, gallery.getId(), null),
            gallery.getTitle(), null, gallery.getPageCount(), Language.UNKNOWN.ordinal(), null, 0);
    }

    @NonNull
    static GenericGallery toGallery(@NonNull RowCodec.ListedRow row) {
        if (row.key.source == GallerySource.NHENTAI) {
            return SimpleGallery.restoreListRow(row.title, GalleryIdentity.numericId(row.key.id),
                row.mediaId, row.thumb, languageOf(row.languageOrdinal), row.pages);
        }
        String raw = row.rawLanguage;
        if (raw == null) raw = EhSimpleGallery.languageToken(languageOf(row.languageOrdinal));
        return EhSimpleGallery.restore(row.key.id, row.key.token,
            row.key.source == GallerySource.EXHENTAI, row.title, row.thumb, row.pages, raw);
    }

    @NonNull
    private static Language languageOf(int ordinal) {
        Language[] values = Language.values();
        if (ordinal < 0 || ordinal >= values.length) return Language.UNKNOWN;
        return values[ordinal];
    }

    private static final class ParcelFields implements RowCodec.FieldWriter, RowCodec.FieldReader {
        private final Parcel parcel;

        ParcelFields(@NonNull Parcel parcel) {
            this.parcel = parcel;
        }

        @Override
        public void writeByte(int value) {
            parcel.writeByte((byte) value);
        }

        @Override
        public void writeInt(int value) {
            parcel.writeInt(value);
        }

        @Override
        public void writeLong(long value) {
            parcel.writeLong(value);
        }

        @Override
        public void writeString(@Nullable String value) {
            parcel.writeString(value);
        }

        @Override
        public int readByte() {
            return parcel.readByte() & 0xff;
        }

        @Override
        public int readInt() {
            return parcel.readInt();
        }

        @Override
        public long readLong() {
            return parcel.readLong();
        }

        @Nullable
        @Override
        public String readString() {
            return parcel.readString();
        }
    }
}
