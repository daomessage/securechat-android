package space.securechat.app.ui.components

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import space.securechat.app.push.FcmService
import space.securechat.app.service.CallForegroundService
import space.securechat.app.ui.theme.*
import space.securechat.app.util.BackgroundPermissionsHelper

/**
 * BackgroundPermissionsDialog — Q3-F + Q3-G + 来电锁屏拉起(2026-04-27 修)
 *
 * 进入 MAIN 路由后,如果设备需要(任一权限缺失 OR 国产 ROM),弹引导
 * 用户处理过(关闭 / 完成)后写 SharedPreferences,不再骚扰
 *
 * 检测项(每项都有独立的状态指示 + 跳转按钮):
 *   1. 电池白名单(允许后台运行)
 *   2. 全屏通知权限(Android 14+)
 *   3. MIUI 其他权限(锁屏显示 / 后台弹出 / 悬浮窗)— 仅 MIUI
 *   4. 自启动权限 — 仅国产 ROM
 *   5. 来电频道亮屏提醒 — 仅国产 ROM
 *
 * 关键:用户从设置页返回 App 时,Lifecycle.RESUMED 会触发,
 * 我们重新读取每项权限状态,UI 自动刷新打勾。这样用户能看到
 * 「这一步真的开了」,激励继续开下一项。
 */
@Composable
fun BackgroundPermissionsDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(BackgroundPermissionsHelper.shouldShowGuide(context)) }
    if (!visible) return

    // 每次 RESUMED 重新读权限状态,从设置页返回后 UI 自动更新
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val battery = remember(permTick) { BackgroundPermissionsHelper.isIgnoringBatteryOptimizations(context) }
    val fullScreen = remember(permTick) { BackgroundPermissionsHelper.canUseFullScreenIntent(context) }
    val isMiui = remember { BackgroundPermissionsHelper.isMiui() }
    val isChinese = remember { BackgroundPermissionsHelper.isChineseVendor() }
    val brand = Build.MANUFACTURER

    AlertDialog(
        onDismissRequest = {
            BackgroundPermissionsHelper.markGuideShown(context)
            visible = false
        },
        containerColor = Surface1,
        title = {
            Text(
                "📨 让消息和来电正常推送",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "为了让你切到后台、锁屏时也能收到加密消息和来电,需要做以下几步:",
                    color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(14.dp))

                var idx = 1

                // 1. 电池白名单
                PermissionRow(
                    index = idx.toString(),
                    granted = battery,
                    title = "允许后台运行",
                    subtitle = "把 DAO Message 加入电池优化白名单,系统不会休眠后台",
                    actionLabel = if (battery) "已允许" else "去设置",
                    onClick = {
                        BackgroundPermissionsHelper.openBatteryOptimizationSettings(context)
                    }
                )
                idx++
                Spacer(Modifier.height(8.dp))

                // 2. 全屏通知权限(Android 14+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PermissionRow(
                        index = idx.toString(),
                        granted = fullScreen,
                        title = "允许全屏来电通知",
                        subtitle = "锁屏时来电才能直接拉起来电界面(Android 14+)",
                        actionLabel = if (fullScreen) "已允许" else "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openFullScreenIntentSettings(context)
                        }
                    )
                    idx++
                    Spacer(Modifier.height(8.dp))
                }

                // 3. MIUI「其他权限」(锁屏显示 + 后台弹出)
                // ⚠️ MIUI 没暴露 API 读这些开关状态,无法显示 ✓,只能让用户每次手动确认
                if (isMiui) {
                    PermissionRow(
                        index = idx.toString(),
                        granted = false,
                        title = "MIUI 其他权限",
                        subtitle = "在该页面把「锁屏显示」+「后台弹出界面」改为允许",
                        actionLabel = "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openMiuiOtherPermissions(context)
                        }
                    )
                    idx++
                    Spacer(Modifier.height(8.dp))
                }

                // 4. 国产 ROM 自启动(独立设置项,通常和 3 在不同页面)
                if (isChinese) {
                    PermissionRow(
                        index = idx.toString(),
                        granted = false,
                        title = "开启自启动",
                        subtitle = "在「自启动管理」里把 SecureChat 开关打开",
                        actionLabel = "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openVendorAutoStartSettings(context)
                        }
                    )
                    idx++
                    Spacer(Modifier.height(8.dp))
                }

                // 5. 来电频道铃声 — 这是关键!图 4 显示「声音=无」就是这里没设
                if (isChinese) {
                    PermissionRow(
                        index = idx.toString(),
                        granted = false,
                        title = "来电铃声",
                        subtitle = "在该页面把「声音」改成任意铃声,否则来电时静音",
                        actionLabel = "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openNotificationChannelSettings(
                                context, CallForegroundService.CHANNEL_ID
                            )
                        }
                    )
                    idx++
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "🔒 这些设置都在你的手机上完成,DAO Message 不会上传任何数据。\n" +
                    "💡 微信 / 钉钉 / Telegram 等所有 IM 在国产手机上都需要这些权限。",
                    color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    BackgroundPermissionsHelper.markGuideShown(context)
                    visible = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text("我都设好了") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    BackgroundPermissionsHelper.markGuideShown(context)
                    visible = false
                }
            ) {
                Text("以后再说", color = TextMuted)
            }
        }
    )
}

@Composable
private fun PermissionRow(
    index: String,
    granted: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // 已允许显示 ✓,未允许显示数字
            if (granted) {
                Text("✓", color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            } else {
                Text(index, color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (granted) TextMuted else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(subtitle, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp)
        }
        TextButton(
            onClick = onClick,
            enabled = !granted
        ) {
            Text(
                actionLabel,
                color = if (granted) TextMuted else BlueAccent,
                fontSize = 13.sp
            )
        }
    }
}
