/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.source;

import android.content.Context;
import android.database.Cursor;

import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.ehentai.EhDatabase;
import com.maxwai.nclientv3.ehentai.EhSimpleGallery;
import com.maxwai.nclientv3.settings.Global;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GalleryHistory {
    private GalleryHistory() {
    }

    public static List<GenericGallery> rows(Context context, String query) {
        int limit = Math.max(0, Global.getMaxHistory());
        List<GenericGallery> rows = new ArrayList<>();
        if (limit == 0) return rows;
        List<EhDatabase.Entry> eh = EhDatabase.get(context).history(limit);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        try (Cursor nh = Queries.HistoryTable.recentRows(limit)) {
            boolean hasNh = nh.moveToFirst();
            int nextEh = 0;
            int visited = 0;
            int timeColumn = nh.getColumnIndexOrThrow(Queries.HistoryTable.TIME);
            while (visited < limit && (hasNh || nextEh < eh.size())) {
                GenericGallery row;
                if (hasNh && (nextEh == eh.size() || nh.getLong(timeColumn) >= eh.get(nextEh).time)) {
                    row = new SimpleGallery(nh);
                    hasNh = nh.moveToNext();
                } else {
                    row = new EhSimpleGallery(eh.get(nextEh++));
                }
                visited++;
                if (needle.isEmpty() || row.getTitle().toLowerCase(Locale.ROOT).contains(needle)) rows.add(row);
            }
        }
        return rows;
    }

    public static int count(Context context) {
        return Queries.HistoryTable.count() + EhDatabase.get(context).historyCount();
    }

    public static void clear(Context context) {
        Queries.HistoryTable.emptyHistory();
        EhDatabase.get(context).clearHistory();
    }
}
