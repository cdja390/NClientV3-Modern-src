package com.maxwai.nclientv3.async;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagI18n;
import com.maxwai.nclientv3.utility.LogUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TranslateTags extends Worker {
    private static final int DAYS_UNTIL_FETCH = 7;
    private static final String FORCE_KEY = "force";
    private static final String CACHE_NAME = "ehtag-db.text.json";
    private static final String PREF_LAST_SYNC = "lastTagI18nSync";
    private static final String PREF_LAST_SHA = "lastTagI18nSha";
    private static final String[] SOURCES = {
        "https://cdn.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json",
        "https://fastly.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json",
    };

    public TranslateTags(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void startWork(Context context) {
        startWork(context, false);
    }

    public static void startWork(Context context, boolean force) {
        Data input = new Data.Builder().putBoolean(FORCE_KEY, force).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TranslateTags.class)
            .setInputData(input)
            .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
            "translate_tags",
            force ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP,
            request
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean force = getInputData().getBoolean(FORCE_KEY, false);
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("Settings", 0);
        TagI18n.applyBundled(getApplicationContext(), force);
        Date nowTime = new Date();
        Date lastTime = new Date(preferences.getLong(PREF_LAST_SYNC, nowTime.getTime()));
        if (!force && !enoughDayPassed(nowTime, lastTime)) return Result.success();

        try {
            String body = loadDocument(force);
            JSONObject root = new JSONObject(body);
            String sha = root.optJSONObject("head") == null ? "" : root.getJSONObject("head").optString("sha", "");
            String lastSha = preferences.getString(PREF_LAST_SHA, "");
            if (!force && !sha.isEmpty() && sha.equals(lastSha)) return Result.success();

            applyRemote(root);
            preferences.edit()
                .putLong(PREF_LAST_SYNC, nowTime.getTime())
                .putString(PREF_LAST_SHA, sha)
                .apply();
            LogUtility.d("Applied remote tag i18n sha=" + sha);
            return Result.success();
        } catch (IOException | JSONException e) {
            LogUtility.w("Error updating tag translations", e);
            return Result.retry();
        }
    }

    private String loadDocument(boolean force) throws IOException {
        File cache = new File(getApplicationContext().getCacheDir(), CACHE_NAME);
        if (force && cache.isFile() && cache.length() > 0) {
            return readFile(cache);
        }
        IOException last = null;
        for (String url : SOURCES) {
            try {
                String body = download(url);
                writeFile(cache, body);
                return body;
            } catch (IOException e) {
                last = e;
                LogUtility.w("Failed downloading tag i18n from " + url, e);
            }
        }
        if (cache.isFile() && cache.length() > 0) return readFile(cache);
        throw last == null ? new IOException("Unable to download tag translations") : last;
    }

    private String download(String url) throws IOException {
        try (Response response = Global.getClient(getApplicationContext())
            .newCall(new Request.Builder().url(url).build())
            .execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            return body.string();
        }
    }

    private void applyRemote(JSONObject root) throws JSONException {
        JSONArray namespaces = root.getJSONArray("data");
        JSONObject[] ordered = new JSONObject[namespaces.length()];
        int[] priority = new int[namespaces.length()];
        for (int i = 0; i < namespaces.length(); i++) {
            ordered[i] = namespaces.getJSONObject(i);
            priority[i] = namespacePriority(ordered[i].optString("namespace"));
        }
        for (int i = 0; i < ordered.length; i++) {
            for (int j = i + 1; j < ordered.length; j++) {
                if (priority[j] < priority[i]) {
                    int tmpP = priority[i];
                    priority[i] = priority[j];
                    priority[j] = tmpP;
                    JSONObject tmp = ordered[i];
                    ordered[i] = ordered[j];
                    ordered[j] = tmp;
                }
            }
        }
        JSONObject maps = new JSONObject();
        for (JSONObject namespace : ordered) {
            int type = typeForNamespace(namespace.optString("namespace"));
            if (type < 0 || !namespace.has("data")) continue;
            String typeKey = Integer.toString(type);
            JSONObject target = maps.optJSONObject(typeKey);
            if (target == null) {
                target = new JSONObject();
                maps.put(typeKey, target);
            }
            JSONObject entries = namespace.getJSONObject("data");
            Iterator<String> keys = entries.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                JSONObject item = entries.optJSONObject(name);
                if (item == null) continue;
                String zh = TagI18n.cleanZh(item.optString("name", ""));
                if (zh == null) continue;
                String normalized = TagI18n.normalize(name);
                if (normalized.isEmpty() || normalized.equalsIgnoreCase(zh)) continue;
                if (!target.has(normalized)) target.put(normalized, zh);
            }
        }
        TagI18n.applyMaps(maps, false);
    }

    private static int typeForNamespace(String namespace) {
        switch (namespace) {
            case "parody":
                return TagType.PARODY.getId();
            case "character":
                return TagType.CHARACTER.getId();
            case "female":
            case "male":
            case "mixed":
            case "other":
                return TagType.TAG.getId();
            case "artist":
                return TagType.ARTIST.getId();
            case "group":
                return TagType.GROUP.getId();
            case "language":
                return TagType.LANGUAGE.getId();
            case "reclass":
                return TagType.CATEGORY.getId();
            default:
                return -1;
        }
    }

    private static int namespacePriority(String namespace) {
        switch (namespace) {
            case "female":
                return 0;
            case "male":
                return 1;
            case "mixed":
                return 2;
            case "other":
                return 3;
            default:
                return 10;
        }
    }

    private static String readFile(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private static void writeFile(File file, String body) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create cache dir");
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean enoughDayPassed(Date nowTime, Date lastTime) {
        if (nowTime.getTime() == lastTime.getTime()) return true;
        int daysBetween = 0;
        Calendar now = Calendar.getInstance(), last = Calendar.getInstance();
        now.setTime(nowTime);
        last.setTime(lastTime);
        while (last.before(now)) {
            last.add(Calendar.DAY_OF_MONTH, 1);
            daysBetween++;
            if (daysBetween > DAYS_UNTIL_FETCH) return true;
        }
        return false;
    }
}
