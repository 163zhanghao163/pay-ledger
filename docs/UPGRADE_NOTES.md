# 升级记录（v09）

> 每步单独提交；校验均为 `testDebugUnitTest` + `lintDebug` 全绿后提交。
> 真机相关验证统一留待装包实测轮（见各变更 tasks.md 标注）。

## 2.1 Gradle wrapper 8.14.8 + AGP 8.13.2

| 项 | 旧 | 新 |
| --- | --- | --- |
| Gradle | 8.10.2（腾讯镜像） | 8.14.8（官方源） |
| AGP | 8.7.3 | 8.13.2 |

遇到的问题与处理：

1. **AGP 必须先升 wrapper**：AGP 8.13.2 要求 Gradle ≥ 8.13，用旧 Gradle 8.10.2 跑
   `wrapper` 任务会在应用插件阶段就报
   `Unable to determine constructor argument #1: missing parameter of type InternalProblems`
   （新 AGP 配旧 Gradle 的典型症状）。由于 catalog 里 AGP 已先行改版，wrapper 任务
   无法先评估构建，改为手动改 `gradle-wrapper.properties` 的 `distributionUrl` 指向
   新发行版，用旧 wrapper jar（纯下载器，与发行版版本无关）拉起新 Gradle 后再跑
   `./gradlew wrapper --gradle-version 8.14.8` 规范化更新 jar 与脚本。
2. **腾讯镜像未收录 8.14.8**：`mirrors.cloud.tencent.com` 对不存在的文件返回 200 + 非
   zip 内容（`.sha256` 也取不到），wrapper 解包报 `zip END header not found`。改回官方
   `services.gradle.org`（直连可达）。
3. **wrapper 任务自改运行中脚本**：`:wrapper` 会重写 `gradlew.bat`；Windows 的 cmd 按
   字节偏移逐步读批处理，文件被替换后从错误偏移继续解析，报 cmd 错误"此时不应有
   the。"且退出码 255（任务本身已 BUILD SUCCESSFUL）。一次性伪故障，重跑即可。
4. **旧发行版 daemon 占用 lint 缓存**：`lintAnalyzeDebug` 报
   `lint-cache\...\RuntimeIssueRegistry-xxx.jar 另一个程序正在使用此文件`。残留的
   8.10.2 daemon 握着文件，杀掉 Gradle/Kotlin daemon、清 `app/build/intermediates/lint-cache`
   后通过。

## 2.2 Kotlin 2.2.21 + KSP 2.2.21-2.0.5

| 项 | 旧 | 新 |
| --- | --- | --- |
| Kotlin | 2.0.21 | 2.2.21 |
| KSP | 2.0.21-1.0.28 | 2.2.21-2.0.5 |
| Compose 编译插件 | 随 Kotlin | 随 Kotlin（2.x 无独立版本） |

遇到的问题与处理：

- **KSP2 与 Room 2.6.1 不兼容**：`kspDebugKotlin` 报
  `unexpected jvm signature V`（Room 2.6 处理器未适配 KSP2 的已知问题）。
  过渡方案 `ksp.useKSP2=false` 走 KSP1 兼容模式（KSP 2.2.x 仍保留），待 2.4 升
  Room 2.8.5 后移除开关回到 KSP2 默认。

## 2.3 Compose BOM 2025.12.01 与 androidx 库

| 项 | 旧 | 新 |
| --- | --- | --- |
| Compose BOM | 2024.12.01 | 2025.12.01 |
| core-ktx | 1.13.1 | 1.18.0 |
| activity-compose | 1.9.3 | 1.12.0 |
| lifecycle | 2.8.7 | 2.10.0 |
| Robolectric | 4.14.1 | 4.18.1 |
| coroutines-test | 1.8.1 | 1.10.2 |
| compileSdk | 35 | 36（提前至此，原因见下） |

遇到的问题与处理：

1. **版本选代必须看 AAR 元数据**：原计划 core-ktx 1.19.1 / activity-compose 1.12.4 /
   lifecycle 2.11.1 属"AGP 9.1.0+ 且 compileSdk 37"一代（`checkDebugAarMetadata`
   报 13 条要求），与本项目 AGP 8.13.2（上限推荐 36）冲突。下载候选版本 AAR 读
   `META-INF/com/android/build/gradle/aar-metadata.properties` 实测：
   - core-ktx 1.18.0 → minCompileSdk=36、minAGP=8.9.1
   - activity-compose 1.12.0 → minCompileSdk=36、minAGP=8.9.1（1.12.4 要 37）
   - lifecycle 2.10.0 → minCompileSdk=35、minAGP=8.6.0（2.11.0 要 37）
   各取 SDK 36 一代的最高可用版本。
2. **core-ktx 1.18.0 强制 compileSdk ≥ 36**：compileSdk 35→36 提前到本步完成，
   2.5 仅剩 targetSdk。
3. **material-icons-core 需显式依赖**：BOM 2025.x 起 material3 不再传递依赖
   `androidx.compose.material:material-icons-core`，全项目 `Icons.Filled.*` 编译失败
   （15 个图标 Unresolved）。核对用到的 15 个图标均在 core 集合内后，显式加入
   `implementation`（版本由 BOM 管理，无需引入更大的 icons-extended）。

## 2.4 Room 2.8.5

| 项 | 旧 | 新 |
| --- | --- | --- |
| Room | 2.6.1 | 2.8.5 |
| KSP 模式 | KSP1 过渡开关 | KSP2 默认（开关移除） |

遇到的问题与处理：

1. **KSP2 兼容恢复**：Room 2.8.5 处理器适配 KSP2，2.2 加的 `ksp.useKSP2=false`
   从 `gradle.properties` 移除。
2. **迁移测试库名必须传绝对路径**：Room 2.8 起 driver 化，
   `SupportSQLiteMigrationTestHelper` 内部用
   `context.getDatabasePath(name).absolutePath` 打开连接，而
   `SupportSQLiteDriver.open()` 校验该路径与 `openHelper.getDatabaseName()`
   （= 传入的裸名）相等——裸名必然失败，报
   `This driver is configured to open a database named 'migration-test.db' but
   'C:\...\databases\migration-test.db' was requested`。
   处理：`MigrationTest` 的库名改为
   `getDatabasePath("migration-test.db").absolutePath`（反编译
   room-testing-android 2.8.5 / sqlite-framework-android 2.6.2 字节码定位）。
3. **schema 无 diff 确认**：`app/schemas/1.json、2.json、3.json` 与 2.6.1 生成物
   完全一致（git 零 diff），升级不触发 schema 变更。

## 2.5 targetSdk 36

| 项 | 旧 | 新 |
| --- | --- | --- |
| targetSdk | 35 | 36 |
| 测试 JVM | JDK 20 | JDK 21（仅 Test 任务） |

遇到的问题与处理：

1. **Robolectric SDK 36 沙箱要求 Java 21**：升级 targetSdk 后 Robolectric 沙箱跟随，
   报 `Failed to create a Robolectric sandbox: Android SDK 36 requires Java 21
   (have Java 20)`。本机最高 JDK 20（`D:\dev\config\java\jdk-20`），不直接安装系统级
   JDK，改为：
   - `settings.gradle.kts` 启用 `org.gradle.toolchains.foojay-resolver-convention`
     （自动供给，JDK 装到 `~/.gradle/jdks`，不改系统 PATH）；
   - `app/build.gradle.kts` 中 `tasks.withType<Test>` 单独指定 JDK 21 启动器，
     编译链保持 JDK 20 / 字节码目标 17 不变。
2. `plugins` 块在 settings 文件中必须位于 `pluginManagement` 之后
   （放最前会报 `Unexpected plugins block found`）。
3. 通知权限、无障碍、通知监听、预算通知在新 targetSdk 下的行为回归需真机验证
   （留待装包实测轮）。
