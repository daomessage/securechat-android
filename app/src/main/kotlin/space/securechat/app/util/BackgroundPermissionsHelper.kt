package space.securechat.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * BackgroundPermissionsHelper — Q3-F + Q3-G + 来电锁屏拉起(2026-04-27 修)
 *
 * Android 后台保活 + 锁屏来电组合拳:
 *
 * 1. 电池白名单 (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 *    系统级 — 即使应用被白名单了,Doze 还是会限制 — 但能避开 App Standby Bucket Restricted 档
 *
 * 2. 国产 ROM 自启动开关 (设置页 deep-link)
 *    小米/华为/OPPO/vivo 都有自家的"自启动管理",FCM 推送也得开
 *
 * 3. 全屏 Intent 权限 (Android 14+,USE_FULL_SCREEN_INTENT)
 *    用户不在系统「特殊权限 → 全屏通知」里手动开,setFullScreenIntent 被静默降级成普通通知
 *    后果:锁屏不亮屏、不响铃,只能等用户主动解锁后看通知
 *
 * 4. MIUI「其他权限」页(锁屏显示 / 后台弹出界面 / 显示悬浮窗)
 *    MIUI 默认对所有非白名单 app 都把这两项设为「拒绝」,
 *    即使 USE_FULL_SCREEN_INTENT 已经开了,缺这两项还是拉不起锁屏来电 UI
 *
 * 用法:
 *   if (BackgroundPermissionsHelper.shouldShowGuide(ctx)) { 弹 dialog }
 *   .openBatteryOptimizationSettings(ctx)
 *   .openVendorAutoStartSettings(ctx)        // 自启动
 *   .openFullScreenIntentSettings(ctx)       // 全屏通知(Android 14+)
 *   .openMiuiOtherPermissions(ctx)           // MIUI 其他权限(锁屏显示 / 后台弹出)
 *   .markGuideShown(ctx)                     // 用户看过一次后不再弹
 */
object BackgroundPermissionsHelper {

    private const val PREF = "securechat_bg"
    private const val KEY_GUIDE_SHOWN = "bg_guide_shown_v2"  // v2:权限项扩充,v1 旧用户重新弹一次

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * 是否应该弹引导 dialog:
     * - 没看过引导(v2)
     * - 且 任一权限缺失 OR 是国产 ROM(国产 ROM 必引导,因为锁屏权限默认全拒)
     */
    fun shouldShowGuide(ctx: Context): Boolean {
        if (prefs(ctx).getBoolean(KEY_GUIDE_SHOWN, false)) return false
        return !isIgnoringBatteryOptimizations(ctx) ||
                !canUseFullScreenIntent(ctx) ||
                isChineseVendor()
    }

    fun markGuideShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    }

    /** 用户每次想再看引导(从设置入口手动唤起)用这个 reset */
    fun resetGuideShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_GUIDE_SHOWN, false).apply()
    }

    // ─── 1. 电池白名单 ────────────────────────────────────────

    /** 是否已经在电池优化白名单里 */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * 跳系统电池白名单设置页
     * 优先用 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS(直接弹授权框)
     * 如果失败(部分厂商 ROM 屏蔽了),降级到通用电池设置页
     */
    fun openBatteryOptimizationSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pkg = ctx.packageName
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(direct)
        } catch (_: Exception) {
            runCatching { ctx.startActivity(fallback) }
        }
    }

    // ─── 2. 国产 ROM 检测 ─────────────────────────────────────

    /** 当前设备是否是常见的国产 ROM(自启动管理需要单独开) */
    fun isChineseVendor(): Boolean {
        val brand = Build.BRAND.lowercase()
        val mfr = Build.MANUFACTURER.lowercase()
        val knownChineseBrands = setOf(
            "xiaomi", "redmi", "poco",            // MIUI / HyperOS
            "huawei", "honor",                     // EMUI / HarmonyOS / MagicOS
            "oppo", "realme", "oneplus",           // ColorOS / RealmeUI
            "vivo", "iqoo",                        // OriginOS / FuntouchOS
            "meizu",                               // Flyme
            "samsung",                             // OneUI(国行也限制)
        )
        return brand in knownChineseBrands || mfr in knownChineseBrands
    }

    /** 当前设备是否是小米/红米/POCO(MIUI / HyperOS) */
    fun isMiui(): Boolean {
        val brand = Build.BRAND.lowercase()
        val mfr = Build.MANUFACTURER.lowercase()
        return brand in setOf("xiaomi", "redmi", "poco") ||
                mfr in setOf("xiaomi", "redmi", "poco")
    }

    // ─── 3. 自启动管理 ────────────────────────────────────────

    /**
     * 跳厂商自启动管理页
     * 各家厂商 Activity 名都不一样,挨个尝试,任一成功就返回
     * 失败回退:打开应用详情页让用户自己找
     */
    fun openVendorAutoStartSettings(ctx: Context) {
        val candidates = listOf(
            // MIUI / HyperOS
            ComponentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // EMUI / HarmonyOS
            ComponentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // ColorOS (OPPO / OnePlus / Realme)
            ComponentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // OriginOS / FuntouchOS (vivo / iQOO)
            ComponentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Samsung
            ComponentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        for (c in candidates) {
            val intent = Intent().apply {
                setClassName(c.pkg, c.cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(intent)
                return
            } catch (_: Exception) { /* 试下一个 */ }
        }
        // 都不行就打开应用详情页
        openAppDetailsSettings(ctx)
    }

    // ─── 4. 全屏 Intent 权限(Android 14+) ───────────────────

    /**
     * 是否能用 setFullScreenIntent 拉起锁屏来电 UI
     *
     * Android 14 (API 34) 起,即使 Manifest 里声明了 USE_FULL_SCREEN_INTENT,
     * 用户也必须在系统「特殊权限 → 全屏通知」里手动开,不开就降级成普通通知。
     * Android 13 及以下永远返回 true(权限只声明一次即可)。
     */
    fun canUseFullScreenIntent(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    /**
     * 跳系统「全屏通知」权限设置页(Android 14+)
     * Android 13 及以下没这个开关,直接跳应用详情页
     */
    fun openFullScreenIntentSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(intent)
                return
            } catch (_: Exception) { /* 降级 */ }
        }
        openAppDetailsSettings(ctx)
    }

    // ─── 5. MIUI 其他权限(锁屏显示 / 后台弹出 / 悬浮窗) ─────

    /**
     * 跳 MIUI 「其他权限」页面 — 包含「锁屏显示」、「后台弹出界面」、「显示悬浮窗」
     * 这是 MIUI 来电锁屏拉起的关键,默认这两项都是「拒绝」
     *
     * MIUI 内部叫 PermissionsEditorActivity,需要传 extra_pkgname 指定 app 包名
     * Activity 不可导出,但可以通过 com.miui.securitycenter 包跳转
     */
    fun openMiuiOtherPermissions(ctx: Context) {
        if (!isMiui()) {
            openAppDetailsSettings(ctx)
            return
        }
        // MIUI 12+ / HyperOS 路径
        val candidates = listOf(
            // 主流路径(MIUI 12 / 13 / 14 / HyperOS)
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 老 MIUI 路径
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                putExtra("extra_pkgname", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        for (intent in candidates) {
            try {
                ctx.startActivity(intent)
                return
            } catch (_: Exception) { /* 试下一个 */ }
        }
        // 都不行就打开应用详情页
        openAppDetailsSettings(ctx)
    }

    // ─── 通用降级:跳应用详情 ────────────────────────────────

    fun openAppDetailsSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
    }

    // ─── 通知频道设置(锁屏可见性 / 响铃 / 亮屏) ─────────────

    /**
     * 跳到指定通知频道的系统设置页 — 用户可在此调整锁屏可见性 / 响铃 / 亮屏 / 横幅
     * 主要用途:让用户给「来电」频道开亮屏提醒,因为 MIUI 默认对部分 app 关闭
     */
    fun openNotificationChannelSettings(ctx: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAppDetailsSettings(ctx)
            return
        }
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(ctx)
        }
    }

    private data class ComponentIntent(val pkg: String, val cls: String)
}
