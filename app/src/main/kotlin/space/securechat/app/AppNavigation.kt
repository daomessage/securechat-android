package space.securechat.app

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.messaging.StoredMessage
import space.securechat.app.call.CallManager
import space.securechat.app.push.NotificationHelper
import space.securechat.app.service.MessagingForegroundService
import space.securechat.app.ui.call.CallScreen
import space.securechat.app.ui.components.BackgroundPermissionsDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
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
    val context = LocalContext.current

    // ── 前台服务生命周期：MAIN ⇒ start，其他 ⇒ stop ────────────────────
    // Q3 后台保活:进入 MAIN 即认为登录成功,启动 MessagingForegroundService
    // 持有 WS 长连接;路由回 WELCOME(登出 / GOAWAY)立即停服
    LaunchedEffect(route) {
        android.util.Log.d("AppNavigation", "LaunchedEffect(route) → $route")
        when (route) {
            AppRoute.MAIN -> MessagingForegroundService.start(context)
            AppRoute.WELCOME -> MessagingForegroundService.stop(context)
            else -> { /* LOADING / 注册流程中:不动 */ }
        }
    }

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

    // ── 订阅 SDK 消息（全局未读计数 + 后台通知升级）─────────────────────
    DisposableEffect(Unit) {
        val listener: (StoredMessage) -> Unit = { msg ->
            val currentChatId = appViewModel.activeChatId.value
            if (msg.conversationId != currentChatId) {
                appViewModel.incrementUnread(msg.conversationId)
            }
            // Q3-A' 通知升级:进程在后台 / 锁屏时把 FCM 占位通知升级为带 nickname 的会话通知
            // 前台或正在看这个会话则不弹,避免骚扰
            val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            if (!inForeground && msg.conversationId != currentChatId) {
                NotificationHelper.upgradeMessageNotification(context, msg)
            }
        }
        val unsub = client.on(SecureChatClient.EVENT_MESSAGE, listener)
        onDispose { unsub() }
    }

    // ── GOAWAY 监听 ──────────────────────────────────────────────────
    // 服务端 GOAWAY 帧的 reason 字段决定客户端行为:
    //   new_device_login → 真·多端登录,弹窗 + logout + 跳 WELCOME
    //   jwt_revoked      → JWT 失效(异常情况),弹窗 + logout + 跳 WELCOME
    //   server_shutdown  → 服务端重启,SDK 自己重连,App 不要弹窗、不要 logout
    //   其他 / 未知       → 保守处理:弹通用提示,不 logout,允许用户重试
    var showGoaway by remember { mutableStateOf(false) }
    var goawayReason by remember { mutableStateOf("") }
    // 防止 jwt_revoked 自愈死循环:同一会话最多自愈 3 次,避免来回踢
    var jwtRevokedHealCount by remember { mutableStateOf(0) }
    var newDeviceHealAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        client.networkState.collect { state ->
            if (state is space.securechat.sdk.messaging.WSTransport.NetworkState.Kicked) {
                val reason = state.reason
                android.util.Log.w("AppNavigation", "GOAWAY reason=$reason")
                when (reason) {
                    "server_shutdown", "network_reset" -> {
                        // 良性断开:不打扰用户,SDK 重连即可
                        // 注意:SDK 当前 Kicked 状态会停 reconnect,需要主动 reset
                        // 服务端重启后 JTI 仍然有效,直接 connect 即可
                        scope.launch {
                            try {
                                client.disconnect()
                                kotlinx.coroutines.delay(500)
                                client.connect()
                            } catch (e: Exception) {
                                android.util.Log.w("AppNavigation", "auto-reconnect failed: ${e.message}")
                            }
                        }
                    }
                    "new_device_login" -> {
                        // P1-E(2026-04-26): 收到 new_device_login 先尝试复活一次再 logout。
                        // 服务端 P0-A 已对 same-JTI 不发此 reason,但客户端做"复活兜底"防止
                        // 服务端版本未更新或竞态导致的误踢。复活成功 = 误判;失败才真 logout。
                        if (newDeviceHealAttempted) {
                            android.util.Log.w("AppNavigation", "new_device_login 复活已试过,真·下线")
                            goawayReason = reason
                            showGoaway = true
                        } else {
                            newDeviceHealAttempted = true
                            android.util.Log.w("AppNavigation", "new_device_login 先尝试复活一次...")
                            scope.launch {
                                try {
                                    client.disconnect()
                                    kotlinx.coroutines.delay(500)
                                    client.connect()
                                    android.util.Log.i("AppNavigation", "new_device_login 复活成功 — 判定为误踢")
                                    // 30s 冷却,允许下次再尝试
                                    kotlinx.coroutines.delay(30_000)
                                    newDeviceHealAttempted = false
                                } catch (e: Exception) {
                                    android.util.Log.w("AppNavigation", "new_device_login 复活失败: ${e.message}")
                                    goawayReason = reason
                                    showGoaway = true
                                }
                            }
                        }
                    }
                    "jwt_revoked" -> {
                        // JWT 被服务端撤销:必须重新走 authenticate 拿新 JTI 才能重连
                        // 否则用旧 JTI 重连会被 revalidateLoop 再次踢掉,陷入死循环
                        if (jwtRevokedHealCount >= 3) {
                            android.util.Log.w("AppNavigation", "jwt_revoked 自愈已超 3 次,放弃 → 弹窗")
                            goawayReason = reason
                            showGoaway = true
                        } else {
                            jwtRevokedHealCount++
                            android.util.Log.w("AppNavigation", "jwt_revoked 自愈尝试 #$jwtRevokedHealCount")
                            scope.launch {
                                try {
                                    client.disconnect()
                                    // 必须强制重新认证(restoreSession 现在是幂等的,不会刷新 JWT)
                                    val session = client.reauthenticate()
                                    if (session != null) {
                                        client.connect()
                                        android.util.Log.i("AppNavigation", "jwt_revoked 自愈成功 #$jwtRevokedHealCount")
                                    } else {
                                        goawayReason = reason
                                        showGoaway = true
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("AppNavigation", "jwt_revoked 自愈失败: ${e.message}")
                                    goawayReason = reason
                                    showGoaway = true
                                }
                            }
                        }
                    }
                    else -> {
                        goawayReason = reason
                        showGoaway = true
                    }
                }
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

        // Q3-F + Q3-G 后台保活引导:进入 MAIN 路由后弹一次
        if (route == AppRoute.MAIN) {
            BackgroundPermissionsDialog()
        }
    }

    // GOAWAY 全屏弹窗
    // 区分两种语义:
    //   isHardKick=true (new_device_login / jwt_revoked):
    //     真·下线 → 必须 logout + 跳 WELCOME
    //   isHardKick=false (其他未知 reason):
    //     保守提示,不 logout,允许用户尝试重连
    if (showGoaway) {
        val isHardKick = goawayReason == "new_device_login" || goawayReason == "jwt_revoked"
        AlertDialog(
            onDismissRequest = {},  // 不可关闭
            containerColor = Surface1,
            title = {
                Text(
                    if (isHardKick) "已被踢下线" else "连接已断开",
                    color = TextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Text(
                    when (goawayReason) {
                        "new_device_login" -> "你的账号在另一台设备登录了。出于安全考虑,同一时间只能在一台设备上使用。"
                        "jwt_revoked" -> "登录凭证已失效,请重新登录。"
                        else -> "服务端断开了连接(原因:$goawayReason)。点击重连。"
                    },
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoaway = false
                        if (isHardKick) {
                            // 真·下线:清本地 identity 并跳 WELCOME
                            scope.launch {
                                try { client.logout() } catch (_: Exception) {}
                            }
                            appViewModel.setRoute(AppRoute.WELCOME)
                        } else {
                            // 软下线:尝试重连,保留 identity
                            scope.launch {
                                try {
                                    client.disconnect()
                                    kotlinx.coroutines.delay(500)
                                    client.connect()
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                ) { Text(if (isHardKick) "好的" else "重连") }
            }
        )
    }
}
