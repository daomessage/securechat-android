# SecureChat ProGuard Rules
# ═══════════════════════════════════════════════════════════════

# 保留我们自己的代码（必须）
-keep class space.securechat.** { *; }
-keep class space.securechat.app.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Room
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-dontwarn androidx.room.**

# Coil
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# 保留 Lifecycle 相关类
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

# 保留 ViewModel
-keep class androidx.lifecycle.ViewModel { *; }

# 保留活动和服务
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# 保留 AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# 保留注解
-keep class * extends java.lang.annotation.Annotation { *; }

# 移除日志记录（可选，生产环境）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 优化设置
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses 'space.securechat.app.obfuscated'

# 强制保留 main 方法
-keepclasseswithmembers class * {
    public static void main(java.lang.String[]);
}

# 保留 R 资源类
-keepclassmembers class **.R$* {
    public static <fields>;
}
