/* Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Added for Modern 4.7.2; one selection shared by toolbar and settings. */
package com.maxwai.nclientv3.source;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;

import java.util.EnumSet;

public final class SourceDialogs {
    private SourceDialogs() {
    }

    public static int label(GallerySource source) {
        switch (source) {
            case EXHENTAI: return R.string.source_name_ex;
            case EHENTAI: return R.string.source_name_eh;
            default: return R.string.source_name_nh;
        }
    }

    public static String summary(Context context) {
        StringBuilder text = new StringBuilder();
        for (GallerySource source : SourcePreferences.enabled(context)) {
            if (text.length() > 0) text.append(" / ");
            text.append(context.getString(label(source)));
        }
        return text.length() == 0 ? context.getString(R.string.sources_choose_hint) : text.toString();
    }

    public static void show(Context context, Runnable onChanged) {
        EnumSet<GallerySource> current = SourcePreferences.enabled(context);
        GallerySource eh = SourcePreferences.ehSource(context);
        boolean[] checked = {current.contains(GallerySource.NHENTAI),
            current.contains(GallerySource.EHENTAI) || current.contains(GallerySource.EXHENTAI)};
        CharSequence[] names = {context.getString(label(GallerySource.NHENTAI)), context.getString(label(eh))};
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.gallery_sources_title)
            .setMultiChoiceItems(names, checked, (d, which, selected) -> {
                checked[which] = selected;
                ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked[0] || checked[1]);
            })
            .setPositiveButton(R.string.ok, (d, which) -> {
                EnumSet<GallerySource> selected = EnumSet.noneOf(GallerySource.class);
                if (checked[0]) selected.add(GallerySource.NHENTAI);
                if (checked[1]) selected.add(eh);
                if (SourcePreferences.save(context, selected)) onChanged.run();
            })
            .setNegativeButton(R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked[0] || checked[1]));
        dialog.show();
    }
}
