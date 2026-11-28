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
plugins {
    // v09 2.5：Robolectric 的 SDK 36 沙箱要求 Java 21，本机最高 JDK 20，
    // 启用工具链自动供给（JDK 装到 ~/.gradle/jdks，不改系统 PATH）
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pay-ledger"
include(":app")
