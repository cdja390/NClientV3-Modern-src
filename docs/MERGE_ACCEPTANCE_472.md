# 4.7.2 来源合并验收记录

比较基线：本地 study b2ee7ed006e6b06790f1b23d1528c339f298f7b5。
接手源码：feature/4.7，6028d6347ea1b830228bf6d030bc5b67d309379b（已有 P1）。
代码实施负责人：codex:ab0b8b47。所有子任务只读。

## 已确认的行为

- 无来源配置且 mirror 为空：默认 NH。
- 请求没有对应分类/评分筛选：继续 EH 全局设置。
- 方案 B：保留 EH 独立详情/阅读器；不宣称等同上游 ZoomActivity。
- 收藏页对齐 favoriteSource；全部下载按来源分流。
- 每阶段仅本地提交；不推送。P7 升至 4.7.2，产物核验 470200。
- 本次新增确认：禁止保存空来源选择，至少选一项。
- 本次新增确认：主页菜单和设置页都有入口，NH 与 EH/EX 两项，第二项随现有 ExHentai 开关切换；切换后重置缓存。

## 本次 P1 复验（2026-11-13，Asia/Shanghai）

- 00、04、01、02 已依次完整阅读。最新且唯一 ZIP 独立解压，未覆盖源码。
- 工作区、stash 干净；无 Git 锁或并发构建。Antigravity 已通过 Concord 明确让出写入和构建权。
- 两轮只读审查没有发现确定的 P1 来源混淆/字段顺序错误；保留已有实现。
- 指定三任务构建成功；首次测试 UP-TO-DATE 后，额外执行 `gradlew.bat --offline testPost28DebugUnitTest --rerun`。
- 本次 XML：99 tests / 0 failures / 0 errors / 0 skipped；SourceKeyTest 7，LocalizationCompletenessTest 2。
- P1 包仍为 4.7.1-Modern / 470100，包名 com.IlFforever.nclientv3.clone，标签 NClientV3 2。
- release APK 18043665 字节；SHA-256 EDE8BD2EA848015D4B96F1831F72DD3D48FE6049535049ACF4375B575520D385。未执行正式签名或安装。
- T27/T28 仅纯 Java 字段流通过，没有执行 Android Parcel；T29 只核验 kind 分支，真实旧 INSPECTOR 解包待验。
- INSPECTOR extra 当前写入点为 BookmarkAdapter，读取点为 MainActivity。纯 NH 保留原 typed-list 格式，混合列表使用 kind 3。
- EhSimpleGallery 不得直接经普通 SimpleGallery Parcelable 路径传递；后续路由使用 EhIntents 或混合列表编码。
- 状态/已打开行刷新只读 NH 表并限定 NH 来源；EH 跳过。ehentai.db 的 gid-only 键没有由 P1 升级。
- 当前完整通过的新增矩阵条目：T32。T27、T28、T29、T31 有部分证据；其它条目按后续阶段逐项验证。无进度百分比。
- N01–N07、真机项以及真实账号登录均未验收；历史手机版本不作为本次检查结果。

## 本次 P2 验证（2026-11-13，Asia/Shanghai）

- 新增 MergeSource / MergePage / SourceMerger / SourceCapabilities；InspectorV3 双来源请求逐个捕获异常，任一成功（包括空结果）即可返回，全部失败重抛最后 IOException 或包装最后其它异常。
- NH 复用原请求和解析器，克隆后采用共同支持的排序并重新生成 URL；不修改保存的排序偏好。HTTP 非成功响应在合并模式中作为 NH 来源失败。
- 页数取各成功来源最大值；EH 解析结果为空时报告当前页；来源键去重及轮流插入保留尾部。每个 Inspector 的 seen 独立，clone 不复制，第一页重置；不宣称跨站内容去重或跨页稳定去重。
- 指定三任务通过，debug/release javac 实际重新编译，-Werror 未失败；本次 XML 112 tests / 0 failures / 0 errors / 0 skipped。SourceMergerTest 12，EhFeedTest 7，LocalizationCompletenessTest 2。
- 本阶段 APK 18048121 字节，SHA-256 509863157FE5D1E915A9F877EB78E7DF424A79233468A9F163B8B336294C30F3。版本仍为 4.7.1，P7 升版。
- T04、T06-T14、T32 共 11 个条目有完整 JVM 证据；T05 菜单接入待 P4。N01-N07、Android Parcel 和真机验收未完成。

## 本次 P3 验证（2026-11-13，Asia/Shanghai）

- 标签不限数量，保留双引号/避免前缀及热门占位适配；CATEGORY 映射覆盖普通十分类和显式 Private（上游 f_cats=1023），无请求分类才继承全局分类。
- Ranges 支持最低评分、页数映射；未给评分时继承全局，0 显式关闭。评分不进入 NH 查询语法。搜索页评分控件将在 P4 接入。
- Ranges 无评分时仍写旧六个 int；有评分时写 marker + 六个 int + rating。真实 Android 往返仍待验。
- EhPager：24 条查询链、每链 4 页 LRU、MAX_WALK=8；第1页刷新链；淘汰页重新请求但沿用已记游标判定下一页，与上游一致。模拟测试覆盖动态末页变化、缓存淘汰、连续超过8页。
- 最终指定三任务通过；XML 124 tests / 0 failures / 0 errors / 0 skipped。EhSearchPagerTest 12，本地化 2。APK 18051049 字节，SHA-256 C3BCC9314CF63C1E364849F7B0B6E0020CB850876F372C02BFE8DA45F4048264。
- JVM 矩阵有证据条目：T04、T06-T26、T32，共23项；真实网络、Android Parcel和真机项未验收。
- 本次用户进一步明确：方案 B 保留独立 EH 阅读器，同时补齐垂直/条漫、边缘点击、音量键、自动翻页、自动阅读状态等阅读交互和设置；不得把这些差异作为已接受保留项。后续逐项验收。

## 本次 P4 验证（2026-11-13，Asia/Shanghai）

- 主页菜单和设置页共用来源选择，至少选一项才能保存。导入的空配置显示来源选择入口，可重新保存。导入同时带 EH 和 EX 时，按现有开关保留其中一项；明确的单个 EH 或 EX 来源与开关同步。独立 EH 页仍按账号能否访问 EX 决定实际站点。
- 选择变更重载第一页，失效旧游标缓存。MainActivity 在主线程核对请求身份后一次处理对应回调，防止旧请求更新新列表或刷新指示器。程序更新页码显示不触发新请求。
- 排序菜单采用来源能力交集，保留仅 NH 时的排序；双来源显示每行徽标。搜索页可指定最低评分。
- 指定三任务成功。XML 130 tests / 0 failures / 0 errors / 0 skipped，SourcePolicyTest 5，LocalizationCompletenessTest 3。release APK 18068217 字节，SHA-256 1BDC6BE7CC746B65D1A228058945C8F21201803309AE7E787B610EB99556FD0F。P4 产物版本仍为 4.7.1-Modern。
- JVM 矩阵 T01-T26、T32 共 27 项有通过证据。选框、排序菜单、徽标及切换时序仍待设备验证。

## 本次 P5 验证（2026-11-13，Asia/Shanghai）

- 合并列表使用保留来源、长 gid、token 的详情 Intent。详情标签映射到公共标签请求，独立 EH 页面继续走自己的搜索。合成标签 ID 不进入 NH 标签表。
- ehentai.db v2 将历史、收藏和下载改为 (ex, gid) 主键。下载查询还核对 token。旧目录按升级时的记录或导入的旧备份保留归属；新目录区分 EH 和 EX。离线详情只有在确认该来源已有下载且本地首页存在时才显示。
- EH / EX 阅读状态独立存储，使用已有状态名称和颜色。主列表返回时依来源键重绘打开的条目；EH 请求缓存还区分 token。方案 B 采用独立详情及阅读器界面，属于用户已同意的界面差异，阅读交互另验。
- 指定三任务成功。XML 132 tests / 0 failures / 0 errors / 0 skipped，本地化 3/3。release APK 18073945 字节，SHA-256 7B7D284D808EA4949476BB86EDEFB78007A113F2B02EC6DBFB95FAE61E55653F。
- 从旧版与当前 Java 源码提取建表及升级 SQL，在内存 SQLite 以 64 位 gid 执行，确认历史进度、收藏和下载字段保留，同 gid 的 EH/EX 行及状态可共存，旧下载目录归属只匹配原来源。Android 升级、恢复、列表着色及线下回退仍待真机确认。

## 本次 P6 验证（2026-11-13，Asia/Shanghai）

- 公共历史按两站真实时间合并、搜索和清除。EH 详情和阅读的访问记录与计数分开，清除历史保留续读页、阅读统计、收藏和下载。
- 收藏页的本地视图合并两站，保留 NH 同步。在线视图按已选来源、登录状态、来源顺序选定单站，切换后重置请求和页码。EH/EX 支持站点收藏夹和搜索。批量下载使用确认时的当前结果，原 EH/EX token 随条目进入 EH 队列。
- EH 阅读器使用共享的水平、垂直和条漫偏好、默认缩放、边缘点击、音量键方向、自动翻页、续读和自动状态。隐藏工具栏时页码栏与进度条仍显示。新开阅读器在统计开启时计一次，后台时间不计，前台时间按单调时钟累计到毫秒。设置列出 NH、EH、EX 统计，清除统计不清除历史和阅读进度。
- 主列表保存最近接受的页面及来源签名，混合列表仍经 kind 3 还原。无限滚动重建后保留最近一批结果。Android Parcelable 和实际重建尚待验收。
- 指定三任务成功。XML 140 tests / 0 failures / 0 errors / 0 skipped，本地化 3/3。APK 18117501 字节，SHA-256 3F7AFDD55834950DBF5D350DDA5BFE0E61A811C28D17294F75E94809D16E82C1。T01-T26、T30、T32 共 28 个 JVM 条目通过。
- 从 Java 源码提取 SQL，在内存 SQLite 验证全新 v3、v1 到 v3、v2 到 v3，64 位阅读时间换算、同 gid 并存及清历史保留续读与统计。Android 文件升级和真实使用待验。

## 本次 P7 构建（2026-11-13，Asia/Shanghai）

- 使用 Zulu JDK 21 执行 `gradlew.bat --offline assemblePost28Debug testPost28DebugUnitTest assemblePost28Release`，三项成功。XML 140 tests / 0 failures / 0 errors / 0 skipped，LocalizationCompletenessTest 3/3。Debug 与 Release 的 Java 编译均通过 -Werror。
- 最终产物 `D:\workcache\NClientV3-Modern-src\app\build\outputs\apk\post28\release\NClientV3_4.7.2.apk`，18118233 字节，SHA-256 `FA85D897B2221BFAFFE93F5D0F47F0E20068ED5816215122E81082D6C76CB37A`。与 Gradle 原始 Release APK 字节相同。
- `aapt2 dump badging`：`com.IlFforever.nclientv3.clone`、`470200`、`4.7.2-Modern`、`NClientV3 2`。`apksigner verify` 报缺失签名清单，确认未签名。
- DatabaseHelper、主库 Database、依赖、minify 和既有 NH User-Agent 均与接手时一致。资源编译仍报告原有的 update_version_format 和 upload_date_format 位置参数提示。
- 28 个 JVM 矩阵条目通过。T27–T29、T31 有部分证据，N01–N07 及真机功能验收待完成。华为当前版本仅通过只读 ADB 核对，见第 10 节。

## 热门混排追加验证（2026-11-13，Asia/Shanghai）

- 首页无关键词、标签、语言或范围筛选时，启用 EH/EX 可切换「最近」和「热门（日榜）」。双选热门时第一页交替显示 NH 日榜和 EH/EX 热门榜，后续页读取 NH 日榜。仅 EH/EX 热门榜共一页，并应用 EH 全局分类和最低评分。仅 NH 恢复独立保存的排序。
- 指定三项离线任务全部成功。测试 XML 146 tests / 0 failures / 0 errors / 0 skipped，LocalizationCompletenessTest 3/3。新增覆盖 NH 传输 URL、旧书签、筛选边界、两站顺序、EH/EX 地址和单页与分类评分行为。
- Release `D:\workcache\NClientV3-Modern-src\app\build\outputs\apk\post28\release\NClientV3_4.7.2.apk`，18119993 字节，SHA-256 `8F11901AD7EC8B127A4E0E33791EC95AA5A38AE52E290261DCE36A45DAB678B7`。核验包名 `com.IlFforever.nclientv3.clone`、`470200`、`4.7.2-Modern`，未签名。
- Debug `D:\workcache\NClientV3-Modern-src\app\build\outputs\apk\post28\debug\NClientV3_4.7.2-Modern_debug.apk`，24096491 字节，SHA-256 `5041C77E220FFD3C01FD89FB52086D8C734A51A389B24ED26523C3EB938FB911`。核验包名 `com.IlFforever.nclientv3.clone.debug`、`470200` 和既有 Debug 证书。覆盖更新和真机结果见 10.4。

## 正式版名称复验（2026-11-14，Asia/Shanghai）

- 经用户确认，正式版应用标签使用 `NClientV3 Modern`。包名为 `com.IlFforever.nclientv3.clone`，版本为 `4.7.2-Modern` / `470200`。Debug 标签为 `NClientV3 Debug`。
- 指定三个离线任务成功，随后强制重跑 `testPost28DebugUnitTest`。26 份新 XML 共 146 项，0 失败、0 错误、0 跳过，本地化 3/3。Debug APK 与 10.4、10.5 已测 APK 的 SHA-256 一致。
- 改名后未签名 Release 为 18120001 字节，SHA-256 `DC126157B2B389315BAF7981DDE5D80E63EE7F3ED36E6C086436D25507A1B7BC`。与改名前的热门正式包对比，1276 个 APK 内容项中仅资源表和构建的版本控制元数据有变化。用户签名后核对同证书与内容，再验收覆盖升级。

## 逐项状态

| ID | 本次状态 | 证据/限制 |
|---|---|---|
| T01 | 通过（JVM） | P4 SourcePolicyTest，保留导入空串并忽略未知名称 |
| T02 | 通过（JVM） | P4 SourcePolicyTest，覆盖三个旧选项和 EH/EX |
| T03 | 通过（JVM） | P4 SourcePolicyTest，覆盖镜像推导与 NH 默认值 |
| T04 | 通过（JVM） | SourceMergerTest 覆盖筛选时回落、双站热门及切回 NH 的排序能力 |
| T05 | 通过（JVM） | SourceMergerTest 覆盖主页及搜索的可用选项，菜单交互待验 |
| T06 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T07 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T08 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T09 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T10 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T11 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T12 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T13 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T14 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T15 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T16 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T17 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T18 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T19 | 通过（JVM） | P3 EhSearchPagerTest；P4 评分 UI 已接入、待实测 |
| T20 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T21 | 通过（JVM） | P3 EhSearchPagerTest |
| T22 | 通过（JVM） | P3 EhSearchPagerTest |
| T23 | 通过（JVM） | P3 EhSearchPagerTest |
| T24 | 通过（JVM） | P3 EhSearchPagerTest |
| T25 | 通过（JVM） | P3 EhSearchPagerTest |
| T26 | 通过（JVM） | P3 EhSearchPagerTest / EhFeedTest |
| T27 | 部分验证 | SourceKeyTest 字段流；Android Parcel 待验 |
| T28 | 部分验证 | SourceKeyTest 混合字段流；Android Parcel 待验 |
| T29 | 部分验证 | 保留旧 kind 读取分支；真实解包待验 |
| T30 | 通过（JVM） | GalleryDownloadsTest 同 gid 的 NH、EH、EX 与原 token；文件下载待验 |
| T31 | 部分验证 | 来源键单测、SQLite 同 gid 异来源状态、回调检查；Android 着色待验 |
| T32 | 通过 | LocalizationCompletenessTest 3/3 |
| N01 | 待验证 | 未做网络/设备验收 |
| N02 | 待验证 | 未做网络/设备验收 |
| N03 | 待验证 | 未做网络/设备验收 |
| N04 | 待验证 | 未做网络/设备验收 |
| N05 | 待验证 | 未做网络/设备验收 |
| N06 | 待验证 | 未做网络/设备验收 |
| N07 | 待验证 | 未做网络/设备验收 |

以下保留完整原验收要求，执行结果以上方逐项状态为准。

## 9. 验收矩阵（自动测试与手工测试）

### 9.1 JVM 单元测试（新增）（验收要求）

| ID | 用例 | 预期 |
|---|---|---|
| T01 | `enabled_sources` 解析：`NHENTAI,EHENTAI`、未知名称、空串 | 与上游 `loadEnabledSources` 一致 |
| T02 | 迁移：`gallery_sources` 为 nh、eh（含 ex 开关）、both | 得到对应的集合 |
| T03 | 无配置时按 mirror 推导，分别测试含 exhentai、含 e-hentai、其它非空、空 | 非空 mirror 与上游一致；**空 mirror 得到默认 NH**（经用户同意的偏差，不是上游空集合） |
| T04 | 排序：NH+EH，主页日榜、周榜和带筛选的日榜 | 未筛选主页允许日榜；周榜及带筛选的日榜回落到最近 |
| T05 | 排序菜单：仅 NH、仅 EH/EX、NH+EH/EX | 仅 NH 提供全部；含 EH/EX 的无筛选主页提供最近和热门（日榜），搜索保留最近 |
| T06 | 轮流插入长度 3+5、0+5、5+0 | 顺序正确，保留尾部 |
| T07 | pageCount 取最大值；EH 解析为 0 时 pageCount 等于当前页 | 一致 |
| T08 | NH 失败、EH 成功 | 显示 EH，不抛异常 |
| T09 | NH 成功、EH 失败 | 显示 NH |
| T10 | 两者都失败（IOException 和 RuntimeException） | 抛出最后一个异常（IOException 包装） |
| T11 | NH 抛出 InvalidResponseException，EH 成功 | 显示 EH |
| T12 | 同一来源同 ID 重复出现 | 去掉重复 |
| T13 | **NH id=123 与 EH gid=123 同时出现** | 两者都保留（不同来源键） |
| T14 | 第 1 页清空已见集合；克隆后的行为 | 与上游实际行为一致 |
| T15 | 查询：自由文本加 15 个标签 | 不截断 |
| T16 | 标签名含双引号 | 与上游一致（不删除） |
| T17 | 避免的标签 | 带 `-` 前缀 |
| T18 | CATEGORY 标签映射为分类位 | 一致 |
| T19 | Ranges 映射到页数和评分 | 请求带有 Ranges 时与上游映射一致；**请求未带分类或评分筛选 / 默认 Ranges 时，继续使用 EH 全局分类和评分设置**（经用户同意的偏差；不再写成“一律不加参数”） |
| T20 | `-nclientv3` 占位串 | 被过滤（我方适配） |
| T21 | 分页：第 1、2、3 页的游标链 | 请求序列正确 |
| T22 | 重复请求同一页 | 命中缓存，不再发请求 |
| T23 | 缓存超过 4 页后回翻 | 最早的被淘汰，重新请求 |
| T24 | 查询链超过 24 条 | 最旧的链被淘汰 |
| T25 | 跳页超出 MAX_WALK | 返回空结果，与上游一致 |
| T26 | 游标为空的末页 | hasNext=false |
| T27 | `SourceKey` 的 Parcel 往返（EH 含 token 和 ex 标志） | 相等 |
| T28 | 混合列表的 Parcel 往返（同数字 ID、不同来源） | 两者都在，来源正确 |
| T29 | 旧格式的 INSPECTOR Parcel | 不崩溃 |
| T30 | “全部下载”分流：NH 与 EH 条目 | 分别进入各自的下载器（用户已批准的对上游缺口的修正） |
| T31 | 状态颜色和已打开记录按来源键处理 | 不串色 |
| T32 | 本地化：新增字符串三种语言都存在，values/ 中无 CJK | `LocalizationCompletenessTest` 通过 |

> 注意：`EhSimpleGallery` 这类依赖 `Uri` 的类无法在 JVM 单测中构造【原交接已验证：当初就因此避开了】。设计时应把纯逻辑放进不依赖 Android 的类中。T27–T29 涉及 Parcel / Uri / Parcelable：普通 JVM 上的可测试能力必须**先核验**，不能编造已经跑过。旧 Parcel 兼容（T29）须基于真实使用评估。
>
> T01–T32 全部保留。下方证据记录区分纯 Java 逻辑和 Android / 真机执行。

### 9.2 网络与鉴权的组合（单测加手工）

| ID | 场景 | 预期 |
|---|---|---|
| N01 | nh 返回 401，有 refresh token | 刷新后重试一次，列表正常 |
| N02 | nh 返回 401，refresh 也返回 401 | 会话过期、登出；合并模式下 EH 仍能显示 |
| N03 | nh 返回 403（CF） | **不登出**；走 CF 流程（`cf_clearance` 出现后关闭）；EH 仍能显示 |
| N04 | EH 返回 509 或限流 | NH 仍能显示，日志有记录 |
| N05 | 断网 | 两者都失败，显示错误和重试 |
| N06 | 只启用 EH 时 EH 失败 | 显示错误（与上游单源行为一致） |
| N07 | 切换 exhentai 开关后 | 来源和链缓存重置 |

### 9.3 真机手工验收（第 10 节）

## 10. 华为真机验收

华为 GLA-AL00（API 31）保留 `4.5.0-Modern` / `450000`，并存 Debug 的热门排序验收见 10.4。正式包的同证书覆盖升级、数据迁移和升级后验收仍待执行。

### 10.1 升级准备

1. 在华为上确认目标包为 `com.IlFforever.nclientv3.clone`、当前版本及已有本地数据。先从设置导出备份。
2. 由用户使用与旧包相同的证书正式签名。以 `aapt2 dump badging` 和 `apksigner verify` 检查签名后 APK。
3. 获得用户明确同意后，只对华为执行覆盖安装（`adb -s <华为序列号> install -r <签名后的 APK>`）。签名不符或安装失败时保留旧应用和全部数据。
4. 确认设置显示 `4.7.2-Modern`，旧收藏、下载、历史、续读和统计仍可读取。

### 10.2 功能检查

| 项目 | 操作及预期 |
|---|---|
| D01 来源迁移 | 记录升级前配置。旧 nh、eh、both 各迁移到对应选择。无配置且 mirror 为空时为 NH。华为上不存在的升级起点留待另测。 |
| D02 来源选择 | 分别从主页和设置打开。只选 NH、只选 EH、两者都选；双选轮流显示并给每行显示来源。全部取消时无法保存。EX 开关改变后第二项与结果同步并重载第一页。 |
| D03 排序 | 无关键词、标签、语言或范围筛选的主页，分别选择 NH+EH、NH+EX 和仅 EH/EX。选热门（日榜），核对两站各自榜单顺序和来源标签。热门的 EH/EX 只出现在第一页，单选 EH/EX 时共一页。切回最近从第一页加载；切回仅 NH 恢复原排序。关键词、标签和范围搜索在含 EH/EX 时只提供最近。 |
| D04 游标 | 页码 1→2→3→2，结果连续，回翻命中缓存。 |
| D05 搜索和路由 | 搜索关键词，带分类、页数和评分。分别点击 NH 与 EH 作者标签。从合并 EH 详情进入公共标签列表，独立 EH 详情进入 EH 搜索。 |
| D06 单源错误 | 对照全部 N01–N07。断网检查错误和重试后恢复网络。无法取得单源失败条件的条目保留未测。 |
| D07 历史和离线 | 打开同 gid 的不同来源条目，确认历史按访问时间排序，状态颜色不串。断网打开已下载 EH 条目，详情和阅读找到对应来源的文件。清历史与恢复备份用独立测试数据，核对续读和统计。 |
| D08 收藏和批量下载 | 本地收藏显示两站。在线收藏切换来源、登录态和 EH 收藏夹后从第 1 页加载，搜索和随机入口遵循所选站点能力。检查主页及收藏的本页下载各进正确队列，使用已选测试条目。 |
| D09 系统重建 | 用户可临时开启“不保留活动”，从混合列表进入详情再返回。列表恢复且打开原来源和 token，测试后恢复开发者选项。T27–T29 同时核对 Android Parcel 和旧 INSPECTOR 解包；JVM 结果仅作辅助。 |
| D10 布局和导航 | 保留独立 EH 详情和阅读器（方案 B 的界面差异）。检查返回键、底栏、AMOLED 纯黑、横竖屏和系统手势导航。隐藏工具栏、自动翻页时页码与进度条都可见。 |
| D11 阅读交互 | 水平、垂直、条漫互切保留页数，测试缩放和快速滑动。左右边缘或音量键按方向切页，关闭相应设置后恢复普通点击或系统音量。自动翻页始终向后，末页、触摸、退到后台时停止。 |
| D12 阅读统计和状态 | 关闭统计时没有新增次数或时长。开启后新进一次计一次，后台及重建不重复计数。跳到最后一页与恢复到最后一页分别验证；保护手动状态。清统计保留历史、续读、收藏、下载和状态。 |

### 10.3 登录

保留《01》5.1.5 的八项。真实账号操作由用户完成。

1. 原生登录成功后关闭登录页，侧栏显示用户名。
2. Cloudflare 页面取得 `cf_clearance` 后自动关闭。
3. 网页回退能进入登录页，登录后回到 App。
4. 重启 App 后登录仍有效。
5. access token 过期后自动续期。
6. refresh token 返回 401 后正确登出。
7. 遇到 403 时保留会话。
8. 手动登出清理会话凭据。

出现问题时记录北京时间、动作和结果。开发侧读取应用包版本和经过过滤的 `NCLIENTLOG`。

### 10.4 热门排序实测（2026-11-13 至 2026-11-14，Asia/Shanghai）

- 用户同意后，于 11 月 13 日 23:43 用 ADB 覆盖更新华为 GLA-AL00 上的 Debug，返回 `Success`。更新前后 Debug 的签名证书、UID 和首次安装时间一致，设备上的 `base.apk` SHA-256 与 4.7.2 热门构建一致。原 4.5.0 的版本、UID、首次安装及更新时间均一致。
- D03 双站热门通过。选择 NH+EH，排序框出现「最近」和「热门（日榜）」。第 1 页前三行的标签为 NH、EH 交替排列，页码为 1 / 20。第 2 页可见六张卡片均为 NH，日志记录第 2 页的 `sort=popular-today` 请求完成。
- D03 切换来源和排序通过。仅 EH 时热门结果可见，单页列表隐藏翻页栏。仅 NH 时恢复原先的「最近」，菜单保留五种排序。双站热门第 2 页切换「最近」后回到第 1 页，测试结束时恢复到双站热门第 1 页。
- D03 关键词搜索通过。从搜索入口提交测试词，结果页显示 NH 和 EH，排序框仅有「最近」。标签和范围筛选的现场操作仍待验。
- D03 的 EX 联网项目待验。现场 EH 菜单显示登录入口，ExHentai 开关待有访问权限的账号登录后可用。正式包迁移和其余真机项目按原矩阵继续验收。

### 10.5 小米 13 热门排序实测（2026-11-14，Asia/Shanghai）

- 用户同意后，于 00:07 在小米 13（2211133C，API 36）并存安装 `4.7.2-Modern-debug` / `470200`。ADB 返回 `Success`，已装 APK SHA-256 与 10.4 使用的热门构建一致。正式版 `4.7.0-Modern` / `470000` 的应用 ID、更新时间和 APK SHA-256 与安装前一致。
- D03 双站热门通过。排序菜单有「最近」和「热门（日榜）」。热门第 1 页为 1 / 20，前三行标签 NH、EH 交替；第 2 页为 2 / 20，可见六张卡片均标 NH。日志确认两页的 `sort=popular-today` 请求完成。
- D03 单站与排序切换通过。从热门第 2 页切换「最近」，双站重新从第 1 页加载。仅 EH 且选中「热门（日榜）」时可见六张卡片，翻页栏隐藏。恢复 NH+EH 后保留热门选择。
- D03 关键词搜索通过。测试词返回两站结果，排序菜单只显示「最近」。返回后保留双站热门设置。
- 首次启动时观察到 HTTP 403、连接超时及验证页「网页无法打开」。重新进入 Debug 后普通浏览和上述热门请求成功。EX、标签和范围筛选的现场操作，以及正式版覆盖升级和数据迁移待验。
