// template-app-android/settings.gradle.kts
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

rootProject.name = "template-app-android"

// 本地 SDK 模块（monorepo composite build）
// dependencySubstitution 显式声明：将 space.securechat:sdk 替换为 ../sdk-android 里的 :sdk 子项目
includeBuild("../sdk-android") {
    dependencySubstitution {
        substitute(module("space.securechat:sdk:1.0.0")).using(project(":sdk"))
    }
}

include(":app")

