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
 * Based on ShareReceiverActivity from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * Modified for NClientV3 Modern: PIN lock first, NEW_TASK|CLEAR_TOP instead of CLEAR_TASK,
 * mirror-aware links, page sanitising; e-hentai gallery links (shared or opened) since 4.7.0.
 * See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.maxwai.nclientv3.utility.ShareLinkParser;
import com.maxwai.nclientv3.utility.Utility;

/**
 * Receives text shared from other apps (ACTION_SEND text/plain) and forwards it: a gallery link
 * opens the gallery, another site link opens the matching list, a number opens that id, any
 * other text becomes a search. When the device-credential lock is on, PINActivity runs first.
 */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent forward = buildForward(getIntent());
        if (forward == null) {
            Toast.makeText(this, R.string.nothing_shared, Toast.LENGTH_SHORT).show();
        } else if (PINActivity.isLocked(this)) {
            Intent pin = new Intent(this, PINActivity.class);
            pin.putExtra(PINActivity.EXTRA_FORWARD, forward);
            pin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(pin);
        } else {
            startActivity(forward);
        }
        finish();
    }

    private Intent buildForward(Intent intent) {
        if (intent == null) return null;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
            text = intent.getData().toString();
        if (text == null || text.toString().trim().isEmpty())
            text = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        ShareLinkParser.Result r = ShareLinkParser.parse(text, Utility::isSiteHost);
        Intent out;
        switch (r.kind) {
            case GALLERY:
                out = new Intent(this, GalleryActivity.class)
                    .setData(Uri.parse(Utility.getBaseUrl() + "g/" + r.value + "/"));
                break;
            case SITE_URL:
                out = siteIntent(Uri.parse(r.value));
                break;
            case EHENTAI:
                out = new Intent(this, com.maxwai.nclientv3.ehentai.EhGalleryActivity.class)
                    .setData(Uri.parse(r.value));
                break;
            case ID:
            case SEARCH:
                out = new Intent(this, MainActivity.class)
                    .putExtra(getPackageName() + ".SEARCHMODE", true)
                    .putExtra(getPackageName() + ".QUERY", r.value);
                break;
            default:
                return null;
        }
        out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return out;
    }

    private Intent siteIntent(Uri uri) {
        String first = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        Class<?> target;
        switch (first) {
            case "random":
                target = RandomActivity.class;
                break;
            case "tags":
            case "artists":
            case "characters":
            case "parodies":
            case "groups":
                target = TagFilterActivity.class;
                break;
            default:
                target = MainActivity.class;
        }
        return new Intent(this, target).setData(uri);
    }
}
