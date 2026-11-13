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
        final boolean zh = isZh(activity);
        String title = zh ? "清除阅读历史？" : "Clear reading history?";
        String message;
        if (zh) {
            if (count > 0) {
                message = "将删除全部 " + count + " 条阅读历史，此操作无法撤销。\n不会影响收藏、已下载、阅读状态和断点续读进度。";
            } else {
                message = "当前没有阅读历史。仍要清除吗？此操作不会影响收藏、已下载、阅读状态和断点续读进度。";
            }
        } else {
            if (count > 0) {
                message = "This will delete all " + count + " reading-history entries. This cannot be undone.\nFavorites, downloads, reading status, and resume progress are not affected.";
            } else {
                message = "There is no reading history. Clear anyway? Favorites, downloads, reading status, and resume progress are not affected.";
            }
        }
        String positive = zh ? "清除" : "Clear";

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
