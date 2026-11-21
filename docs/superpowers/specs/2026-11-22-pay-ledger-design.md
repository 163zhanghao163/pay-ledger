# PayLedger 设计规格：支付通知自动记账（MVP）

- 日期：2026-11-22
- 状态：已批准（用户口头批准 + 本文档为唯一权威规格）
- 仓库：本地 `E:\dev\workspace\deepseekharness\pay-ledger`（git 已初始化；暂不推送 GitHub，试用满意后由用户决定推送与 release）

## 1. 背景与目标

用户日常支付走微信支付与支付宝，底层绑定宁波银行卡。希望手机收到「付款/扣款」通知时自动生成记账条目，无需手动录入。

**三条硬性非功能要求：**

1. 安全：数据不出设备；
2. 内存占用小；
3. 后台耗电小。

**MVP 成功标准：**

- 手机上收到一笔真实的微信/支付宝付款通知后，打开 App 能看到对应记录（金额、渠道、时间）；
- 今日/本月支出汇总数字正确；
- 单条记录可删除、可补充备注；
- 真机长时间后台待机无明显耗电（系统电池统计中占比可忽略）。

## 2. 范围

**MVP 包含（in scope）：**

- 监听微信支付、支付宝的付款通知并自动记账（仅支出）；
- 记录列表（按日分组）+ 今日/本月支出汇总；
- 单条记录：删除、编辑备注；
- 通知使用权授权引导；
- debug 构建内置「最近通知预览」调试页，用于校准解析规则；
- Parser 纯 JVM 单元测试。

**明确不做（out of scope，YAGNI）：**

- 收入/收款记账、退款识别；
- 分类与统计图表；
- CSV/Excel 导出；
- 手动补记（可通过备注 MVP 后再加）；
- 云同步、多设备、账号体系；
- 银行短信监听（SMS 权限）；
- 其他支付渠道（云闪付、卡券、第三方聚合等）；
- SQLCipher 数据库加密（无网络权限 + 私有目录已满足 MVP 安全要求，加密列为二期可选项）。

## 3. 技术选型（已批准）

| 项 | 选择 | 理由 |
|---|---|---|
| 语言/UI | Kotlin 2.x + Jetpack Compose Material 3 | 官方 androidx 依赖，零第三方库 |
| 存储 | Room（SQLite） | 金额用「分」长整型，避免浮点误差 |
| 监听 | `NotificationListenerService` | 系统事件驱动被动回调，无轮询 |
| DI | 手动注入（Application 持有依赖） | 依赖克制，避免 Hilt |
| 构建 | Gradle 8.10 + AGP 8.7，版本目录 `libs.versions.toml` | 本机已有 SDK |
| SDK | compileSdk 35 / minSdk 26 / targetSdk 35 | 本机 platforms 已有 30/33/34/35 |

本机构建环境事实：Android Studio 位于 `D:\dev\ide\Android Studio`（自带 JBR 为 JDK 11，**不可用**）；SDK 位于 `D:\dev\ide\AndroidStudioSDK`（build-tools 35.0.0、platform-tools、licenses 齐全）；Gradle daemon 使用系统 Java 20（`java version "20.0.2"`，已加入 PATH）。无 gh CLI；git 身份 `zhaoyingqi / 517546148@qq.com`。

## 4. 架构与组件

```
MainActivity(Compose) ── PayLedgerViewModel ── TransactionDao(Room)
NotificationLedgerService ── NotificationParser ── TransactionDao
PayLedgerApplication：单例容器，持有 Database 与 Parser
```

- 包名 `com.zhaoyingqi.payledger`，单模块 `app/`；
- 文件按职责拆分，单文件 ≤300~500 行，函数 ≤50 行（工作区规范）；
- 组件边界：`NotificationParser` 为纯函数式规则引擎（输入渠道+标题+文本，输出可空记录草稿），不依赖 Android Context，便于单测；Service 只做「取文本→调 Parser→写库→去重」，不含解析细节。

## 5. 数据模型

```kotlin
@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,          // 金额（分），正数
    val channel: String,            // "WECHAT" | "ALIPAY"
    val counterparty: String,       // 商户/对方，解析不到为空串
    val occurredAt: Long,           // epochMillis，取通知收到时间
    val summary: String,            // 通知原文摘要（含宁波银行卡尾号等信息，便于对账）
    val note: String,               // 用户备注，默认空串
    val createdAt: Long,            // epochMillis，入库时间
)
```

Dao 接口（suspend + Flow）：

- `insert(record)`：写库；重复检查用「渠道 + amountCents + occurredAt ± 3 秒」查询命中即跳过；
- `observeGroupedByDay()`：`Flow<List<DayGroup>>`，按 `occurredAt` 自然日倒序分组，每条含日小计；
- `observeTodaySum(now)` / `observeMonthSum(now)`：`Flow<Long>` 支出汇总（分）；
- `updateNote(id, note)`、`delete(id)`。

`DayGroup(date, records, dayTotalCents)` 由 ViewModel 从 Flow 组合，Dao 不做复杂 SQL 分组。

## 6. 通知监听与解析规则（核心）

### 6.1 Service 行为

- Manifest 声明 `NotificationListenerService` + `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`；`onNotificationPosted(sbn)` 中：
  1. 包名过滤：`com.tencent.mm`（微信）、`com.eg.android.AlipayGphone`（支付宝），其余立即返回；
  2. 从 `sbn.notification.extras` 取 `EXTRA_TITLE` / `EXTRA_TEXT`（CharSequence→String，null 安全）；
  3. 交给 `NotificationParser`；返回 null → 忽略；
  4. 命中 → 去重检查 → `insert`（协程 `launch`，瞬时完成）。
- Service 内不做任何轮询、定时、前台通知；回调结束即空闲。

### 6.2 解析策略（宽匹配优先）

**微信（`com.tencent.mm`）：**

- 标题含「微信支付」→ 付款类通知；文本含「微信支付凭证 / 支付成功 / 已成功付款 / 付款成功」任一关键词即视为付款；
- 标题或文本含「收款助手 / 收款到账 / 收款成功」→ 收入，**忽略**；
- 金额提取：正则 `¥?\s*[1-9]\d*(?:[.,]\d{1,2})?`（支持 `20.00`、`¥20`、`1,234.56`、`20元`），取第一个匹配；分值 = 元×100 四舍五入；
- 商户名：文本中「付款给/收款方/商户」类短语后的片段，宽松提取，失败置空。

**支付宝（`com.eg.android.AlipayGphone`）：**

- 标题含「支付宝」且文本含「付款成功 / 支付成功 / 已扣款 / 扣款成功」任一关键词 → 付款；
- 文本含「收款 / 余额宝收益 / 花呗还款 / 转账到账」等非付款语义 → 忽略；
- 金额提取与微信同一正则；商户名同样宽松提取。

**共同规则：**

- 金额 ≤ 0 或解析不出 → 忽略；
- 宁波银行卡尾号等附加信息随 `summary` 原文保存（截断至 200 字符）；
- 关键词与正则集中定义在 `ParserRules` 常量对象中，便于样本迭代；
- 解析失败静默忽略；debug 构建输出文本日志（tag `PayLedgerParser`），release 构建不落任何通知文本日志。

### 6.3 防重复

同一通知可能被系统回调多次，且支付宝/微信偶发重发：入库前按「渠道 + amountCents + |occurredAt−已存在| ≤ 3 秒」查询，命中则跳过。代价是一次小查询，收益是数据正确性。

## 7. UI 规格（Compose Material 3）

**页面 1：授权引导（状态 A）**

- 检测 `NotificationManagerCompat.getEnabledListenerPackages(context)` 是否包含本包名；
- 未授权：居中说明 + 按钮「去开启通知使用权」跳转 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`；返回后自动重新检测并进入主列表；
- 已授权：直接进主列表。

**页面 2：主列表（状态 B）**

- 顶部汇总卡：今日支出、本月支出（元，两位小数）；
- 记录流按自然日分组倒序，组头为「MM月dd日 + 日小计」；每组渲染金额、渠道徽标（微信/支付宝文案）、商户名（空则显示通知摘要首 20 字）、备注（有则显示）、时间 HH:mm；
- 点击记录 → 底部弹层编辑备注（保存/取消）；长按 → 确认删除对话框；
- 列表查询 LIMIT 500（数据量小，MVP 不做分页）；
- 空态：提示「暂无记录，去支付一笔试试」。

**调试页（仅 debug 构建）：**

- 从主列表长按顶部汇总卡进入，展示最近 10 条原始通知（包名/标题/文本/时间），只读，帮助校准解析规则；release 构建不包含此代码路径。

## 8. 安全 / 耗电 / 内存（对照三条硬性要求）

| 要求 | 措施 |
|---|---|
| 安全 | 不声明 `INTERNET` 权限（数据物理上无法出设备）；通知文本不写入 release 日志；数据库位于 `/data/data/<pkg>/databases` 私有目录；除 `MainActivity`（必须可启动）外所有组件 `android:exported="false"`；无 WebView、无 Multidex |
| 耗电 | 无前台服务、无 WorkManager/AlarmManager/JobScheduler；Service 仅在通知回调瞬间工作，其余时间系统可自由回收进程；无任何周期任务 |
| 内存 | 零第三方依赖（仅官方 androidx）；R8 `minifyEnabled` + `shrinkResources`；APK 预计 <3MB；列表 LIMIT 500；不加载图片资源 |

## 9. 错误处理

- 授权中途被撤销：下次启动重新走引导页；Service 被系统解绑无需应用干预；
- DB 写失败：`try/catch` 记录异常日志，不抛出、不影响 Service 存活；
- 通知 extras 为 null / 文本为空：Parser 返回 null，静默忽略；
- 金额千分位、两位小数以外的格式：正则兜底失败即忽略（宁可漏记不可错记）。

## 10. 测试策略

- **Parser 单元测试（质量主力，纯 JVM）**：微信/支付宝典型付款文案命中；收款类文案忽略；千分位 `1,234.56`、`¥20`、`20元`、整数金额边界；金额≤0；空文本；无关通知（微信聊天消息等）忽略；商户名提取命中与置空；
- **去重逻辑单测**：同渠道同金额 ±3 秒命中/未命中；
- DB/UI 走真机手动验证（本机无可运行模拟器也不强制 instrumented test）。

## 11. 构建与交付流程

1. 在 `pay-ledger/` 建立 Gradle 工程（Wrapper，Gradle 8.10.x），`org.gradle.java.home` 指向系统 Java 20；
2. `gradlew :app:assembleDebug` 打 debug APK；
3. 用户真机安装试用（adb install 或直接传输安装）；`gradlew :app:test` 跑 Parser 单测；
4. 本地 git 提交（暂不推送）；
5. 试用满意后：协助创建 GitHub 仓库并推送、打 release（用户决定时机）。

## 12. 项目结构（约定）

```
pay-ledger/
  app/src/main/java/com/zhaoyingqi/payledger/
    PayLedgerApplication.kt
    notification/NotificationLedgerService.kt
    notification/NotificationParser.kt
    notification/ParserRules.kt
    data/TransactionRecord.kt
    data/TransactionDao.kt
    data/LedgerDatabase.kt
    ui/MainActivity.kt
    ui/PayLedgerViewModel.kt
    ui/screens/（授权页、主列表、debug 预览页、主题）
  app/src/test/java/.../NotificationParserTest.kt
  docs/superpowers/specs/2026-11-22-pay-ledger-design.md（本文件）
```

工作区级变更日志位于 `changelogs/pay-ledger/CHANGELOG.md`（实现落地时创建并更新索引）。
