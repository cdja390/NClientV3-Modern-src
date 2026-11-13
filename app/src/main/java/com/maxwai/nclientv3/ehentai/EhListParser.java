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
 * Added for NClientV3 Modern 4.7.0. Selectors and URL rules follow api/source/ehentai/* from
 * yosefario-dev/NClientV3 (https://github.com/yosefario-dev/NClientV3, Apache-2.0), rewritten as
 * pure Java so they can be unit tested. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses e-hentai gallery lists (front page, search, popular, favorites, tag pages) in all four
 * display modes: minimal (gltm), compact (gltc, the default), extended (glte) and thumbnail (gld).
 */
public final class EhListParser {
    private static final Pattern NEXT_PARAM = Pattern.compile("[?&]next=([^&#]+)");
    private static final Pattern PAGES = Pattern.compile("(\\d+)\\s+pages?");
    private static final Pattern BG_POS = Pattern.compile("background-position:\\s*(-?\\d+)px\\s+(-?\\d+)px");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");
    private static final Pattern STATIC_ICON = Pattern.compile("/g/[\\w.-]+\\.(png|gif)$");
    private static final Pattern BORDER_COLOR = Pattern.compile("border-color:\\s*#([0-9a-fA-F]{3,6})");
    /** Favorite slot colours as used on list pages (posted_ div border). */
    private static final String[] SLOT_COLORS = {"000", "f00", "fa0", "dd0", "080", "9f4", "4bf", "00f", "508", "e8e"};

    private EhListParser() {
    }

    public static final class Page {
        @NonNull
        public final List<EhGalleryInfo> items;
        /** Value for the next page's {@code next=} parameter, or null on the last page. */
        @Nullable
        public final String nextCursor;
        public final boolean noHits;

        Page(@NonNull List<EhGalleryInfo> items, @Nullable String nextCursor, boolean noHits) {
            this.items = items;
            this.nextCursor = nextCursor;
            this.noHits = noHits;
        }
    }

    @NonNull
    public static Page parse(@Nullable String html, boolean exhentai) throws EhException {
        List<EhGalleryInfo> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return new Page(out, null, true);
        checkBanned(html);
        Document doc = Jsoup.parse(html);
        Set<Long> seen = new HashSet<>();
        Element table = doc.selectFirst(".itg");
        Element scope = table != null ? table : doc.body();
        for (Element link : scope.select("a[href*=/g/]")) {
            EhGalleryKey key = EhGalleryKey.fromHref(link.attr("href"), exhentai);
            if (key == null || !seen.add(key.gid)) continue;
            Element card = cardOf(link);
            EhGalleryInfo g = new EhGalleryInfo(key);
            g.title = title(link, card);
            g.thumb = thumb(card);
            g.category = category(card);
            g.pages = pages(card);
            g.rating = rating(card);
            Element posted = card.selectFirst("[id^=posted_]");
            if (posted != null) {
                g.posted = posted.text().trim();
                g.favoriteSlot = favoriteSlotFromStyle(posted.attr("style"));
            }
            Element uploader = card.selectFirst("a[href*=/uploader/]");
            if (uploader != null) g.uploader = uploader.text().trim();
            g.language = language(card);
            out.add(g);
        }
        boolean noHits = out.isEmpty();
        return new Page(out, nextCursor(doc), noHits);
    }

    static void checkBanned(@NonNull String html) throws EhException {
        if (html.length() < 2000 && html.contains("temporarily banned"))
            throw new EhException(EhException.Kind.IP_BANNED, html.replaceAll("<[^>]+>", " ").trim());
    }

    @Nullable
    static String nextCursor(@NonNull Document doc) {
        Element next = doc.getElementById("unext");
        if (next == null) next = doc.getElementById("dnext");
        if (next == null || !"a".equals(next.tagName())) return null;
        Matcher m = NEXT_PARAM.matcher(next.attr("href"));
        return m.find() ? m.group(1) : null;
    }

    @NonNull
    private static Element cardOf(@NonNull Element link) {
        Element thumbCard = link.closest("div.gl1t");
        if (thumbCard != null) return thumbCard;
        Element row = link.closest("tr");
        return row != null ? row : link;
    }

    @NonNull
    private static String title(@NonNull Element link, @NonNull Element card) {
        Element name = card.selectFirst(".glink");
        if (name != null && !name.text().trim().isEmpty()) return name.text().trim();
        Element img = card.selectFirst("img[title]");
        if (img != null && !img.attr("title").isEmpty()) return img.attr("title");
        return link.text().trim();
    }

    @Nullable
    private static String thumb(@NonNull Element card) {
        for (Element img : card.select("img")) {
            String url = img.attr("data-src");
            if (url.isEmpty()) url = img.attr("src");
            if (url.isEmpty() || url.startsWith("data:")) continue;
            if (STATIC_ICON.matcher(url).find()) continue;
            return EhUrls.absolute(url);
        }
        for (Element el : card.select("[style*=url(]")) {
            Matcher m = CSS_URL.matcher(el.attr("style"));
            if (m.find() && !m.group(1).isEmpty() && !STATIC_ICON.matcher(m.group(1)).find())
                return EhUrls.absolute(m.group(1));
        }
        return null;
    }

    @NonNull
    private static EhCategory category(@NonNull Element card) {
        Element chip = card.selectFirst(".cn, .cs");
        if (chip == null) return EhCategory.UNKNOWN;
        EhCategory c = EhCategory.fromCssClasses(chip.className());
        return c != EhCategory.UNKNOWN ? c : EhCategory.fromLabel(chip.text());
    }

    private static int pages(@NonNull Element card) {
        for (Element div : card.select("div")) {
            if (div.childrenSize() > 0) continue;
            Matcher m = PAGES.matcher(div.text().trim());
            if (m.matches()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    static float rating(@NonNull Element card) {
        Element ir = card.selectFirst(".ir");
        return ir == null ? -1 : ratingFromStyle(ir.attr("style"));
    }

    /** Star sprite offsets: 16 px per star from the right, a -21 px row means half a star less. */
    static float ratingFromStyle(@Nullable String style) {
        if (style == null) return -1;
        Matcher m = BG_POS.matcher(style);
        if (!m.find()) return -1;
        int x = Integer.parseInt(m.group(1));
        int y = Integer.parseInt(m.group(2));
        float r = 5f + x / 16f;
        if (y == -21) r -= 0.5f;
        return Math.max(0f, Math.min(5f, r));
    }

    static int favoriteSlotFromStyle(@Nullable String style) {
        if (style == null) return -1;
        Matcher m = BORDER_COLOR.matcher(style);
        if (!m.find()) return -1;
        String c = m.group(1).toLowerCase(Locale.ROOT);
        if (c.length() == 6 && c.charAt(0) == c.charAt(1) && c.charAt(2) == c.charAt(3) && c.charAt(4) == c.charAt(5))
            c = "" + c.charAt(0) + c.charAt(2) + c.charAt(4);
        for (int i = 0; i < SLOT_COLORS.length; i++) if (SLOT_COLORS[i].equals(c)) return i;
        return -1;
    }

    @Nullable
    private static String language(@NonNull Element card) {
        for (Element gt : card.select(".gt[title^=language:], .gtl[title^=language:]")) {
            String lang = gt.attr("title").substring("language:".length()).trim();
            if (!lang.equals("translated") && !lang.equals("rewrite")) return lang;
        }
        return null;
    }
}
