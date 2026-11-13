package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.HistoryActivity;
import com.maxwai.nclientv3.source.GalleryHistory;

/**
 * Confirms before wiping the History table. emptyHistory() only DELETE FROM History
 * and does not touch Favorite, Downloads, Status, StatusManga, or Resume.
 */
public class HistoryClearHelper {

    public static boolean onClearClicked(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return true;
        }
        int count = 0;
        try {
            count = GalleryHistory.count(activity);
        } catch (Throwable ignored) {
        }
        String title = activity.getString(R.string.history_clear_title);
        String message;
        if (count > 0) {
            message = activity.getString(R.string.history_clear_message_count, count);
        } else {
            message = activity.getString(R.string.history_clear_message_empty);
        }
        String positive = activity.getString(R.string.history_clear_confirm);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doClear(activity);
                }
            });
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                try {
                    View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    if (cancel != null) {
                        cancel.requestFocus();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        dialog.show();
        return true;
    }

    private static void doClear(Activity activity) {
        try {
            GalleryHistory.clear(activity);
        } catch (Throwable t) {
            LogUtility.e("Cannot clear reading history", t);
            return;
        }
        if (activity instanceof HistoryActivity) ((HistoryActivity) activity).onHistoryCleared();
    }
}
