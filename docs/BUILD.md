# 从源码构建 · Build from source

安装 **JDK 21**、**Android SDK Platform 36** 和 **Build Tools 36.0.0**。让 `JAVA_HOME` 指向 JDK，`ANDROID_HOME` 指向 SDK。也可在本地 `local.properties` 中用 `sdk.dir` 指定 SDK 路径。项目使用自带的 Gradle Wrapper。

Install JDK 21, Android SDK Platform 36, and Build Tools 36.0.0. Set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the SDK, or configure `sdk.dir` in your local `local.properties`.

## 构建与单测 · Build and unit tests

从仓库根目录运行。Windows PowerShell 使用下列命令。

```powershell
.\gradlew.bat assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

Run from the repository root. On Linux and macOS, use the shell wrapper.

```bash
./gradlew assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

首次构建需要联网下载 Gradle 和项目依赖。缓存齐全后，可以使用 `--offline`。单测范围包括英、简中、繁中资源完整性校验。 CI 执行相同的三项任务，并上传单测报告和 `lintPost28Debug` 报告。当前 lint 配置将发现的问题收录到报告。

The first build downloads Gradle and project dependencies. Once cached, you can build with `--offline`. The unit tests include resource checks for English, Simplified Chinese, and Traditional Chinese. CI runs the same three tasks and uploads the unit test results and `lintPost28Debug` findings.

| 报告 / Report | 路径 / Path |
| --- | --- |
| 单测 HTML / Unit test HTML | `app/build/reports/tests/testPost28DebugUnitTest/index.html` |
| 单测 XML / Unit test XML | `app/build/test-results/testPost28DebugUnitTest/` |
| Android lint | `app/build/reports/lint-results-post28Debug.html` |

## 产物 · Outputs

| 变体 / Variant | 应用名称 / App name | 最低 Android / Min Android | 输出目录 / Output directory |
| --- | --- | --- | --- |
| `post28Debug` | NClientV3 Debug | 9 (API 28) | `app/build/outputs/apk/post28/debug/` |
| `post28Release` | NClientV3 Modern | 9 (API 28) | `app/build/outputs/apk/post28/release/` |
| `pre28Release` | NClientV3 Modern | 8 (API 26) | `app/build/outputs/apk/pre28/release/` |

如需构建 API 26／27 版本，运行 `assemblePre28Release`。Debug 包名带 `.debug`，可与正式版并存。干净检出的 Release 默认未签名。维护者在本机签名，并用 `apksigner verify -v --print-certs` 和 `aapt2 dump badging` 校验。

Use `assemblePre28Release` to build for API 26/27. The Debug package ends in `.debug` and installs alongside the stable app. A clean checkout produces an unsigned release. The maintainer signs it locally and checks it with `apksigner verify -v --print-certs` and `aapt2 dump badging`.

4.7.2 正式包为 `post28Release`。The 4.7.2 stable APK uses `post28Release`. [发行说明、校验值及升级步骤](releases/v4.7.2-modern.md)

## 维护任务 · Maintenance workflows

`Dependency submission` 从 `main` 手动运行，更新 GitHub 依赖图。`Prepare tag data update` 手动运行后上传候选文件。维护者审查 `data/` 的差异后，可通过 Pull Request 更新。

Run `Dependency submission` manually from `main` to update GitHub's dependency graph. `Prepare tag data update` uploads the generated files for review. After reviewing the changes in `data/`, a maintainer can submit them in a pull request.
