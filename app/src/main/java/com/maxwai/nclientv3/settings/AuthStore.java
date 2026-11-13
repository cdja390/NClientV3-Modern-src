package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AuthStore {
    private static final String AUTH_PREFERENCES = "Auth";
    private static final String USER_TOKEN_PREFERENCES = "UserToken";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_VALID = "valid";
    private static final String KEY_USER_TOKEN = "token";
    private static final String KEY_USER_TOKEN_EXPIRY = "expires_at";
    /** 4.7.1: nhentai's rotating refresh token, kept so a short-lived access token can be renewed. */
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private AuthStore() {
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(AUTH_PREFERENCES, Context.MODE_PRIVATE);
    }

    @NonNull
    private static SharedPreferences getUserTokenPreferences(@NonNull Context context) {
        return context.getSharedPreferences(USER_TOKEN_PREFERENCES, Context.MODE_PRIVATE);
    }

    public static void saveUserToken(@NonNull Context context, @NonNull String token, long expiresAt) {
        getUserTokenPreferences(context).edit()
            .putString(KEY_USER_TOKEN, token.trim())
            .putLong(KEY_USER_TOKEN_EXPIRY, expiresAt)
            .commit();
    }

    public static void clearUserToken(@NonNull Context context) {
        getUserTokenPreferences(context).edit().clear().commit();
    }

    @Nullable
    public static String getUserToken(@NonNull Context context) {
        SharedPreferences preferences = getUserTokenPreferences(context);
        String token = preferences.getString(KEY_USER_TOKEN, null);
        long expiresAt = preferences.getLong(KEY_USER_TOKEN_EXPIRY, 0);
        if (token == null || token.trim().isEmpty() || expiresAt <= System.currentTimeMillis()) {
            // Only the access token is dropped: the refresh token outlives it and is what renews it.
            if (token != null)
                preferences.edit().remove(KEY_USER_TOKEN).remove(KEY_USER_TOKEN_EXPIRY).commit();
            return null;
        }
        return token.trim();
    }

    public static void saveRefreshToken(@NonNull Context context, @Nullable String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) return;
        getUserTokenPreferences(context).edit().putString(KEY_REFRESH_TOKEN, refreshToken.trim()).commit();
    }

    @Nullable
    public static String getRefreshToken(@NonNull Context context) {
        String token = getUserTokenPreferences(context).getString(KEY_REFRESH_TOKEN, null);
        return token == null || token.trim().isEmpty() ? null : token.trim();
    }

    public static void clearRefreshToken(@NonNull Context context) {
        getUserTokenPreferences(context).edit().remove(KEY_REFRESH_TOKEN).commit();
    }

    public static long getUserTokenExpiry(@NonNull Context context) {
        return getUserToken(context) == null ? 0
            : getUserTokenPreferences(context).getLong(KEY_USER_TOKEN_EXPIRY, 0);
    }

    public static void saveApiKey(@NonNull Context context, @NonNull String apiKey, boolean valid) {
        String normalized = apiKey.trim();
        getPreferences(context).edit()
            .putString(KEY_TYPE, AuthCredentials.Type.API_KEY.name())
            .putString(KEY_SECRET, normalized)
            .putBoolean(KEY_VALID, valid)
            .apply();
    }

    public static void clear(@NonNull Context context) {
        getPreferences(context).edit().clear().apply();
    }

    @Nullable
    public static AuthCredentials getCredentials(@NonNull Context context) {
        SharedPreferences preferences = getPreferences(context);
        String typeName = preferences.getString(KEY_TYPE, null);
        String secret = preferences.getString(KEY_SECRET, null);
        if (typeName == null || secret == null || secret.trim().isEmpty()) return null;
        try {
            return new AuthCredentials(AuthCredentials.Type.valueOf(typeName), secret.trim());
        } catch (IllegalArgumentException ignore) {
            clear(context);
            return null;
        }
    }

    public static boolean hasCredentials(@NonNull Context context) {
        return getCredentials(context) != null;
    }

    public static boolean hasApiKey(@NonNull Context context) {
        AuthCredentials credentials = getCredentials(context);
        return credentials != null && credentials.getType() == AuthCredentials.Type.API_KEY;
    }

    public static boolean hasValidApiKey(@NonNull Context context) {
        AuthCredentials credentials = getCredentials(context);
        if (credentials == null || credentials.getType() != AuthCredentials.Type.API_KEY) return false;

        SharedPreferences preferences = getPreferences(context);
        if (!preferences.contains(KEY_VALID)) return true;
        return preferences.getBoolean(KEY_VALID, false);
    }

    public static void setApiKeyValidation(@NonNull Context context, boolean valid) {
        if (!hasApiKey(context)) return;
        getPreferences(context).edit().putBoolean(KEY_VALID, valid).apply();
    }

    @Nullable
    public static String getApiKey(@NonNull Context context) {
        AuthCredentials credentials = getCredentials(context);
        if (credentials == null || credentials.getType() != AuthCredentials.Type.API_KEY) return null;
        return credentials.getSecret();
    }

    @Nullable
    public static String getAuthorizationHeader(@NonNull Context context) {
        AuthCredentials credentials = getCredentials(context);
        return credentials == null ? null : credentials.toAuthorizationHeader();
    }
}
