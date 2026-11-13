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

import androidx.annotation.NonNull;

import java.io.IOException;

/** A failure the UI should explain rather than just "network error". */
public class EhException extends IOException {
    public enum Kind {
        /** HTTP 509 or the 509 placeholder image: the image quota is used up. */
        QUOTA_EXCEEDED,
        /** "This IP address has been temporarily banned". */
        IP_BANNED,
        /** exhentai answered with an empty page (no access / igneous missing). */
        SAD_PANDA,
        /** HTTP 429/503 that survived the retries. */
        RATE_LIMITED,
        /** The gallery was removed or is not available. */
        GALLERY_GONE,
        /** The page did not have the expected structure. */
        PARSE,
        /** Any other HTTP status. */
        HTTP
    }

    @NonNull
    public final Kind kind;
    public final int httpCode;

    public EhException(@NonNull Kind kind, @NonNull String message) {
        this(kind, 0, message);
    }

    public EhException(@NonNull Kind kind, int httpCode, @NonNull String message) {
        super(message);
        this.kind = kind;
        this.httpCode = httpCode;
    }
}
