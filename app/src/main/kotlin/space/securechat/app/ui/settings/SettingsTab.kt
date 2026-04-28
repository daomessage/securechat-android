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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    val clipboard = LocalClipboardManager.current

    var showMnemonicDialog by remember { mutableStateOf(false) }
    var pushEnabled by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var pushError by remember { mutableStateOf<String?>(null) }

    // 备份告警：7 天未导出 → 红点
    val prefs = remember { context.getSharedPreferences("securechat", android.content.Context.MODE_PRIVATE) }
    val lastExport = remember { prefs.getLong("last_export_ts", 0L) }
    val backupOverdue = (System.currentTimeMillis() - lastExport) > 7 * 24 * 3600 * 1000L

    // 存储估算
    var storageEstimate by remember { mutableStateOf<space.securechat.sdk.http.StorageEstimate?>(null) }
    LaunchedEffect(Unit) {
        try { storageEstimate = client.getStorageEstimate() } catch (_: Exception) {}
    }

    // 请求 POST_NOTIFICATIONS（API 33+），无需权限的旧设备直接 success
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    client.push.register(token)
                    pushEnabled = true
                } catch (e: Exception) {
                    pushError = e.message ?: "推送注册失败"
                }
            }
        } else {
            pushError = "通知权限被拒绝"
        }
    }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("设置", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

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
                    Text(userInfo.nickname.ifEmpty { "加载中..." }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("@${userInfo.aliasId}", color = TextMuted, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔒", fontSize = 10.sp)
                        Text("端到端加密", color = Success, fontSize = 11.sp)
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
                    label = "靓号商城",
                    subtitle = "获取一个好记的别名",
                    onClick = { appViewModel.setRoute(AppRoute.VANITY_SHOP) }
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Notifications,
                    label = "推送通知",
                    subtitle = pushError ?: if (pushEnabled) "已开启" else "点击开启",
                    onClick = {
                        pushError = null
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Android 12 及以下：直接取 token 注册
                            scope.launch {
                                try {
                                    val token = FirebaseMessaging.getInstance().token.await()
                                    client.push.register(token)
                                    pushEnabled = true
                                } catch (e: Exception) {
                                    pushError = e.message ?: "推送注册失败"
                                }
                            }
                        }
                    }
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.FileDownload,
                    label = if (backupOverdue) "导出聊天记录 🔴" else "导出聊天记录",
                    subtitle = if (backupOverdue) "已 7 天以上未备份!" else "保存为 NDJSON 备份",
                    onClick = {
                        scope.launch {
                            try {
                                val ndjson = client.exportAllConversations()
                                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                    .format(java.util.Date())
                                val fileName = "securechat_export_$ts.ndjson"
                                // 走 app cache（无需权限，用 FileProvider 暴露给系统分享）
                                val outFile = java.io.File(context.cacheDir, fileName)
                                outFile.writeText(ndjson, Charsets.UTF_8)

                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, fileName)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享备份"))
                                prefs.edit().putLong("last_export_ts", System.currentTimeMillis()).apply()
                            } catch (e: Exception) {
                                android.util.Log.e("Settings", "export failed", e)
                            }
                        }
                    }
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Shield,
                    label = "查看助记词",
                    subtitle = "备份你的密钥短语",
                    onClick = { showMnemonicDialog = true }
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Storage,
                    label = "存储",
                    subtitle = storageEstimate?.let {
                        val usedMB = it.used_bytes / 1024f / 1024f
                        val totalMB = it.quota_bytes / 1024f / 1024f
                        if (totalMB > 0) "%.1f MB / %.1f MB".format(usedMB, totalMB)
                        else "已用 %.1f MB".format(usedMB)
                    } ?: "加载中…",
                    onClick = {}
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "推送和来电设置",
                    subtitle = "重新打开权限引导",
                    onClick = {
                        // 重置引导标记后重启 MainActivity,让 BackgroundPermissionsDialog 重新弹
                        space.securechat.app.util.BackgroundPermissionsHelper.resetGuideShown(context)
                        // 简单粗暴重启 Activity 让 Compose 重读 shouldShowGuide
                        val intent = (context as? android.app.Activity)?.intent
                        if (intent != null) {
                            (context as android.app.Activity).finish()
                            context.startActivity(intent)
                        }
                    }
                )
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Info,
                    label = "关于 DAO Message",
                    subtitle = "v1.0.0 · 基于 ECDH + AES-GCM 加密",
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
                Text("退出登录", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "DAO Message Android v1.0.0\n你的密钥永远不会离开本设备。",
            color = TextMuted, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    // 查看助记词弹窗（确认后从 SDK 读取并展示）
    var mnemonicText by remember { mutableStateOf<String?>(null) }
    var mnemonicConfirmed by remember { mutableStateOf(false) }
    if (showMnemonicDialog) {
        // 敏感屏：禁截屏/录屏（随 dialog 生命周期）
        space.securechat.app.ui.components.SecureScreen()
        if (!mnemonicConfirmed) {
            AlertDialog(
                onDismissRequest = { showMnemonicDialog = false },
                containerColor = Surface1,
                title = { Text("安全提示", color = TextPrimary) },
                text = {
                    Text(
                        "你的助记词可以完全控制账号。永远不要分享给任何人,只在私密环境查看。",
                        color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mnemonicConfirmed = true
                            scope.launch {
                                mnemonicText = try { client.getMnemonic() } catch (_: Exception) { null }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Danger)
                    ) { Text("显示助记词") }
                },
                dismissButton = {
                    TextButton(onClick = { showMnemonicDialog = false }) { Text("取消", color = TextMuted) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    showMnemonicDialog = false; mnemonicConfirmed = false; mnemonicText = null
                },
                containerColor = Surface1,
                title = { Text("助记词", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (mnemonicText != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    mnemonicText ?: "",
                                    color = BlueAccent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            TextButton(onClick = {
                                val m = mnemonicText ?: return@TextButton
                                // Android 13+ 用 ClipDescription.EXTRA_IS_SENSITIVE 让系统在预览时打码
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("mnemonic", m)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    clip.description.extras = android.os.PersistableBundle().apply {
                                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                                    }
                                }
                                cm.setPrimaryClip(clip)
                                // 60 秒后自动清空剪贴板
                                scope.launch {
                                    kotlinx.coroutines.delay(60_000)
                                    try {
                                        val current = cm.primaryClip
                                        if (current != null && current.itemCount > 0 &&
                                            current.getItemAt(0).text?.toString() == m) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                cm.clearPrimaryClip()
                                            } else {
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }) { Text("复制 (60 秒后自动清空)", color = BlueAccent, fontSize = 13.sp) }
                        } else {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BlueAccent)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showMnemonicDialog = false; mnemonicConfirmed = false; mnemonicText = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                    ) { Text("完成") }
                }
            )
        }
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
