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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.NotificationSettings;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Downloads queued e-hentai galleries one page at a time. Viewer pages go through the paced
 * client, so a download never hits the site faster than browsing would. A 509 (image quota)
 * pauses the queue instead of retrying; already downloaded pages are skipped on resume.
 */
public class EhDownloadWorker extends Worker {
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    public EhDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        EhDatabase db = EhDatabase.get(c);
        while (!isStopped()) {
            EhDatabase.Entry next = null;
            List<EhDatabase.Entry> all = db.downloads();
            for (int i = all.size() - 1; i >= 0; i--) {
                EhDatabase.Entry e = all.get(i);
                if (e.state == EhDatabase.STATE_QUEUED || e.state == EhDatabase.STATE_RUNNING) {
                    next = e;
                    break;
                }
            }
            if (next == null) break;
            if (!download(c, db, next)) break;
        }
        return Result.success();
    }

    /** Returns false when the whole queue should stop (quota, ban, cancellation). */
    private boolean download(@NonNull Context c, @NonNull EhDatabase db, @NonNull EhDatabase.Entry e) {
        int notId = (int) (e.key.gid % 100000) + (e.key.exhentai ? 570000 : 470000);
        NotificationCompat.Builder n = new NotificationCompat.Builder(c, Global.CHANNEL_ID1)
            .setSmallIcon(R.drawable.ic_file)
            .setContentTitle(e.title)
            .setOnlyAlertOnce(true)
            .setOngoing(true);
        db.setDownloadProgress(e.key, EhDatabase.STATE_RUNNING, -1);
        try {
            int pages = e.pages;
            if (pages <= 0) {
                EhGalleryDetail d = EhRepository.loadGallery(c, e.key, 0, false);
                pages = d.pages;
                d.title = d.title.isEmpty() ? e.title : d.title;
                db.putDownload(d, EhDatabase.STATE_RUNNING);
            }
            File dir = EhDownloads.targetDir(c, e.key);
            int done = 0;
            int failures = 0;
            for (int index = 1; index <= pages; index++) {
                if (isStopped()) {
                    db.setDownloadProgress(e.key, EhDatabase.STATE_PAUSED, done);
                    cancel(notId);
                    return false;
                }
                if (EhDownloads.findPage(dir, index) != null) {
                    done++;
                    continue;
                }
                try {
                    fetchPage(c, e.key, index, dir);
                    done++;
                    failures = 0;
                } catch (EhException ex) {
                    if (ex.kind == EhException.Kind.QUOTA_EXCEEDED || ex.kind == EhException.Kind.IP_BANNED
                        || ex.kind == EhException.Kind.SAD_PANDA) {
                        db.setDownloadProgress(e.key, EhDatabase.STATE_PAUSED, done);
                        finish(c, notId, n, e.title, EhUi.describe(c, ex));
                        return false;
                    }
                    if (ex.kind == EhException.Kind.GALLERY_GONE || ++failures >= MAX_CONSECUTIVE_FAILURES) throw ex;
                } catch (IOException io) {
                    if (isStopped()) continue;
                    if (++failures >= MAX_CONSECUTIVE_FAILURES) throw io;
                }
                db.setDownloadProgress(e.key, EhDatabase.STATE_RUNNING, done);
                n.setProgress(pages, done, false)
                    .setContentText(c.getString(R.string.eh_download_state_running, done, pages));
                notify(c, notId, n);
            }
            boolean complete = done >= pages;
            db.setDownloadProgress(e.key, complete ? EhDatabase.STATE_DONE : EhDatabase.STATE_FAILED, done);
            finish(c, notId, n, e.title, complete ? c.getString(R.string.eh_download_state_done, pages)
                : c.getString(R.string.eh_download_state_failed, done, pages));
            return true;
        } catch (IOException ex) {
            LogUtility.e("e-hentai download failed", ex);
            Integer done = null;
            EhDatabase.Entry now = db.download(e.key);
            if (now != null) done = now.done;
            db.setDownloadProgress(e.key, EhDatabase.STATE_FAILED, done == null ? -1 : done);
            finish(c, notId, n, e.title, EhUi.describe(c, ex));
            return !isStopped();
        }
    }

    private void fetchPage(@NonNull Context c, @NonNull EhGalleryKey key, int index, @NonNull File dir) throws IOException {
        EhPageParser.Result r = EhRepository.resolveImage(c, key, index, false);
        try {
            EhClient.download(c, r.imageUrl, new File(dir, EhDownloads.pageFileName(index, r.imageUrl)));
        } catch (EhException ex) {
            throw ex;
        } catch (IOException first) {
            // One more try through the reload key, which picks another image server.
            if (isStopped()) throw first;
            EhPageParser.Result again = EhRepository.resolveImage(c, key, index, true);
            EhClient.download(c, again.imageUrl, new File(dir, EhDownloads.pageFileName(index, again.imageUrl)));
        }
    }

    private static void finish(Context c, int id, NotificationCompat.Builder n, String title, String text) {
        n.setOngoing(false).setProgress(0, 0, false).setContentTitle(title).setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        notify(c, id, n);
    }

    private static void notify(Context c, int id, NotificationCompat.Builder n) {
        try {
            NotificationSettings.notify(c, id, n.build());
        } catch (RuntimeException ignore) {
            // Notifications are optional.
        }
    }

    private static void cancel(int id) {
        try {
            NotificationSettings.cancel(id);
        } catch (RuntimeException ignore) {
            // Not shown.
        }
    }
}
