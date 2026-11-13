package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
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

        performDownload(context, gallery, currentPage, "点击阅读");
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

        performDownload(context, gallery, curPage, "收藏(爱心)");
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
                    performDownload(context, gallery, pageIndex, "读完");
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
                    performDownload(context, gallery, pageIndex, "阅读推进");
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
            showToast(context, "边看边下(" + triggerLabel + "): 当前无网络，未开始下载");
            return;
        }

        int totalPages = gallery.getPageCount();
        if (totalPages <= 0) {
            startDownload(context, gallery, 0, -1, triggerLabel, "整本");
            return;
        }

        SharedPreferences sp = getPrefs(context);
        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);

        int start = 0;
        int end = totalPages - 1;
        String scopeDesc = "整本(" + totalPages + "P)";

        if (MODE_CURRENT.equals(mode)) {
            end = Math.max(0, Math.min(totalPages - 1, currentPage));
            scopeDesc = "当前进度(前" + (end + 1) + "P)";
        } else if (MODE_PERCENT.equals(mode)) {
            int percent = sp.getInt(PREF_FORWARD_PERCENT, 25);
            int delta = Math.max(1, Math.round(totalPages * (percent / 100.0f)));
            end = Math.max(0, Math.min(totalPages - 1, currentPage + delta));
            scopeDesc = "向后推" + percent + "%(前" + (end + 1) + "P)";
        } else if (MODE_PAGES.equals(mode)) {
            int pagesAhead = sp.getInt(PREF_FORWARD_PAGES, 10);
            end = Math.max(0, Math.min(totalPages - 1, currentPage + pagesAhead));
            scopeDesc = "向后推" + pagesAhead + "页(前" + (end + 1) + "P)";
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
            showToast(context, "边看边下(" + triggerLabel + "): 已启动后台下载 " + scopeDesc);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            showToast(context, "边看边下(" + triggerLabel + "): 启动下载失败");
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
        if (sp.getBoolean(PREF_TRIGGER_READ, true)) sbTriggers.append("阅读, ");
        if (sp.getBoolean(PREF_TRIGGER_FAVORITE, false)) sbTriggers.append("收藏, ");
        if (sp.getBoolean(PREF_TRIGGER_FINISH, false)) sbTriggers.append("读完, ");

        String trigStr;
        if (sbTriggers.length() > 2) {
            trigStr = sbTriggers.substring(0, sbTriggers.length() - 2);
        } else {
            trigStr = "未开启任何触发";
        }

        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);
        String scopeStr = "整本";
        if (MODE_CURRENT.equals(mode)) {
            scopeStr = "当前进度";
        } else if (MODE_PERCENT.equals(mode)) {
            scopeStr = "向后推 " + sp.getInt(PREF_FORWARD_PERCENT, 25) + "%";
        } else if (MODE_PAGES.equals(mode)) {
            scopeStr = "向后推 " + sp.getInt(PREF_FORWARD_PAGES, 10) + " 页";
        }

        pref.setSummary("触发: " + trigStr + " | 范围: " + scopeStr);
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
        tvTitle1.setText("一、触发时机（可多选）");
        tvTitle1.setTextSize(14f);
        tvTitle1.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle1.setPadding(0, p8, 0, p4);
        root.addView(tvTitle1);

        final android.widget.CheckBox cbRead = new android.widget.CheckBox(context);
        cbRead.setText("点击图片开始阅读后下载");
        cbRead.setChecked(sp.getBoolean(PREF_TRIGGER_READ, true));
        root.addView(cbRead);

        final android.widget.CheckBox cbFav = new android.widget.CheckBox(context);
        cbFav.setText("收藏漫画(爱心)后下载");
        cbFav.setChecked(sp.getBoolean(PREF_TRIGGER_FAVORITE, false));
        root.addView(cbFav);

        final android.widget.CheckBox cbFinish = new android.widget.CheckBox(context);
        cbFinish.setText("翻至最后一页读完后下载");
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
        tvTitle2.setText("二、下载数量范围（单选）");
        tvTitle2.setTextSize(14f);
        tvTitle2.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle2.setPadding(0, p4, 0, p4);
        root.addView(tvTitle2);

        final int[] curPercent = new int[]{sp.getInt(PREF_FORWARD_PERCENT, 25)};
        final int[] curPages = new int[]{sp.getInt(PREF_FORWARD_PAGES, 10)};
        String curMode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);

        final android.widget.RadioGroup rgMode = new android.widget.RadioGroup(context);
        final android.widget.RadioButton rbAll = new android.widget.RadioButton(context);
        rbAll.setText("整本 (下载整本漫画)");
        rgMode.addView(rbAll);

        final android.widget.RadioButton rbCur = new android.widget.RadioButton(context);
        rbCur.setText("当前阅读进度 (仅已读部分)");
        rgMode.addView(rbCur);

        final android.widget.RadioButton rbPct = new android.widget.RadioButton(context);
        rbPct.setText("当前阅读进度向后推 " + curPercent[0] + "%");
        rgMode.addView(rbPct);

        final android.widget.RadioButton rbPgs = new android.widget.RadioButton(context);
        rbPgs.setText("当前进度向后推 " + curPages[0] + " 页");
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
        tvTitle3.setText("三、向后推参数调节");
        tvTitle3.setTextSize(14f);
        tvTitle3.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle3.setPadding(0, p4, 0, p4);
        root.addView(tvTitle3);

        // 百分比按钮行
        final android.widget.TextView tvPctLabel = new android.widget.TextView(context);
        tvPctLabel.setText("向后推比例: " + curPercent[0] + "%");
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
                    tvPctLabel.setText("向后推比例: " + p + "%");
                    rbPct.setText("当前阅读进度向后推 " + p + "%");
                }
            });
            rowPct.addView(btn);
        }
        root.addView(rowPct);

        // 页数按钮行
        final android.widget.TextView tvPgsLabel = new android.widget.TextView(context);
        tvPgsLabel.setText("向后推页数: " + curPages[0] + " 页");
        tvPgsLabel.setTextSize(13f);
        tvPgsLabel.setPadding(0, p8, 0, 0);
        root.addView(tvPgsLabel);

        android.widget.LinearLayout rowPgs = new android.widget.LinearLayout(context);
        rowPgs.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        final int[] pgsOptions = new int[]{5, 10, 15, 20, 30};
        for (final int pgs : pgsOptions) {
            android.widget.Button btn = new android.widget.Button(context, null, android.R.attr.borderlessButtonStyle);
            btn.setText(pgs + "页");
            btn.setTextSize(12f);
            android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(0, (int) (38 * density + 0.5f), 1f);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    curPages[0] = pgs;
                    tvPgsLabel.setText("向后推页数: " + pgs + " 页");
                    rbPgs.setText("当前进度向后推 " + pgs + " 页");
                }
            });
            rowPgs.addView(btn);
        }
        root.addView(rowPgs);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("边看边下偏好设置");
        builder.setView(scrollView);
        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
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
                Toast.makeText(context, "已保存边看边下设置", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public static void updateQuantitySummary(Preference pref, SharedPreferences sp) {
        if (pref == null || sp == null) return;
        String mode = sp.getString(PREF_QUANTITY_MODE, MODE_ALL);
        String summary = "整本";
        if (MODE_CURRENT.equals(mode)) {
            summary = "当前阅读进度";
        } else if (MODE_PERCENT.equals(mode)) {
            int percent = sp.getInt(PREF_FORWARD_PERCENT, 25);
            summary = "当前阅读进度向后推 " + percent + "%";
        } else if (MODE_PAGES.equals(mode)) {
            int pages = sp.getInt(PREF_FORWARD_PAGES, 10);
            summary = "当前进度向后推 " + pages + " 页";
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
                "整本 (下载整本漫画)",
                "当前阅读进度 (下载至当前所读页)",
                "当前阅读进度向后推 " + percent + "%",
                "当前进度向后推 " + pages + " 页"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择下载数量范围");
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
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
