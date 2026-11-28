# PayLedger 迭代优化方案（基线 v0.5.7）

> 日期：2026-11-26（2026-11-28 更新）　基线：v0.5.7（commit `1510a4c`）　状态：**v06/v07/v08 已实施，v09/后台能耗进行中**（映射见 §7）
> 范围：`app/src/main`（约 4000 行 Kotlin，21 个文件）+ `docs/OPTIMIZATION_BACKLOG.md` + 测试 48 例
> 定位：在已完成的 O-01~05 / O-08~10 之上，给出后台能耗控制 + 后续 4 个迭代的优化路线；每项含现状、方案、验收口径。已在 OpenSpec（`openspec/changes/<name>`）按迭代立项，制品为中文。

---

## 1. 现状基线

**架构**：事件驱动双通道（`NotificationListenerService` + `AccessibilityService`）→ 纯函数解析器（`NotificationParser`）→ `PayLedgerApplication.savePayment`（Room 事务内去重+写入）→ 单 Activity + Compose（明细 / 统计 / 授权 / 帮助 / 调试）。

**已完成**（台账 O-xx）：O-01 应用级 scope 防丢账、O-02 事务去重、O-03 隐私摘要、O-04 多级重连（重绑→组件重置）、O-05 金额候选择优、O-08 去 `LIMIT 500`、O-09 退款收窄、O-10 手动记账日期时间与明细时间轴。

**强项**：无 `INTERNET` 权限、数据最小化、解析器无 Android 依赖可 JVM 单测、Room Flow 单一数据源、事件驱动无轮询无前台服务。

**短板概览**：识别准确率仍有 O-06/O-07 两项 P1 未解；数据模型是 v1 单表、无索引、无 schema 导出；测试集中在解析层；无导出/备份、无 CI、无预算/分类等记账核心能力。

---

## 2. 不变量（所有迭代必须遵守）

- 不申请 `INTERNET`，数据只存本机 Room，不引入任何联网/上报。
- 无前台服务、无轮询、事件驱动；**若需前台服务保活须先征得用户同意**。
- 金额一律整数分（`Long`），不得改成浮点。
- 日志与持久化不得写入通知原文；调试缓冲仅内存、仅 debug 构建。
- 保持 `NotificationParser` 纯函数、可 JVM 单测。

---

## 3. 本轮分析新增发现（台账之外）

| 编号 | 发现 | 位置 | 影响 | 优先级 |
|---|---|---|---|---|
| N-01 ✅ | 「今日」区间在 VM 初始化时固化一次，进程存活跨 0 点后 `todayCents` 仍指向昨天 | `PayLedgerViewModel.kt:54` | 今日支出显示错误，直到进程重启 | P1 |
| N-02 ✅ | `IGNORE_KEYWORDS` 含「应付」，成功页若同时出现「应付金额 / 实付」会被整条忽略 | `ParserRules.kt:27` | 潜在漏记（需真实样本验证） | P1 |
| N-03 ✅ | 编辑只能改金额/备注/时间，**不能改渠道与商户** | `TransactionDao.update` | 已由 v07 解决：`updateFull` 扩展渠道/商户/类型/分类 | P2 |
| N-04 ✅ | Room `version=1`、`exportSchema=false`、`occurredAt` 无索引 | `LedgerDatabase.kt` | 后续加字段无迁移基线；查询随数据量变慢 | P1（前置） |
| N-05 ✅ | 测试只覆盖解析/格式/环形缓冲，**无 DAO、ViewModel、去重并发测试** | `app/src/test` | 查询与分组逻辑回归无保护 | P2 |
| N-06 ✅ | 统计柱状图为裸 `Canvas`，无 `semantics`，读屏不可用；点击索引未扣 padding | `StatsScreen.kt:300+` | 已由 v08 解决：`BarChartGeometry.indexAt` 纯函数命中 + 每柱 `semantics`/`onClick`，分类与 Top5 行合并语义 | P2 |
| N-07 ✅ | 监听状态每 2 秒轮询 `connected` | `MainActivity.kt:105` | 已由 v08 解决：`ListenerStatus` 单一 StateFlow，双服务直接更新，UI `collectAsStateWithLifecycle`，轮询删除 | P3 |
| N-08 ✅ | 注释漂移：A11Y 写「±3 秒」实为 10 秒；`summary` 注释写「含银行卡尾号」实为结构化摘要 | `PaymentAccessibilityService.kt:13`、`TransactionRecord.kt:13` | 已由 v08 解决：A11Y 注释改「±10 秒」；`summary` 相关注释已核对为结构化摘要（v03 起即正确），无漂移 | P3 |
| N-09 ✅ | `allowBackup=false` 且无导出能力，换机/重装数据不可迁移 | `AndroidManifest.xml` | 已由 v07 解决：CSV 导出（纯编码器 + SAF 写入）；导入按 Non-Goal 不做 | P2 |
| N-10 ✅ | 无 CI；`lint { abortOnError = false }` | `app/build.gradle.kts` | 质量门禁缺失 | P2 |
| N-11 | A11Y 事件文本解析失败后再读窗口树并二次解析；`getChild` 节点未复用/回收 | `PaymentAccessibilityService.kt:33-38, 64-70` | 轻微开销；旧 API 需 recycle | P3 |
| N-12 | **通知通道对微信/支付宝的每条通知都渲染 RemoteViews**（`apply` 构建视图树），聊天消息也不例外；渲染发生在解析之前、无任何前置过滤 | `NotificationLedgerService.kt:26, 51-59` | 用户每收一条微信消息都多一次布局渲染，是后台最主要的无效开销 | **P0** |
| N-13 | **A11Y 通道每次内容变化事件（800ms 节流）在事件文本解析失败后都遍历活动窗口**，每次最多 120 次跨进程 `getChild`；页面滚动、输入、列表加载均触发 | `PaymentAccessibilityService.kt:27, 34-38, 55-70` | 微信/支付宝在前台时持续产生 IPC，是后台第二大开销 | **P0** |

**待确认的产品取舍**：个人转账（「转账」不在付款关键词）是否计入支出？收款/收入是否纳入？当前均为「不计」。

---

## 4. 迭代路线图

### 迭代 0：后台能耗控制（优先，change：`background-energy-control`）

目标：在不降低入账准确率的前提下，把「与付款无关的通知/页面」带来的后台 CPU 唤醒和 IPC 降到接近零。监听器是常驻后台的，省下的每一次渲染、每一次跨进程调用都是全天候收益，所以排在所有功能迭代之前。

| ID | 项 | 方案 | 验收口径 |
|---|---|---|---|
| E-1 | N-12 通知两段式过滤 | 先用 `extras` 里的标题/正文做轻量判定（命中忽略词 → 直接丢弃；含付款关键词或金额 → 进入解析）；只有「extras 文本为空」或「含付款信号」时才渲染 RemoteViews | 非付款通知的渲染次数为 0；付款通知渲染次数不变 |
| E-2 | N-13 窗口树读取前置门槛 | 仅当事件自带文本含付款信号（金额/付款关键词）时才读窗口树；纯滚动/输入事件不再触发 IPC | 非付款事件的窗口树遍历次数为 0 |
| E-3 | N-13 遍历范围收窄 | 优先遍历 `event.source` 子树，取不到根节点再回退 `rootInActiveWindow`；同一窗口解析成功后短时间内不重复遍历 | 单次遍历节点数明显下降；同窗口重复遍历为 0 |
| E-4 | N-13/N-11 节流与事件合并 | `notificationTimeout` 300→500ms；内容变化节流 800→1500ms（页面切换事件不节流）；节点用完回收 | 单位时间事件处理次数下降；页面切换仍即时响应 |
| E-5 | 可观测与回归保护 | debug 构建加计数器（收到事件数 / 渲染次数 / 遍历次数 / 读取节点数）显示在调试页；debug 构建「影子比对」：被门槛拦下的事件仍走旧全量流程，记录「本应入账却被拦」的差异 | 影子比对在真实样本上 0 差异；现有 48 例解析测试全过 |
| E-6 | 测量基线 | 修改前后用 `adb shell dumpsys batterystats` 重置并采样相同操作序列（收 20 条聊天消息 + 1 笔付款），记录本应用后台耗电与唤醒次数 | 报告写入 `docs/`，作为后续性能基线 |

**风险与约束**：门槛判错会漏记，这是唯一不可接受的回归，所以 E-5 的影子比对是硬性验收项；门槛放宽程度做成常量，可按真实样本调整。不引入前台服务、WorkManager 或任何轮询。

### 迭代 1：v0.6.x　正确性与数据地基（change：`v06-correctness-foundation`，建议 1~2 周）

目标：消灭剩余漏记/错记，并先把数据模型与测试地基打好，后续功能才好加。

| ID | 项 | 方案 | 验收口径 |
|---|---|---|---|
| I-1.1 | O-06 去重键升级 | 判重键加入 `counterparty` 与来源；新增 `dedupKey`（渠道+金额+商户+分钟级时间 hash）并建唯一索引 `OnConflictStrategy.IGNORE` 兜底；窗口按通道差异化 | 同额不同商户两笔均入账；同笔双通道只记一次；并发 100 次调用仅 1 条 |
| I-1.2 | O-07 交易时间解析 | 优先解析文案中的交易时间；解析不到回退通知到达时间并标 `timeEstimated=true`；编辑可改 | 含时间文案按解析值入账；无时间行为与现状一致 |
| I-1.3 | N-01 ✅ 今日区间动态化 | 用「下一个午夜触发」的时钟流重算 `startOfToday()`；`zone` 随 `TimeZone.getDefault()` 变化刷新 | 跨 0 点后今日汇总自动切换，无需重启 |
| I-1.4 | N-02 ✅ 忽略词收窄 | 「应付」改为「仅当不含付款成功关键词时忽略」；补混合文案样本到单测 | 「应付金额 ¥20 实付 ¥18」能入账 ¥18；纯待支付仍忽略 |
| I-1.5 | N-04 ✅ 数据模型升级 | `exportSchema=true` + schema 目录；加索引 `occurredAt`、`(channel,amountCents,occurredAt)`；新增字段 `type`(EXPENSE/INCOME)、`source`(NOTIFICATION/A11Y/MANUAL)、`category`(可空)、`dedupKey`(唯一)、`timeEstimated`；**DB v1→v2 迁移** | 旧库升级后数据完整；迁移测试通过；查询计划走索引 |
| I-1.6 | N-05 ✅ 测试补齐 | Room in-memory DAO 测试、`groupByDay`/月份导航/跨年/跨午夜 VM 测试、`DedupWindow` 测试、并发去重测试 | 覆盖率从解析层扩到数据与 VM 层；全部通过 |
| I-1.7 | N-10 ✅ CI | GitHub Actions：`testDebugUnitTest` + `lintDebug`；lint 改为 error 时失败（或 baseline） | push/PR 自动跑；失败可见 |

### 迭代 2：v0.7.x　数据能力与可纠错（change：`v07-data-capabilities`，建议 2 周）

目标：让记录「可改、可查、可带走」，并补上分类统计。

| ID | 项 | 方案 | 验收口径 |
|---|---|---|---|
| I-2.1 ✅ | N-03 编辑渠道/商户 | v07 已实现：`updateFull` 一次更新金额/备注/时间/渠道/商户/类型/分类，编辑弹层全量可改 | 误识别记录可改为正确渠道/商户 |
| I-2.2 ✅ | 搜索与筛选 | v07 已实现：可空参数单条 SQL + LIKE 转义，关键词防抖 250ms | 空结果有空态；超 500 条有提示 |
| I-2.3 ✅ | N-09 导出/导入 | v07 已实现：CreateDocument 导出 CSV（UTF-8 BOM + CRLF）；导入不做（Non-Goal） | 导出文件可用 Excel 打开 |
| I-2.4 ✅ | 删除撤销 | v07 已实现：软删除 + Snackbar 撤销 + 启动时清理 7 天过期记录 | 删除后 5 秒内可恢复；列表实时更新 |
| I-2.5 ✅ | 分类体系 | v07 已实现：分类规则表（预置 + 可编辑）+ 入账归类 + 统计页分类占比/Top 商户 | 自动归类命中可见 |
| I-2.6 ✅ | 收入支持（可选） | 新增 INCOME 类型与收款关键词；支出/收入分开展示 | 收款通知计入收入，不混入支出汇总 |

### 迭代 3：v0.8.x　体验与可访问性（change：`v08-experience-accessibility`，建议 1~2 周）

| ID | 项 | 方案 | 验收口径 |
|---|---|---|---|
| I-3.1 ✅ | 预算与提醒 | 预算类型每日 / 每月二选一，未设置不展示；每日预算 = 每日金额 × 当月天数，显示「剩余每天可用」（超支摊薄到后续每天）；达月预算 80%/100% 及当日额度用完时发**本地**通知（不联网） | 每日 30 元、30 天、已用 20 元 → 剩余每天 30.34 元；可在设置关闭 |
| I-3.2 ✅ | N-06 图表语义化 | 柱状图加 `semantics` 逐月描述；点击索引扣除 padding/gap | 读屏可读出每月金额；点击准确 |
| I-3.3 ✅ | N-07 状态流化 | `connected` 改为 `StateFlow`，UI `collectAsStateWithLifecycle` | 去掉 2 秒轮询；状态即时更新 |
| I-3.4 ✅ | Compose 预览与拆分 | 关键组件加 `@Preview`；`RecordDialogs.kt`（441 行）按新增/编辑拆分 | 预览可用；单文件 ≤ 400 行 |
| I-3.5 ✅ | 引导完善 | 国产 ROM 保活指引按品牌分节；空态/首次使用引导 | 新用户按引导能完成授权并记到第一笔 |
| I-3.6（N-11 留待后台能耗迭代） | N-08 / N-11 清理 | 修正注释；A11Y 二次解析合并、节点回收 | 无误导注释；无 lint 警告 |

### 迭代 4：v0.9~1.0　工程化与收尾（change：`v09-engineering-release`，按需）

> CI（I-1.7）已前移到迭代 1，用来保护数据迁移与新增测试；本迭代只保留静态检查、依赖升级、发布流程与性能基线。i18n（I-4.5）暂不立项，有多语言需求时再单独立项。

| ID | 项 | 方案 |
|---|---|---|
| I-4.1 | 静态检查 | detekt/ktlint 接入 + lint baseline，CI 失败即阻断 |
| I-4.2 | 依赖升级评估 | AGP/Kotlin/compileSdk/Room 升级，评估与 KSP、Compose BOM 兼容性 |
| I-4.3 | 发布流程 | release 签名配置、GitHub Release 自动打包、变更日志同步 |
| I-4.4 | 性能基线 | 冷启动、列表滚动、DB 体积基线测量；`allowBackup` 策略复核 |
| I-4.5 | 可选 i18n | 文案迁入 `strings.xml`（当前中文硬编码 ~50 处）；仅在有多语言需求时做 |

---

## 5. 优先级与规模汇总

| ID | 优先级 | 规模 | 依赖 |
|---|---|---|---|
| E-1~E-4 能耗控制 | **P0** | S~M | —（独立，可最先做） |
| E-5~E-6 可观测/基线 | P0 | M | 真实设备 + 真实样本 |
| I-1.1 O-06 去重 | P1 | M | I-1.5 字段 |
| I-1.2 O-07 时间 | P1 | M | I-1.5 `timeEstimated` |
| I-1.3 N-01 午夜 | P1 | S | — |
| I-1.4 N-02 忽略词 | P1 | S | 真实样本 |
| I-1.5 N-04 模型 | P1 | M | — |
| I-1.6 N-05 测试 | P2 | M | I-1.5 |
| I-1.7 N-10 CI | P2 | S | — |
| I-2.1 N-03 编辑 | P2 | S | I-1.5 |
| I-2.2 搜索筛选 | P2 | M | — |
| I-2.3 N-09 导出 | P2 | M | I-1.5 字段 |
| I-2.4 删除撤销 | P2 | S | I-1.5 `deletedAt` |
| I-2.5 分类体系 | P2 | L | I-1.5 `category` |
| I-2.6 收入支持 | P3 | M | I-1.5 `type` |
| I-3.1 预算提醒 | P2 | M | I-2.5 分类 |
| I-3.2 N-06 语义化 | P2 | S | — |
| I-3.3 N-07 状态流 | P3 | S | — |
| I-3.4 预览/拆分 | P3 | M | — |
| I-3.5 引导完善 | P3 | S | — |
| I-3.6 注释/A11Y 清理 | P3 | S | — |
| I-4.x 工程化 | P3 | S~M | — |

规模：S ≤ 0.5 天，M ≈ 1~2 天，L ≈ 3~5 天（单人估算）。

---

## 6. 风险与取舍

- **去重键加商户**会降低「同商户连买两笔同额」的误判，但商户为空时仍退回窗口判重；用 `dedupKey` 唯一索引做最终兜底。
- **时间解析**可能把「通知发送时间」当「交易时间」；用 `timeEstimated` 标注并允许编辑，不追求 100%。
- **导出**与「本地最小数据」原则不冲突：由用户主动选目录，不自动上传；CSV 含金额与商户，提示用户妥善保管。
- **分类自动规则**有维护成本；先内置常见商户关键词表，允许用户改。
- **DB 迁移**必须保留旧数据：先启用 schema 导出再改字段，并写 Migration 测试。
- **国产 ROM 保活**无法承诺 100%，前台服务需用户同意；迭代 3 只做指引与状态可视化。
- **gradle/依赖升级**可能触发 KSP/Compose 兼容问题，放在最后且单独提交。

---

## 7. 立项结果（2026-11-27）

已确认「后面都加上」，按迭代在 OpenSpec 立项如下，实施顺序自上而下（能耗控制独立，可最先做）：

| change | 覆盖项 | 能力域（`openspec/specs/` 下） |
|---|---|---|
| `background-energy-control` | E-1~E-6 | `pay-ledger/energy-control` |
| `v06-correctness-foundation` | I-1.1~I-1.7 | `pay-ledger/payment-capture`、`pay-ledger/ledger-storage`、`pay-ledger/records-timeline` |
| `v07-data-capabilities` | I-2.1~I-2.6 | `pay-ledger/record-editing`、`pay-ledger/record-search`、`pay-ledger/data-export`、`pay-ledger/record-deletion`、`pay-ledger/categories`、`pay-ledger/income` |
| `v08-experience-accessibility` | I-3.1~I-3.6 | `pay-ledger/budget-alerts`、`pay-ledger/statistics-accessibility`、`pay-ledger/listener-status`、`pay-ledger/onboarding` |
| `v09-engineering-release` | I-4.1~I-4.4 | 无（纯工程，`skip_specs`） |

立项时采用的默认取舍（可在 apply 前调整对应 change 的 design.md）：

1. 接受 DB v1→v2 迁移，并一次性加上后续迭代要用的列（`type/source/category/dedupKey/timeEstimated/deletedAt`），避免多次迁移。
2. 收入、分类、预算三项均纳入；其中**收入默认关闭**，由用户在设置里开启。
3. 导出只做 CSV，**导入暂不做**；不做多语言。
4. 预算提醒需要系统通知权限（Android 13+ 运行时申请），仅在用户开启预算时申请。

每个 change 的 `tasks.md` 是实施清单；确认后从 `background-energy-control` 开始 apply。
