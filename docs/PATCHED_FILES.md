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
| `app/src/main/java/com/maxwai/nclientv3/loginapi/PowSolver.java` | `loginapi/PowSolver.java` | package, cancellation check, `verify()` helper (4.7.0) |
| `app/src/main/java/com/maxwai/nclientv3/loginapi/AuthApi.java` | `loginapi/AuthApi.java`, `loginapi/KeyProvisioner.java` | own OkHttp chain that keeps the `NClient/4.5.0` User-Agent and never sends the stored API key with a password login; MiniJson instead of org.json; response bodies are not logged (4.7.0) |
| `app/src/main/java/com/maxwai/nclientv3/components/views/TurnstileDialog.java` | `components/views/TurnstileDialog.java` | system WebView User-Agent, navigation limited to Cloudflare's challenge frames, load timeout (4.7.0) |
| `app/src/main/res/layout/dialog_turnstile.xml` | `res/layout/dialog_turnstile.xml` | unchanged (4.7.0) |
| `app/src/main/java/com/maxwai/nclientv3/LoginActivity.java` | flow of `LoginActivity.java` | native form added in front of the existing WebView login, which stays as a 60 s fallback; optional API-key button (4.7.0) |
| `app/src/main/java/com/maxwai/nclientv3/ehentai/Eh*Parser.java`, `EhUrls.java`, `EhCategory.java`, `EhGalleryKey.java`, `EhGalleryDetail.java`, `EhAuth.java` | `api/source/ehentai/EHentaiSearchParser.java`, `EHentaiGalleryPageParser.java`, `EHentaiViewerLoader.java`, `EHentaiUrls.java`, `EHentaiCategory.java`, `EHentaiFavorites.java`, `EHentaiAuth.java` | rewritten on OkHttp + jsoup without GeckoView; only selectors, URL shapes and cookie names are reused (4.7.0) |
| `app/src/main/java/com/maxwai/nclientv3/ehentai/EhLoginActivity.java` | `EHentaiLoginActivity.java` | system WebView instead of GeckoView, cookie polling, paste-cookies dialog (4.7.0) |

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

## Files added or modified in 4.7.0

- Turnstile login: `LoginActivity.java` and `res/layout/activity_login.xml` (rewritten), new
  `loginapi/AuthApi.java`, `loginapi/PowSolver.java`, `components/views/TurnstileDialog.java`,
  `utility/MiniJson.java`, `res/layout/dialog_turnstile.xml`; strings in `res/values*/strings_47.xml`.
- e-hentai source, P1 (browse, search, gallery, reader): new package `ehentai/` with its own
  OkHttp client (paced, 509 / ban / rate-limit aware), `res/layout/*eh*`, `res/menu/eh_*.xml`,
  `res/drawable/eh_chip_background.xml`. Hooks in existing classes: one drawer item in
  `res/menu/activity_main_drawer.xml`, one branch each in `MainActivity.onNavigationItemSelected`
  and `BottomNavRouter.setupNavigationView`, and the e-hentai activities in `AndroidManifest.xml`.
- e-hentai P2: `ehentai/EhLoginActivity.java` (WebView forums login / paste cookies),
  online and local favorites, history; `ehentai/EhDatabase.java` (separate `ehentai.db`,
  Entries.db untouched). Share-in and VIEW links for e-hentai/exhentai galleries:
  `utility/ShareLinkParser.java` (new `EHENTAI` kind), `ShareReceiverActivity.java`
  (VIEW intent filter in `AndroidManifest.xml`), `PINActivity.java` (one whitelist entry).
- e-hentai P3: downloads (`ehentai/EhDownloads.java`, `ehentai/EhDownloadWorker.java`),
  offline reading, resume and reading statistics; backup of ehentai.db:
  `async/database/export/Exporter.java` (one extra zip entry) and `Importer.java` (one
  branch). Format and compatibility: `docs/BACKUP_FORMAT.md`.
- Version 4.7.0 (`app/build.gradle.kts`: `minorVersion = 7`, versionCode 470000).

## Files added or modified in 4.7.1

- Login fix: `settings/Login.java`, `settings/AuthStore.java`, `settings/ApiAuthInterceptor.java`, `settings/CustomInterceptor.java`, `loginapi/AuthApi.java`, `loginapi/User.java`, `LoginActivity.java`, `components/views/TurnstileDialog.java` (refresh-token persistence and refresh, API-based user profile, Cloudflare page ends on `cf_clearance`, web login returns to the app).
- Merged main list: new `ehentai/EhFeed.java` and `ehentai/EhSimpleGallery.java` (design from yosefario-dev/NClientV3 `InspectorV3.mergeAcross`, `EHentaiPager`, `EHentaiSource.buildSearchQuery`, Apache-2.0), plus `api/InspectorV3.java`, `api/SimpleGallery.java` (protected constructor), `adapters/ListAdapter.java`, `res/xml/settings.xml`, `values*/strings_471.xml`, `values/arrays_471.xml`.
- Tests: `loginapi/LoginFlowTest`, `settings/ClearanceCookieTest`, `ehentai/EhFeedTest`.

## Files added or modified in 4.7.2 (P1, source identity)

- New `source/GallerySource.java`, `source/SourceKey.java`, `source/RowCodec.java`, `source/GalleryIdentity.java`, `source/GalleryListParcel.java`. Identity follows yosefario-dev/NClientV3 `SourceId` plus `SimpleGallery.source` / `token` (Apache-2.0), rewritten for this app. The negative gid is no longer the source flag.
- `ehentai/EhSimpleGallery.java` (positive clamped gid, `sourceKey()`, list restore), `api/SimpleGallery.java` (`restoreListRow`), `api/InspectorV3.java` (mixed list parcel, kind byte 3; classic lists stay on the old typed-list path), `adapters/ListAdapter.java` (status and opened-row lookup by source key), `ehentai/EhFeed.java` (dedup key), `async/downloader/DownloadGalleryV2.java` and `async/database/Queries.java` (nhentai tables reject other sources until P6 routes downloads).
- Test: `source/SourceKeyTest`. JVM tests do not call `android.os.Parcel` (`isReturnDefaultValues`).

- P1 handover verification: `docs/MERGE_ACCEPTANCE_472.md` retains all T01-T32, N01-N07 and device checks, with explicit Android Parcel limits and current user decisions. No P1 production code changed.
