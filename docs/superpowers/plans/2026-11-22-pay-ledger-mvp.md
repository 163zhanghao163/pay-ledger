# PayLedger MVP 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 监听微信/支付宝付款通知自动记账的安卓 App（最小闭环：自动记账 + 列表 + 今日/本月汇总 + 备注/删除）。

**架构：** `NotificationListenerService` 被动回调 → `NotificationParser`（纯函数规则）→ Room 落库；Compose 单 Activity UI 直接消费 Dao 的 Flow。手动 DI（Application 持有依赖），零第三方依赖，无网络权限。

**技术栈：** Kotlin 2.0.21 + AGP 8.7.3 + Gradle 8.10.2 + Compose BOM 2024.12.01 + Room 2.6.1（KSP）。本机 SDK `D:\dev\ide\AndroidStudioSDK`（platform 35、build-tools 35.0.0 已装），Gradle 跑在系统 Java 20 上（AS 自带 JBR 11 不可用）。

**规格：** `docs/superpowers/specs/2026-11-22-pay-ledger-design.md`（唯一权威）。

**环境注意：**
- 临时文件一律放工作区根目录 `_tmp/pay-ledger/`，不入 git；
- `local.properties`（含 sdk.dir）不提交；
- 若依赖下载被代理拦（`127.0.0.1:8762` 403）：Gradle 侧在 `gradle.properties` 临时加 `systemProp.https.proxyHost/systemProp.https.proxyPort`，或 curl 下载时加 `--noproxy '*'`；
- Git Bash 中用 `./gradlew` 执行（java 已在 PATH，无需设 JAVA_HOME）。

---

## 文件结构

```
pay-ledger/
  .gitignore
  gradle.properties
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  local.properties            # 不提交：sdk.dir=D:\dev\ide\AndroidStudioSDK
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/res/values/{strings,themes,colors}.xml
    src/main/res/drawable/ic_launcher_foreground.xml
    src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    src/main/java/com/zhaoyingqi/payledger/
      PayLedgerApplication.kt            # Application：db/dao/通知环形缓冲
      notification/ParserRules.kt        # 渠道常量/关键词/正则/DedupWindow（可迭代校准点）
      notification/NotificationParser.kt # 纯函数解析器（无 Android 依赖）
      notification/NotificationRingBuffer.kt # debug 预览页的原始通知样本缓冲
      notification/NotificationLedgerService.kt # 监听→解析→去重→入库
      data/TransactionRecord.kt          # Room 实体
      data/TransactionDao.kt             # Flow 查询 + suspend 写
      data/LedgerDatabase.kt
      ui/MainActivity.kt                 # 授权检测 + 页面切换
      ui/Format.kt                       # 分→¥字符串
      ui/PayLedgerViewModel.kt          # 分组/汇总 Flow + 删除/备注
      ui/screens/AuthorizationScreen.kt
      ui/screens/MainListScreen.kt       # 汇总卡+日分组列表+备注弹层+删除确认
      ui/screens/DebugPreviewScreen.kt   # debug 用：最近原始通知
      ui/theme/Theme.kt
    src/test/java/com/zhaoyingqi/payledger/notification/NotificationParserTest.kt
```

---

### 任务 1：Gradle 工程骨架（可编译的 Hello 应用）

**文件：** 见上方结构中根级配置 + app 模块配置 + `ui/MainActivity.kt`（桩）+ `ui/theme/Theme.kt` + res。

- [ ] **步骤 1.1：写根级配置文件**

`.gitignore`：
```
.gradle/
build/
local.properties
.idea/
*.iml
captures/
.DS_Store
```

`gradle.properties`：
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`settings.gradle.kts`：
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "pay-ledger"
include(":app")
```

根 `build.gradle.kts`：
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle/libs.versions.toml`：
```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
room = "2.6.1"
coreKtx = "1.13.1"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **步骤 1.2：写 app 模块配置**

`app/build.gradle.kts`：
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zhaoyingqi.payledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhaoyingqi.payledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint { abortOnError = false }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
}
```

空文件 `app/proguard-rules.pro`（Room/Compose 自带 consumer rules）。

- [ ] **步骤 1.3：写 Manifest 与资源（任务 1 阶段不含 Service 与 Application）**

`app/src/main/AndroidManifest.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false"
        android:supportsRtl="true"
        android:theme="@style/Theme.PayLedger">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
注意：全程不声明 `INTERNET` 权限（规格 §8 安全要求）。

`app/src/main/res/values/strings.xml`：
```xml
<resources><string name="app_name">PayLedger</string></resources>
```

`app/src/main/res/values/themes.xml`：
```xml
<resources><style name="Theme.PayLedger" parent="android:Theme.Material.Light.NoActionBar"/></resources>
```

`app/src/main/res/values/colors.xml`：
```xml
<resources><color name="ic_launcher_background">#26A69A</color></resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M54,54m-18,0a18,18 0 1,0 36,0a18,18 0 1,1 -36,0"/>
    <path android:fillColor="#26A69A" android:pathData="M54,54m-10,0a10,10 0 1,0 20,0a10,10 0 1,1 -20,0"/>
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`：
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

- [ ] **步骤 1.4：写 Theme 与 MainActivity 桩**

`ui/theme/Theme.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun PayLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
```

`ui/MainActivity.kt`（桩，任务 5 替换为完整版）：
```kotlin
package com.zhaoyingqi.payledger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PayLedgerTheme { Text("PayLedger") } }
    }
}
```

- [ ] **步骤 1.5：引导 Gradle Wrapper**

```bash
mkdir -p /e/dev/workspace/deepseekharness/_tmp/pay-ledger && cd /e/dev/workspace/deepseekharness/_tmp/pay-ledger
curl -L -o gradle-8.10.2-bin.zip https://services.gradle.org/distributions/gradle-8.10.2-bin.zip   # 失败则加 --noproxy '*'
unzip -q gradle-8.10.2-bin.zip
cd /e/dev/workspace/deepseekharness/pay-ledger
/e/dev/workspace/deepseekharness/_tmp/pay-ledger/gradle-8.10.2/bin/gradle.bat wrapper --gradle-version 8.10.2 --distribution-type bin --no-daemon
```
生成 `gradlew`、`gradlew.bat`、`gradle/wrapper/*`。

- [ ] **步骤 1.6：写 local.properties（不提交）**

`local.properties`：
```
sdk.dir=D\:\\dev\\ide\\AndroidStudioSDK
```

- [ ] **步骤 1.7：验证构建**

运行：`./gradlew :app:assembleDebug`
预期：`BUILD SUCCESSFUL`，产物 `app/build/outputs/apk/debug/app-debug.apk`。首次运行会下载 AGP/依赖，耗时数分钟属正常。

- [ ] **步骤 1.8：Commit**

```bash
git add .gitignore gradle.properties settings.gradle.kts build.gradle.kts gradle/ gradlew gradlew.bat app/ docs/
git commit -m "chore: 搭建 Gradle 工程骨架（Hello 应用可编译）"
```

---

### 任务 2：通知解析器（TDD）

**文件：**
- 创建：`notification/ParserRules.kt`、`notification/NotificationParser.kt`
- 测试：`app/src/test/java/com/zhaoyingqi/payledger/notification/NotificationParserTest.kt`

- [ ] **步骤 2.1：写 ParserRules 骨架 + Parser 桩（返回 null）**

`notification/ParserRules.kt`：
```kotlin
package com.zhaoyingqi.payledger.notification

/** 解析规则集中定义：真实通知文案有差异时，在此迭代关键词与正则。 */
object ParserRules {
    const val PACKAGE_WECHAT = "com.tencent.mm"
    const val PACKAGE_ALIPAY = "com.eg.android.AlipayGphone"

    // 渠道通知标题标识（不匹配则直接忽略，过滤掉微信聊天等其他通知）
    const val WECHAT_TITLE_FLAG = "微信支付"
    const val ALIPAY_TITLE_FLAG = "支付宝"

    // 付款语义关键词（标题+文本中命中任一）
    val PAY_KEYWORDS = listOf("付款成功", "支付成功", "已成功付款", "已支付", "已付款", "已扣款", "扣款成功", "微信支付凭证")

    // 收款 / 非付款语义关键词（命中即忽略）
    val IGNORE_KEYWORDS = listOf("收款助手", "收款到账", "收款成功", "转账到账", "余额宝收益", "花呗还款", "退款")

    // 金额：¥ 前缀优先，其次「元」后缀；整数 8 位以内，支持千分位与最多两位小数
    val CURRENCY_PREFIX = Regex("""[¥￥]\s*([1-9]\d{0,7}(?:,\d{3})*(?:\.\d{1,2})?)""")
    val UNIT_SUFFIX = Regex("""([1-9]\d{0,7}(?:,\d{3})*(?:\.\d{1,2})?)\s*元""")

    // 商户名：宽松提取，失败置空
    val MERCHANT_WECHAT = Regex("""付款给\s*([^\s，,。]{1,30})""")
    val MERCHANT_ALIPAY = Regex("""向\s*([^\s，,。]{1,20})""")

    const val MAX_SUMMARY_LEN = 200
}

/** 去重时间窗：同渠道同金额、时间差 ≤3 秒视为重复通知。 */
object DedupWindow {
    const val WINDOW_MS = 3_000L
    fun of(occurredAt: Long): LongRange = LongRange(occurredAt - WINDOW_MS, occurredAt + WINDOW_MS)
}
```

`notification/NotificationParser.kt`（桩）：
```kotlin
package com.zhaoyingqi.payledger.notification

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    val summary: String,
)

object NotificationParser {
    fun parse(packageName: String, title: String?, text: String?): ParsedPayment? = null
}
```

- [ ] **步骤 2.2：写失败测试（覆盖规格 §10 全部用例）**

`app/src/test/java/com/zhaoyingqi/payledger/notification/NotificationParserTest.kt`：
```kotlin
package com.zhaoyingqi.payledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    // —— 命中：微信付款 ——
    @Test fun wechat_voucher_style() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "微信支付凭证\n¥20.00\n付款成功")
        assertEquals(2000L, r?.amountCents)
        assertEquals(Channel.WECHAT, r?.channel)
    }

    @Test fun wechat_merchant_extracted() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "你已成功付款 ¥35.50\n付款给 星巴克咖啡")
        assertEquals(3550L, r?.amountCents)
        assertEquals("星巴克咖啡", r?.counterparty)
    }

    @Test fun wechat_unit_suffix_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "已扣款 128.00元 宁波银行(尾号1234)")
        assertEquals(12800L, r?.amountCents)
    }

    @Test fun thousands_separator() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥1,234.56")
        assertEquals(123456L, r?.amountCents)
    }

    @Test fun integer_amount() {
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", "已支付¥20")
        assertEquals(2000L, r?.amountCents)
    }

    // —— 命中：支付宝付款 ——
    @Test fun alipay_payment_parsed() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "付款成功 ¥35.50 向 星巴克")
        assertEquals(3550L, r?.amountCents)
        assertEquals(Channel.ALIPAY, r?.channel)
        assertEquals("星巴克", r?.counterparty)
    }

    @Test fun alipay_bank_deduction() {
        val r = NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "已扣款 128.00元 宁波银行(尾号6688)")
        assertEquals(12800L, r?.amountCents)
    }

    // —— 忽略：收款 / 非付款 ——
    @Test fun wechat_income_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "收款到账12.00元"))
    }

    @Test fun wechat_chat_message_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "张三", "我刚支付成功了"))
    }

    @Test fun alipay_yield_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "余额宝收益 0.12元"))
    }

    @Test fun alipay_transfer_in_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "支付宝通知", "转账到账 500.00元"))
    }

    @Test fun alipay_title_missing_ignored() {
        assertNull(NotificationParser.parse("com.eg.android.AlipayGphone", "通知", "付款成功 ¥20.00"))
    }

    // —— 忽略：金额异常 / 无关输入 ——
    @Test fun zero_amount_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功 ¥0.00"))
    }

    @Test fun no_amount_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "微信支付", "付款成功"))
    }

    @Test fun empty_text_ignored() {
        assertNull(NotificationParser.parse("com.tencent.mm", "", ""))
    }

    @Test fun unrelated_package_ignored() {
        assertNull(NotificationParser.parse("com.android.systemui", "通知", "付款成功 ¥20.00"))
    }

    // —— 摘要与去重窗口 ——
    @Test fun summary_truncated_to_200() {
        val long = "付款成功 ¥20.00 " + "x".repeat(300)
        val r = NotificationParser.parse("com.tencent.mm", "微信支付", long)
        assertEquals(200, r?.summary?.length)
    }

    @Test fun dedup_window_boundaries() {
        val w = DedupWindow.of(1000L)
        assertEquals(2000L, w.first)
        assertEquals(4000L, w.last)
        assertEquals(true, w.contains(-2000L))
        assertEquals(false, w.contains(-2001L))
    }
}
```

- [ ] **步骤 2.3：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests "com.zhaoyingqi.payledger.notification.NotificationParserTest"`
预期：FAIL（桩返回 null，所有命中用例断言失败）。

- [ ] **步骤 2.4：实现解析器**

`NotificationParser.kt` 桩替换为：
```kotlin
package com.zhaoyingqi.payledger.notification

enum class Channel { WECHAT, ALIPAY }

/** 解析成功产出的付款信息。 */
data class ParsedPayment(
    val channel: Channel,
    val amountCents: Long,
    val counterparty: String,
    val summary: String,
)

/** 纯函数式解析器：无 Android 依赖，便于 JVM 单测。 */
object NotificationParser {

    /** 非付款或解析失败返回 null（宁可漏记不可错记）。 */
    fun parse(packageName: String, title: String?, text: String?): ParsedPayment? {
        val t = title?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (t.isEmpty() && body.isEmpty()) return null

        val channel = resolveChannel(packageName, t) ?: return null
        val haystack = "$t $body"
        if (ParserRules.IGNORE_KEYWORDS.any { haystack.contains(it) }) return null
        if (!ParserRules.PAY_KEYWORDS.any { haystack.contains(it) }) return null
        val amountCents = extractAmountCents(haystack) ?: return null
        val counterparty = extractMerchant(channel, haystack).orEmpty()
        return ParsedPayment(channel, amountCents, counterparty, haystack.take(ParserRules.MAX_SUMMARY_LEN))
    }

    private fun resolveChannel(packageName: String, title: String): Channel? = when (packageName) {
        ParserRules.PACKAGE_WECHAT -> if (title.contains(ParserRules.WECHAT_TITLE_FLAG)) Channel.WECHAT else null
        ParserRules.PACKAGE_ALIPAY -> if (title.contains(ParserRules.ALIPAY_TITLE_FLAG)) Channel.ALIPAY else null
        else -> null
    }

    /** 金额提取：优先 ¥ 前缀，其次「元」后缀；都缺失则放弃（避免把「11月22日」这类日期误当金额）。 */
    internal fun extractAmountCents(text: String): Long? {
        val raw = ParserRules.CURRENCY_PREFIX.find(text)?.groupValues?.getOrNull(1)
            ?: ParserRules.UNIT_SUFFIX.find(text)?.groupValues?.getOrNull(1)
            ?: return null
        val cleaned = raw.replace(",", "")
        val dot = cleaned.indexOf('.')
        val cents = if (dot < 0) {
            cleaned.toLong() * 100
        } else {
            cleaned.substring(0, dot).toLong() * 100 +
                cleaned.substring(dot + 1).padEnd(2, '0').take(2).toLong()
        }
        return if (cents > 0) cents else null
    }

    private fun extractMerchant(channel: Channel, text: String): String? = when (channel) {
        Channel.WECHAT -> ParserRules.MERCHANT_WECHAT.find(text)?.groupValues?.getOrNull(1)
        Channel.ALIPAY -> ParserRules.MERCHANT_ALIPAY.find(text)?.groupValues?.getOrNull(1)
    }
}
```

- [ ] **步骤 2.5：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest --tests "com.zhaoyingqi.payledger.notification.NotificationParserTest"`
预期：PASS（18 个用例全绿）。测试报告在 `app/build/reports/tests/`。

- [ ] **步骤 2.6：Commit**

```bash
git add app/src/main/java/com/zhaoyingqi/payledger/notification/ app/src/test/
git commit -m "feat: 通知解析器与去重窗口（含单元测试）"
```

---

### 任务 3：Room 数据层

**文件：** `data/TransactionRecord.kt`、`data/TransactionDao.kt`、`data/LedgerDatabase.kt`

- [ ] **步骤 3.1：写实体**

```kotlin
package com.zhaoyingqi.payledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,      // 金额（分），正数
    val channel: String,        // "WECHAT" | "ALIPAY"
    val counterparty: String,   // 商户/对方，解析不到为空串
    val occurredAt: Long,       // epochMillis，取通知收到时间
    val summary: String,        // 通知原文摘要（含银行卡尾号等，便于对账）
    val note: String = "",      // 用户备注
    val createdAt: Long,        // epochMillis，入库时间
)
```

- [ ] **步骤 3.2：写 Dao**

```kotlin
package com.zhaoyingqi.payledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(record: TransactionRecord): Long

    /** 列表 LIMIT 500：数据量小，MVP 不做分页（规格 §7）。 */
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT 500")
    fun observeAll(): Flow<List<TransactionRecord>>

    /** 今日/本月汇总：按 [startInclusive, endExclusive) 区间求和。 */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE occurredAt >= :startInclusive AND occurredAt < :endExclusive")
    fun observeSumBetween(startInclusive: Long, endExclusive: Long): Flow<Long>

    /** 防重复：同渠道同金额、时间窗内已有记录返回其 id。 */
    @Query("SELECT id FROM transactions WHERE channel = :channel AND amountCents = :amountCents AND occurredAt BETWEEN :from AND :to LIMIT 1")
    suspend fun findDuplicate(channel: String, amountCents: Long, from: Long, to: Long): Long?

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **步骤 3.3：写 Database**

```kotlin
package com.zhaoyingqi.payledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransactionRecord::class], version = 1, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "payledger.db").build()
    }
}
```

- [ ] **步骤 3.4：编译验证（Room KSP 在编译期校验 SQL）**

运行：`./gradlew :app:compileDebugKotlin`
预期：`BUILD SUCCESSFUL`（KSP 通过即表名/列名/参数正确）。

- [ ] **步骤 3.5：Commit**

```bash
git add app/src/main/java/com/zhaoyingqi/payledger/data/
git commit -m "feat: Room 数据层（实体/Dao/数据库）"
```

---

### 任务 4：通知监听服务接入

**文件：**
- 创建：`PayLedgerApplication.kt`、`notification/NotificationRingBuffer.kt`、`notification/NotificationLedgerService.kt`
- 修改：`app/src/main/AndroidManifest.xml`（application 加 name；注册 Service）

- [ ] **步骤 4.1：写环形缓冲（debug 预览页数据源）**

```kotlin
package com.zhaoyingqi.payledger.notification

/** 原始通知样本，仅 debug 预览页使用。 */
data class RawNotification(val packageName: String, val title: String, val text: String, val postedAt: Long)

/** 固定容量环形缓冲，线程安全。 */
class NotificationRingBuffer(private val capacity: Int = 10) {
    private val buf = ArrayDeque<RawNotification>(capacity)

    @Synchronized
    fun push(item: RawNotification) {
        if (buf.size >= capacity) buf.removeFirst()
        buf.addLast(item)
    }

    @Synchronized
    fun snapshot(): List<RawNotification> = buf.toList()
}
```

- [ ] **步骤 4.2：写 Application（手动 DI 容器）**

```kotlin
package com.zhaoyingqi.payledger

import android.app.Application
import com.zhaoyingqi.payledger.data.LedgerDatabase
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.notification.NotificationRingBuffer

class PayLedgerApplication : Application() {
    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }
    val dao: TransactionDao get() = database.transactionDao()
    val recentNotifications = NotificationRingBuffer()
}
```

- [ ] **步骤 4.3：写 Service**

```kotlin
package com.zhaoyingqi.payledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zhaoyingqi.payledger.BuildConfig
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 被动监听付款通知：回调→解析→去重→入库。无轮询、无前台服务、无周期任务。 */
class NotificationLedgerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app by lazy { application as PayLedgerApplication }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (pkg != ParserRules.PACKAGE_WECHAT && pkg != ParserRules.PACKAGE_ALIPAY) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        app.recentNotifications.push(RawNotification(pkg, title.orEmpty(), text.orEmpty(), sbn.postTime))

        val parsed = NotificationParser.parse(pkg, title, text)
        if (parsed == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ignored: pkg=$pkg title=$title")
            return
        }
        val occurredAt = sbn.postTime
        scope.launch { save(parsed, occurredAt) }
    }

    private suspend fun save(parsed: ParsedPayment, occurredAt: Long) {
        try {
            val window = DedupWindow.of(occurredAt)
            if (app.dao.findDuplicate(parsed.channel.name, parsed.amountCents, window.first, window.last) != null) return
            app.dao.insert(
                TransactionRecord(
                    amountCents = parsed.amountCents,
                    channel = parsed.channel.name,
                    counterparty = parsed.counterparty,
                    occurredAt = occurredAt,
                    summary = parsed.summary,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e) // 不写通知文本，避免敏感信息落日志
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val TAG = "PayLedgerService" }
}
```

- [ ] **步骤 4.4：改 Manifest**

`<application ...>` 加属性 `android:name=".PayLedgerApplication"`，并在 `</activity>` 后注册：

```xml
        <service
            android:name=".notification.NotificationLedgerService"
            android:exported="false"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **步骤 4.5：编译验证**

运行：`./gradlew :app:compileDebugKotlin`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 4.6：Commit**

```bash
git add app/
git commit -m "feat: 通知监听服务接入解析与去重"
```

---

### 任务 5：Compose UI

**文件：**
- 创建：`ui/Format.kt`、`ui/PayLedgerViewModel.kt`、`ui/screens/AuthorizationScreen.kt`、`ui/screens/MainListScreen.kt`、`ui/screens/DebugPreviewScreen.kt`
- 修改：`ui/MainActivity.kt`（桩替换为完整版）

- [ ] **步骤 5.1：写格式化工具**

`ui/Format.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** 分 → ¥x.xx（显示用，两位小数）。 */
fun formatCents(cents: Long): String =
    "¥" + BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
```

- [ ] **步骤 5.2：写 ViewModel**

`ui/PayLedgerViewModel.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhaoyingqi.payledger.data.TransactionDao
import com.zhaoyingqi.payledger.data.TransactionRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayGroup(
    val date: LocalDate,
    val label: String,
    val records: List<TransactionRecord>,
    val totalCents: Long,
)

class PayLedgerViewModel(private val dao: TransactionDao) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("MM月dd日")

    val groups: StateFlow<List<DayGroup>> = dao.observeAll()
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 汇总区间在 ViewModel 创建时计算：跨天后回到本页即由 ON_RESUME 重建状态（MVP 取舍）。
    val todayCents: StateFlow<Long> = dao.observeSumBetween(startOfToday(), startOfTomorrow())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthCents: StateFlow<Long> = dao.observeSumBetween(startOfMonth(), startOfNextMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun updateNote(id: Long, note: String) = viewModelScope.launch { dao.updateNote(id, note) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    internal fun groupByDay(records: List<TransactionRecord>): List<DayGroup> =
        records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) -> DayGroup(date, date.format(dayFmt), list, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.date }

    private fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfTomorrow(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfMonth(): Long = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun startOfNextMonth(): Long = LocalDate.now(zone).plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
```

- [ ] **步骤 5.3：写授权引导页**

`ui/screens/AuthorizationScreen.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AuthorizationScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PayLedger", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "需要「通知使用权」来读取微信 / 支付宝的付款通知。\n数据只保存在本机，本应用不申请网络权限。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("去开启通知使用权")
        }
    }
}
```

- [ ] **步骤 5.4：写主列表页**

`ui/screens/MainListScreen.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.DayGroup
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatCents

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainListScreen() {
    val app = LocalContext.current.applicationContext as PayLedgerApplication
    val vm: PayLedgerViewModel = viewModel(initializer = { PayLedgerViewModel(app.dao) })
    val groups by vm.groups.collectAsStateWithLifecycle()
    val todayCents by vm.todayCents.collectAsStateWithLifecycle()
    val monthCents by vm.monthCents.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionRecord?>(null) }
    var deleting by remember { mutableStateOf<TransactionRecord?>(null) }
    var showDebug by remember { mutableStateOf(false) }

    if (showDebug) {
        DebugPreviewScreen(onBack = { showDebug = false })
        return
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            SummaryCard(todayCents, monthCents, onLongClick = { showDebug = true })
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录，去支付一笔试试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { group ->
                        item(key = "header-${group.date.toEpochDay()}") {
                            Text(
                                "${group.label}  支出 ${formatCents(group.totalCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.records, key = { it.id }) { record ->
                            RecordRow(
                                record = record,
                                onClick = { editing = record },
                                onLongClick = { deleting = record },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { record ->
        NoteEditSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { note ->
                vm.updateNote(record.id, note)
                editing = null
            },
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("删除 ${formatCents(record.amountCents)} 这条记录？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(record.id); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SummaryCard(todayCents: Long, monthCents: Long, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryItem("今日支出", todayCents)
        SummaryItem("本月支出", monthCents)
    }
}

@Composable
private fun SummaryItem(label: String, cents: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(formatCents(cents), style = MaterialTheme.typography.titleLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(record: TransactionRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (record.channel == "WECHAT") "微信" else "支付宝",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                record.counterparty.ifEmpty { record.summary.take(20) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatCents(record.amountCents), style = MaterialTheme.typography.titleMedium)
        }
        if (record.note.isNotEmpty()) {
            Text(
                "备注：${record.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteEditSheet(record: TransactionRecord, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(record.id) { mutableStateOf(record.note) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("编辑备注", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("这笔钱花在了哪？") },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(text.trim()) }) { Text("保存") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **步骤 5.5：写 debug 预览页**

`ui/screens/DebugPreviewScreen.kt`：
```kotlin
package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoyingqi.payledger.PayLedgerApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 仅 debug 构建可达（主列表长按汇总卡进入）：查看最近原始通知，校准解析规则。 */
@Composable
fun DebugPreviewScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as PayLedgerApplication
    val items = remember { app.recentNotifications.snapshot() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("最近通知（调试）", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items.size) { i ->
                val n = items[i]
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${n.packageName}  ${timeFmt.format(Date(n.postedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("标题：${n.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "文本：${n.text}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **步骤 5.6：MainActivity 换成完整版（授权检测 + 页面切换）**

`ui/MainActivity.kt` 整体替换为：
```kotlin
package com.zhaoyingqi.payledger.ui

import android.app.NotificationManagerCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zhaoyingqi.payledger.ui.screens.AuthorizationScreen
import com.zhaoyingqi.payledger.ui.screens.MainListScreen
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {

    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listenerEnabled = isListenerEnabled()
        setContent {
            PayLedgerTheme {
                if (listenerEnabled) MainListScreen() else AuthorizationScreen()
            }
        }
        // 从系统设置返回后重新检测授权状态
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listenerEnabled = isListenerEnabled()
        })
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
```

- [ ] **步骤 5.7：编译并打 debug 包**

运行：`./gradlew :app:assembleDebug`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5.8：Commit**

```bash
git add app/
git commit -m "feat: Compose UI（授权引导/主列表/备注删除/调试预览）"
```

---

### 任务 6：全量验证、变更日志与交付

- [ ] **步骤 6.1：全量单测**

运行：`./gradlew :app:testDebugUnitTest`
预期：BUILD SUCCESSFUL，18 个用例全过（报告 `app/build/reports/tests/testDebugUnitTest/`）。

- [ ] **步骤 6.2：APK 产物确认**

`app/build/outputs/apk/debug/app-debug.apk` 存在；如手机已连接（`D:\dev\ide\AndroidStudioSDK\platform-tools\adb.exe devices`），可选执行：
```bash
/e/dev/workspace/deepseekharness/_tmp/pay-ledger/.keep 2>/dev/null; "D:\dev\ide\AndroidStudioSDK\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```
未连接则把 APK 路径告知用户手动安装。

- [ ] **步骤 6.3：真机试用清单（交给用户）**

1. 安装 APK → 打开 → 点「去开启通知使用权」→ 在系统设置中允许 PayLedger；
2. 返回 App 应进入主列表；用微信/支付宝小额支付一笔；
3. 下拉通知后打开 App，核对金额/渠道/时间；点单条改备注、长按删除验证；
4. 长按顶部汇总卡进入调试页，核对原始通知文本与解析命中情况（不命中的文案发给我，我改 `ParserRules`）；
5. 观察系统电池统计中 PayLedger 占比（预期可忽略）。

- [ ] **步骤 6.4：清理与变更日志**

- 删除 `_tmp/pay-ledger/` 中 gradle zip（保留解压目录便于复用或一并删除）；
- 复制 `changelogs/_template/CHANGELOG.md` → `changelogs/pay-ledger/CHANGELOG.md`，`Unreleased` 增加一条：
```markdown
### Added
- MVP：监听微信/支付宝付款通知自动记账，本地 Room 存储（无网络权限），Compose 列表+今日/本月汇总，单条备注/删除，debug 构建内置最近通知预览页（校准解析规则用）
```
- 在 `changelogs/README.md` 索引中新增 `pay-ledger` 行并更新最近记录日期。

- [ ] **步骤 6.5：Commit**

```bash
cd /e/dev/workspace/deepseekharness
git -C pay-ledger add -A && git -C pay-ledger commit -m "chore: MVP 收尾（验证通过、变更日志）"
git add changelogs/ && git commit -m "chore: 新增 pay-ledger 项目变更日志"
```
注意：`changelogs/` 属于工作区仓库（若工作区根目录未初始化 git，则只改文件不提交）。

---

## 自检记录

- **规格覆盖度**：§2 范围→任务 2-5；§4 架构→任务 1/3/4；§5 数据模型→任务 3；§6 监听/解析/去重→任务 2/4；§7 UI→任务 5；§8 安全耗电内存→任务 1（无 INTERNET、minify）+任务 4（无轮询）+任务 5（LIMIT 500）；§9 错误处理→任务 4（try/catch）+任务 5（ON_RESUME 重检测）；§10 测试→任务 2（Parser 18 例含 DedupWindow 边界）；§11 构建→任务 1/6；§12 结构→文件结构与实际一致。无遗漏。
- **占位符扫描**：全部步骤含实际代码/命令，无 TODO/待定。
- **类型一致性**：`ParsedPayment(channel, amountCents, counterparty, summary)` 在任务 2 定义、任务 4 消费；`DedupWindow.of` 返回 `LongRange`，Service 用 `first/last` 传 DAO 的 `BETWEEN`；`DayGroup(date, label, records, totalCents)` 定义与 UI 消费一致；Dao 方法名（observeAll/observeSumBetween/findDuplicate/updateNote/delete）在任务 4/5 中一致引用。
