plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.zhaoyingqi.payledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhaoyingqi.payledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.5.7"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
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
