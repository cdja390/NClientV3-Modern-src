package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import com.maxwai.nclientv3.R;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadWhileReadingHelper {
    public static final String PREF_SETTINGS = "Settings";
    public static final String PREF_MASTER = "download_while_reading";
    public static final String PREF_TRIGGER_READ = "download_trigger_read";
    public static final String PREF_TRIGGER_FAVORITE = "download_trigger_favorite";
    public static final String PREF_TRIGGER_FINISH = "download_trigger_finish";
    public static final String PREF_QUANTITY_MODE = "download_quantity_mode";
    public static final String PREF_FORWARD_PERCENT = "download_forward_percent";
    public static final String PREF_FORWARD_PAGES = "download_forward_pages";

    public static final String MODE_ALL = "all";
    public static final String MODE_CURRENT = "current";
    public static final String MODE_PERCENT = "percent";
    public static final String MODE_PAGES = "pages";

    private static final Set<Integer> sFinishedTriggered = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private static final Map<Integer, Integer> sGalleryDownloadedEnd = new ConcurrentHashMap<Integer, Integer>();

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
    }

    public static boolean isMasterEnabled(Context context) {
        if (context == null) return false;
        return getPrefs(context).getBoolean(PREF_MASTER, true);
    }

    /**
     * Trigger 1: 点击图片开始阅读后下载 (ZoomActivity 初始化或进入时调用)
     */
    public static void onReadStart(Context context, GenericGallery gallery, int currentPage) {
        if (!isMasterEnabled(context)) return;
        SharedPreferences sp = getPrefs(context);
        if (!sp.getBoolean(PREF_TRIGGER_READ, true)) return;

        performDownload(context, gallery, currentPage, context.getString(R.string.dwr_trigger_tap_read));
    }

    /**
     * Trigger 2: 收藏后(爱心)开始下载 (GalleryActivity 收藏成功后调用)
     */
    public static void onFavorite(Context context, GenericGallery gallery) {
        if (!isMasterEnabled(context)) return;
        SharedPreferences sp = getPrefs(context);
        if (!sp.getBoolean(PREF_TRIGGER_FAVORITE, false)) return;

        if (gallery == null || gallery.getId() <= 0) return;
        int lastPage = 0;
        try {
            lastPage = Queries.ResumeTable.pageFromId(gallery.getId());
        } catch (Throwable ignored) {}
        int curPage = (lastPage > 0) ? lastPage - 1 : 0;

        performDownload(context, gallery, curPage, context.getString(R.string.dwr_trigger_favorite));
    }

    /**
     * 翻页变化时调用 (ZoomActivity setPageText 时调用)
     * 既用于检测 "读完后下载"，也用于向后推模式下的连贯预载推进
     */
    public static void onPageChanged(Context context, GenericGallery gallery, int pageIndex) {
        if (!isMasterEnabled(context)) return;
        if (context == null || gallery == null || gallery.getId() <= 0) return;

        int totalPages = gallery.getPageCount();
        if (totalPages <= 0) return;

        SharedPreferences sp = getPrefs(context);
        int galleryId = gallery.getId();

        // 读完后下载检测
        if (pageIndex >= totalPages - 1) {
            if (sp.getBoolean(PREF_TRIGGER_FINISH, false)) {
                if (!sFinishedTriggered.contains(galleryId)) {
                    sFinishedTriggered.add(galleryId);
                    performDownload(context, gallery, pageIndex, context.getString(R.string.dwr_trigger_finished));
                    return;
                }
            }
        }

        // 向后推预载推进检测 (仅在开启点击阅读下载且处于向后推模式下)
        if (sp.getBoolean(PREF_TRIGGER_READ, true)) {
            String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);
            if (MODE_PERCENT.equals(mode) || MODE_PAGES.equals(mode)) {
                Integer lastEndObj = sGalleryDownloadedEnd.get(galleryId);
                int lastEnd = (lastEndObj != null) ? lastEndObj.intValue() : -1;
                // 当阅读进度接近上次下载范围末尾(倒数 2 页以内)，且未达到整本末尾时，触发后续下载
                if (lastEnd >= 0 && pageIndex >= lastEnd - 2 && lastEnd < totalPages - 1) {
                    performDownload(context, gallery, pageIndex, context.getString(R.string.dwr_trigger_progress));
                }
            }
        }
    }

    /**
     * 核心范围计算与下载触发
     */
    public static void performDownload(Context context, GenericGallery gallery, int currentPage, String triggerLabel) {
        if (context == null || gallery == null || gallery.getId() <= 0) return;
        if (gallery.isLocal()) return;

        if (!isNetworkAvailable(context)) {
            showToast(context, context.getString(R.string.dwr_toast_no_network, triggerLabel));
            return;
        }

        int totalPages = gallery.getPageCount();
        if (totalPages <= 0) {
            startDownload(context, gallery, 0, -1, triggerLabel, context.getString(R.string.dwr_scope_whole));
            return;
        }

        SharedPreferences sp = getPrefs(context);
        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);

        int start = 0;
        int end = totalPages - 1;
        String scopeDesc = context.getString(R.string.dwr_scope_whole_pages, totalPages);

        if (MODE_CURRENT.equals(mode)) {
            end = Math.max(0, Math.min(totalPages - 1, currentPage));
            scopeDesc = context.getString(R.string.dwr_scope_current_pages, end + 1);
        } else if (MODE_PERCENT.equals(mode)) {
            int percent = sp.getInt(PREF_FORWARD_PERCENT, 25);
            int delta = Math.max(1, Math.round(totalPages * (percent / 100.0f)));
            end = Math.max(0, Math.min(totalPages - 1, currentPage + delta));
            scopeDesc = context.getString(R.string.dwr_scope_percent_pages, percent, end + 1);
        } else if (MODE_PAGES.equals(mode)) {
            int pagesAhead = sp.getInt(PREF_FORWARD_PAGES, 10);
            end = Math.max(0, Math.min(totalPages - 1, currentPage + pagesAhead));
            scopeDesc = context.getString(R.string.dwr_scope_ahead_pages, pagesAhead, end + 1);
        }

        startDownload(context, gallery, start, end, triggerLabel, scopeDesc);
    }

    private static boolean startDownload(
            Context context,
            GenericGallery gallery,
            int start,
            int end,
            String triggerLabel,
            String scopeDesc) {
        try {
            int totalPages = gallery.getPageCount();
            if (end < 0 || totalPages <= 0 || (start == 0 && end == totalPages - 1)) {
                DownloadGalleryV2.downloadGallery(context, gallery);
            } else if (gallery instanceof Gallery) {
                DownloadGalleryV2.downloadRange(context, (Gallery) gallery, start, end);
            } else {
                DownloadGalleryV2.downloadGallery(context, gallery);
            }
            if (end >= 0) {
                sGalleryDownloadedEnd.put(gallery.getId(), Integer.valueOf(end));
            }
            showToast(context, context.getString(R.string.dwr_toast_started, triggerLabel, scopeDesc));
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            showToast(context, context.getString(R.string.dwr_toast_failed, triggerLabel));
            return false;
        }
    }

    private static void showToast(final Context context, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) {
                return false;
            }
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
        } catch (Throwable ignored) {
            return false;
        }
    }


    public static final String PREF_OPTIONS = "download_while_reading_options";

    /**
     * 绑定设置页面中的细分项与点击交互
     */
    public static void bindPreference(PreferenceFragmentCompat fragment) {
        if (fragment == null) return;
        final Context context = fragment.getContext();
        if (context == null) return;

        final SharedPreferences sp = getPrefs(context);

        // 统一合并入口：边看边下偏好设置
        final Preference prefOptions = fragment.findPreference(PREF_OPTIONS);
        if (prefOptions != null) {
            updateOverallSummary(prefOptions, sp);
            prefOptions.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showConfigDialog(preference.getContext(), preference);
                    return true;
                }
            });
        }

        // 兼容旧的平铺项（如果存在）
        final Preference prefMode = fragment.findPreference(PREF_QUANTITY_MODE);
        if (prefMode != null) {
            updateQuantitySummary(prefMode, sp);
            prefMode.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showQuantityDialog(preference.getContext(), preference);
                    return true;
                }
            });
        }
    }

    public static void updateOverallSummary(Preference pref, SharedPreferences sp) {
        if (pref == null || sp == null) return;

        StringBuilder sbTriggers = new StringBuilder();
        if (sp.getBoolean(PREF_TRIGGER_READ, true)) sbTriggers.append(pref.getContext().getString(R.string.dwr_sum_trigger_read)).append(pref.getContext().getString(R.string.dwr_sum_separator));
        if (sp.getBoolean(PREF_TRIGGER_FAVORITE, false)) sbTriggers.append(pref.getContext().getString(R.string.dwr_sum_trigger_favorite)).append(pref.getContext().getString(R.string.dwr_sum_separator));
        if (sp.getBoolean(PREF_TRIGGER_FINISH, false)) sbTriggers.append(pref.getContext().getString(R.string.dwr_sum_trigger_finish)).append(pref.getContext().getString(R.string.dwr_sum_separator));

        String trigStr;
        if (sbTriggers.length() > 2) {
            trigStr = sbTriggers.substring(0, sbTriggers.length() - 2);
        } else {
            trigStr = pref.getContext().getString(R.string.dwr_sum_no_trigger);
        }

        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);
        String scopeStr = pref.getContext().getString(R.string.dwr_sum_scope_whole);
        if (MODE_CURRENT.equals(mode)) {
            scopeStr = pref.getContext().getString(R.string.dwr_sum_scope_current);
        } else if (MODE_PERCENT.equals(mode)) {
            scopeStr = pref.getContext().getString(R.string.dwr_sum_scope_percent, sp.getInt(PREF_FORWARD_PERCENT, 25));
        } else if (MODE_PAGES.equals(mode)) {
            scopeStr = pref.getContext().getString(R.string.dwr_sum_scope_pages, sp.getInt(PREF_FORWARD_PAGES, 10));
        }

        pref.setSummary(pref.getContext().getString(R.string.dwr_sum_format, trigStr, scopeStr));
    }

    public static void showConfigDialog(final Context context, final Preference pref) {
        if (context == null) return;
        final SharedPreferences sp = getPrefs(context);

        final float density = context.getResources().getDisplayMetrics().density;
        final int p16 = (int) (16 * density + 0.5f);
        final int p8 = (int) (8 * density + 0.5f);
        final int p4 = (int) (4 * density + 0.5f);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        android.widget.LinearLayout root = new android.widget.LinearLayout(context);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(p16, p8, p16, p16);
        scrollView.addView(root);

        // 1. 触发时机区域
        android.widget.TextView tvTitle1 = new android.widget.TextView(context);
        tvTitle1.setText(R.string.dwr_cfg_section_trigger);
        tvTitle1.setTextSize(14f);
        tvTitle1.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle1.setPadding(0, p8, 0, p4);
        root.addView(tvTitle1);

        final android.widget.CheckBox cbRead = new android.widget.CheckBox(context);
        cbRead.setText(R.string.dwr_cfg_trigger_read);
        cbRead.setChecked(sp.getBoolean(PREF_TRIGGER_READ, true));
        root.addView(cbRead);

        final android.widget.CheckBox cbFav = new android.widget.CheckBox(context);
        cbFav.setText(R.string.dwr_cfg_trigger_favorite);
        cbFav.setChecked(sp.getBoolean(PREF_TRIGGER_FAVORITE, false));
        root.addView(cbFav);

        final android.widget.CheckBox cbFinish = new android.widget.CheckBox(context);
        cbFinish.setText(R.string.dwr_cfg_trigger_finish);
        cbFinish.setChecked(sp.getBoolean(PREF_TRIGGER_FINISH, false));
        root.addView(cbFinish);

        // 分割线
        android.view.View div1 = new android.view.View(context);
        div1.setBackgroundColor(0x22000000);
        android.widget.LinearLayout.LayoutParams dlp1 = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density + 0.5f));
        dlp1.topMargin = p8;
        dlp1.bottomMargin = p8;
        root.addView(div1, dlp1);

        // 2. 下载数量范围区域
        android.widget.TextView tvTitle2 = new android.widget.TextView(context);
        tvTitle2.setText(R.string.dwr_cfg_section_range);
        tvTitle2.setTextSize(14f);
        tvTitle2.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle2.setPadding(0, p4, 0, p4);
        root.addView(tvTitle2);

        final int[] curPercent = new int[]{sp.getInt(PREF_FORWARD_PERCENT, 25)};
        final int[] curPages = new int[]{sp.getInt(PREF_FORWARD_PAGES, 10)};
        String curMode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);

        final android.widget.RadioGroup rgMode = new android.widget.RadioGroup(context);
        final android.widget.RadioButton rbAll = new android.widget.RadioButton(context);
        rbAll.setText(R.string.dwr_cfg_range_whole);
        rgMode.addView(rbAll);

        final android.widget.RadioButton rbCur = new android.widget.RadioButton(context);
        rbCur.setText(R.string.dwr_cfg_range_current);
        rgMode.addView(rbCur);

        final android.widget.RadioButton rbPct = new android.widget.RadioButton(context);
        rbPct.setText(context.getString(R.string.dwr_cfg_range_percent, curPercent[0]));
        rgMode.addView(rbPct);

        final android.widget.RadioButton rbPgs = new android.widget.RadioButton(context);
        rbPgs.setText(context.getString(R.string.dwr_cfg_range_pages, curPages[0]));
        rgMode.addView(rbPgs);

        if (MODE_CURRENT.equals(curMode)) {
            rbCur.setChecked(true);
        } else if (MODE_PERCENT.equals(curMode)) {
            rbPct.setChecked(true);
        } else if (MODE_PAGES.equals(curMode)) {
            rbPgs.setChecked(true);
        } else {
            rbAll.setChecked(true);
        }
        root.addView(rgMode);

        // 分割线
        android.view.View div2 = new android.view.View(context);
        div2.setBackgroundColor(0x22000000);
        android.widget.LinearLayout.LayoutParams dlp2 = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density + 0.5f));
        dlp2.topMargin = p8;
        dlp2.bottomMargin = p8;
        root.addView(div2, dlp2);

        // 3. 向后推数值快捷调节
        android.widget.TextView tvTitle3 = new android.widget.TextView(context);
        tvTitle3.setText(R.string.dwr_cfg_section_ahead);
        tvTitle3.setTextSize(14f);
        tvTitle3.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle3.setPadding(0, p4, 0, p4);
        root.addView(tvTitle3);

        // 百分比按钮行
        final android.widget.TextView tvPctLabel = new android.widget.TextView(context);
        tvPctLabel.setText(context.getString(R.string.dwr_cfg_percent_label, curPercent[0]));
        tvPctLabel.setTextSize(13f);
        root.addView(tvPctLabel);

        android.widget.LinearLayout rowPct = new android.widget.LinearLayout(context);
        rowPct.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        final int[] pctOptions = new int[]{10, 20, 25, 33, 50};
        for (final int p : pctOptions) {
            android.widget.Button btn = new android.widget.Button(context, null, android.R.attr.borderlessButtonStyle);
            btn.setText(p + "%");
            btn.setTextSize(12f);
            android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(0, (int) (38 * density + 0.5f), 1f);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    curPercent[0] = p;
                    tvPctLabel.setText(context.getString(R.string.dwr_cfg_percent_label, p));
                    rbPct.setText(context.getString(R.string.dwr_cfg_range_percent, p));
                }
            });
            rowPct.addView(btn);
        }
        root.addView(rowPct);

        // 页数按钮行
        final android.widget.TextView tvPgsLabel = new android.widget.TextView(context);
        tvPgsLabel.setText(context.getString(R.string.dwr_cfg_pages_label, curPages[0]));
        tvPgsLabel.setTextSize(13f);
        tvPgsLabel.setPadding(0, p8, 0, 0);
        root.addView(tvPgsLabel);

        android.widget.LinearLayout rowPgs = new android.widget.LinearLayout(context);
        rowPgs.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        final int[] pgsOptions = new int[]{5, 10, 15, 20, 30};
        for (final int pgs : pgsOptions) {
            android.widget.Button btn = new android.widget.Button(context, null, android.R.attr.borderlessButtonStyle);
            btn.setText(context.getString(R.string.dwr_cfg_pages_button, pgs));
            btn.setTextSize(12f);
            android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(0, (int) (38 * density + 0.5f), 1f);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    curPages[0] = pgs;
                    tvPgsLabel.setText(context.getString(R.string.dwr_cfg_pages_label, pgs));
                    rbPgs.setText(context.getString(R.string.dwr_cfg_range_pages, pgs));
                }
            });
            rowPgs.addView(btn);
        }
        root.addView(rowPgs);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dwr_prefs_title);
        builder.setView(scrollView);
        builder.setPositiveButton(R.string.dwr_cfg_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedMode = MODE_ALL;
                if (rbCur.isChecked()) {
                    selectedMode = MODE_CURRENT;
                } else if (rbPct.isChecked()) {
                    selectedMode = MODE_PERCENT;
                } else if (rbPgs.isChecked()) {
                    selectedMode = MODE_PAGES;
                }

                sp.edit()
                        .putBoolean(PREF_TRIGGER_READ, cbRead.isChecked())
                        .putBoolean(PREF_TRIGGER_FAVORITE, cbFav.isChecked())
                        .putBoolean(PREF_TRIGGER_FINISH, cbFinish.isChecked())
                        .putString(PREF_QUANTITY_MODE, selectedMode)
                        .putInt(PREF_FORWARD_PERCENT, curPercent[0])
                        .putInt(PREF_FORWARD_PAGES, curPages[0])
                        .apply();

                updateOverallSummary(pref, sp);
                Toast.makeText(context, R.string.dwr_cfg_saved, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    public static void updateQuantitySummary(Preference pref, SharedPreferences sp) {
        if (pref == null || sp == null) return;
        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);
        String summary = pref.getContext().getString(R.string.dwr_sum_scope_whole);
        if (MODE_CURRENT.equals(mode)) {
            summary = pref.getContext().getString(R.string.dwr_qty_current);
        } else if (MODE_PERCENT.equals(mode)) {
            int percent = sp.getInt(PREF_FORWARD_PERCENT, 25);
            summary = pref.getContext().getString(R.string.dwr_cfg_range_percent, percent);
        } else if (MODE_PAGES.equals(mode)) {
            int pages = sp.getInt(PREF_FORWARD_PAGES, 10);
            summary = pref.getContext().getString(R.string.dwr_cfg_range_pages, pages);
        }
        pref.setSummary(summary);
    }

    public static void showQuantityDialog(Context context, final Preference pref) {
        if (context == null) return;
        final SharedPreferences sp = getPrefs(context);
        String curMode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);

        final String[] modes = new String[]{MODE_ALL, MODE_CURRENT, MODE_PERCENT, MODE_PAGES};
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(curMode)) {
                selectedIndex = i;
                break;
            }
        }

        int percent = sp.getInt(PREF_FORWARD_PERCENT, 25);
        int pages = sp.getInt(PREF_FORWARD_PAGES, 10);

        final CharSequence[] items = new CharSequence[]{
                context.getString(R.string.dwr_cfg_range_whole),
                context.getString(R.string.dwr_qty_current_long),
                context.getString(R.string.dwr_cfg_range_percent, percent),
                context.getString(R.string.dwr_cfg_range_pages, pages)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dwr_qty_title);
        builder.setSingleChoiceItems(items, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which >= 0 && which < modes.length) {
                    sp.edit().putString(PREF_QUANTITY_MODE, modes[which]).apply();
                    updateQuantitySummary(pref, sp);
                }
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}
