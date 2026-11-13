package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.maxwai.nclientv3.utility.MiniJson;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** ehentai.db rows survive a backup archive round trip, in a shape older builds can import. */
public class EhBackupTest {

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, List<Map<String, Object>>> sample() {
        Map<String, List<Map<String, Object>>> t = new LinkedHashMap<>();
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(row("gid", 4325674L, "token", "08c0da7832", "ex", 0L, "title", "標題 \"quoted\" \\ back\nline",
            "thumb", null, "category", 2L, "pages", 29L, "last_page", 12L, "read_count", 3L,
            "read_seconds", 640L, "last_read", 1763000000000L));
        t.put(EhDatabase.HISTORY, history);
        List<Map<String, Object>> fav = new ArrayList<>();
        fav.add(row("gid", 4325690L, "token", "7c4cfd2995", "ex", 1L, "title", "x", "thumb",
            "https://ehgt.org/t/ab/cd/x.webp", "category", 512L, "pages", 328L, "added", 1L));
        t.put(EhDatabase.FAVORITES, fav);
        t.put(EhDatabase.DOWNLOADS, new ArrayList<>());
        return t;
    }

    @Test
    public void entryRoundTripsThroughAZipArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("Database.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(EhBackup.ENTRY_NAME));
            zip.write(EhBackup.encode(sample()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Map<String, List<Map<String, Object>>> back = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!EhBackup.ENTRY_NAME.equals(e.getName())) continue;
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) body.write(buf, 0, n);
                back = EhBackup.decode(new String(body.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(sample(), back);
    }

    @Test
    public void entryLooksLikeSharedPreferencesForOlderBuilds() {
        // Importers before 4.7.0 read every non-database entry as {"key":{"TYPE":value}}.
        Map<String, Object> outer = MiniJson.parseObject(EhBackup.encode(sample()));
        Set<String> types = new HashSet<>(Arrays.asList("FLOAT", "INT", "LONG", "STRING_SET", "STRING", "BOOLEAN"));
        assertTrue(outer != null && !outer.isEmpty());
        for (Map.Entry<String, Object> e : outer.entrySet()) {
            assertTrue(e.getValue() instanceof Map);
            Map<?, ?> typed = (Map<?, ?>) e.getValue();
            assertEquals(1, typed.size());
            String type = (String) typed.keySet().iterator().next();
            assertTrue(type, types.contains(type));
        }
        assertEquals(EhBackup.ENTRY_NAME, "EHentaiBackup.json");
    }

    @Test
    public void emptyDatabaseRoundTrips() throws IOException {
        assertTrue(EhBackup.decode(EhBackup.encode(new LinkedHashMap<>())).isEmpty());
    }

    @Test
    public void brokenEntryIsRejected() {
        for (String bad : new String[]{null, "", "nope", "{\"payload\":{\"STRING\":\"[]\"}}", "{\"payload\":{\"INT\":1}}"}) {
            try {
                EhBackup.decode(bad);
                fail("accepted " + bad);
            } catch (IOException expected) {
                // rejected as it should be
            }
        }
    }
}
