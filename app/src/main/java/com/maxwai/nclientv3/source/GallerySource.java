/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors (source identity)
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
 * Added for NClientV3 Modern 4.7.2. The three-way source id follows SourceId in
 * yosefario-dev/NClientV3 (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * This type is not named SourceId, so a later read of migrate/source-v1 can still
 * choose its own name. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.source;

/**
 * Which site a list row came from. Ordinals are written into parcels; do not reorder.
 */
public enum GallerySource {
    NHENTAI,
    EHENTAI,
    EXHENTAI
}
