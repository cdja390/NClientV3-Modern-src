/*
 * Copyright 2026 NClientV3-Modern contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Added for NClientV3 Modern 4.7.0 (e-hentai source, no GeckoView). See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.settings.Global;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline copies of e-hentai galleries. Files live in source-specific folders under {@code EHentai}
 * when shared storage is usable, else in the app's own external files folder; the rows in
 * ehentai.db keep the queue and progress.
 */
public final class EhDownloads {
    static final String UNIQUE_WORK = "ehentai-download";
    private static final String FOLDER = "EHentai";

    private EhDownloads() {
    }

    /** Candidate roots, preferred first. */
    @NonNull
    static List<File> roots(@NonNull Context context) {
        List<File> out = new ArrayList<>();
        if (Global.MAINFOLDER != null && Global.hasStoragePermission(context)) out.add(new File(Global.MAINFOLDER, FOLDER));
        File own = context.getExternalFilesDir(FOLDER);
        if (own != null) out.add(own);
        out.add(new File(context.getFilesDir(), FOLDER));
        return out;
    }

    @NonNull
    static String folderName(@NonNull EhGalleryKey key) {
        return (key.exhentai ? "ex_" : "eh_") + legacyFolderName(key);
    }

    private static String legacyFolderName(EhGalleryKey key) {
        return key.gid + "_" + key.token;
    }

    /** Existing folder of a downloaded gallery, or null. */
    @Nullable
    public static File existingDir(@NonNull Context context, @NonNull EhGalleryKey key) {
        if (!key.hasFileToken()) return null;
        List<File> roots = roots(context);
        for (File root : roots) {
            File dir = new File(root, folderName(key));
            if (dir.isDirectory()) return dir;
        }
        if (EhDatabase.get(context).ownsLegacyDownloadDir(key)) {
            for (File root : roots) {
                File dir = new File(root, legacyFolderName(key));
                if (dir.isDirectory()) return dir;
            }
        }
        return null;
    }

    /** Folder to write into (the existing one, else under the first writable root). */
    @NonNull
    static File targetDir(@NonNull Context context, @NonNull EhGalleryKey key) throws IOException {
        if (!key.hasFileToken()) throw new IOException("invalid e-hentai download token");
        File existing = existingDir(context, key);
        if (existing != null) return existing;
        for (File root : roots(context)) {
            if (!root.isDirectory() && !root.mkdirs()) continue;
            File nomedia = new File(root, ".nomedia");
            try {
                //noinspection ResultOfMethodCallIgnored
                nomedia.createNewFile();
            } catch (IOException ignore) {
                // Optional.
            }
            File dir = new File(root, folderName(key));
            if (dir.isDirectory() || dir.mkdirs()) return dir;
        }
        throw new IOException("no writable folder for e-hentai downloads");
    }

    @NonNull
    static String pagePrefix(int index) {
        return String.format(Locale.US, "%04d.", index);
    }

    /** File name for page {@code index} of an image at {@code url}. */
    @NonNull
    static String pageFileName(int index, @Nullable String url) {
        String ext = "jpg";
        if (url != null) {
            String path = url;
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            int dot = path.lastIndexOf('.');
            int slash = path.lastIndexOf('/');
            if (dot > slash && dot >= 0) {
                String e = path.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (e.matches("jpe?g|png|gif|webp|avif")) ext = e.equals("jpeg") ? "jpg" : e;
            }
        }
        return pagePrefix(index) + ext;
    }

    @Nullable
    static File findPage(@Nullable File dir, int index) {
        if (dir == null) return null;
        String prefix = pagePrefix(index);
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && !name.endsWith(".part"));
        return files == null || files.length == 0 || files[0].length() == 0 ? null : files[0];
    }

    /** Local copy of page {@code index}, or null. */
    @Nullable
    public static File localPage(@NonNull Context context, @NonNull EhGalleryKey key, int index) {
        return findPage(existingDir(context, key), index);
    }

    /** Queues a gallery and makes sure the worker runs. */
    public static void enqueue(@NonNull Context context, @NonNull EhGalleryInfo info) {
        EhDatabase.get(context).putDownload(info, EhDatabase.STATE_QUEUED);
        start(context);
    }

    static void start(@NonNull Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EhDownloadWorker.class)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build();
        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /** Pauses every download (the worker stops after the current page). */
    public static void pauseAll(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK);
        EhDatabase db = EhDatabase.get(context);
        for (EhDatabase.Entry e : db.downloads()) {
            if (e.state == EhDatabase.STATE_QUEUED || e.state == EhDatabase.STATE_RUNNING)
                db.setDownloadProgress(e.key, EhDatabase.STATE_PAUSED, -1);
        }
    }

    public static void resume(@NonNull Context context, @NonNull EhGalleryInfo info) {
        EhDatabase.get(context).setDownloadProgress(info.key, EhDatabase.STATE_QUEUED, -1);
        start(context);
    }

    /** Deletes the files and the row. */
    public static void delete(@NonNull Context context, @NonNull EhGalleryKey key) {
        EhDatabase.get(context).removeDownload(key);
        File dir = existingDir(context, key);
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    static void confirmDelete(@NonNull Activity activity, @NonNull EhGalleryInfo info, @NonNull Runnable onDeleted) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.eh_download_delete)
            .setMessage(activity.getString(R.string.eh_download_delete_confirm, info.title))
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                new Thread(() -> delete(activity.getApplicationContext(), info.key), "eh-delete").start();
                onDeleted.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    static String describe(@NonNull Context c, @NonNull EhDatabase.Entry e) {
        int total = Math.max(e.pages, e.done);
        switch (e.state) {
            case EhDatabase.STATE_DONE:
                return c.getString(R.string.eh_download_state_done, total);
            case EhDatabase.STATE_FAILED:
                return c.getString(R.string.eh_download_state_failed, e.done, total);
            case EhDatabase.STATE_PAUSED:
                return c.getString(R.string.eh_download_state_paused, e.done, total);
            case EhDatabase.STATE_RUNNING:
                return c.getString(R.string.eh_download_state_running, e.done, total);
            default:
                return c.getString(R.string.eh_download_state_queued, e.done, total);
        }
    }
}
