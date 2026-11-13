/* Copyright 2026 yosefario-dev and NClientV3 contributors (EHentaiPager).
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Adapted at b2ee7ed for parsed page caching in Modern 4.7.2.
 * See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.ehentai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cursor chains are shared; refreshing page 1 resets just that query. */
final class EhPager {
    private static final Map<String, Chain> CHAINS = new LinkedHashMap<String, Chain>(24, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Chain> eldest) {
            return size() > 24;
        }
    };

    private EhPager() {
    }

    static EhFeed.Result page(EhFeed.UrlBuilder urls, int requestedPage, EhFeed.Fetcher fetcher) throws IOException {
        String base = urls.url(null);
        Chain chain;
        synchronized (CHAINS) {
            chain = CHAINS.computeIfAbsent(base, ignored -> new Chain());
        }
        int page = Math.max(0, requestedPage - 1);
        synchronized (chain) {
            if (page == 0 || chain.cursors.isEmpty()) {
                EhListParser.Page first = fetcher.fetch(base);
                chain.reset(first);
                if (page == 0) return result(first);
            }
            if (page - chain.cursors.size() >= EhFeed.MAX_WALK) return empty();
            while (chain.cursors.size() <= page) {
                int next = chain.cursors.size();
                String cursor = chain.cursors.get(next - 1);
                if (cursor == null || cursor.isEmpty()) return empty();
                EhListParser.Page parsed = fetcher.fetch(urls.url(cursor));
                chain.cursors.add(parsed.nextCursor);
                chain.pages.put(next, parsed);
                if (next == page) return result(parsed);
            }
            EhListParser.Page cached = chain.pages.get(page);
            if (cached != null) return result(cached);
            String cursor = chain.cursors.get(page - 1);
            // Match upstream: an evicted page is fetched using its remembered cursor.
            if (cursor == null || cursor.isEmpty()) return empty();
            EhListParser.Page fetched = fetcher.fetch(urls.url(cursor));
            String rememberedNext = chain.cursors.get(page);
            // Upstream hasNext reads the chain even after an evicted page is fetched again.
            return new EhFeed.Result(fetched.items, rememberedNext != null && !rememberedNext.isEmpty());
        }
    }

    static void reset() {
        synchronized (CHAINS) {
            CHAINS.clear();
        }
    }

    private static EhFeed.Result result(EhListParser.Page page) {
        return new EhFeed.Result(page.items, page.nextCursor != null && !page.nextCursor.isEmpty());
    }

    private static EhFeed.Result empty() {
        return new EhFeed.Result(Collections.emptyList(), false);
    }

    private static final class Chain {
        final List<String> cursors = new ArrayList<>();
        final Map<Integer, EhListParser.Page> pages = new LinkedHashMap<Integer, EhListParser.Page>(8, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, EhListParser.Page> eldest) {
                return size() > 4;
            }
        };

        void reset(EhListParser.Page first) {
            cursors.clear();
            pages.clear();
            cursors.add(first.nextCursor);
            pages.put(0, first);
        }
    }
}
