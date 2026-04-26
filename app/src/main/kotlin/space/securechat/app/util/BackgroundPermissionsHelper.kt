package space.securechat.app.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * BackgroundPermissionsHelper — Q3-F + Q3-G
 *
 * Android 后台保活组合拳:
 * 1. 电池白名单 (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 *    系统级 — 即使应用被白名单了,Doze 还是会限制 — 但能避开 App Standby Bucket Restricted 档
 * 2. 国产 ROM 自启动开关 (设置页 deep-link)
 *    小米/华为/OPPO/vivo 都有自家的"自启动管理",FCM 推送也得开
 *
 * 用法:
 *   if (BackgroundPermissionsHelper.shouldShowGuide(ctx)) { 弹 dialog }
 *   .openBatteryOptimizationSettings(ctx)
 *   .openVendorAutoStartSettings(ctx)  // 自动判别厂商
 *   .markGuideShown(ctx)               // 用户看过一次后不再弹
 */
object BackgroundPermissionsHelper {

    private const val PREF = "securechat_bg"
    private const val KEY_GUIDE_SHOWN = "bg_guide_shown_v1"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 是否应该弹引导 dialog:仅当 (没在电池白名单 OR 国产 ROM) AND 还没弹过 */
    fun shouldShowGuide(ctx: Context): Boolean {
        if (prefs(ctx).getBoolean(KEY_GUIDE_SHOWN, false)) return false
        return !isIgnoringBatteryOptimizations(ctx) || isChineseVendor()
    }

    fun markGuideShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    }

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

    /**
     * 跳厂商自启动管理页
     * 各家厂商 Activity 名都不一样,挨个尝试,任一成功就返回
     * 失败回退:打开应用详情页让用户自己找
     */
    fun openVendorAutoStartSettings(ctx: Context) {
        val candidates = listOf(
            // MIUI
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
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(fallback) }
    }

    private data class ComponentIntent(val pkg: String, val cls: String)
}
