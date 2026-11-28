# 性能基线（v09 D4）

> 方法可重复、数值留待装包实测轮填写。测量前提：先在调试页「性能测试数据 → 生成 1 万条」灌入确定性测试数据，测完「清空测试数据」。

## 1. 冷启动

```bash
adb shell am force-stop com.zhaoyingqi.payledger
adb shell am start -W com.zhaoyingqi.payledger/.ui.MainActivity   # 记录 TotalTime
# 重复 5 次（首启丢弃，取后 5 次中位数），期间不操作手机
```

| 日期 | 构建 | 数据量 | TotalTime 中位数 (ms) | 备注 |
| --- | --- | --- | --- | --- |
| _待实测_ | debug | 0 | | |
| _待实测_ | debug | 1 万 | | |
| _待实测_ | release | 0 | | |

## 2. 明细页滚动掉帧

```bash
adb shell dumpsys gfxinfo com.zhaoyingqi.payledger reset
# 固定脚本匀速滑动明细页 10 秒（swipe 5 次以上）
adb shell dumpsys gfxinfo com.zhaoyingqi.payledger framestats
# 取 janky_frames 比例与 95/99 分位帧耗时
```

| 日期 | 构建 | 数据量 | janky% | P95 (ms) | P99 (ms) |
| --- | --- | --- | --- | --- | --- |
| _待实测_ | debug | 1 万 | | | |

## 3. 查询耗时（debug 埋点复现）

调试页灌入 1 万条后，`adb logcat -s PerfTiming` 读取埋点输出（`debug/QueryTiming.kt`，
tag = `PerfTiming`，只含查询标签与行数，不含账目内容）：

| 埋点标签 | 覆盖路径 | 读取方式 |
| --- | --- | --- |
| `records.groups` | 明细页当月列表（首发射 total 即查询耗时） | 冷进明细页一次 |
| `records.todaySum` / `records.currentMonthSum` | 预算进度订阅的当日/当月汇总 | 冷启动一次 |
| `stats.monthSum` / `stats.monthIncomeSum` | 明细页头部月汇总（切月各测一次） | 左右切月 |
| `stats.monthlyTrend` / `stats.categorySums` / `stats.topMerchants` | 统计页三项聚合 | 冷进统计页 |
| `search.results` | 搜索（含 250ms debounce 后的查询） | 输入关键词一次 |

| 日期 | 构建 | 数据量 | groups (ms) | monthSum (ms) | monthlyTrend (ms) | search (ms) |
| --- | --- | --- | --- | --- | --- | --- |
| _待实测_ | debug | 1 万 | | | | |

## 4. 数据库体积

```bash
adb shell run-as com.zhaoyingqi.payledger ls -la databases/payledger.db
# 记录 .db 与 -wal 的大小；灌数据前后各记一次
```

| 日期 | 构建 | 行数 | payledger.db (KB) | -wal (KB) |
| --- | --- | --- | --- | --- |
| _待实测_ | debug | 0 | | |
| _待实测_ | debug | 1 万 | | |

## 备注

- 测试数据为确定性生成（`debug/PerfTestData.kt`）：近 365 天、微信/支付宝各半、
  20% 收入、6 类分类轮换；counterparty 统一标记「性能测试商户」，清理不触碰真实记录。
- 「性能测试数据」卡片仅 debug 构建显示（`BuildConfig.DEBUG` 守卫，release 不含该入口）。
- release 包测冷启动/滚动时同样可用测试数据灌库（release 无入口，可在 release 前于
  debug 包灌好数据后覆盖安装 release，或用 `adb shell run-as` 复制 db 文件）。
