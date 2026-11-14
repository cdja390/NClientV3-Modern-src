# NClientV3 Modern

> [!IMPORTANT]
> **This project was developed almost entirely with AI through vibe coding.**
>
> The maintainer, **QALAN**, defined the requirements, chose the implementation approach and acceptance criteria, coordinated testing on physical devices, personally signed the release APK, and approved its publication. Multiple AI models collaborated on implementation, review, testing, and documentation.

[简体中文](README.md) · [Download 4.7.2 APK](https://github.com/cdja390/NClientV3-Modern-src/releases/download/v4.7.2-modern/NClientV3-Modern-v4.7.2.apk) · [All releases](https://github.com/cdja390/NClientV3-Modern-src/releases)

An unofficial Android client based on NClientV3. Browse nhentai (NH) and E-Hentai (EH) in the same list, with Chinese tags, offline downloads, saved reading progress, and per-gallery statistics. Users who already have ExHentai (EX) access can also use EX.

## Download and upgrade

| Field | 4.7.2 stable release |
| --- | --- |
| App name | NClientV3 Modern |
| Version | `4.7.2-Modern` (`470200`) |
| Package | `com.IlFforever.nclientv3.clone` |
| System requirements | Android 9 (API 28) or later |

1. If you already have an older version, open **Settings → Export data** and save the ZIP. Keep downloaded image files separately. [Backup format](docs/BACKUP_FORMAT.md)
2. Download the signed APK and check the SHA-256 digests of the file and its signing certificate. Android can upgrade in place when both the package name and signing certificate match. If Android reports a signature conflict, keep the installed app and its data. [Release notes and APK verification](docs/releases/v4.7.2-modern.md)
3. After upgrading, check the app version. Open Favorites, History, Downloads, and a gallery you have read to verify your data and saved page.

## Merged browsing and popular lists

1. Choose **Gallery sources** in the home menu or open the same picker in Settings. Select **NH** and **EH**. If you already have EX access, open **E-Hentai** from the navigation drawer, sign in, and enable **Use ExHentai** in that screen's menu. EH changes to EX in the source picker.
2. Clear the search query and filters, then choose **Popular (daily)** in the home sort menu.
3. The first page alternates NH and EH/EX galleries in each site's own ranking order. Later pages load the NH daily list. With only EH/EX selected, the popular list has one page.

A two-source query with keywords or filters uses **Recent**. With only NH selected, NH's sort options are available. Local favorites and history show all sources together. Online favorites follow the selected source, and batch downloads go to each gallery's source. EH/EX galleries use a separate detail screen and reader.

## Features

- **Reading and resuming.** The reader can ask before resuming, jump directly to your last page, or remember your choice. The EH/EX reader supports horizontal, vertical, and continuous scrolling modes, plus edge taps, volume keys, and timed page turns.
- **Offline downloads.** Background downloads can start while you read so you can continue offline later.
- **Reading status and statistics.** The app can mark galleries as **Reading** or **Completed** based on page progress. This is off by default. Reading time and session counts are tracked per gallery.
- **Chinese tags and favorites sync.** Local tag filtering matches English and Chinese names. Favorites sync both ways with the website, with a choice of how many pages to sync.
- **Material 3 and AMOLED.** The floating pill-shaped navigation bar supports frosted glass and uses a solid background when the effect is off. The drawer header follows the theme, and the reader supports a pure-black AMOLED background.
- **Search and history.** Submitting a search dismisses the keyboard. Back dismisses the keyboard first. Clearing history asks for confirmation and then displays an empty state.

## Verification

The 4.7.2 stable release passed 146 JVM tests. A Xiaomi 13 upgraded in place from 4.7.0 using the signed release. All 11 exported primary database tables matched row for row before and after the upgrade. The signed release also loaded the first page of the mixed popular list. Huawei testing used the separate Debug package alongside the existing app. Live EX and migration with existing EH records still need device coverage. [4.7.2 acceptance record](docs/MERGE_ACCEPTANCE_472.md)

Builds, unit test results, and lint reports for the source branch are available in GitHub Actions. [Android CI](https://github.com/cdja390/NClientV3-Modern-src/actions/workflows/android.yml)

## Build from source

Use JDK 21, Android SDK Platform 36, and Build Tools 36.0.0. Set `JAVA_HOME` and the Android SDK path, then run this command from the repository root.

```powershell
.\gradlew.bat assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

Use `./gradlew` on Linux and macOS. The Debug build has a separate package name and can be installed alongside the release. The source also supports a `pre28` build for API 26/27. [Setup, build outputs, and test reports](docs/BUILD.md)

## Contributing and feedback

- When filing an issue, include the app version, Android version, selected sources, and steps to reproduce. Remove cookies, tokens, account details, and personal paths from logs before uploading them. [Report an issue](https://github.com/cdja390/NClientV3-Modern-src/issues/new/choose)
- Code, translation, and documentation changes are welcome via pull requests. [Contributing guide](CONTRIBUTING.md)
- Submit security issues through GitHub's private reporting form. [Security policy](SECURITY.md)
- Release changes, verification scope, and downloads. [Changelog](CHANGELOG.md)

## Project history and design

The project uses ILFforever/NClientV3 as its upstream base and traces its lineage through maxwai/NClientV3 and Dar9586/NClientV2. The DSH layer supplies Chinese localization and DB v16. Modern builds on it to improve browsing, search, reading, and the interface. The separate package name gives the app its own data space. [ILFforever/NClientV3](https://github.com/ILFforever/NClientV3) · [maxwai/NClientV3](https://github.com/maxwai/NClientV3) · [Dar9586/NClientV2](https://github.com/Dar9586/NClientV2)

The navigation bar and dialogs use Material 3. The drawer header blends theme colors such as `colorSurfaceContainerHigh` and `colorPrimaryContainer`. The floating navigation bar draws on FlClash's 64dp pill shape, convex lens, and droplet effects, and on SukiSU-Ultra's pill navigation interactions. Two-way sync between local and website favorites comes from upstream v4.5.0. This fork adds a choice of how many pages to sync. [Adapted files and attribution](docs/PATCHED_FILES.md)

## Credits

- Dar9586, maxwai, and ILFforever provided the original project and upstream implementations. [Dar9586](https://github.com/Dar9586) · [maxwai](https://github.com/maxwai) · [ILFforever](https://github.com/ILFforever)
- yosefario-dev/NClientV3 provided the behavior reference and code adapted for source merging in 4.7.2. [yosefario-dev/NClientV3](https://github.com/yosefario-dev/NClientV3)
- EhTagTranslation provides Chinese tag names (`name_zh`). [EhTagTranslation](https://github.com/EhTagTranslation/DatabaseReleases)
- FlClash and SukiSU-Ultra provided visual and interaction references for frosted glass, liquid glass, and the floating navigation bar. [FlClash](https://github.com/chen08209/FlClash) · [SukiSU-Ultra](https://github.com/sukisu-ultra/SukiSU-Ultra)
- Han1meViewer provided early UI references for themes and navigation. [Han1meViewer](https://github.com/misaka10032w/Han1meViewer)

Thanks to upstream translators, maintainers, and contributors. [NClientV3 contributors](https://github.com/ILFforever/NClientV3#contributors) · [Original project contributors](https://github.com/ILFforever/NClientV3#contributors-of-original-project)

## License

This project uses the upstream **Apache License 2.0**. Copyright 2024 maxwai. [Full license](LICENSE)
