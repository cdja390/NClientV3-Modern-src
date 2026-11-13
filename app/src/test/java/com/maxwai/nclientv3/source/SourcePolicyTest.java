package com.maxwai.nclientv3.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.EnumSet;

public class SourcePolicyTest {
    @Test
    public void parsesConfiguredNamesIgnoringUnknownAndPreservingEmpty() {
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EHENTAI),
            SourcePolicy.parse("EHENTAI,unknown,NHENTAI,NHENTAI"));
        assertTrue(SourcePolicy.parse("").isEmpty());
        assertEquals("NHENTAI,EXHENTAI", SourcePolicy.serialize(EnumSet.of(GallerySource.EXHENTAI, GallerySource.NHENTAI)));
        assertTrue(SourcePolicy.migrate("", "both", "nhentai.net", false).isEmpty());
    }

    @Test
    public void migratesAllLegacyChoicesAndHostSwitch() {
        assertEquals(EnumSet.of(GallerySource.NHENTAI), SourcePolicy.migrate(null, "nh", "", false));
        assertEquals(EnumSet.of(GallerySource.EHENTAI), SourcePolicy.migrate(null, "eh", "", false));
        assertEquals(EnumSet.of(GallerySource.EXHENTAI), SourcePolicy.migrate(null, "eh", "", true));
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EHENTAI), SourcePolicy.migrate(null, "both", "", false));
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EXHENTAI), SourcePolicy.migrate(null, "both", "", true));
    }

    @Test
    public void migrationUsesMirrorAndDefaultsEmptyToNh() {
        assertEquals(EnumSet.of(GallerySource.EXHENTAI), SourcePolicy.migrate(null, null, "https://exhentai.org", false));
        assertEquals(EnumSet.of(GallerySource.EHENTAI), SourcePolicy.migrate(null, null, "https://e-hentai.org", false));
        assertEquals(EnumSet.of(GallerySource.NHENTAI), SourcePolicy.migrate(null, null, "other", false));
        assertEquals(EnumSet.of(GallerySource.NHENTAI), SourcePolicy.migrate(null, null, "", true));
    }

    @Test
    public void togglingEhHostPreservesNhAndDoesNotEnableUnselectedSource() {
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EXHENTAI), SourcePolicy.withEhHost(EnumSet.of(GallerySource.NHENTAI, GallerySource.EHENTAI), true));
        assertEquals(EnumSet.of(GallerySource.NHENTAI), SourcePolicy.withEhHost(EnumSet.of(GallerySource.NHENTAI), true));
        assertEquals(EnumSet.of(GallerySource.EHENTAI), SourcePolicy.withEhHost(EnumSet.of(GallerySource.EXHENTAI), false));
    }

    @Test
    public void importedEhAndExCollapseToTheExistingSwitch() {
        String both = "NHENTAI,EHENTAI,EXHENTAI";
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EHENTAI), SourcePolicy.migrate(both, null, "", false));
        assertEquals(EnumSet.of(GallerySource.NHENTAI, GallerySource.EXHENTAI), SourcePolicy.migrate(both, null, "", true));
        assertEquals(EnumSet.of(GallerySource.EXHENTAI), SourcePolicy.migrate("EXHENTAI", null, "", false));
        assertEquals(EnumSet.of(GallerySource.EHENTAI), SourcePolicy.migrate("EHENTAI", null, "", true));
    }
}
