# Patched and ported files

NClientV3 Modern is a derivative of NClientV3 (maxwai and contributors, based on
Dar9586/NClientV2), licensed under the Apache License 2.0. Source files carrying the header
`Modified/Added for NClientV3 Modern under Apache-2.0 ... see docs/PATCHED_FILES.md` were added
or modified for this fork.

## Code adapted from yosefario-dev/NClientV3 (Apache-2.0)

Source: https://github.com/yosefario-dev/NClientV3 (studied at commit b2ee7ed).

| File | Origin | Changes in this fork |
|---|---|---|
| `app/src/main/java/com/maxwai/nclientv3/ShareReceiverActivity.java` | `ShareReceiverActivity.java` | PIN lock first, `NEW_TASK \| CLEAR_TOP` instead of `CLEAR_TASK`, mirror-aware hosts, no e-hentai branch |
| `app/src/main/java/com/maxwai/nclientv3/utility/ShareLinkParser.java` | routing order of `ShareReceiverActivity.java` | rewritten as a pure-Java parser, drops a non-numeric `?page=` |

## Files modified in 4.6.0

- Share-in: `AndroidManifest.xml`, `PINActivity.java` (forwards a whitelisted intent after unlock).
- Dynamic colour: `utility/ThemeColors.java`, `utility/ThemeCardHelper.java`, `res/values/arrays.xml`,
  `res/values/attrs.xml`, `res/values/styles.xml`, `res/values-v31/styles.xml`,
  `res/values-v31/colors.xml`, `res/values-night-v31/colors.xml`.
- Hide bottom navigation: `MainActivity.java`, `utility/BottomNavRouter.java`, `utility/BarSize.java`,
  `res/menu/activity_main_drawer.xml`, `res/xml/settings.xml`, `res/values/ids.xml`.
- Localization (literals swapped for string resources only): `GalleryActivity.java`,
  `adapters/BookmarkAdapter.java`, `components/classes/Bookmark.java`, and the helpers
  `AutoPageTurner`, `DownloadWhileReadingHelper`, `FavoriteSyncHelper`, `HistoryClearHelper`,
  `ReadingSessionHelper`, `ReadingStatsHelper`, `ResumeHelper`, `ThemeCardHelper`,
  `VolumeDirectionHelper`; `res/layout/app_bar_gallery.xml`; `values/`, `values-zh-rCN/` and
  `values-zh-rTW/` `strings.xml`.
