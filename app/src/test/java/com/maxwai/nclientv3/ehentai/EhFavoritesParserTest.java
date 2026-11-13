package com.maxwai.nclientv3.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EhFavoritesParserTest {

    @Test
    public void namesFromFavoritesHeader() {
        StringBuilder html = new StringBuilder("<div class=\"ido\"><div class=\"nosel\">");
        for (int i = 0; i < 10; i++) {
            html.append("<div class=\"fp\" onclick=\"\"><div>").append(i * 3).append("</div><div class=\"i\"></div><div>")
                .append(i == 2 ? "Reading" : "Favorites " + i).append("</div></div>");
        }
        html.append("<div class=\"fp fps\"><div>27</div><div></div><div>Show All Favorites</div></div></div></div>");
        String[] names = EhFavoritesParser.folderNames(html.toString());
        assertEquals("Favorites 0", names[0]);
        assertEquals("Reading", names[2]);
        assertEquals("Favorites 9", names[9]);
    }

    @Test
    public void namesFromPopup() {
        String html = "<div onclick=\"\"><div style=\"float:left\"><input type=\"radio\" name=\"favcat\" value=\"0\" /></div>"
            + "<div class=\"i\"></div><div>Later</div></div>"
            + "<div onclick=\"\"><div style=\"float:left\"><input type=\"radio\" name=\"favcat\" value=\"favdel\" /></div>"
            + "<div>Remove from Favorites</div></div>";
        String[] names = EhFavoritesParser.folderNames(html);
        assertEquals("Later", names[0]);
        assertNull(names[1]);
        assertTrue(EhFavoritesParser.popupHasFavorite(html));
        assertFalse(EhFavoritesParser.popupHasFavorite("<input name=\"favcat\" value=\"1\">"));
    }
}
