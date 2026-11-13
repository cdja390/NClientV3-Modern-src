# NClientV3 Modern

NClientV3 的非官方 Modern 分支，在中文本地化上补毛玻璃界面、续读与阅读统计。

Unofficial NClientV3 Modern branch with a glass UI, resume, and per-gallery reading stats.

当前版本 **4.5.1-Modern**。包名 `com.IlFforever.nclientv3.clone`，应用名 **NClientV3 2**。这是独立安装包，数据不与原版或其他同名应用混用。

本仓库是 [ILFforever/NClientV3](https://github.com/ILFforever/NClientV3) 的非官方衍生分支，上游可继续追溯到 [maxwai/NClientV3](https://github.com/maxwai/NClientV3) 与 [Dar9586/NClientV2](https://github.com/Dar9586/NClientV2)。它不替换上游：源码分三层保留，便于对照。

- 上游代码：以 `ILFforever/NClientV3` 为基线，保持可追溯。
- DSH 层：中文本地化与数据库 v16。
- Modern 层：只补阅读和列表上缺的能力，以及界面现代化。

## 设计灵感来源

界面没有另找一套漫画客户端来仿。底栏和对话框沿用 Material 3。抽屉顶栏按当前主题的 Material 3 色（`colorSurfaceContainerHigh`、`colorPrimaryContainer` 等）做动态调和，文档里称为 Material You 调和色。

毛玻璃、液态玻璃和底栏的设计，主要参考了 [FlClash](https://github.com/chen08209/FlClash) 的物理凸透镜水滴美学（含 64dp 胶囊尺度）和 [SukiSU-Ultra](https://github.com/sukisu-ultra/SukiSU-Ultra) 的悬浮胶囊底栏交互。对照的是交互与外观，没有把这些项目的源码抄进本仓库。毛玻璃是为阅读加上的：底栏做成可透视的胶囊；关闭后改为不透明实心胶囊，避免栏位变透明。

中文使用是另一层缺口：原版界面和标签以英文为主。标签中文名 `name_zh` 来自 DSH 层的中文本地化（DB v16），数据源是 [EhTagTranslation](https://github.com/EhTagTranslation/DatabaseReleases) 的标签翻译库。收藏同步最初来自上游 ILFforever/NClientV3 v4.5.0 的本地与网站双向同步，本分支保留，并补了同步页数选择等能力。

## 设计初衷

这个分支不替换上游。上游代码保持可追溯，DSH 做中文本地化和 DB v16，Modern 只补阅读和列表上缺的能力。包名与应用名标明这是独立安装，数据不与原版混用。

要接上的是使用中会断掉的几步：冷启动或从收藏打开时回到第一页，历史清空后整页发黑，阅读器菜单白底白字，以及搜索时返回键和输入法抢焦点。读完没有状态，读了多久也没有数。自动翻页、边看边下、续读、阅读状态自动标记，还有按漫画统计的时长和次数，都是为了从打开到下次接着读，少靠记忆。

## 主要特性

- **毛玻璃与胶囊底栏**：可透视的悬浮胶囊底栏；关闭毛玻璃后降为实心 Material 3 胶囊，栏位不再变透明。
- **Material 3 界面**：底栏、对话框和主题色沿用 Material 3；抽屉顶栏做主题色动态调和。
- **中文标签**：标签可显示 `name_zh`，本地过滤同时匹配英文名与中文名。
- **收藏同步**：本地收藏与网站收藏双向同步，可选择同步页数或全部同步。
- **续读**：再次打开已读作品时，可询问、自动跳转或记住选择，从上次阅读页接着读。
- **自动翻页**：阅读器内可按间隔自动翻页，并可改音量键方向。
- **边看边下**：阅读过程中按设置触发后台下载，方便稍后离线接着看。
- **阅读状态自动标记**：可按翻页进度自动升为「正在阅读」或「已完成」（默认关闭）。
- **按作品阅读统计**：记录每本的阅读时长与次数，可在应用内查看。
- **搜索与空状态修复**：搜索时回车收起输入法，返回键先收键盘再退出搜索；清空历史前会确认，并显示空状态，不再整页发黑。

## 安装与签名

需要 Android 8（API 26）及以上。API 28 及以上使用 `post28` 产物；Android 8 的 API 26/27 使用带 `pre28` 的产物。

本仓库默认产出未签名的 release 包。签名由维护者在本地完成。请不要把密钥库、别名或口令写入仓库、提交记录或说明文档。若在项目根目录自行提供已被忽略的 `keystore.properties`，构建可以在本地完成签名；未提供时，构建只会生成未签名包。

这是非官方、源码私有的衍生分支。「检查更新」和「报告问题」指向本仓库。仓库为私有时，未获授权的安装包将无法在线检查更新。

## 致谢

- [@Dar9586](https://github.com/Dar9586) 的原始项目 [NClientV2](https://github.com/Dar9586/NClientV2)
- [@maxwai](https://github.com/maxwai) 的 [NClientV3](https://github.com/maxwai/NClientV3)，以及继续维护本项目的贡献者
- [@ILFforever](https://github.com/ILFforever) 的 [NClientV3](https://github.com/ILFforever/NClientV3)，本分支以此为上游基线
- [EhTagTranslation](https://github.com/EhTagTranslation/DatabaseReleases)，标签中文名数据源
- [FlClash](https://github.com/chen08209/FlClash)、[SukiSU-Ultra](https://github.com/sukisu-ultra/SukiSU-Ultra)，毛玻璃、液态玻璃与底栏的设计参照（仅对照交互与外观，未使用其源码）
- [Han1meViewer](https://github.com/misaka10032w/Han1meViewer)，早期主题与导航的界面参考（只作界面参考，未移植其业务代码）

上游 README 中列出的翻译与修复贡献者仍然适用，完整名单见历史版本说明。

## 许可证

本项目基于上游 NClientV3，遵循 **Apache License 2.0**。完整协议见 [LICENSE](LICENSE)。

```text
Copyright 2024 maxwai

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
