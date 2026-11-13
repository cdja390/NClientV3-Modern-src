# 从源码构建

本项目使用 Gradle Wrapper 和 Android SDK Platform 36。已验证的构建环境是 JDK 21，APK 检查使用 Build Tools 36.0.0。配置 `JAVA_HOME` 与 Android SDK 路径后，从仓库根目录运行。

```powershell
.\gradlew.bat assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

```bash
./gradlew assemblePost28Debug testPost28DebugUnitTest assemblePost28Release
```

Gradle 首次构建需要下载 Wrapper 和构建依赖。缓存齐全后，可加 `--offline` 使用本地缓存。Debug 单测报告在 `app/build/reports/tests/testPost28DebugUnitTest/`，XML 在 `app/build/test-results/testPost28DebugUnitTest/`。

## 产物

| Gradle 变体 | 应用 | 最低 Android | 输出目录 |
| --- | --- | --- | --- |
| `post28Debug` | NClientV3 Debug | API 28 | `app/build/outputs/apk/post28/debug/` |
| `post28Release` | NClientV3 Modern | API 28 | `app/build/outputs/apk/post28/release/` |
| `pre28Release` | NClientV3 Modern | API 26 | `app/build/outputs/apk/pre28/release/` |

Debug 包名带 `.debug`，可与正式版并存。未提供 `keystore.properties` 时，Release 默认未签名。维护者在本机签名，并用 `apksigner verify -v --print-certs` 与 `aapt2 dump badging` 校验证书、包名、版本和标签。

4.7.2 发布包采用 `post28Release`。其版本、SHA-256 和升级说明见发行说明。[4.7.2 发行说明](releases/v4.7.2-modern.md)
