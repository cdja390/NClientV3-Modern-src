package com.maxwai.nclientv3.loginapi;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.AvatarHelper;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.MiniJson;
import com.maxwai.nclientv3.utility.Utility;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class User {
    private final String username, codename, avatarUrl;
    private final int id;

    private User(String username, String id, String codename, String avatarUrl) {
        this.username = username;
        this.id = Integer.parseInt(id);
        this.codename = codename;
        this.avatarUrl = avatarUrl;
    }

    private User(String username, int id, String codename, String avatarUrl) {
        this.username = username;
        this.id = id;
        this.codename = codename;
        this.avatarUrl = avatarUrl;
    }

    /**
     * 4.7.1: builds the profile from nhentai's UserInfo / UserMeResponse JSON (id, username, slug,
     * avatar_url), as returned by /api/v2/user and inside the login/refresh token responses.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static User fromApi(@Nullable Map<String, Object> json) {
        if (json == null) return null;
        Object nested = json.get("user");
        if (nested instanceof Map) json = (Map<String, Object>) nested;
        long id = MiniJson.optLong(json, "id", 0);
        String username = MiniJson.optString(json, "username");
        String slug = MiniJson.optString(json, "slug");
        if (id <= 0 || id > Integer.MAX_VALUE || username == null || username.trim().isEmpty()) return null;
        if (slug == null || slug.trim().isEmpty()) slug = username.trim();
        return new User(username.trim(), (int) id, slug.trim(), MiniJson.optString(json, "avatar_url"));
    }

    /**
     * Loads the signed-in profile. 4.7.1: asks /api/v2/user first (the interceptor adds the user
     * token or API key). The rebuilt nhentai site no longer renders {@code span.username}, and an
     * HTML request to "/" could start the Cloudflare WebView, so the page scrape only runs when the
     * API is unreachable for a reason other than missing credentials.
     */
    public static void createUser(@NonNull Context context, final CreateUser createUser) {
        Request request = new Request.Builder().url(Utility.getBaseUrl() + "api/v2/user").build();
        Global.getClient(context).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                LogUtility.e("User: profile request failed: " + e.getMessage());
                if (createUser != null) createUser.onCreateUser(Login.getUser());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code();
                String text;
                try (Response r = response) {
                    text = r.body() == null ? "" : r.body().string();
                }
                if (code == 200) {
                    User user = fromApi(MiniJson.parseObject(text));
                    if (user != null) {
                        Login.updateUser(user);
                        if (createUser != null) createUser.onCreateUser(user);
                        return;
                    }
                }
                LogUtility.d("User: /api/v2/user answered HTTP " + code);
                if (code == 401 || code == 403 || code == 429) {
                    if (code == 401) Login.updateUser(null);
                    if (createUser != null) createUser.onCreateUser(Login.getUser());
                    return;
                }
                createUserFromHtml(context, createUser);
            }
        });
    }

    private static void createUserFromHtml(@NonNull Context context, final CreateUser createUser) {
        Global.getClient(context).newCall(new Request.Builder().url(Login.BASE_HTTP_URL).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (createUser != null) createUser.onCreateUser(Login.getUser());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                User user = null;
                Document doc = Jsoup.parse(response.body().byteStream(), null, Utility.getBaseUrl());
                Elements usernameElements = doc.select("span.username");
                if (!usernameElements.isEmpty()) {
                    Element span = usernameElements.first();
                    Element link = Objects.requireNonNull(span).parent();
                    String username = span.text().trim();
                    String href = link.attr("href");
                    String[] parts = href.split("/");
                    Element avatar = link.selectFirst("img.nav-avatar");
                    String avatarUrl = avatar != null ? avatar.attr("src") : null;
                    user = new User(username, parts[2], parts[3], avatarUrl);
                }
                if (user != null) Login.updateUser(user);
                if (createUser != null) createUser.onCreateUser(Login.getUser());
            }
        });
    }

    @NonNull
    @Override
    public String toString() {
        return username + '(' + id + '/' + codename + ')';
    }

    public String getUsername() {
        return username;
    }

    public int getId() {
        return id;
    }

    public String getCodename() {
        return codename;
    }

    public String getAvatarUrl() {
        // Scraped straight from the page HTML, so it can carry an absolute numbered-host URL.
        return AvatarHelper.normalizeAvatarString(avatarUrl, id);
    }

    public interface CreateUser {
        void onCreateUser(User user);
    }
}
