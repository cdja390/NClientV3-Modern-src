package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            List<?> hist = Queries.HistoryTable.getHistory();
            count = hist == null ? 0 : hist.size();
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
            Queries.HistoryTable.emptyHistory();
        } catch (Throwable t) {
            return;
        }
        try {
            Object adapter = findField(activity, "adapter");
            if (adapter != null) {
                adapter.getClass().getMethod("restartDataset", List.class)
                    .invoke(adapter, new ArrayList<Object>());
            }
        } catch (Throwable ignored) {
        }
        try {
            BottomNavRouter.noteHistory(activity);
        } catch (Throwable ignored) {
        }
        try {
            View empty = activity.findViewById(R.id.history_empty);
            if (empty != null) {
                empty.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isZh(Activity activity) {
        try {
            Locale locale;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                locale = activity.getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = activity.getResources().getConfiguration().locale;
            }
            if (locale == null) return false;
            String lang = locale.getLanguage();
            return lang != null && lang.toLowerCase(Locale.US).startsWith("zh");
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object findField(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
