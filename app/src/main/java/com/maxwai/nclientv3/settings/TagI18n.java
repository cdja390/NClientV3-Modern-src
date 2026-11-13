package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.utility.LogUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

public final class TagI18n {
    public static final String ASSET_FILE = "tag_i18n.json";
    public static final String PREF_BUNDLED_VERSION = "tag_i18n_bundled_version";
    private static final String DISPLAY_SUFFIX_START = " (";

    private TagI18n() {
    }

    @NonNull
    public static String normalize(@Nullable String name) {
        if (name == null) return "";
        return name.trim().replace('_', ' ').toLowerCase(Locale.US);
    }

    @Nullable
    public static String cleanZh(@Nullable String value) {
        if (value == null) return null;
        String text = value.split("\n", 2)[0].replaceAll("[\\p{So}\\p{Cn}]+", "").trim();
        if (text.isEmpty() || normalize(text).equals("todo") || normalize(text).equals("n/a")
            || "-".equals(text) || "?".equals(text)) {
            return null;
        }
        return text;
    }

    @NonNull
    public static String formatDisplayName(@Nullable String name, @Nullable String nameZh) {
        if (name == null) return "";
        if (!Global.isTranslateTags()) return name;
        if (nameZh == null || nameZh.isEmpty() || nameZh.equalsIgnoreCase(name)) return name;
        return nameZh + DISPLAY_SUFFIX_START + name + ")";
    }

    @NonNull
    public static String extractEnglishName(@NonNull String text) {
        String trimmed = text.trim();
        int open = trimmed.lastIndexOf(DISPLAY_SUFFIX_START);
        if (open > 0 && trimmed.endsWith(")")) {
            return trimmed.substring(open + DISPLAY_SUFFIX_START.length(), trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    @NonNull
    public static String rewriteQuery(@Nullable String query) {
        if (query == null) return "";
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return trimmed;
        Tag exact = Queries.TagTable.findByTranslatedName(trimmed);
        if (exact != null) return quoteIfNeeded(exact.getName());
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return trimmed;
        StringBuilder builder = new StringBuilder();
        boolean changed = false;
        for (String part : parts) {
            if (builder.length() > 0) builder.append(' ');
            Tag tag = Queries.TagTable.findByTranslatedName(part);
            if (tag != null) {
                builder.append(quoteIfNeeded(tag.getName()));
                changed = true;
            } else {
                builder.append(part);
            }
        }
        return changed ? builder.toString() : trimmed;
    }

    @NonNull
    private static String quoteIfNeeded(@NonNull String name) {
        if (name.indexOf(' ') >= 0) return '"' + name + '"';
        return name;
    }

    public static void applyBundled(@NonNull Context context, boolean force) {
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        try {
            JSONObject root = new JSONObject(readAsset(context, ASSET_FILE));
            int version = root.optInt("v", 1);
            int last = preferences.getInt(PREF_BUNDLED_VERSION, -1);
            if (!force && last >= version) return;
            applyMaps(root.getJSONObject("maps"), true);
            preferences.edit().putInt(PREF_BUNDLED_VERSION, version).apply();
            LogUtility.d("Applied bundled tag i18n v" + version);
        } catch (IOException | JSONException e) {
            LogUtility.w("Unable to apply bundled tag translations", e);
        }
    }

    public static void applyMaps(@NonNull JSONObject maps, boolean exactName) throws JSONException {
        Iterator<String> types = maps.keys();
        while (types.hasNext()) {
            String typeKey = types.next();
            int type;
            try {
                type = Integer.parseInt(typeKey);
            } catch (NumberFormatException ignore) {
                continue;
            }
            Queries.TagTable.fillTranslations(type, maps.getJSONObject(typeKey), exactName);
        }
    }

    @NonNull
    private static String readAsset(@NonNull Context context, @NonNull String fileName) throws IOException {
        try (InputStream in = context.getAssets().open(fileName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
