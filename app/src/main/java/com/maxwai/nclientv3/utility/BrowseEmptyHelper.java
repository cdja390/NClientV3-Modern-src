package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.maxwai.nclientv3.R;

/**
 * Homepage empty/error placeholder for MainActivity.
 * Shown when the gallery list is empty after a load failure or empty result,
 * so a dark theme does not look like a black screen.
 */
public final class BrowseEmptyHelper implements Runnable {
    private static final int ID_BROWSE_EMPTY = R.id.browse_empty;
    private static final int STR_RETRY_HINT = R.string.browse_empty_retry_hint;

    private final Activity activity;
    private final String text;
    private final boolean hasItems;

    private BrowseEmptyHelper(Activity activity, String text, boolean hasItems) {
        this.activity = activity;
        this.text = text;
        this.hasItems = hasItems;
    }

    public static void show(Activity activity, String text, boolean hasItems) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(new BrowseEmptyHelper(activity, text, hasItems));
    }

    public static void hide(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(new BrowseEmptyHelper(activity, null, false));
    }

    @Override
    public void run() {
        if (activity.isFinishing()) {
            return;
        }
        View empty = activity.findViewById(ID_BROWSE_EMPTY);
        if (empty == null) {
            return;
        }
        boolean show = text != null && !hasItems;
        if (show && empty instanceof TextView) {
            String hint = activity.getString(STR_RETRY_HINT);
            ((TextView) empty).setText(text + "\n\n" + hint);
        }
        empty.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
