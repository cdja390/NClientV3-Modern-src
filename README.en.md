# NClientV3 Modern

[简体中文](README.md) · [Download 4.7.2](https://github.com/cdja390/NClientV3-Modern-src/releases/tag/v4.7.2-modern) · [Release notes](docs/releases/v4.7.2-modern.md) · [Build from source](docs/BUILD.md)

NClientV3 Modern is an unofficial Android client based on NClientV3. It adds a merged nhentai and E-Hentai feed, Chinese tag translations, a Material 3 interface, offline downloads, reading progress, and per-gallery reading statistics.

The current release is **4.7.2-Modern** (`470200`). The launcher label is **NClientV3 Modern** and the package is `com.IlFforever.nclientv3.clone`.

## Browsing and reading

- Choose NH and EH or EX in the home menu or Settings. ExHentai requires an account with access.
- On an unfiltered home screen, **Popular (daily)** alternates NH daily results and EH or EX popular results on page one, preserving each source's rank. Later pages load the NH daily list. EH or EX alone has one popular page.
- The app combines local favorites and history across sources and sends each download to its source's queue. EH and EX use their own detail screen and reader.
- The readers support saved progress and optional reading statistics. The separate EH reader also supports vertical and continuous reading, volume and edge navigation, and auto page turning.

## Install and upgrade

The signed APK on GitHub Releases requires **Android 9 (API 28) or later**. Export a backup in Settings on the old version and keep your offline image files. Android can upgrade in place when the installed package and its signing certificate match. If Android reports a signature mismatch, keep the old app and its data. [Download and verify the APK](docs/releases/v4.7.2-modern.md) · [Backup format](docs/BACKUP_FORMAT.md)

The signed build was tested on Xiaomi 13 by upgrading from 4.7.0. All 11 exported primary database tables matched before and after the upgrade. The signed build also loaded the mixed popular feed. Huawei testing used the parallel Debug app. Live ExHentai and migration with existing EH records still need device coverage. [Acceptance record](docs/MERGE_ACCEPTANCE_472.md)

## Contributing

Build and run the unit tests with JDK 21 and Android SDK Platform 36. Include the app version, Android version, selected sources, and steps to reproduce when filing an issue. Remove account information from diagnostic logs. [Build guide](docs/BUILD.md) · [Issues](https://github.com/cdja390/NClientV3-Modern-src/issues) · [Security policy](SECURITY.md)

The source follows the NClientV2 and NClientV3 lineage through Dar9586, maxwai, and ILFforever. The DSH layer provides Chinese localization and DB v16. Source merging draws on yosefario-dev/NClientV3, and tag translations come from EhTagTranslation. [Credits and changes](README.md#致谢) · [Patched files](docs/PATCHED_FILES.md) · [Apache License 2.0](LICENSE)
