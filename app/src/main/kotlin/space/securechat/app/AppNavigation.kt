package space.securechat.app

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import space.securechat.sdk.SecureChatClient
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel
import space.securechat.app.ui.onboarding.*
import space.securechat.app.ui.main.MainScreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import space.securechat.app.ui.theme.BlueAccent
import space.securechat.app.ui.theme.DarkBg

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
        val unsub = client.on(SecureChatClient.EVENT_MESSAGE) { msg ->
            val currentChatId = appViewModel.activeChatId.value
            if (msg.conversationId != currentChatId) {
                appViewModel.incrementUnread(msg.conversationId)
            }
        }
        onDispose { unsub() }
    }

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
}
