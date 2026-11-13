package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagType;

import org.junit.Test;

/** Small helpers of the e-hentai source. */
public class EhMiscTest {

    @Test
    public void mergedTagMappingRetainsCommonTypesAndUsesSyntheticIds() {
        Tag artist = EhTags.forMergedSearch("artist", "an artist");
        Tag other = EhTags.forMergedSearch("female", "an artist");
        assertEquals(TagType.ARTIST, artist.getType());
        assertEquals(TagType.TAG, other.getType());
        assertTrue(artist.getId() < -1);
        assertNotEquals(artist, other);
        assertEquals("artist:\"an artist$\"", EhFeed.buildQuery(null, java.util.Collections.singleton(artist)));
    }

    @Test
    public void cachedPagesAndDownloadFoldersDistinguishEhFromEx() {
        EhGalleryKey eh = new EhGalleryKey(123, "0123456789", false);
        EhGalleryKey ex = new EhGalleryKey(123, "0123456789", true);
        assertNotEquals(eh, ex);
        assertNotEquals(eh, new EhGalleryKey(123, "a123456789", false));
        assertNotEquals(EhDownloads.folderName(eh), EhDownloads.folderName(ex));
    }

    @Test
    public void tagSearchSyntax() {
        assertEquals("language:chinese$", EhGalleryActivity.tagQuery("language", "chinese"));
        assertEquals("female:\"big breasts$\"", EhGalleryActivity.tagQuery("female", "big breasts"));
        assertEquals("\"some tag$\"", EhGalleryActivity.tagQuery("", "some tag"));
    }

    @Test
    public void downloadFileNamesKeepTheImageType() {
        assertEquals("0001.jpg", EhDownloads.pageFileName(1, "https://x.hath.network/h/abc/keystamp=1/a.jpg"));
        assertEquals("0012.png", EhDownloads.pageFileName(12, "https://x.hath.network/om/1/a.PNG?dl=1"));
        assertEquals("0003.webp", EhDownloads.pageFileName(3, "https://x/a.webp"));
        assertEquals("0004.jpg", EhDownloads.pageFileName(4, "https://x/a.jpeg"));
        assertEquals("0005.jpg", EhDownloads.pageFileName(5, "https://x.hath.network/h/abc"));
        assertEquals("0006.jpg", EhDownloads.pageFileName(6, null));
    }

    @Test
    public void igneousIsReadFromSetCookie() {
        assertEquals("a1b2c3", EhClient.cookieValue("igneous=a1b2c3; expires=Fri; path=/; domain=.exhentai.org", "igneous"));
        assertEquals("mystery", EhClient.cookieValue("igneous=mystery", "igneous"));
        assertNull(EhClient.cookieValue("yay=louder; path=/", "igneous"));
        assertNull(EhClient.cookieValue(null, "igneous"));
    }
}
