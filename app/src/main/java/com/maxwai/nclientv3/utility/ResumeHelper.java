package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import com.maxwai.nclientv3.R;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.maxwai.nclientv3.ZoomActivity;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;

public class ResumeHelper {
    public static final String PREF_AUTO_JUMP = "auto_jump_last_read";
    public static final String PREF_RESET_PROMPT = "reset_auto_jump_prompt";

    /**
     * Called when a gallery is opened in GalleryActivity.loadGallery.
     */
    public static void handleResumeOnLoad(
            final Activity activity,
            final GenericGallery gallery,
            final Parcelable directory) {
        if (activity == null || gallery == null || activity.isFinishing()) {
            return;
        }

        try {
            final int galleryId = gallery.getId();
            final int lastPage = Queries.ResumeTable.pageFromId(galleryId);
            final SharedPreferences sp = activity.getSharedPreferences("Settings", Context.MODE_PRIVATE);
            final boolean contains = sp.contains(PREF_AUTO_JUMP);
            final boolean autoJump = sp.getBoolean(PREF_AUTO_JUMP, false);

            if (lastPage <= 0) {
                // Not read before or page 0
                return;
            }

            if (contains) {
                if (autoJump) {
                    openZoom(activity, gallery, directory, lastPage);
                }
                return;
            }

            // User has not set preference yet -> Prompt
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    showResumePromptDialog(activity, gallery, directory, lastPage);
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Shows prompt dialog asking whether to jump to the last read page.
     */
    public static void showResumePromptDialog(
            final Activity activity,
            final GenericGallery gallery,
            final Parcelable directory,
            final int lastPage) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        final SharedPreferences sp = activity.getSharedPreferences("Settings", Context.MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.resume_dialog_title);

        // Main layout container
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dp2px(activity, 22);
        int padTop = dp2px(activity, 14);
        int padBottom = dp2px(activity, 8);
        root.setPadding(padH, padTop, padH, padBottom);

        // 1. Prompt message
        TextView msg = new TextView(activity);
        msg.setText(activity.getString(R.string.resume_dialog_message, lastPage));
        msg.setTextSize(15.5f);
        root.addView(msg);

        // 2. Quick persistent choice section label
        TextView quickLabel = new TextView(activity);
        quickLabel.setText(R.string.resume_quick_label);
        quickLabel.setTextSize(12.5f);
        quickLabel.setAlpha(0.65f);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = dp2px(activity, 14);
        qlp.bottomMargin = dp2px(activity, 4);
        quickLabel.setLayoutParams(qlp);
        root.addView(quickLabel);

        // 3. Quick action buttons: [ 设为默认是 ] [ 设为默认否 ]
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp2px(activity, 10);
        btnRow.setLayoutParams(rowParams);

        Button btnDefaultYes = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        btnDefaultYes.setText(R.string.resume_default_yes);
        btnDefaultYes.setTextSize(13f);
        btnDefaultYes.setPadding(dp2px(activity, 4), dp2px(activity, 6), dp2px(activity, 4), dp2px(activity, 6));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p1.rightMargin = dp2px(activity, 4);
        btnDefaultYes.setLayoutParams(p1);

        Button btnDefaultNo = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        btnDefaultNo.setText(R.string.resume_default_no);
        btnDefaultNo.setTextSize(13f);
        btnDefaultNo.setPadding(dp2px(activity, 4), dp2px(activity, 6), dp2px(activity, 4), dp2px(activity, 6));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p2.leftMargin = dp2px(activity, 4);
        btnDefaultNo.setLayoutParams(p2);

        btnRow.addView(btnDefaultYes);
        btnRow.addView(btnDefaultNo);
        root.addView(btnRow);

        // 4. Checkbox: 不再提示（记住本次选择）
        final CheckBox cbDontAsk = new CheckBox(activity);
        cbDontAsk.setText(R.string.resume_dont_ask);
        cbDontAsk.setTextSize(14f);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbParams.bottomMargin = dp2px(activity, 4);
        cbDontAsk.setLayoutParams(cbParams);
        root.addView(cbDontAsk);

        builder.setView(root);

        // Single-time action buttons: 否, 是
        builder.setNegativeButton(R.string.resume_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (cbDontAsk.isChecked()) {
                    sp.edit().putBoolean(PREF_AUTO_JUMP, false).apply();
                    Toast.makeText(activity, R.string.resume_set_never, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(R.string.resume_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (cbDontAsk.isChecked()) {
                    sp.edit().putBoolean(PREF_AUTO_JUMP, true).apply();
                    Toast.makeText(activity, R.string.resume_set_always, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                openZoom(activity, gallery, directory, lastPage);
            }
        });

        final AlertDialog dialog = builder.create();

        // Handle quick persistent buttons
        btnDefaultYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean(PREF_AUTO_JUMP, true).apply();
                Toast.makeText(activity, R.string.resume_set_always, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                openZoom(activity, gallery, directory, lastPage);
            }
        });

        btnDefaultNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean(PREF_AUTO_JUMP, false).apply();
                Toast.makeText(activity, R.string.resume_set_never, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Launches ZoomActivity at the specified page.
     */
    public static void openZoom(
            Activity activity,
            GenericGallery gallery,
            Parcelable directory,
            int page) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || gallery == null) {
            return;
        }
        try {
            Intent intent = new Intent(activity, ZoomActivity.class);
            intent.putExtra(activity.getPackageName() + ".GALLERY", (Parcelable) gallery);
            if (directory != null) {
                intent.putExtra(activity.getPackageName() + ".DIRECTORY", directory);
            }
            intent.putExtra(activity.getPackageName() + ".PAGE", page);
            activity.startActivity(intent);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static final String PREF_RESUME_BEHAVIOR = "pref_resume_behavior";

    /**
     * Binds preference listener in GeneralPreferenceFragment.onResume.
     */
    public static void bindPreference(final PreferenceFragmentCompat fragment) {
        if (fragment == null) {
            return;
        }
        final Context ctx = fragment.getContext();
        if (ctx == null) {
            return;
        }

        try {
            final Preference resumePref = fragment.findPreference(PREF_RESUME_BEHAVIOR);
            if (resumePref != null) {
                final SharedPreferences sp = ctx.getSharedPreferences("Settings", Context.MODE_PRIVATE);
                updateResumeSummary(resumePref, sp);
                resumePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showResumeBehaviorDialog(preference.getContext(), preference);
                        return true;
                    }
                });
            }

            Preference resetPref = fragment.findPreference(PREF_RESET_PROMPT);
            if (resetPref != null) {
                resetPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        SwitchPreferenceCompat sw = fragment.findPreference(PREF_AUTO_JUMP);
                        if (sw != null) {
                            sw.setChecked(false);
                        }
                        ctx.getSharedPreferences("Settings", Context.MODE_PRIVATE)
                                .edit()
                                .remove(PREF_AUTO_JUMP)
                                .apply();
                        Toast.makeText(ctx, R.string.resume_reset_done, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void updateResumeSummary(Preference pref, SharedPreferences sp) {
        if (pref == null || sp == null) return;
        if (!sp.contains(PREF_AUTO_JUMP)) {
            pref.setSummary(R.string.resume_mode_ask);
        } else if (sp.getBoolean(PREF_AUTO_JUMP, false)) {
            pref.setSummary(R.string.resume_mode_always);
        } else {
            pref.setSummary(R.string.resume_mode_never);
        }
    }

    public static void showResumeBehaviorDialog(Context context, final Preference pref) {
        if (context == null) return;
        final SharedPreferences sp = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        int selected = 0;
        if (sp.contains(PREF_AUTO_JUMP)) {
            selected = sp.getBoolean(PREF_AUTO_JUMP, false) ? 1 : 2;
        }

        CharSequence[] items = new CharSequence[]{
                context.getString(R.string.resume_option_ask),
                context.getString(R.string.resume_option_always),
                context.getString(R.string.resume_option_never)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_prefs_title);
        builder.setSingleChoiceItems(items, selected, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    sp.edit().remove(PREF_AUTO_JUMP).apply();
                    Toast.makeText(context, R.string.resume_toast_ask, Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    sp.edit().putBoolean(PREF_AUTO_JUMP, true).apply();
                    Toast.makeText(context, R.string.resume_toast_always, Toast.LENGTH_SHORT).show();
                } else if (which == 2) {
                    sp.edit().putBoolean(PREF_AUTO_JUMP, false).apply();
                    Toast.makeText(context, R.string.resume_toast_never, Toast.LENGTH_SHORT).show();
                }
                updateResumeSummary(pref, sp);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private static int dp2px(Context context, float dp) {
        if (context == null || context.getResources() == null) {
            return (int) dp;
        }
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
