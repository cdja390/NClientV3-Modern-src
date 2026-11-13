/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (source identity)
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
 * Added for NClientV3 Modern 4.7.2. Equality is source plus id, matching the upstream
 * {@code source.ordinal():id} dedup key. The token is carried for routing and is not
 * part of equality. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stable identity of one list row. Replaces the 4.7.1 negative-gid trick.
 */
public final class SourceKey {
    @NonNull
    public final GallerySource source;
    public final long id;
    @Nullable
    public final String token;

    public SourceKey(@NonNull GallerySource source, long id, @Nullable String token) {
        this.source = source;
        this.id = id;
        this.token = token;
    }

    /** {@code SOURCE:id}. The token is intentionally absent. */
    @NonNull
    public String stableKey() {
        return source.name() + ":" + id;
    }

    public void write(@NonNull RowCodec.FieldWriter out) {
        out.writeByte(source.ordinal());
        out.writeLong(id);
        out.writeString(token);
    }

    @NonNull
    public static SourceKey read(@NonNull RowCodec.FieldReader in) {
        int ordinal = in.readByte();
        GallerySource[] values = GallerySource.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("source ordinal " + ordinal);
        }
        return new SourceKey(values[ordinal], in.readLong(), in.readString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceKey)) return false;
        SourceKey other = (SourceKey) o;
        return id == other.id && source == other.source;
    }

    @Override
    public int hashCode() {
        return source.hashCode() * 31 + Long.hashCode(id);
    }

    @NonNull
    @Override
    public String toString() {
        return stableKey();
    }
}
