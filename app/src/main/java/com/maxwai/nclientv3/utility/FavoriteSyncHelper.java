package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.FavoriteActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.settings.FavoriteSyncManager;
import com.maxwai.nclientv3.settings.Login;

public class FavoriteSyncHelper {
    private static final String PREF_NAME = "FavoriteSync";
    private static final String KEY_LAST_CHOICE = "last_sync_choice";
    private static final String KEY_CUSTOM_PAGES = "custom_sync_pages";

    public static void showSyncDialog(final FavoriteActivity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (!Login.canAccessAuthenticatedApi(activity)) {
            Toast.makeText(activity, R.string.favorite_sync_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (FavoriteSyncManager.isRunning()) {
            activity.showSyncProgressDialog();
            return;
        }

        final SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int lastChoice = prefs.getInt(KEY_LAST_CHOICE, 0);
        if (lastChoice < 0 || lastChoice > 4) {
            lastChoice = 0;
        }

        final String[] options = new String[] {
            activity.getString(R.string.fav_sync_option_1),
            activity.getString(R.string.fav_sync_option_3),
            activity.getString(R.string.fav_sync_option_5),
            activity.getString(R.string.fav_sync_option_all),
            activity.getString(R.string.fav_sync_option_custom)
        };

        final int[] selected = new int[] { lastChoice };

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.fav_sync_dialog_title)
            .setSingleChoiceItems(options, lastChoice, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selected[0] = which;
                }
            })
            .setPositiveButton(R.string.fav_sync_start, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int choice = selected[0];
                    prefs.edit().putInt(KEY_LAST_CHOICE, choice).apply();

                    if (choice == 0) {
                        activity.syncFavorites(1);
                    } else if (choice == 1) {
                        activity.syncFavorites(3);
                    } else if (choice == 2) {
                        activity.syncFavorites(5);
                    } else if (choice == 3) {
                        activity.syncFavorites(0);
                    } else if (choice == 4) {
                        showCustomPagesDialog(activity, prefs);
                    }
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private static void showCustomPagesDialog(final FavoriteActivity activity, final SharedPreferences prefs) {
        int lastCustom = prefs.getInt(KEY_CUSTOM_PAGES, 10);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.fav_sync_custom_hint);
        input.setText(String.valueOf(lastCustom));
        input.setSelection(input.getText().length());

        FrameLayout container = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginHorizontal = (int) (24 * activity.getResources().getDisplayMetrics().density);
        int marginVertical = (int) (8 * activity.getResources().getDisplayMetrics().density);
        params.leftMargin = marginHorizontal;
        params.rightMargin = marginHorizontal;
        params.topMargin = marginVertical;
        params.bottomMargin = marginVertical;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.fav_sync_custom_title)
            .setMessage(R.string.fav_sync_custom_message)
            .setView(container)
            .setPositiveButton(R.string.fav_sync_start, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String text = input.getText().toString().trim();
                    int pages = 0;
                    try {
                        pages = Integer.parseInt(text);
                    } catch (Exception ignored) {}

                    if (pages <= 0) {
                        Toast.makeText(activity, R.string.fav_sync_custom_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    prefs.edit().putInt(KEY_CUSTOM_PAGES, pages).apply();
                    activity.syncFavorites(pages);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
}
