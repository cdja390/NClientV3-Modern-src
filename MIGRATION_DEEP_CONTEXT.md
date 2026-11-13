# NClientV3-Modern 源码迁移深度隐性契约（交接排障备忘）

> 本文档按交接要求**纯基于当前会话已直接读取的文件原文、smali/DEX 审计输出及构建日志固化**；对于当前恢复会话中未直接打开逐行原文的片段（例如 `DatabaseHelper.java` L46-202 及早先整文件移入的 `utility/*.java` 内部实现），文中均已明确标出边界，继任模型排查时请直接打开对应源码确认，切勿臆测。

---

## 1. Smali 逆向还原脆弱点与推断记录 (Smali-to-Java Fragility Map)

### 1.1 `ThemePanelPreference.java`（`components/views/ThemePanelPreference.java`，直接审计 L1-174）
- **基类与布局契约**：继承 `androidx.preference.Preference`，构造器绑定 `setLayoutResource(R.layout.pref_theme_panel)`。
- **刻意剔除的不可达死方法 `tintPreview`**：
  - 扫描 `ThemePanelPreference.smali` L126-141 确认存在 `.method private tintPreview(Landroid/view/View;Landroid/view/ViewGroup;)V`，但方法体首行（L129）即无条件执行 `return-void`，其后的 `R$id;->preview_accent` 属于早先被重构废弃的不可达死代码，实际调色板渲染已由 `ThemeCardHelper.bindSukiSuCards` 接管。为通过编译静态检查与保持整洁，未还原 `tintPreview`。
- **存留的未使用辅助方法（潜在警告/维护注意点）**：
  - `ThemePanelPreference.java` L45-64 仍保留私有方法 `selectColor(ViewGroup group, String selected)` 与 `wireDots(ViewGroup group)`（按 smali/前序还原保留，实际 `onBindViewHolder` 中已由 `ThemeCardHelper.bindSukiSuCards(colorGroup, color, new Dot(this))` 替代调用）。若继任者开启更严格 Lint（例如 unused-private-method 转 error），应核实后决定去留。
- **高风险运行时假设**：
  - `onBindViewHolder` 直接对 `itemView.findViewById(R.id.mode_group)` 强制作为 `MaterialButtonToggleGroup` 调用 `modeGroup.check(checkedId)`、`clearOnButtonCheckedListeners()`、`addOnButtonCheckedListener(new Pick(this, 0))`，无 `null` 防护；若 `pref_theme_panel.xml` ID 或控件类型变更加上旧缓存布局异常，会直接抛出 `NullPointerException` 或 `ClassCastException`。
  - `applyChoice(int kind, int checkedId)` 在 `binding || kind != 0` 时直接返回；切模式或 `applyColor(String)` 时写 `"Settings"` 中的 `"theme_select"`、`"black_theme"`、`"theme_color"` 后直接解包 `ContextWrapper` 调用 `((Activity) context).recreate()`。
  - 当前 `ThemeColors.java` 位于 `com.maxwai.nclientv3.utility.ThemeColors`（非 `settings` 包）；由 `GeneralActivity.smali:220` 对应位置调用 `ThemeColors.apply(Resources$Theme, Context)`。本会话仅验证该类存在及编入 DEX，具体色板表内容请继任者直接读取 `utility/ThemeColors.java` 与 `components/activities/GeneralActivity.java`。

### 1.2 `MainActivity.java` 对 smali 的安全等价改写与边界条件
- **`showError` 触发 `BrowseEmptyHelper.show` 的 NPE 修复**：
  - 根据前序 `MainActivity.smali:1737` 比对记录，原 smali 在检查 `adapter` 数量前存在条件跳转倒置风险（若 `adapter == null` 可能直接调用 `getItemCount()` 引发 NPE）。
  - Java 源码改写为安全短路判断：`BrowseEmptyHelper.show(this, text, adapter != null && adapter.getItemCount() > 0);`。
- **`changeLanguageRunnable` 中 `LanguageFilterHelper.reloadSearch` 的无条件返回**：
  - 直接查证 `MainActivity.smali` L2859-2871：当 `modeType == ModeType.SEARCH` 时，调用 `LanguageFilterHelper.reloadSearch(this)`（该方法签名为返回 `boolean`），**紧接着不判断返回值直接执行 `return-void`**。
  - Java 源码严格按 smali 等价保留：`if (modeType == ModeType.SEARCH) { LanguageFilterHelper.reloadSearch(this); return; }`。继任者不要擅自改成 `if (LanguageFilterHelper.reloadSearch(this)) return;`，除非实测确认返回 `false` 时允许落入后方 `useNormalMode()` 重建首页流。
- **未迁入的残留布尔字段 `adjustingBottomNav`**：
  - `MainActivity.smali` L32 声明了 `.field adjustingBottomNav:Z`，全库检索 `utility/*.java`（含 `BottomNavRouter`、`LiquidHostLayout`、`SwipeTabs` 等）均无任何直接或反射引用，判定为前序重构遗留字段，未再声明以免触发 unused-field 问题；而 `bottomNavigationView`（package-private `BottomNavigationView bottomNavigationView;`）已按原名称保留。
- **`onNewIntent` 跨页抽屉拉起**：
  - 还原自 `MainActivity.smali` L3879-3904：检查 `intent.getBooleanExtra("nclient_open_drawer", false)`，为 `true` 时调用 `drawerLayout.openDrawer(GravityCompat.START)`。若处于 `modeType != ModeType.NORMAL`（`drawerLayout` 被锁为 `LOCK_MODE_LOCKED_CLOSED`），行为需真机确认。

### 1.3 `ZoomActivity.java` 还原脆弱点（对比 `ZoomActivity.smali`）
- **`maybeDownloadWhileReading()` 起始页码语义风险**：
  - 按 `ZoomActivity.smali` L2323 精确位置，`onCreate` 在赋值 `directory = gallery.getGalleryFolder()` 后、`findViewById(R.id.toolbar)` 和 `goToPage(page)` **之前**即调用 `maybeDownloadWhileReading()`，方法内读取成员字段 `actualPage`（此时尚未被 `goToPage` 更新，仍为 `0`），即调用 `DownloadWhileReadingHelper.onReadStart(this, gallery, actualPage)`（见 `DownloadWhileReadingHelper.java` L56-62）。
  - **影响**：从续读或特定页直达 `ZoomActivity` 且开启“点击阅读触发边读边下”时，初次触发传递的页索引是 `0`；随后 `setPageText(page + 1)` 与后续翻页通过 `DownloadWhileReadingHelper.onPageChanged(this, gallery, page - 1)` 纠正当前页。
- **`onPause()` 主线程写库与强行保存断点**：
  - 还原自 `ZoomActivity.smali` L3355-3386：只要 `gallery != null`，每次 `onPause()` 均直接调用 `Queries.ResumeTable.insert(gallery.getId(), actualPage + 1)`，随后依次调用 `AutoPageTurner.onPause(this)`、`ReadingSessionHelper.onLeave(this, gallery)`、`ReadingStatsHelper.onPause(this, gallery)`、`super.onPause()`。
  - **风险**：`ResumeTable.insert` 在主 UI 线程执行数据库写入，依赖 `DatabaseHelper` 的 WAL + `busy_timeout = 3000` 缓解阻塞，但若后台正在高并发写库仍需留意真机卡顿/ANR。
- **7 个新增 `public` 桥接方法（严禁改签名或降可见性）**：
  - `autoTurnNextPage()`（检查 `gallery != null && actualPage < gallery.getPageCount() - 1` 后调用 `changeClosePage(true)`）、`getZoomCurrentPage()`（返回 `actualPage + 1`）、`getZoomTotalPages()`、`hideBars()`、`showBars()`（均直接读写 `isHidden` 并调用私有 `applyVisibilityFlag()` + `animateLayout()`）、`setVolumeSide(boolean)`、`getVolumeSide()`。此外 `changeClosePage(boolean)` 已保持 `public`。

### 1.4 `GalleryActivity.java` 还原关键点
- **`checkBookmark()` 行为对齐与潜在双提示观察点**：
  - 上游 `GalleryActivity` 在 `checkBookmark()` 点击「继续/恢复」仅滚动底层列表（`recycler.scrollToPosition(page)`）；而 `GalleryActivity.smali` L1480-1595 已改为调用私有 `openZoomPage(int page)` 直接构造 `Intent` 跳转 `ZoomActivity`（校验 `page <= 0` 返回，携带 `.GALLERY`、`.DIRECTORY`、`page`）。Java 已按 smali 改回 `openZoomPage(page)`。
  - `loadGallery` 在 `zoom <= 0` 时调用 `ResumeHelper.handleResumeOnLoad(this, gallery, adapter != null ? adapter.getDirectory() : null)`，紧接着在 L180 调用 `checkBookmark()`。真机测试三档续读偏好（每次询问 / 始终跳转 / 始终留封面）时，需确认两者不会发生抢焦点或同时弹出对话框与 Snackbar 的体验冲突。

### 1.5 死代码 `TabDeck` 剔除结论
- 前序全库审计确认 `TabDeck` 零调用。
- 本轮已通过 `Select-String` 检查确认所有有效 `utility/*.java` 与 `ThemePanelPreference.java` **均无 `0x7f` 硬编码资源字面量**，并通过检查打包后的 Release APK（`app-post28-release-unsigned.apk`）DEX，实测确认 `TabDeck` **未编入 DEX**，其余 24 个有效 Modern 新增类全部存在（无悬空类加载报错风险）。未检查 `res/` 中是否还存在仅服务于早期实验的孤立未使用布局/字符串，第一版按与旧包兼容为优先暂不删除。

---

## 2. 三层叠加架构（Upstream + DSH + Modern）冲突裁决账本

### 2.1 三层逻辑交汇与裁决落点
1. **设置页主题偏好（Upstream vs Modern）**：
   - 保留 `res/xml/settings.xml` 中原有 `preference_key_theme_select` 项但配置 `app:isPreferenceVisible="false"`，在上方插入自定义 `<com.maxwai.nclientv3.components.views.ThemePanelPreference .../>`，共用 `"Settings"` XML 中的 `"theme_select"` / `"black_theme"` / `"theme_color"` 键，保证旧逻辑与备份兼容性。
2. **主导航与历史/收藏/本地跳转（Upstream Drawer vs Modern BottomNav）**：
   - 上游通过 `NavigationView` 抽屉直接 `startActivity`；Modern 引入 `R.id.bottom_nav` 与 `BottomNavRouter`。
   - 裁决：`MainActivity.onNavigationItemSelected` 首行统一执行 `BottomNavRouter.closeDrawer(this)`，且 `R.id.history`、`R.id.favorite_manager` 及 `startLocalActivity()` 改走 `BottomNavRouter.switchTo(this, ...)`。同时在 `MainActivity.manageDrawer()` 中，`modeType != ModeType.NORMAL` 时隐藏 `bottomNavigationView`（`View.GONE`），`NORMAL` 时显示（`View.VISIBLE`）。
3. **画廊详情页标题展示（Upstream ActionBar vs Modern Collapsing 展开标题）**：
   - `GalleryActivity.applyTitle()` 中强制 `collapsing.setMaxLines(1)` 且 `actionBar.setTitle("")` 清空系统标题栏文字，转交 `R.id.gallery_full_title` 与 `R.id.title_expand`（通过静态内部类 `TitleToggle` 切换 `"展开"`/`"收起"`）。
4. **搜索、收藏过滤与网络恢复（DSH DB v16 / TagI18n vs Modern Helpers）**：
   - **本地与收藏筛选**：`LocalAdapter` 与 `Queries.FavoriteTable`（`getAllFavoriteGalleriesCursor` / `countFavorite`）收口至 `LocalTagFilterHelper`。
   - **网络请求认证与恢复**：逐行审计确认 `ApiAuthInterceptor.smali`（426 行）与仓库源码一致（保留 `"NClient/4.5.0 (https://github.com/ILFforever/NClientV3)"` UA 与 `AuthStore`），仅在 `CustomInterceptor` 中外包 `NetworkRecoveryHelper.tryBeginReload()`/`endReload()`（`try/finally`）、`rec` header 重入保护、`response.close()` 后重试及 `catch (IOException)` 重试路径。
   - **用户头像字段收敛**：`api/comments/User`、`api/users/UserProfile`、`loginapi/User` 的 `getAvatarUrl()` 委托 `AvatarHelper.normalizeAvatarUri` / `normalizeAvatarString`；`MainActivity.loadStringLogin` 改为 `AvatarHelper.loadUserAvatar(this, user, navAvatar)`。
   - **仓库元信息**：`VersionChecker.java`（L35-36）与 `GeneralPreferenceFragment.java`（L387）已将 Release API、最新版本页与反馈 Issue 链接指向 `cdja390/NClientV3-Modern`。

### 2.2 宿主严格初始化时序要求（禁止打乱顺序）
- **`MainActivity`**：
  1. `onCreate`: `super.onCreate`（触发 `GeneralActivity.getTheme()` 中 `ThemeColors.apply` 注入动态主题）→ `opensUnifiedFavorites(getIntent())` 检查（命中则跳 `FavoriteActivity` 并 `finish()` 返回，不再初始化主页视图）→ `setContentView(R.layout.activity_main)` → `selectStartMode` → `findUsefulViews()`（先获取 `bottomNavigationView = findViewById(R.id.bottom_nav)` 及 `pageSwitcher` 等）→ `initializeToolbar()` → `initializeNavigationView()`（末尾调用 `setupBottomNav()`：`BottomNavRouter.install(this, R.id.nav_browse); GlassBar.attach(pageSwitcher);`）→ `manageDrawer()`（必须放在 `findUsefulViews()` 与 `selectStartMode()` 之后，确保 `bottomNavigationView` 与 `modeType` 均已就绪）。
  2. `onResume`: `super.onResume()` → 紧跟 `BottomNavRouter.sync(this, R.id.nav_browse); GlassBar.refresh(this);` → `Login.initLogin(this)` → 检查 `SafeMode.consumeChange(this)` 并刷新 `adapter` → `invalidateOptionsMenu()`。
- **`GeneralPreferenceFragment`**：
  - `ResumeHelper.bindPreference(this)`、`ReadingStatsHelper.bindPreference(this)`、`DownloadWhileReadingHelper.bindPreference(this)`、`VolumeDirectionHelper.bindPreference(this)` 必须在 `mainMenu()` 末尾和 `onResume()` 顶部均执行一次，确保偏好入口与动态摘要实时同步。
- **`GalleryActivity`**：
  - `loadGallery` 必须先构造 `adapter = new GalleryAdapter(...)` 并挂载 `recycler.setAdapter(adapter)`，再调用 `ResumeHelper.handleResumeOnLoad` 与 `checkBookmark()`（依赖 `adapter.getDirectory()`）。
- **`ZoomActivity`**：
  - `onCreate`: 解析 `gallery` 与 `directory` → `maybeDownloadWhileReading()` → 初始化视图、`goToPage(page)`、`setPageText(page + 1)` → `ReadingSessionHelper.onSessionStart(this, page + 1)` → `ReadingStatsHelper.onSessionStart(this, page + 1, savedInstanceState)` → `seekBar.setProgress(page)`。
  - `onDestroy`: 先 `AutoPageTurner.onDestroy(this)` 再 `super.onDestroy()`。
  - `dispatchTouchEvent`: 先 `ReadingStatsHelper.onTouch(this, ev)` → 若 `AutoPageTurner.onDispatchTouchEvent(this, ev)` 返回 `true` 则直接截断并返回 `true`。

*(注：本恢复会话未打开 `CrashApplication.java`、`Global.java` 完整源码，如涉及冷启动全局初始化与 `TagI18n` 加载顺序，请直接核对对应 Java 文件。)*

---

## 3. DB v16、WAL 配置与 Exporter/Importer 底层数据契约

### 3.1 `DatabaseHelper.java` 已确认契约（直接读取 L1-45）
- 库名：`DATABASE_NAME = "Entries.db"`
- 版本号：`DATABASE_VERSION = 16`（对齐 DSH 标签汉化层，严禁回退或擅改）。
- `onConfigure(SQLiteDatabase db)` 确切实现：
  ```java
  super.onConfigure(db);
  db.enableWriteAheadLogging();
  try (Cursor c = db.rawQuery("PRAGMA busy_timeout = 3000", null)) {
      if (c != null) {
          c.moveToFirst();
      }
  }
  ```
- *(注：`DatabaseHelper.smali` 方法扫描确认存在 `addRangeColumn`、`addStatusTables`、`addTagNameZhColumn`、`createAllTables`、`updateFavoriteTable`、`updateGalleryWithSizes` 及 `onUpgrade`/`onDowngrade`，具体列顺序与迁移 SQL 在 `DatabaseHelper.java` L46-202，本会话未读其具体行，真机升级排查前请直接读取该区间确认与原版 `Entries.db` 兼容。)*

### 3.2 `Queries.java` 暴露契约
- `Queries.db` 必须维持 `public static SQLiteDatabase db;`（供外部工具或导出逻辑直接读取）。

### 3.3 `Exporter.java` & `Importer.java` 备份互导契约（直接读取 `Exporter.java` L60-165、`Importer.java` L60-120）
- **导出（`Exporter.java`）**：
  - `exportData` 向 ZIP 依次写入 `DB_ZIP_FILE`（调用 `dumpDB(zip)`）及各 `SHARED_FILES` 对应的 `<shared>.json`（调用 `exportSharedPreferences`）。
  - **关键结构位置**：`ReadingStatsHelper.exportTable(db, writer);` 必须且**只能**位于 `dumpDB` L87（导出全部标准 SQLite 表之后、`writer.endObject(); writer.flush();` 之前），使阅读统计作为独立表键写入数据库 JSON。
  - **历史踩坑已修复**：此前在 `exportSharedPreferences` L155 误留了重复的 `ReadingStatsHelper.exportTable(db, writer);`（直接触发编译报错 `找不到符号 db` 且即使传参正确也会破坏 `<shared>.json` 结构），现已删去。
- **导入（`Importer.java`）**：
  - `importDB` 先校验 `Database.getDatabase() != null`，开启事务后遍历 JSON 顶层表名 `tableName`。
  - 在 `db.delete(tableName, null, null)` 与逐行插入之前，**必须先行调用 `ReadingStatsHelper.onImportTable(db, tableName);`**（保证特殊扩展表在清表/写表前已就绪），切勿后置。
  - 保留旧版画廊页路径格式识别：`oldFormatGalleryIds` 与 `"/"` 缺失检测注释逻辑原样保留（用于导入旧格式备份后批量触发刷新）。

---

## 4. `-Werror` 编译防线与继任者【绝对禁改红线】 (Do-Not-Touch Guardrails)

### 4.1 编译器与 Lint 配置事实（直接读取根目录 `build.gradle.kts` & `app/build.gradle.kts`）
- **Java `-Werror` 铁律**：根目录 `build.gradle.kts` L27-33 对全部 `JavaCompile` 任务注入：
  - `options.compilerArgs.add("-Xlint:unchecked")`
  - `options.compilerArgs.add("-Werror")`
  - 注意 `-Xlint:deprecation` **已注释**（当前编译输出会提示使用了过时 API 但不会因 deprecation 失败；但**任何新增的 unchecked 泛型警告**或**新增编译警告**都会在 `-Werror` 下直接编译失败）。
- **App 模块构建妥协**：
  - `lint { abortOnError = false; checkReleaseBuilds = false; disable += "RestrictedApi" }`。
  - `values/strings.xml` 及多语言 `strings.xml` 中 `update_version_format`、`upload_date_format` 存在 `Multiple substitutions specified in non-positional format` 的 AAPT2 警告（属于上游遗留 XML 写法，当前允许通过，暂不强改多语言 XML 以免破坏占位行为）。
  - `testOptions.unitTests.isReturnDefaultValues = true` 必须保持开启，当前 `FavoriteSyncManagerTest`（17 个）与 `UtilityHostTest`（6 个）依赖该设定通过 JVM 单测。

### 4.2 绝对禁改清单
1. **包名与版本签名基线**：
   - `applicationId = "com.IlFforever.nclientv3.clone"`（注意 `IlFforever` 第二字母是小写 `l`、`F` 大写）、`versionCode = 450000`、`versionName = "4.5.0-Modern"`、`resValue("string", "app_name", "NClientV3 2")`、`DATABASE_VERSION = 16`。
2. **禁止在首轮真机双验收前开启混淆**：
   - `buildTypes.release` 中保持 `isMinifyEnabled = false` 与 `isShrinkResources = false`；未在真机完整验证反射/跨包调用/备份序列化的情况下开启 R8 极易造成覆盖安装或运行期崩溃。
3. **禁止更改公开字段/方法签名与特殊注解/静态入口**：
   - `Queries.db` 保持 `public static`。
   - `DatabaseHelper.onConfigure` WAL + `PRAGMA busy_timeout = 3000` 不得删减。
   - `MainActivity.bottomNavigationView`（package-private）及 `xml/settings.xml` 中带 `app:isPreferenceVisible="false"` 的 `preference_key_theme_select` 不得删除。
   - `FavoriteActivity.syncFavorites(int maxPages)`（`public`）与 `showSyncProgressDialog()`（`public`）、`FavoriteSyncManager.syncMaxPages`（`public static volatile int`，且在 `finally` 复位 `0`）不可改回 `private`。
   - `ZoomActivity` 的 `changeClosePage(boolean)` 及尾部 7 个 `public` 方法（`autoTurnNextPage`、`getZoomCurrentPage`、`getZoomTotalPages`、`hideBars`、`showBars`、`setVolumeSide`、`getVolumeSide`）不可降级可见性或改名。
   - `MainActivity.changeLanguageRunnable` 对 `ModeType.SEARCH` 调用 `LanguageFilterHelper.reloadSearch(this)` 后直接 `return` 的写法不要“顺手优化”为条件返回，除非实测核对。
4. **物理目录禁区**：
   - 工作范围限于源码工作区。发布仓、本机密钥和备份由各自维护流程管理。

---

## 5. 真机验证排障指南与 Git 分支收口状态

### 5.1 Git 分支与提交结构（`NClientV3-Modern-src`，当前分支 `migrate/source-v1`）
- `eb2e3b4`：上游 `ILFforever/NClientV3` main 基线
- `8ad520c`：`feat(tag-i18n): snapshot Layer-2 Chinese tag translation and DB v16`（DSH 汉化与 DB v16）
- `5c00f7c`：`feat(modern): migrate theme panel, Modern UI resources, and assets`（全部 Modern `res/`、`ThemePanelPreference`、`ThemeColors`、`GeneralActivity`）
- `f778ff0`：`feat(modern): port Modern utility helpers, repo URLs, and SQLite WAL config`（22 个 `utility` 类、仓库/反馈 URL、WAL `DatabaseHelper`、`VersionChecker`、`PageSwitcher`）
- `5f54833`：`feat(modern): wire Modern helpers into activities, adapters, and backup/sync`（`app/build.gradle.kts`、`AndroidManifest.xml`、各 Activity/Adapter/User/Interceptor/Queries/FavoriteSyncManager/Exporter/Importer/GeneralPreferenceFragment）
- `ed5e93f`：`docs(migration): add source migration handoff guide`（`MIGRATION_HANDOFF.md`）
- *(工作区已补充生成本文件 `MIGRATION_DEEP_CONTEXT.md`，未提交。)*

### 5.2 `keystore.properties` 签名读取逻辑与打包任务特性（直接读取 `app/build.gradle.kts` L11-34、L62-70、L129-155、L203-229）
- `app/build.gradle.kts` 检测 `rootProject.file("keystore.properties")` 是否存在（该文件已列入 `.gitignore`）；若不存在，Release 构建自动跳过 `signingConfig` 赋权，产出未签名包 `app/build/outputs/apk/post28/release/app-post28-release-unsigned.apk`。
- 接入正式签名时，需在 `keystore.properties` 提供精确的 4 个属性键：
  ```properties
  storeFile=<release.jks 绝对或相对路径>
  storePassword=<keystore 密码>
  keyAlias=<签名别名>
  keyPassword=<私钥密码>
  ```
- **`CreateRenamedApk` 文件名截断注意**：该自定义任务使用 `variant.outputs.single().versionName.get().substringBeforeLast("-")` 生成重命名 APK，因此 `"4.5.0-Modern"` 会被截断成 `NClientV3_4.5.0.apk` 输出到 `app/build/outputs/apk/post28/release/NClientV3_4.5.0.apk` 和 `app/build/outputs/apk/release/NClientV3_4.5.0.apk`。同时请留意 `local.properties` 本机环境指向 `sdk.dir=D:\\workcache\\DSH\\nclient\\android-sdk`。

### 5.3 真机排障最短路径（按故障现象定位类）
1. **覆盖安装失败（`INSTALL_FAILED_UPDATE_INCOMPATIBLE` / 包冲突）**：
   - 优先检查 `app/build.gradle.kts` 的 `applicationId`（`com.IlFforever.nclientv3.clone`）、`versionCode`（`450000`）与 `keystore.properties` 实际证书指纹是否与设备已装旧包一致。
2. **覆盖升级后启动秒退 / 主页崩**：
   - 第一优先：查 `DatabaseHelper.java`（WAL/`busy_timeout`、L46-202 的 `onUpgrade`/DB v16 列迁移）与 `Queries.java`；
   - 第二优先：查 `GeneralActivity.java` → `ThemeColors.java` → `ThemePanelPreference.java`（旧 `"Settings"` 键值兼容与布局 ID 强制转换）；
   - 第三优先：查 `MainActivity.java`（`findUsefulViews` 中的 `R.id.bottom_nav`、`setupBottomNav` 中的 `BottomNavRouter.install` 与 `GlassBar.attach`、`LiquidHostLayout`）。
3. **备份导出失败 / 旧包新包 ZIP 互导失效 / 阅读统计丢失**：
   - 按顺序排查 `SettingsActivity.java` → `Exporter.java`（重点核对 L87 `dumpDB` 内 `ReadingStatsHelper.exportTable(db, writer)` JSON 键名）→ `Importer.java`（重点核对 L92 `ReadingStatsHelper.onImportTable(db, tableName)` 及清表顺序）→ `ReadingStatsHelper.java`。
4. **阅读/收藏同步/图片/标签过滤异常**：
   - 续读弹窗与跳转：`GalleryActivity.java`（`loadGallery` / `checkBookmark` / `openZoomPage` / `ResumeHelper.java`）→ `ZoomActivity.java`（`onCreate` 调用顺序与 `onPause` `ResumeTable.insert`）。
   - 边读边下 / 自动翻页 / 音量键方向：`ZoomActivity.java` 7 个 `public` 方法与对应 `*Helper.java`。
   - 收藏增量同步：`FavoriteActivity.java` → `FavoriteSyncManager.java`（`syncMaxPages`）→ `FavoriteSyncHelper.java`。
   - 头像与网络重连：`AvatarHelper.java` + 3 个 `User` 类 → `CustomInterceptor.java`（`NetworkRecoveryHelper` `try/finally`）。
