/* Copyright 2026 yosefario-dev and NClientV3 contributors (namespace mapping).
 * Copyright 2026 NClientV3-Modern contributors. Licensed under Apache-2.0.
 * Adapted from EHentaiSource at b2ee7ed. See docs/PATCHED_FILES.md. */
package com.maxwai.nclientv3.ehentai;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;

final class EhTags {
    private EhTags() {
    }

    static Tag forMergedSearch(String namespace, String name) {
        TagType type = TagType.typeByName(namespace);
        if (type == TagType.UNKNOWN) type = TagType.TAG;
        int id = -(((namespace + ":" + name).hashCode() & 0x3fffffff) + 2);
        return new Tag(name, 0, id, type, TagStatus.DEFAULT);
    }
}
