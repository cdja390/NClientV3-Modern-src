#!/usr/bin/env python3
"""Build assets/tag_i18n.json from EhTagTranslation + data/tags.json."""

from __future__ import annotations

import json
import re
import ssl
import urllib.request
from os import path

EMOJI_RE = re.compile(
    "["
    "\U0001F300-\U0001FAFF"
    "\U00002700-\U000027BF"
    "\U00002600-\U000026FF"
    "\U0000FE00-\U0000FE0F"
    "]+",
    flags=re.UNICODE,
)

DATA_DIR = path.join(path.dirname(path.abspath(__file__)), "..", "data")
TAGS_FILE = path.join(DATA_DIR, "tags.json")
ASSET_FILE = path.join(
    path.dirname(path.abspath(__file__)),
    "..",
    "app",
    "src",
    "main",
    "assets",
    "tag_i18n.json",
)

EH_URLS = [
    "https://cdn.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json",
    "https://fastly.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json",
]

# nhentai TagType id -> EhTag namespaces, first match wins
TYPE_NAMESPACES: dict[int, tuple[str, ...]] = {
    1: ("parody",),
    2: ("character",),
    3: ("female", "male", "mixed", "other"),
    6: ("language",),
    7: ("reclass",),
}

SKIP_ZH = {"", "-", "?", "todo", "n/a"}


def normalize(name: str) -> str:
    return name.strip().replace("_", " ").lower()


def clean_zh(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    text = EMOJI_RE.sub("", value.strip().split("\n", 1)[0]).strip()
    if not text or normalize(text) in SKIP_ZH:
        return None
    return text


def download_eh() -> dict:
    ctx = ssl.create_default_context()
    last_error: Exception | None = None
    for url in EH_URLS:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "NClient/tag-i18n"})
            with urllib.request.urlopen(req, timeout=120, context=ctx) as response:
                return json.load(response)
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            print(f"Failed {url}: {exc}")
    raise RuntimeError("Unable to download EhTagTranslation") from last_error


def build_eh_maps(eh: dict) -> dict[str, dict[str, str]]:
    maps: dict[str, dict[str, str]] = {}
    for namespace in eh.get("data", []):
        ns = namespace.get("namespace")
        entries = namespace.get("data")
        if not isinstance(ns, str) or not isinstance(entries, dict):
            continue
        current: dict[str, str] = {}
        for key, item in entries.items():
            if not isinstance(item, dict):
                continue
            zh = clean_zh(item.get("name"))
            if zh is None:
                continue
            current[normalize(str(key))] = zh
        maps[ns] = current
    return maps


def lookup(eh_maps: dict[str, dict[str, str]], type_id: int, name: str) -> str | None:
    namespaces = TYPE_NAMESPACES.get(type_id)
    if not namespaces:
        return None
    key = normalize(name)
    for ns in namespaces:
        zh = eh_maps.get(ns, {}).get(key)
        if zh and normalize(zh) != key:
            return zh
    return None


def main() -> None:
    with open(TAGS_FILE, encoding="utf-8") as reader:
        tags = json.load(reader)
    eh = download_eh()
    eh_maps = build_eh_maps(eh)
    maps: dict[str, dict[str, str]] = {}
    matched = 0
    considered = 0
    for entry in tags:
        tag_id, name, _count, type_id = entry
        if type_id not in TYPE_NAMESPACES:
            continue
        considered += 1
        zh = lookup(eh_maps, type_id, name)
        if zh is None:
            continue
        maps.setdefault(str(type_id), {})[name] = zh
        matched += 1
    payload = {
        "v": 1,
        "sha": eh.get("head", {}).get("sha", ""),
        "maps": maps,
    }
    asset_dir = path.dirname(ASSET_FILE)
    if not path.isdir(asset_dir):
        from os import makedirs

        makedirs(asset_dir)
    with open(ASSET_FILE, "w", encoding="utf-8") as writer:
        json.dump(payload, writer, ensure_ascii=False, separators=(",", ":"))
    print(f"Wrote {ASSET_FILE}")
    print(f"matched {matched}/{considered}")
    for type_id, mapping in maps.items():
        print(f"  type {type_id}: {len(mapping)}")


if __name__ == "__main__":
    main()
