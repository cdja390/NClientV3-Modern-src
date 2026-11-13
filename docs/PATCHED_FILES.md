# Patched and ported files

NClientV3 Modern is a derivative of NClientV3 (maxwai and contributors, based on
Dar9586/NClientV2), licensed under the Apache License 2.0. Source files carrying the header
`Modified/Added for NClientV3 Modern under Apache-2.0 ... see docs/PATCHED_FILES.md` were added
or modified for this fork.

## Code adapted from yosefario-dev/NClientV3 (Apache-2.0)

Source: [yosefario-dev/NClientV3, b2ee7ed006e6b06790f1b23d1528c339f298f7b5](https://github.com/yosefario-dev/NClientV3/tree/b2ee7ed006e6b06790f1b23d1528c339f298f7b5).

The studied tree contains an Apache 2.0 `LICENSE` with the attribution “Copyright 2024 Maxwai”.
This fork retains that attribution here alongside its existing `LICENSE` and the adapted source
files' notices. The studied tree contains no separate `NOTICE` file. The app's version row credits
yosefario-dev/NClientV3 in English, Simplified Chinese and Traditional Chinese.

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

## Files added or modified in 4.7.2 (P2, independent source merging)

- New `source/MergeSource.java`, `MergePage.java`, `SourceMerger.java`, `SourceCapabilities.java`: request sort intersection, independent failures, last-error propagation, round-robin and source-key dedup; semantics from yosefario-dev/NClientV3 b2ee7ed (Apache-2.0).
- `api/InspectorV3.java`: adapt the existing NH transport/parser and EH feed to the merger without changing stored sort. `ehentai/EhFeed.java`: empty parsed pages report the current page.
- Tests: `source/SourceMergerTest` and additional `ehentai/EhFeedTest` empty-page case. Evidence and remaining device checks in `MERGE_ACCEPTANCE_472.md`.

## Files added or modified in 4.7.2 (P3, EH query and cursor alignment)

- `ehentai/EhFeed.java`, new `EhPager.java`, `EhUrls.java`, `EhCategory.java`: all tag terms, request category/page/rating filters and global defaults, 24-query/4-page LRU and upstream cursor boundaries. Explicit Private and evicted-page cursor behavior match b2ee7ed.
- `api/components/Ranges.java`: optional EH request rating with legacy six-int Parcel reader preserved; `api/InspectorV3.java` passes ranges to EH. Rating UI follows in P4.
- Tests: `ehentai/EhSearchPagerTest`, updated `EhFeedTest`. No real Android Parcel test is claimed.

## Files added or modified in 4.7.2 (P4, source selection)

- New `source/SourcePolicy.java`, `SourcePreferences.java`, and `SourceDialogs.java`. Source migration follows the upstream preference structure, with the confirmed NH default and paired EH/EX switch. The toolbar and settings share the selector.
- `MainActivity.java`, `InspectorV3.java`, and `PageSwitcher.java` reset list paging for a new source selection and guard callbacks on the main thread. `ListAdapter.java` marks both sources in a merged list. Sort options use the source intersection.
- `EhSettings.java`, `EhAuth.java`, `EhBrowseActivity.java`, `EhFeed.java`, and `EhRepository.java` coordinate host changes and reset the list and detail caches.
- `SearchActivity.java`, `GeneralPreferenceFragment.java`, and the three `strings_472.xml` files expose the request rating and source choices. Tests cover source migration and the three locale resource sets.

## Files added or modified in 4.7.2 (P5, detail routes)

- `EhIntents.java`, `EhSimpleGallery.java`, `EhGalleryActivity.java`, and new `EhTags.java` route merged detail tags to MainActivity. The namespace mapping follows EHentaiSource at b2ee7ed. `InspectorV3.java` handles the synthetic tag IDs while retaining the NH server query.
- `EhDatabase.java` migrates to a source-qualified primary key for history, favorites and downloads and adds reading status. `EhDownloads.java`, `EhDownloadWorker.java`, and `EhRepository.java` isolate EH and EX offline files, progress and caches.
- `ListAdapter.java` and `MainActivity.java` refresh reading status using SourceKey. `EhMiscTest.java` covers tag mapping and distinct cache and download identities.

## Files added or modified in 4.7.2 (P6, library and reading)

- `GalleryHistory.java`, `HistoryActivity.java`, `Queries.java`, and the history navigation, search, and clear helpers combine the NH and EH history by time. The existing NH history schema is unchanged.
- `FavoriteActivity.java`, `FavoriteAdapter.java`, new `FavoriteSources.java`, and the favorite controls provide merged local favorites and a single online source. Source selection follows SourceManager and OnlineFavoriteFragment at b2ee7ed. The existing NH sync remains available under local favorites.
- `GalleryDownloads.java` dispatches browse and favorite batches by SourceKey, preserving EH/EX tokens. `InspectorV3.java` and `MainActivity.java` use the source-aware list format when saving an accepted page.
- `EhReaderActivity.java`, `EhPageNavigation.java`, `EhReadingSession.java`, the EH reader layouts and menu, and `AutoPageTurner.java` implement the agreed reading controls in the independent EH reader. ZoomActivity implements the shared auto-turn host through its existing methods.
- `EhDatabase.java` v3 separates EH reading statistics from visibility in history. `ReadingSessionHelper.java` shares status rules, and `ReadingStatsHelper.java` includes EH and EX totals, details and clearing.
- Tests: `GalleryDownloadsTest`, `FavoriteSourcesTest`, and `EhReadingSessionTest`. Device verification is tracked in `MERGE_ACCEPTANCE_472.md`.

## P7 release verification

- `app/build.gradle.kts` uses patch version 2. The version row in settings credits upstream source
  merging. `MERGE_ACCEPTANCE_472.md` records the compiled APK and upgrade acceptance steps.

## Popular browse for 4.7.2

- `InspectorV3.java` uses the EH/EX Popular list and NH daily results for the first mixed page. Further mixed pages request only NH daily results. `EhFeed.java` applies the EH category and minimum rating settings while preserving the site's list order.
- `MainActivity.java` exposes Popular (daily) on unfiltered browse when EH/EX is selected. `SourceCapabilities.java` resolves the sort for the request, and `SourcePreferences.java` stores the EH/EX browse choice alongside the existing NH preference.
- `Bookmark.java` and `SortType.java` restore the bookmarked sort, including older NH popular URLs. New menu text uses all three `strings_472.xml` locale files.
