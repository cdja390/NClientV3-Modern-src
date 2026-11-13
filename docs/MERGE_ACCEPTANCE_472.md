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

## 逐项状态

| ID | 本次状态 | 证据/限制 |
|---|---|---|
| T01 | 通过（JVM） | P4 SourcePolicyTest，保留导入空串并忽略未知名称 |
| T02 | 通过（JVM） | P4 SourcePolicyTest，覆盖三个旧选项和 EH/EX |
| T03 | 通过（JVM） | P4 SourcePolicyTest，覆盖镜像推导与 NH 默认值 |
| T04 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T05 | 通过（JVM） | SourceMergerTest；P4 菜单使用同一交集方法，交互待验 |
| T06 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T07 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T08 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T09 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T10 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T11 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T12 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T13 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T14 | 通过（JVM） | P2 SourceMergerTest；T07 另含 EhFeedTest 空页边界 |
| T15 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T16 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T17 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T18 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T19 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T20 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T21 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T22 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T23 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T24 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T25 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
| T26 | 通过（JVM） | P3 EhSearchPagerTest/EhFeedTest；评分 UI 待 P4 |
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
| T04 | 排序：NH+EH 且排序为 POPULAR | 请求降为 RECENT_ALL_TIME |
| T05 | 排序菜单取交集：只有 NH / NH+EH | 全部 / 只有 RECENT |
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

## 10. 真机验收说明（供用户执行，不可破坏数据）（验收要求）

**前置条件：**
- 只允许覆盖安装（`adb install -r`）；
- **禁止**卸载、禁止清数据；
- 先在 App 内导出一份备份；
- 安装前在“关于”页确认当前版本。

**检查点：**

1. 升级后，来源设置是否按迁移规则得到正确结果（原本是 nh、eh、both 时各自对应什么）。截图。
2. 打开来源选择：
   - 只选 NH：与旧版相同；
   - 只选 EH：只显示 EH；
   - 两者都选：轮流显示，所有条目都有 NH/EH 徽标；
   - 迁移后的缺省：老用户未设来源且 mirror 为空时，应为默认 nh（已决定）。
   - 全部取消：本次用户确认禁止保存空选择，至少保留一个来源（经用户同意的偏差）。
3. 排序：两者都选时，菜单只有“最新”。
4. 翻页 1→2→3→2：内容连续；回翻较快（命中缓存）。
5. 搜索关键词、点击作者标签：两站都有结果；在 EH 详情页点标签会回到主列表。
6. 单站失败：在真机上很难只让一个站点失败，所以以单元测试 T08–T11 和 N04 为准。用户可以自愿短暂关闭网络，确认两站都失败时会显示错误和重试，测完立即恢复。不能测的项目记为“未测”，不要伪造结果。
7. 点击 EH 条目进入详情 → 阅读 → 返回，历史中出现该条目（来源正确）。
8. 收藏 EH 条目（本地和在线）；“下载本页全部”时两站条目分别进入各自的下载。
9. 开发者选项“不保留活动”（用户自愿，测试后恢复）：返回列表时不崩溃，列表可见。
10. 登录（《01》5.1.5 节）：全部 8 项。

**出问题时：** 记录时间（北京时间）和截图。agent 只做只读的 logcat（tag `NCLIENTLOG`）。
