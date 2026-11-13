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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Non-secret e-hentai preferences ("EHentai" preferences file). */
public final class EhSettings {
    static final String PREFS = "EHentai";
    private static final String KEY_EXHENTAI = "use_exhentai";
    private static final String KEY_CATEGORIES = "shown_categories";
    private static final String KEY_MIN_RATING = "min_rating";
    private static final String KEY_READER_RTL = "reader_rtl";

    private EhSettings() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** exhentai is only used when switched on and the account has an igneous cookie. */
    public static boolean useExhentai(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_EXHENTAI, false) && EhAuth.hasExhentaiAccess(context);
    }

    public static boolean wantsExhentai(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_EXHENTAI, false);
    }

    public static void setUseExhentai(@NonNull Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_EXHENTAI, value).apply();
    }

    /** "Show only" category bits; 0 means all. */
    public static int shownCategories(@NonNull Context context) {
        return prefs(context).getInt(KEY_CATEGORIES, 0);
    }

    public static void setShownCategories(@NonNull Context context, int bits) {
        prefs(context).edit().putInt(KEY_CATEGORIES, bits & EhCategory.ALL).apply();
    }

    public static int minRating(@NonNull Context context) {
        return prefs(context).getInt(KEY_MIN_RATING, 0);
    }

    public static void setMinRating(@NonNull Context context, int stars) {
        prefs(context).edit().putInt(KEY_MIN_RATING, stars).apply();
    }

    public static boolean readerRtl(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_READER_RTL, false);
    }

    public static void setReaderRtl(@NonNull Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_READER_RTL, value).apply();
    }
}
