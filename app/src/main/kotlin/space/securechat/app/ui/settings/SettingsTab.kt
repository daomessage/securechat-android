package space.securechat.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * SettingsTab — 对标 Web: SettingsTab.tsx
 *
 * 显示：用户信息卡片 / 靓号 / 推送权限 / 安全码 / 登出
 */
@Composable
fun SettingsTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    val userInfo by appViewModel.userInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMnemonicDialog by remember { mutableStateOf(false) }
    var pushEnabled by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // 用户信息卡
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(BlueAccent.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userInfo.nickname.take(2).uppercase().ifEmpty { "?" },
                        color = BlueAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(userInfo.nickname.ifEmpty { "Loading..." }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("@${userInfo.aliasId}", color = TextMuted, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔒", fontSize = 10.sp)
                        Text("End-to-end encrypted", color = Success, fontSize = 11.sp)
                    }
                }
            }
        }

        // 设置项列表
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingRow(
                    icon = Icons.Default.Tag,
                    label = "Vanity ID Shop",
                    subtitle = "Get a memorable alias",
                    onClick = { /* 跳转靓号商店（设置入口）TODO */ }
                )
                HorizontalDivider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Notifications,
                    label = "Push Notifications",
                    subtitle = if (pushEnabled) "Enabled" else "Tap to enable",
                    onClick = {
                        scope.launch {
                            pushEnabled = true
                            // TODO: 触发 FCM 权限请求 + SDK push.register()
                        }
                    }
                )
                HorizontalDivider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Shield,
                    label = "View Recovery Phrase",
                    subtitle = "Backup your secret words",
                    onClick = { showMnemonicDialog = true }
                )
                HorizontalDivider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Info,
                    label = "About SecureChat",
                    subtitle = "v1.0.0 · Powered by ECDH + AES-GCM",
                    onClick = {}
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 登出
        Button(
            onClick = {
                isLoggingOut = true
                scope.launch {
                    try {
                        client.logout()
                        appViewModel.setRoute(AppRoute.WELCOME)
                    } finally { isLoggingOut = false }
                }
            },
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Danger.copy(0.15f), contentColor = Danger)
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(color = Danger, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "SecureChat Android v1.0.0\nYour keys never leave this device.",
            color = TextMuted, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    // 查看助记词弹窗（提示：只显示提示，实际助记词由用户安全存储）
    if (showMnemonicDialog) {
        AlertDialog(
            onDismissRequest = { showMnemonicDialog = false },
            containerColor = Surface1,
            title = { Text("Recovery Phrase", color = TextPrimary) },
            text = {
                Text(
                    "For security, your recovery phrase was only shown once during setup. " +
                    "If you didn't back it up, you cannot recover your account if you lose this device.",
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showMnemonicDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                ) { Text("I understand") }
            }
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BlueAccent.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}
