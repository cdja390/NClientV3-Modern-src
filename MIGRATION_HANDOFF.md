# NClientV3-Modern 源码工程迁移交接文档

## 1. 仓库与基线状态

- **源码工作区**：`NClientV3-Modern-src`（分支：`migrate/source-v1`）
- **历史基线**。本次迁移以已有源码和发布仓记录为参照。
- **提交栈**：
  1. `eb2e3b4`（上游基线 `ILFforever/NClientV3` main）
  2. `8ad520c` `feat(tag-i18n): snapshot Layer-2 Chinese tag translation and DB v16`
  3. `5c00f7c` `feat(modern): migrate theme panel, Modern UI resources, and assets`
  4. `f778ff0` `feat(modern): port Modern utility helpers, repo URLs, and SQLite WAL config`
  5. `5f54833` `feat(modern): wire Modern helpers into activities, adapters, and backup/sync`

## 2. 已完成并验证的内容

### 2.1 应用标识与构建配置（`app/build.gradle.kts` / `AndroidManifest.xml`）
- `applicationId = "com.IlFforever.nclientv3.clone"`
- `versionCode = 450000`、`versionName = "4.5.0-Modern"`
- Release 配置保持 `isMinifyEnabled = false`、`isShrinkResources = false`，`resValue("string", "app_name", "NClientV3 2")`
- 支持根目录可选的 `keystore.properties`（已在 `.gitignore` 忽略），未提供时自动产出未签名包。
- `AndroidManifest.xml` 中 `ZoomActivity` 主题已与 Modern APK 对齐。

### 2.2 资源、主题面板与数据库 WAL
- 已完成 `app/src/main/res/` 资源迁移（主题面板 `pref_theme_panel.xml`、底栏导航 `bottom_nav.xml`、历史页布局 `activity_history.xml`、玻璃栏背景、`arrays.xml`、`ids.xml`、`values/colors.xml`、`values-night/colors.xml`、`values/styles.xml`、`values-v31/styles.xml`、`strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`）。
- 还原了仅存在于原 APK smali 的 `ThemePanelPreference.java` 与 `ThemeColors.java`，并接入 `GeneralActivity.getTheme()`。
- 还原了 `DatabaseHelper.onConfigure(SQLiteDatabase)` 的 WAL（`enableWriteAheadLogging()` + `PRAGMA busy_timeout = 3000`），保持 `DATABASE_VERSION = 16`。

### 2.3 功能层与宿主接线
- 已迁入全部 22 个有效 `utility/*.java` 功能辅助类（连同 `ThemeColors.java` 与 `ThemePanelPreference.java` 共 24 个新增 Modern 类，排除死代码 `TabDeck`），全量检查无 `0x7f` 硬编码资源字面量，并已将 `VersionChecker` 与 `GeneralPreferenceFragment` 仓库/反馈链接指向 `cdja390/NClientV3-Modern`。
- 已完成宿主接线：
  - **页面与菜单**：`MainActivity`（底栏安装/同步、`GlassBar`、`SafeMode` 切换与状态同步、`LanguageFilterHelper`、`BrowseEmptyHelper`、`AvatarHelper`、`onNewIntent` 抽屉拉起）、`GalleryActivity`（展开标题 `TitleToggle`、阅读统计菜单、`ResumeHelper`、收藏联动下载、`checkBookmark` 打开 `ZoomActivity` `openZoomPage`）、`ZoomActivity`（边读边下、自动翻页 `AutoPageTurner`、音量键翻页方向 `VolumeDirectionHelper`、阅读会话与阅读统计埋点、`onPause` 自动保存进度及自动翻页辅助方法）、`FavoriteActivity`（底栏路由、同步页数上限弹窗、输入法回车搜索、安全模式）、`HistoryActivity`（新布局与空态提示、清空历史确认、`SearchImeHelper`）、`LocalActivity`（底栏路由、`SearchImeHelper`）、`GeneralPreferenceFragment`（接入 `ResumeHelper`、`ReadingStatsHelper`、`DownloadWhileReadingHelper`、`VolumeDirectionHelper` 偏好绑定）。
  - **适配器与用户模型**：`FavoriteAdapter`、`ListAdapter`、`LocalAdapter`、`RecentFavoriteAdapter`、`StatusViewerAdapter`、`api/comments/User`、`api/users/UserProfile`、`loginapi/User`。
  - **数据与网络**：`Queries`（`db` 公开与 `LocalTagFilterHelper` 过滤）、`FavoriteSyncManager`（`syncMaxPages` 增量同步上限）、`CustomInterceptor`（`NetworkRecoveryHelper` 重载与 `IOException` 重试）、`Exporter` / `Importer`（阅读统计表备份导入导出）。
- 通过脚本对比 `NClientV3-publish/decoded` 全部非合成 `.method` 与当前 Java 源码（含 `ThemePanelPreference.smali` 中空返回的死方法 `tintPreview` 排查确认）。

### 2.4 构建、单测与 APK 产物核验结果
在完成全部源码修改后（未再修改任何源码/资源文件，随后等价提交为 `5f54833`），于 `NClientV3-Modern-src` 实测通过：
1. `cmd /c "gradlew.bat assemblePost28Debug testPost28DebugUnitTest --console=plain < nul"` → `BUILD SUCCESSFUL`（Java 编译启用 `-Xlint:unchecked -Werror`，无警告无报错）
   - `FavoriteSyncManagerTest`：17/17 通过（`skipped=0`, `failures=0`, `errors=0`）
   - `UtilityHostTest`：6/6 通过（`skipped=0`, `failures=0`, `errors=0`）
2. `cmd /c "gradlew.bat assemblePost28Release --console=plain < nul"` → `BUILD SUCCESSFUL`（未签名 Release 构建通过）
3. `aapt2 dump badging` 核验 `app\build\outputs\apk\post28\release\app-post28-release-unsigned.apk`：
   - `package: name='com.IlFforever.nclientv3.clone' versionCode='450000' versionName='4.5.0-Modern'`
   - `minSdkVersion:'28'`、`targetSdkVersion:'36'`、`application-label:'NClientV3 2'`
4. DEX 校验：已确认上述 24 个 Modern 类（`ThemePanelPreference`、`ThemeColors` 及 22 个有效功能辅助类）均已编入 `classes*.dex`，`TabDeck` 未编入。

## 3. 待后续交接完成的事项

以下事项因涉及正式密钥、真机环境或仓库权限，留待后续执行：

1. **正式密钥签名打包**
   - 在 `keystore.properties` 填入正式签名配置：
     ```properties
     storeFile=<正式 release.jks 路径>
     storePassword=<密钥库密码>
     keyAlias=<别名>
     keyPassword=<私钥密码>
     ```
   - 执行 `gradlew.bat assemblePost28Release` 生成已签名正式包（产物位于 `app/build/outputs/apk/post28/release/` 与 `app/build/outputs/apk/release/`，注意 `CreateRenamedApk` 任务会将 `4.5.0-Modern` 截断 `-Modern` 命名为 `NClientV3_4.5.0.apk`，可按需调整或直接使用 `app-post28-release.apk`）。
2. **真机覆盖安装验收**
   - 在已安装旧版 `NClientV3 2`（同签名）的设备上直接 `adb install -r` 覆盖安装，验证启动不闪退、数据库 v16 WAL 正常读写、原有收藏/历史/登录 Cookie/设置保留完整。
3. **备份导出与互导验收**
   - 在真机上分别验证「源码版导出备份 ZIP → 覆盖或干净安装后导入」、「原 smali 候选版导出备份 ZIP → 源码版导入」及阅读统计表 `reading_stats` 恢复。
4. **全功能 UI 人工走查**
   - 核对设置页 `ThemePanelPreference` 主题色卡切换、底部导航与液态玻璃效果、画廊标题展开、断点续读跳转、边读边下、自动翻页与音量键方向、历史清空、收藏同步页数上限等交互。
5. **（可选）后续收敛与推送**
   - 配置 `cdja390/NClientV3-Modern` 远程仓库并推送；下一阶段再按计划开启 R8 混淆、补齐英文翻译、清理其他无用分支/遗留字符串。
