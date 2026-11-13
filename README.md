# NClientV3 Modern

> [!IMPORTANT]
> **此项目几乎全程由ai开发，主要采用 Vibe Coding。**

[English](README.en.md) · [下载 4.7.2 APK](https://github.com/cdja390/NClientV3-Modern-src/releases/download/v4.7.2-modern/NClientV3-Modern-v4.7.2.apk) · [所有版本](https://github.com/cdja390/NClientV3-Modern-src/releases)

NClientV3 的非官方 Android 分支。在同一列表浏览 nhentai（NH）和 E-Hentai（EH），支持中文标签、离线下载、续读与按作品统计。已有 ExHentai（EX）访问权限的用户也可以使用 EX。

## 下载与升级

| 项目 | 4.7.2 正式版 |
| --- | --- |
| 应用名称 | NClientV3 Modern |
| 版本 | `4.7.2-Modern`（`470200`） |
| 包名 | `com.IlFforever.nclientv3.clone` |
| 系统要求 | Android 9（API 28）或以上 |

1. 已有旧版时，先打开 **设置 → 导出数据**，保存 ZIP。已下载的图片文件需要单独保留。 [备份格式](docs/BACKUP_FORMAT.md)
2. 下载签名 APK，并核对文件与签名证书的 SHA-256。包名和签名证书都匹配时，Android 可以原位升级。遇到签名冲突时，保留已安装的应用和数据。 [发行说明与 APK 校验](docs/releases/v4.7.2-modern.md)
3. 升级后核对应用版本，并打开收藏、历史、下载记录和已阅读的作品，确认数据和续读页。

## 混排与热门榜

1. 在主页菜单选择 **画廊来源**，或从设置打开同一个选择框。勾选 **NH** 和 **EH**。已有 EX 权限时，从侧边栏进入 **E-Hentai**，登录后在该页菜单打开 **使用 ExHentai**，来源选择框里的 EH 会切换为 EX。
2. 清除搜索关键词和筛选，在主页排序菜单选择 **热门（日榜）**。
3. 第一页按各站的榜单顺序交替显示 NH 和 EH／EX 条目。后续页加载 NH 日榜。只选 EH／EX 时，热门榜为单页。

带关键词或筛选的双站查询使用「最近」。只选 NH 时可使用 NH 的排序选项。本地收藏和历史汇总各来源，在线收藏按所选来源查询，本页批量下载按来源分流。EH／EX 作品使用独立的详情和阅读器。

## 主要功能

- **阅读与续读**。按设置询问、自动跳到上次阅读页或记住选择。EH／EX 阅读器支持水平、垂直和条漫模式，也支持边缘点击、音量键和定时翻页。
- **离线下载**。可在阅读时触发后台下载，稍后离线继续阅读。
- **阅读状态与统计**。可按翻页进度自动标记「正在阅读」或「已完成」，默认关闭。按作品记录阅读时间与次数。
- **中文标签与收藏同步**。本地标签过滤匹配英文名与中文名。支持本地收藏与网站收藏双向同步，可选择同步页数。
- **Material 3 与 AMOLED**。悬浮胶囊底栏可启用毛玻璃，关闭后显示实心底栏。抽屉顶栏跟随主题色，阅读背景支持 AMOLED 纯黑。
- **搜索与历史**。确认搜索时收起键盘，返回键先收键盘。清空历史前会要求确认，清空后显示空状态。

## 验证范围

4.7.2 正式版通过 146 项 JVM 测试。在小米 13 上用正式签名包从 4.7.0 原位升级，前后导出的 11 张主库表逐行一致，正式包通过热门首屏混排验证。华为测试使用与旧版并存的 Debug 包。EX 联网和有既有记录的 EH 库迁移仍待真机验证。 [4.7.2 验收记录](docs/MERGE_ACCEPTANCE_472.md)

源码分支的构建、单测结果和 lint 报告可在 GitHub Actions 查看。 [Android CI](https://github.com/cdja390/NClientV3-Modern-src/actions/workflows/android.yml)

## 从源码构建

使用 JDK 21、Android SDK Platform 36 和 Build Tools 36.0.0。先配置 `JAVA_HOME` 和 Android SDK 路径，再从仓库根目录运行。

```powershell
.\gradlew.bat assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

Linux 和 macOS 使用 `./gradlew`。Debug 有独立包名，可以并存安装。源码也提供面向 API 26／27 的 `pre28` 构建。 [环境配置、产物和测试报告](docs/BUILD.md)

## 贡献与反馈

- 提交问题时附应用版本、Android 版本、使用的来源和复现步骤。上传日志前，移除 Cookie、令牌、账号和个人路径。 [问题反馈](https://github.com/cdja390/NClientV3-Modern-src/issues/new/choose)
- 代码、翻译和文档修改均可通过 Pull Request 提交。 [贡献指南](CONTRIBUTING.md)
- 安全问题通过 GitHub 私密报告提交。 [安全政策](SECURITY.md)
- 版本变化、已验证范围和下载入口。 [变更记录](CHANGELOG.md)

## 项目沿革与设计

项目以 ILFforever/NClientV3 为上游基线，向前可追溯到 maxwai/NClientV3 与 Dar9586/NClientV2。DSH 层提供中文本地化和 DB v16，Modern 在此基础上完善浏览、搜索、阅读和界面。独立包名提供独立的应用数据空间。 [ILFforever/NClientV3](https://github.com/ILFforever/NClientV3) · [maxwai/NClientV3](https://github.com/maxwai/NClientV3) · [Dar9586/NClientV2](https://github.com/Dar9586/NClientV2)

底栏和对话框采用 Material 3。抽屉顶栏用 `colorSurfaceContainerHigh` 和 `colorPrimaryContainer` 等主题色动态调和。悬浮底栏参考 FlClash 的 64dp 胶囊、凸透镜与水滴效果，以及 SukiSU-Ultra 的胶囊交互。本地与网站收藏的双向同步沿用上游 v4.5.0，本分支增加了同步页数选择。 [适配文件与署名](docs/PATCHED_FILES.md)

## 致谢

- Dar9586、maxwai 和 ILFforever 提供原始项目和上游实现。 [Dar9586](https://github.com/Dar9586) · [maxwai](https://github.com/maxwai) · [ILFforever](https://github.com/ILFforever)
- yosefario-dev/NClientV3 提供 4.7.2 来源合并的行为参考和适配来源。 [yosefario-dev/NClientV3](https://github.com/yosefario-dev/NClientV3)
- EhTagTranslation 提供标签中文名（`name_zh`）数据。 [EhTagTranslation](https://github.com/EhTagTranslation/DatabaseReleases)
- FlClash 和 SukiSU-Ultra 提供毛玻璃、液态玻璃与胶囊底栏的视觉和交互参考。 [FlClash](https://github.com/chen08209/FlClash) · [SukiSU-Ultra](https://github.com/sukisu-ultra/SukiSU-Ultra)
- Han1meViewer 提供早期主题与导航的界面参考。 [Han1meViewer](https://github.com/misaka10032w/Han1meViewer)

感谢上游的翻译、修复和维护贡献者。 [NClientV3 贡献者](https://github.com/ILFforever/NClientV3#contributors) · [原始项目贡献者](https://github.com/ILFforever/NClientV3#contributors-of-original-project)

## 许可证

本项目沿用上游的 **Apache License 2.0**。Copyright 2024 maxwai。 [完整许可证](LICENSE)
