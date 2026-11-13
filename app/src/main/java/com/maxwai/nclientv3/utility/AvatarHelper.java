package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.net.Uri;
import android.widget.ImageView;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.users.UserProfile;
import com.maxwai.nclientv3.loginapi.User;

public class AvatarHelper {

    public static String normalizeAvatarString(String rawUrl, int userId) {
        Uri uri = normalizeAvatarUri(rawUrl, userId);
        return uri != null ? uri.toString() : null;
    }

    public static Uri normalizeAvatarUri(String rawUrl, int userId) {
        String host = Utility.getImageHost();
        if (host == null || host.isEmpty()) {
            host = "i.nhentai.net";
        }

        String url = rawUrl != null ? rawUrl.trim() : "";
        if (url.isEmpty()) {
            if (userId > 0) {
                return Uri.parse("https://" + host + "/avatars/" + userId + ".jpg");
            }
            return null;
        }

        if (url.startsWith("//")) {
            url = "https:" + url;
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            // Already absolute
        } else if (url.startsWith("/")) {
            url = "https://" + host + url;
        } else {
            url = "https://" + host + "/" + url;
        }

        url = Utility.normalizeImageHost(url);
        return Uri.parse(url);
    }

    public static void loadUserAvatar(Activity activity, User user, ImageView imageView) {
        if (activity == null || imageView == null) return;
        if (user == null) {
            imageView.setImageResource(R.drawable.ic_person);
            return;
        }
        imageView.clearColorFilter();
        loadAvatar(activity, user.getAvatarUrl(), user.getId(), imageView);
    }

    public static void loadProfileAvatar(Activity activity, UserProfile profile, ImageView imageView) {
        if (activity == null || imageView == null) return;
        if (profile == null) {
            imageView.setImageResource(R.drawable.ic_person);
            return;
        }
        imageView.clearColorFilter();
        Uri uri = profile.getAvatarUrl();
        loadAvatarUri(activity, uri, imageView);
    }

    public static void loadAvatar(Activity activity, String rawUrl, int userId, ImageView imageView) {
        if (activity == null || imageView == null) return;
        Uri uri = normalizeAvatarUri(rawUrl, userId);
        loadAvatarUri(activity, uri, imageView);
    }

    public static void loadAvatarUri(Activity activity, Uri uri, ImageView imageView) {
        if (activity == null || imageView == null) return;
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_person);
            return;
        }
        imageView.clearColorFilter();
        ImageDownloadUtility.loadAvatar(activity, uri, imageView);
    }
}
