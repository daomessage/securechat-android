package space.securechat.app.ui.components

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.theme.*
import space.securechat.app.util.BackgroundPermissionsHelper

/**
 * BackgroundPermissionsDialog — Q3-F + Q3-G
 *
 * 进入 MAIN 路由后,如果设备需要(没在电池白名单 OR 国产 ROM),弹一次引导
 * 用户处理过(关闭 / 完成)后写 SharedPreferences,不再骚扰
 *
 * UI 风格对齐 GOAWAY dialog (DarkBg + BlueAccent)
 */
@Composable
fun BackgroundPermissionsDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(BackgroundPermissionsHelper.shouldShowGuide(context)) }
    if (!visible) return

    val needBattery = !BackgroundPermissionsHelper.isIgnoringBatteryOptimizations(context)
    val isChinese = BackgroundPermissionsHelper.isChineseVendor()
    val brand = Build.MANUFACTURER

    AlertDialog(
        onDismissRequest = {
            BackgroundPermissionsHelper.markGuideShown(context)
            visible = false
        },
        containerColor = Surface1,
        title = {
            Text(
                "保持后台收消息",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    "为了让你切到后台时也能及时收到加密消息和来电,需要做下面 ${if (needBattery && isChinese) 2 else 1} 步:",
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))

                if (needBattery) {
                    PermissionRow(
                        index = "1",
                        title = "允许后台运行",
                        subtitle = "把 DAO Message 加入电池优化白名单",
                        actionLabel = "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (isChinese) {
                    PermissionRow(
                        index = if (needBattery) "2" else "1",
                        title = "开启自启动",
                        subtitle = "$brand 系统需要在系统设置里开启",
                        actionLabel = "去设置",
                        onClick = {
                            BackgroundPermissionsHelper.openVendorAutoStartSettings(context)
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "🔒 这些设置都在你的手机上完成,不会上传任何数据",
                    color = TextMuted, fontSize = 11.sp
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
            ) { Text("我知道了") }
        }
    )
}

@Composable
private fun PermissionRow(
    index: String,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .padding(end = 0.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                index, color = BlueAccent,
                fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        TextButton(onClick = onClick) {
            Text(actionLabel, color = BlueAccent, fontSize = 13.sp)
        }
    }
}
