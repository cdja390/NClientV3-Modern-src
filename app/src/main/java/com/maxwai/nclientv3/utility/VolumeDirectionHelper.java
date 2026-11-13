package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.ZoomActivity;

public class VolumeDirectionHelper {
    public static final int MENU_ID_VOLUME_DIRECTION = 0x70ffff10;
    public static final String PREF_NAME = "Settings";
    public static final String PREF_KEY_VOLUME_SIDE = "volumeSide";
    public static final String PREF_KEY_SETTING_ROW = "pref_volume_side";

    public static void setupMenu(Activity activity, Menu menu) {
        if (activity == null || menu == null) return;
        SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean side = sp.getBoolean(PREF_KEY_VOLUME_SIDE, true);
        String title = side ? "音量键换页: 音量+下一页" : "音量键换页: 音量-下一页";

        MenuItem item = menu.findItem(MENU_ID_VOLUME_DIRECTION);
        if (item == null) {
            item = menu.add(0, MENU_ID_VOLUME_DIRECTION, 1000, title);
        } else {
            item.setTitle(title);
        }
    }

    public static boolean onMenuItemSelected(Activity activity, MenuItem item) {
        if (item != null && item.getItemId() == MENU_ID_VOLUME_DIRECTION) {
            showDirectionDialog(activity);
            return true;
        }
        return false;
    }

    public static void showDirectionDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final boolean currentSide = sp.getBoolean(PREF_KEY_VOLUME_SIDE, true);
        final String[] options = new String[] {
            "音量+ 下一页，音量- 上一页 (默认)",
            "音量- 下一页，音量+ 上一页"
        };
        int checkedItem = currentSide ? 0 : 1;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle("音量键翻页方向");
        builder.setSingleChoiceItems(options, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                boolean newSide = (which == 0);
                if (newSide != currentSide) {
                    setVolumeSide(activity, newSide);
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public static void setVolumeSide(Activity activity, boolean side) {
        if (activity == null) return;
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_VOLUME_SIDE, side)
                .apply();

        if (activity instanceof ZoomActivity) {
            ((ZoomActivity) activity).setVolumeSide(side);
        }

        Toast.makeText(activity, side ? "音量+切换下一页" : "音量-切换下一页", Toast.LENGTH_SHORT).show();
        activity.invalidateOptionsMenu();
    }

    public static void bindPreference(final PreferenceFragmentCompat fragment) {
        if (fragment == null) return;
        final Preference pref = fragment.findPreference(PREF_KEY_SETTING_ROW);
        if (pref == null) return;

        updatePreferenceSummary(pref.getContext(), pref);

        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showSettingsDialog(preference.getContext(), preference);
                return true;
            }
        });
    }

    public static void updatePreferenceSummary(Context context, Preference pref) {
        if (context == null || pref == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean side = sp.getBoolean(PREF_KEY_VOLUME_SIDE, true);
        pref.setSummary(side ? "当前：音量+下一页，音量-上一页 (默认)" : "当前：音量-下一页，音量+上一页");
    }

    public static void showSettingsDialog(final Context context, final Preference preference) {
        if (context == null) return;

        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final boolean currentSide = sp.getBoolean(PREF_KEY_VOLUME_SIDE, true);
        final String[] options = new String[] {
            "音量+ 下一页，音量- 上一页 (默认)",
            "音量- 下一页，音量+ 上一页"
        };
        int checkedItem = currentSide ? 0 : 1;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("音量键翻页方向");
        builder.setSingleChoiceItems(options, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                boolean newSide = (which == 0);
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_KEY_VOLUME_SIDE, newSide)
                        .apply();
                updatePreferenceSummary(context, preference);
                Toast.makeText(context, newSide ? "音量+切换下一页" : "音量-切换下一页", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
