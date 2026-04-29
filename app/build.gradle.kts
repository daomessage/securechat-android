plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "space.securechat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "space.securechat.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // 向量图支持
        vectorDrawables { useSupportLibrary = true }

        // P1-3 设置页版本信息(对齐 PWA SettingsTab.tsx 的 __PWA_VERSION__/__GIT_COMMIT__/__BUILD_TIME__):
        // 用 buildConfigField 把 git commit + 构建时间嵌入到 BuildConfig.kt,运行时读
        // 注意:git rev-parse 失败(比如不在 git 仓库中构建)时 fallback 'unknown'
        val gitCommit = try {
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
            }.standardOutput.asText.get().trim()
        } catch (_: Exception) { "unknown" }
        val gitBranch = try {
            providers.exec {
                commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            }.standardOutput.asText.get().trim()
        } catch (_: Exception) { "unknown" }
        // 用 shell 命令读时间(避开 Gradle 沙箱对 java.* 的限制)
        val buildTime = try {
            providers.exec {
                commandLine("date", "+%Y-%m-%d %H:%M:%S")
            }.standardOutput.asText.get().trim()
        } catch (_: Exception) { "unknown" }
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true   // 启用 BuildConfig 类生成,默认 AGP 8+ 关掉了
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Release 签名 · 通过环境变量提供 keystore, 避免泄露
    // 设置:
    //   export DAOMESSAGE_KEYSTORE=/path/to/release.jks
    //   export DAOMESSAGE_KEYSTORE_PASSWORD=xxx
    //   export DAOMESSAGE_KEY_ALIAS=daomessage
    //   export DAOMESSAGE_KEY_PASSWORD=xxx
    // 生成 keystore:
    //   keytool -genkey -v -keystore release.jks -alias daomessage -keyalg RSA -keysize 4096 -validity 10000
    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("DAOMESSAGE_KEYSTORE")
            if (keystoreFile != null && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("DAOMESSAGE_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("DAOMESSAGE_KEY_ALIAS") ?: "daomessage"
                keyPassword = System.getenv("DAOMESSAGE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 仅当 keystore 已配置时启用签名 (本地开发跑 debug build 不受影响)
            if (System.getenv("DAOMESSAGE_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // BouncyCastle 三个 jar 都带了相同的 OSGI 元数据，AGP 合并冲突，统一忽略
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    // SecureChat SDK（本地 monorepo 模块）
    implementation("space.securechat:sdk:1.0.0")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    // 图片
    implementation(libs.coil.compose)

    // FCM
    implementation(libs.firebase.messaging)

    // QR：core 用于生成（纯 Java），zxing-android-embedded 用于扫描
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // Coroutines x Google Play Services（FirebaseMessaging.token.await）
    implementation(libs.coroutines.play.services)

    // WebRTC（通话音视频）
    implementation(libs.stream.webrtc.android)
    implementation(libs.stream.webrtc.android.ui)
}
