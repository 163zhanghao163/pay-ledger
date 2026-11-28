import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

// v09 D3：签名信息外置到仓库根 keystore.properties（已 gitignore）。
// 文件存在才启用 release 签名；不存在时 release 出未签名包，本地无密钥也能构建。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.zhaoyingqi.payledger"
    // v09 2.3：core 1.18 / activity 1.12 的 AAR 元数据要求 compileSdk >= 36（AGP 8.13.2 上限即 36）
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zhaoyingqi.payledger"
        minSdk = 26
        // v09 2.5：targetSdk 跟随 compileSdk 升到 36（通知权限、无障碍、监听行为需真机回归）
        targetSdk = 36
        versionCode = 16
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    detekt {
        // v09 D1：空规则集 + 按需开启（complexity/performance/exception/coroutines/formatting），
        // 存量问题用 detekt-baseline.xml 收口，基线只减不增
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("$rootDir/app/detekt-baseline.xml")
    }
    lint {
        // 存量问题已基线化（lint-baseline.xml），只拦新增问题；基线只减不增
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets {
        // Room 迁移测试需要读取各版本 schema JSON：
        // test 供 instrumented test 使用，debug 供 Robolectric 单测（AGP 不合并 test sourceSet 的 assets）。
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
    testOptions {
        unitTests {
            // Robolectric 使用合并后的 manifest 与资源（ packageName / schema assets）。
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    // Robolectric SDK 36 沙箱要求 Java 21（compile 链保持 JDK 20/目标 17 不变）
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) },
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    // BOM 2025.x 起 material3 不再传递依赖 icons，需显式引入（项目只用 core 集合 15 个图标）
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.coroutines.test)
}
