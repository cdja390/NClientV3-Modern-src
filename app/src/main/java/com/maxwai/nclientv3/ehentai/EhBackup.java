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
 * Added for NClientV3 Modern 4.7.0 (e-hentai source, no GeckoView). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.utility.MiniJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backup codec for ehentai.db. The tables are serialised to JSON and wrapped in the same
 * {@code {"key":{"TYPE":value}}} shape the backup importer uses for shared preferences, so a
 * build older than 4.7.0 that meets this zip entry stores it as an unused preferences file
 * instead of failing the import. Account cookies are never part of the backup.
 * See docs/BACKUP_FORMAT.md.
 */
public final class EhBackup {
    /** Zip entry name inside the backup archive. */
    public static final String ENTRY_NAME = "EHentaiBackup.json";
    public static final int FORMAT_VERSION = 1;

    private EhBackup() {
    }

    @NonNull
    public static String encode(@NonNull Map<String, List<Map<String, Object>>> tables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", (long) FORMAT_VERSION);
        payload.put("tables", tables);
        Map<String, Object> wrappedPayload = new LinkedHashMap<>();
        wrappedPayload.put("STRING", MiniJson.write(payload));
        Map<String, Object> wrappedVersion = new LinkedHashMap<>();
        wrappedVersion.put("INT", (long) FORMAT_VERSION);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("payload", wrappedPayload);
        outer.put("version", wrappedVersion);
        return MiniJson.write(outer);
    }

    /** Decodes an entry written by {@link #encode}. Unknown value types are dropped. */
    @NonNull
    public static Map<String, List<Map<String, Object>>> decode(@Nullable String entry) throws IOException {
        Map<String, Object> outer = MiniJson.parseObject(entry);
        if (outer == null) throw new IOException("e-hentai backup entry is not JSON");
        Object wrapped = outer.get("payload");
        String inner = null;
        if (wrapped instanceof Map) {
            Object s = ((Map<?, ?>) wrapped).get("STRING");
            if (s instanceof String) inner = (String) s;
        }
        Map<String, Object> payload = MiniJson.parseObject(inner);
        if (payload == null) throw new IOException("e-hentai backup payload missing");
        if (MiniJson.optLong(payload, "format", 0) < 1) throw new IOException("e-hentai backup format unknown");
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        Object tables = payload.get("tables");
        if (!(tables instanceof Map)) return out;
        for (Map.Entry<?, ?> t : ((Map<?, ?>) tables).entrySet()) {
            if (!(t.getKey() instanceof String) || !(t.getValue() instanceof List)) continue;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object row : (List<?>) t.getValue()) {
                if (!(row instanceof Map)) continue;
                Map<String, Object> clean = new LinkedHashMap<>();
                for (Map.Entry<?, ?> c : ((Map<?, ?>) row).entrySet()) {
                    Object v = c.getValue();
                    if (!(c.getKey() instanceof String)) continue;
                    if (v == null || v instanceof String || v instanceof Long || v instanceof Double)
                        clean.put((String) c.getKey(), v);
                    else if (v instanceof Boolean) clean.put((String) c.getKey(), ((Boolean) v) ? 1L : 0L);
                }
                rows.add(clean);
            }
            out.put((String) t.getKey(), rows);
        }
        return out;
    }
}
