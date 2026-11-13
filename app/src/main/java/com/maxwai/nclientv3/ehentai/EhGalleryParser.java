/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (selector and URL knowledge)
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
 * Added for NClientV3 Modern 4.7.0. Selectors follow EHentaiGalleryPageParser, EHentaiPageResolver and
 * EHentaiFavorites from yosefario-dev/NClientV3 (https://github.com/yosefario-dev/NClientV3,
 * Apache-2.0), rewritten as pure Java so they can be unit tested. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a gallery page (/g/gid/token/?p=N). */
public final class EhGalleryParser {
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");
    private static final Pattern SPRITE = Pattern.compile(
        "width:\\s*(\\d+)px;\\s*height:\\s*(\\d+)px;.*?url\\(\\s*['\"]?(.*?)['\"]?\\s*\\)\\s*(-?\\d+)(?:px)?\\s+(-?\\d+)(?:px)?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern VIEWER = Pattern.compile("/s/([0-9a-f]+)/(\\d+)-(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("([\\d,]+)");
    private static final Pattern DECIMAL = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern BG_Y = Pattern.compile("background-position:\\s*-?\\d+px\\s+(-?\\d+)px");
    private static final Pattern RGB = Pattern.compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)");
    private static final int[][] SLOT_RGB = {
        {0, 0, 0}, {240, 0, 0}, {240, 160, 0}, {208, 208, 0}, {0, 128, 0},
        {144, 240, 64}, {64, 176, 240}, {0, 0, 240}, {80, 0, 128}, {224, 128, 224},
    };

    private EhGalleryParser() {
    }

    @NonNull
    public static EhGalleryDetail parse(@Nullable String html, @NonNull EhGalleryKey key) throws EhException {
        if (html == null || html.trim().isEmpty())
            throw new EhException(key.exhentai ? EhException.Kind.SAD_PANDA : EhException.Kind.PARSE, "empty page");
        EhListParser.checkBanned(html);
        Document doc = Jsoup.parse(html);
        Element gn = doc.getElementById("gn");
        if (gn == null) {
            String text = doc.text();
            if (text.contains("removed") || text.contains("unavailable") || text.contains("Key missing"))
                throw new EhException(EhException.Kind.GALLERY_GONE, text.length() > 200 ? text.substring(0, 200) : text);
            throw new EhException(EhException.Kind.PARSE, "no gallery title");
        }
        EhGalleryDetail d = new EhGalleryDetail(key);
        d.title = gn.text().trim();
        Element gj = doc.getElementById("gj");
        if (gj != null && !gj.text().trim().isEmpty()) d.titleJpn = gj.text().trim();
        Element cat = doc.selectFirst("#gdc .cs, #gdc .cn, #gdc div");
        if (cat != null) {
            d.category = EhCategory.fromCssClasses(cat.className());
            if (d.category == EhCategory.UNKNOWN) d.category = EhCategory.fromLabel(cat.text());
        }
        Element uploader = doc.selectFirst("#gdn a");
        if (uploader != null) d.uploader = uploader.text().trim();
        Element cover = doc.selectFirst("#gd1 div[style*=url(], #gd1 img");
        if (cover != null) {
            if ("img".equals(cover.tagName())) d.thumb = EhUrls.absolute(cover.attr("src"));
            else {
                Matcher m = CSS_URL.matcher(cover.attr("style"));
                if (m.find()) d.thumb = EhUrls.absolute(m.group(1));
            }
        }
        for (Element row : doc.select("#gdd tr")) {
            Element label = row.selectFirst(".gdt1");
            Element value = row.selectFirst(".gdt2");
            if (label == null || value == null) continue;
            String l = label.text().trim().toLowerCase(Locale.ROOT);
            String v = value.ownText().trim().isEmpty() ? value.text().trim() : value.ownText().trim();
            if (l.startsWith("posted")) d.posted = v;
            else if (l.startsWith("language")) d.language = v.replace("\u00a0", " ").trim();
            else if (l.startsWith("file size")) d.fileSize = v;
            else if (l.startsWith("length")) d.pages = firstInt(v, 0);
            else if (l.startsWith("favorited")) d.favoritedCount = firstInt(value.text(), -1);
            else if (l.startsWith("parent")) {
                Element a = value.selectFirst("a");
                if (a != null) d.parent = EhGalleryKey.fromHref(a.attr("href"), key.exhentai);
            }
        }
        Element ratingLabel = doc.getElementById("rating_label");
        if (ratingLabel != null) {
            Matcher m = DECIMAL.matcher(ratingLabel.text());
            if (m.find()) d.rating = Float.parseFloat(m.group(1));
        }
        Element ratingCount = doc.getElementById("rating_count");
        if (ratingCount != null) d.ratingCount = firstInt(ratingCount.text(), -1);
        parseTags(doc, d);
        parseFavorite(doc, d);
        parsePreviews(doc, d);
        d.previewPages = previewPageCount(doc);
        return d;
    }

    private static int firstInt(@Nullable String text, int def) {
        if (text == null) return def;
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) return def;
        try {
            return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void parseTags(Document doc, EhGalleryDetail d) {
        for (Element row : doc.select("#taglist tr")) {
            Element tc = row.selectFirst("td.tc");
            String ns = tc == null ? "" : tc.text().trim();
            if (ns.endsWith(":")) ns = ns.substring(0, ns.length() - 1);
            List<String> list = new ArrayList<>();
            for (Element a : row.select("div[id^=td_] a, .gt a, .gtl a, .gtw a")) {
                String t = a.text().trim();
                if (!t.isEmpty() && !list.contains(t)) list.add(t);
            }
            if (!list.isEmpty()) d.tags.put(ns, list);
        }
    }

    static void parseFavorite(Document doc, EhGalleryDetail d) {
        Element link = doc.getElementById("favoritelink");
        String linkText = link == null ? "" : link.text().trim();
        if (link == null || linkText.equalsIgnoreCase("Add to Favorites")) {
            d.favoriteSlot = -1;
            return;
        }
        d.favoriteName = linkText;
        d.favoriteSlot = 0;
        Element fav = doc.getElementById("fav");
        if (fav == null) return;
        Element icon = fav.selectFirst(".i");
        String style = (icon != null ? icon.attr("style") : "") + ";" + fav.attr("style");
        if (icon != null && !icon.attr("title").isEmpty()) d.favoriteName = icon.attr("title");
        Matcher rgb = RGB.matcher(style);
        if (rgb.find()) {
            int r = Integer.parseInt(rgb.group(1)), g = Integer.parseInt(rgb.group(2)), b = Integer.parseInt(rgb.group(3));
            for (int i = 0; i < SLOT_RGB.length; i++) {
                if (SLOT_RGB[i][0] == r && SLOT_RGB[i][1] == g && SLOT_RGB[i][2] == b) {
                    d.favoriteSlot = i;
                    return;
                }
            }
        }
        Matcher y = BG_Y.matcher(style);
        if (y.find()) {
            int slot = (Math.abs(Integer.parseInt(y.group(1))) - 2) / 19;
            if (slot >= 0 && slot <= 9) d.favoriteSlot = slot;
        }
    }

    private static void parsePreviews(Document doc, EhGalleryDetail d) {
        for (Element a : doc.select("#gdt a[href*=/s/]")) {
            String href = a.attr("href");
            Matcher v = VIEWER.matcher(href);
            if (!v.find()) continue;
            int index = Integer.parseInt(v.group(3));
            String url = EhUrls.absolute(href);
            Element img = a.selectFirst("img");
            Element styled = a.selectFirst("[style*=url(]");
            if (styled != null) {
                Matcher s = SPRITE.matcher(styled.attr("style"));
                if (s.find()) {
                    d.previews.add(new EhGalleryDetail.EhPreview(index, url, EhUrls.absolute(s.group(3)),
                        -Integer.parseInt(s.group(4)), -Integer.parseInt(s.group(5)),
                        Integer.parseInt(s.group(1)), Integer.parseInt(s.group(2))));
                    continue;
                }
                Matcher u = CSS_URL.matcher(styled.attr("style"));
                if (u.find()) {
                    d.previews.add(new EhGalleryDetail.EhPreview(index, url, EhUrls.absolute(u.group(1)), 0, 0, 0, 0));
                    continue;
                }
            }
            String src = img == null ? null : img.attr("src");
            d.previews.add(new EhGalleryDetail.EhPreview(index, url,
                src == null || src.isEmpty() ? null : EhUrls.absolute(src), 0, 0, 0, 0));
        }
    }

    static int previewPageCount(Document doc) {
        int max = 1;
        for (Element td : doc.select(".ptt td")) {
            String t = td.text().trim();
            if (t.matches("\\d+")) max = Math.max(max, Integer.parseInt(t));
        }
        return max;
    }
}
