/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (selector and URL knowledge)
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
 * Added for NClientV3 Modern 4.7.0. Cookie handling follows EHentaiAuth from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0); stored in its own preferences file
 * so it never ends up in a backup. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.ehentai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** e-hentai account cookies (ipb_member_id, ipb_pass_hash, igneous). */
public final class EhAuth {
    private static final String PREFS = "EHentaiAuth";
    private static final String KEY_MEMBER = "ipb_member_id";
    private static final String KEY_HASH = "ipb_pass_hash";
    private static final String KEY_IGNEOUS = "igneous";
    private static final String KEY_NAME = "display_name";

    private EhAuth() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(@NonNull Context context) {
        SharedPreferences p = prefs(context);
        return !p.getString(KEY_MEMBER, "").isEmpty() && !p.getString(KEY_HASH, "").isEmpty();
    }

    public static boolean hasExhentaiAccess(@NonNull Context context) {
        return isLoggedIn(context) && !prefs(context).getString(KEY_IGNEOUS, "").isEmpty();
    }

    @Nullable
    public static String displayName(@NonNull Context context) {
        String n = prefs(context).getString(KEY_NAME, "");
        if (n.isEmpty()) n = prefs(context).getString(KEY_MEMBER, "");
        return n.isEmpty() ? null : n;
    }

    public static void setDisplayName(@NonNull Context context, @Nullable String name) {
        prefs(context).edit().putString(KEY_NAME, name == null ? "" : name).apply();
    }

    /** Cookie header for a request to e-hentai/exhentai. Always includes nw=1 (skip content warnings). */
    @NonNull
    public static String cookieHeader(@NonNull Context context, boolean exhentai) {
        SharedPreferences p = prefs(context);
        return buildCookieHeader(p.getString(KEY_MEMBER, ""), p.getString(KEY_HASH, ""),
            p.getString(KEY_IGNEOUS, ""), exhentai);
    }

    @NonNull
    static String buildCookieHeader(@NonNull String member, @NonNull String hash, @NonNull String igneous, boolean exhentai) {
        StringBuilder sb = new StringBuilder("nw=1");
        if (!member.isEmpty() && !hash.isEmpty()) {
            sb.append("; ipb_member_id=").append(member).append("; ipb_pass_hash=").append(hash);
            if (exhentai && !igneous.isEmpty()) sb.append("; igneous=").append(igneous);
        }
        return sb.toString();
    }

    /** Splits a "name=value; name2=value2" header. */
    @NonNull
    public static Map<String, String> parseCookieHeader(@Nullable String header) {
        Map<String, String> out = new LinkedHashMap<>();
        if (header == null) return out;
        for (String part : header.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            out.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
        }
        return out;
    }

    /** Accepts usable values only: a member id other than 0, a 32-hex pass hash, a real igneous. */
    static boolean validMember(@Nullable String member) {
        return member != null && member.matches("\\d+") && !member.equals("0");
    }

    static boolean validHash(@Nullable String hash) {
        return hash != null && hash.matches("[0-9a-fA-F]{32}");
    }

    static boolean validIgneous(@Nullable String igneous) {
        return igneous != null && !igneous.isEmpty() && !igneous.equals("mystery") && igneous.matches("[0-9a-zA-Z]+");
    }

    /**
     * Stores the account from cookie values. Returns false (and stores nothing) when member id or
     * pass hash is missing or malformed. An invalid igneous is ignored, a valid one replaces the old.
     */
    public static boolean store(@NonNull Context context, @Nullable String member, @Nullable String hash,
                                @Nullable String igneous) {
        if (member != null) member = member.trim();
        if (hash != null) hash = hash.trim();
        if (igneous != null) igneous = igneous.trim();
        if (!validMember(member) || !validHash(hash)) return false;
        SharedPreferences.Editor e = prefs(context).edit().putString(KEY_MEMBER, member).putString(KEY_HASH, hash);
        if (validIgneous(igneous)) e.putString(KEY_IGNEOUS, igneous);
        e.apply();
        return true;
    }

    public static boolean storeFromCookieHeader(@NonNull Context context, @Nullable String header) {
        Map<String, String> c = parseCookieHeader(header);
        return store(context, c.get("ipb_member_id"), c.get("ipb_pass_hash"), c.get("igneous"));
    }

    public static void storeIgneous(@NonNull Context context, @Nullable String igneous) {
        if (validIgneous(igneous)) prefs(context).edit().putString(KEY_IGNEOUS, igneous.trim()).apply();
    }

    public static void logout(@NonNull Context context) {
        prefs(context).edit().clear().apply();
        EhSettings.setUseExhentai(context, false);
    }
}
