package com.maxwai.nclientv3.ehentai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Saved e-hentai pages (fetched 2026-11-13) used by the parser tests. */
final class Fixtures {
    private Fixtures() {
    }

    static String load(String name) throws IOException {
        try (InputStream in = Fixtures.class.getResourceAsStream("/ehentai/" + name)) {
            if (in == null) throw new IOException("missing fixture " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
