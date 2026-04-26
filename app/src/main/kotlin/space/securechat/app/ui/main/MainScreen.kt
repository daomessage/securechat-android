package space.securechat.app.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.securechat.app.viewmodel.*
import space.securechat.app.ui.theme.*
import space.securechat.app.ui.messages.*
import space.securechat.app.ui.channels.*
import space.securechat.app.ui.contacts.*
import space.securechat.app.ui.settings.*
import space.securechat.app.ui.chat.ChatScreen
import space.securechat.app.ui.components.NetworkBanner
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import space.securechat.sdk.SecureChatClient

/**
 * MainScreen — 对标 Web: MainLayout.tsx + ChatWindow 叠加
 *
 * 四个 Tab：Messages / Channels / Contacts / Settings
 * 点击会话条目 → 推入 ChatScreen（对标 activeChatId 逻辑）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(appViewModel: AppViewModel) {
    val activeTab by appViewModel.activeTab.collectAsStateWithLifecycle()
    val activeChatId by appViewModel.activeChatId.collectAsStateWithLifecycle()
    val activeChannelId by appViewModel.activeChannelId.collectAsStateWithLifecycle()
    val pendingCount by appViewModel.pendingRequestCount.collectAsStateWithLifecycle()
    val unreadCounts by appViewModel.unreadCounts.collectAsStateWithLifecycle()
    // P2-3: 订阅网络状态, 驱动 NetworkBanner 显示 / 隐藏
    val networkState by SecureChatClient.getInstance().networkState.collectAsStateWithLifecycle()

    // 同步好友（进入 main 后）
    val client = SecureChatClient.getInstance()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        try { client.contacts.syncFriends() } catch (_: Exception) {}
    }

    // 推送权限引导（首次进 MainScreen 弹一次）
    val prefs = remember { context.getSharedPreferences("securechat", android.content.Context.MODE_PRIVATE) }
    var showPushPrompt by remember { mutableStateOf(!prefs.getBoolean("push_prompted", false)) }
    if (showPushPrompt && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            prefs.edit().putBoolean("push_prompted", true).apply()
            showPushPrompt = false
            if (granted) {
                kotlinx.coroutines.MainScope().launch {
                    try {
                        val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                        client.push.register(token)
                    } catch (_: Exception) {}
                }
            }
        }
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putBoolean("push_prompted", true).apply()
                showPushPrompt = false
            },
            containerColor = space.securechat.app.ui.theme.Surface1,
            title = { Text("开启通知", color = space.securechat.app.ui.theme.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "开启通知，及时收到好友的消息。",
                    color = space.securechat.app.ui.theme.TextMuted, fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    colors = ButtonDefaults.buttonColors(containerColor = space.securechat.app.ui.theme.BlueAccent)
                ) { Text("开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("push_prompted", true).apply()
                    showPushPrompt = false
                }) { Text("稍后", color = space.securechat.app.ui.theme.TextMuted) }
            }
        )
    }

    // ChatScreen 覆盖（animatedVisibility）
    if (activeChatId != null) {
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            ChatScreen(
                convId = activeChatId!!,
                appViewModel = appViewModel,
                onBack = { appViewModel.setActiveChatId(null) }
            )
        }
        return
    }

    // 频道详情覆盖
    if (activeChannelId != null) {
        ChannelDetailScreen(
            channelId = activeChannelId!!,
            onBack = { appViewModel.setActiveChannelId(null) }
        )
        return
    }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            // design tokens · TabBar 高 56dp 是 web/iOS 规范, 但 Material 3 NavigationBar
            // 用 56dp 会裁掉 selected indicator pill (24dp) + icon + label 三层堆叠.
            // 这里用 Material 3 默认 80dp, 让选中态图标/文字都能完整显示.
            NavigationBar(
                containerColor = Surface1,
                tonalElevation = 0.dp,
            ) {
                TabItem.entries.forEach { tab ->
                    val isSelected = activeTab == tab.mainTab
                    val badge = when (tab.mainTab) {
                        MainTab.CONTACTS -> pendingCount
                        MainTab.MESSAGES -> unreadCounts.values.sum()
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { appViewModel.setActiveTab(tab.mainTab) },
                        icon = {
                            BadgedBox(badge = {
                                if (badge > 0) Badge { Text("$badge", fontSize = 10.sp) }
                            }) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = { Text(tab.label, fontSize = TextSize.xs) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandPrimary,
                            selectedTextColor = BrandPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = BrandPrimary.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // P2-3 对齐 iOS/PWA: 顶部网络状态横幅
            NetworkBanner(state = networkState)

            Box(Modifier.weight(1f)) {
                when (activeTab) {
                    MainTab.MESSAGES  -> MessagesTab(appViewModel)
                    MainTab.CHANNELS  -> ChannelsTab(appViewModel)
                    MainTab.CONTACTS  -> ContactsTab(appViewModel)
                    MainTab.SETTINGS  -> SettingsTab(appViewModel)
                }
            }
        }
    }
}

// ── Tab 元数据 ─────────────────────────────────────────────────────────────

private enum class TabItem(
    val mainTab: MainTab,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    MESSAGES (MainTab.MESSAGES,  "消息",      Icons.Default.ChatBubbleOutline, Icons.Default.ChatBubble),
    CHANNELS (MainTab.CHANNELS,  "频道",      Icons.Default.Campaign,          Icons.Default.Campaign),
    CONTACTS (MainTab.CONTACTS,  "联系人",    Icons.Default.PeopleOutline,     Icons.Default.People),
    SETTINGS (MainTab.SETTINGS,  "设置",      Icons.Default.Settings,          Icons.Default.Settings),
}
