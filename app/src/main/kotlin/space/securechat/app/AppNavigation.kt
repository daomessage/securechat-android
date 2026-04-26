package space.securechat.app

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.messaging.StoredMessage
import space.securechat.app.call.CallManager
import space.securechat.app.ui.call.CallScreen
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel
import space.securechat.app.ui.onboarding.*
import space.securechat.app.ui.main.MainScreen
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.app.ui.theme.*

/**
 * AppNavigation — 路由容器（对标 App.tsx switch(route)）
 *
 * 🔒 SDK 自动：restoreSession() — 读 Room DB → Ed25519 签名 → 获取 JWT
 * 👤 App: 根据返回值决定路由
 */
@Composable
fun AppNavigation(appViewModel: AppViewModel) {
    val route by appViewModel.route.collectAsStateWithLifecycle()
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()

    // ── 冷启动会话恢复（对标 App.tsx useEffect restoreSession）──────────
    LaunchedEffect(Unit) {
        val session = client.restoreSession()
        if (session != null) {
            val (aliasId, nickname) = session
            appViewModel.setUserInfo(aliasId, nickname)
            // 🔒 SDK: client.connect()
            client.connect()
            appViewModel.setSdkReady(true)
            appViewModel.setRoute(AppRoute.MAIN)
        } else {
            appViewModel.setRoute(AppRoute.WELCOME)
        }
    }

    // ── 订阅 SDK 消息（全局未读计数）────────────────────────────────────
    DisposableEffect(Unit) {
        val listener: (StoredMessage) -> Unit = { msg ->
            val currentChatId = appViewModel.activeChatId.value
            if (msg.conversationId != currentChatId) {
                appViewModel.incrementUnread(msg.conversationId)
            }
        }
        val unsub = client.on(SecureChatClient.EVENT_MESSAGE, listener)
        onDispose { unsub() }
    }

    // ── GOAWAY 监听（多端踢出）────────────────────────────────────────
    var showGoaway by remember { mutableStateOf(false) }
    var goawayReason by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        client.networkState.collect { state ->
            if (state is space.securechat.sdk.messaging.WSTransport.NetworkState.Kicked) {
                goawayReason = state.reason
                showGoaway = true
            }
        }
    }

    // 启动通话信令监听（全局一次）
    val callManager = remember { CallManager.getInstance() }
    DisposableEffect(Unit) {
        callManager.start()
        onDispose { callManager.stop() }
    }

    Box(Modifier.fillMaxSize()) {
        when (route) {
            AppRoute.LOADING -> Box(
                Modifier.fillMaxSize().background(DarkBg),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BlueAccent) }

            AppRoute.WELCOME           -> WelcomeScreen(appViewModel)
            AppRoute.GENERATE_MNEMONIC -> GenerateMnemonicScreen(appViewModel)
            AppRoute.CONFIRM_BACKUP    -> ConfirmBackupScreen(appViewModel)
            AppRoute.VANITY_SHOP       -> VanityShopScreen(appViewModel)
            AppRoute.SET_NICKNAME      -> SetNicknameScreen(appViewModel)
            AppRoute.RECOVER           -> RecoverScreen(appViewModel)
            AppRoute.MAIN              -> MainScreen(appViewModel)
        }
        // 通话覆盖层（任何路由下都顶层显示）
        CallScreen(callManager)
    }

    // GOAWAY 全屏弹窗 — 被新设备挤下线
    if (showGoaway) {
        AlertDialog(
            onDismissRequest = {},  // 不可关闭
            containerColor = Surface1,
            title = { Text("已被踢下线", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Text(
                    "你的账号在另一台设备登录了。出于安全考虑,同一时间只能在一台设备上使用。",
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoaway = false
                        scope.launch {
                            try { client.logout() } catch (_: Exception) {}
                        }
                        appViewModel.setRoute(AppRoute.WELCOME)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                ) { Text("好的") }
            }
        )
    }
}
