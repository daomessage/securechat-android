package space.securechat.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.securechat.app.service.MessagingForegroundService
import space.securechat.sdk.SecureChatClient

/**
 * FcmService — FCM 推送接收
 *
 * 🔒 Q3-A' 零知识方案 A
 *
 * 服务端 push payload 仅包含 {"type":"new_msg"},不含 conv_id / sender / 任何密文
 * (relay-server/internal/push/handler.go::PushPayload)
 *
 * 推送处理链:
 *   1. 收到推送 → 立刻调用 MessagingForegroundService.wakeAndSync(this)
 *      触发 Service 持锁 60s + WS 重连(系统会重启已被 freeze 的进程)
 *   2. 立刻显示中性占位通知,告诉用户"有新消息",不暴露任何元数据
 *   3. WS 重连完成后, SDK observeMessages 拉离线消息 → 触发 EVENT_MESSAGE
 *      AppNavigation 已挂的 listener 会调用 NotificationHelper.upgradeMessageNotification()
 *      把通知文案升级为 "@alice: 🔒 加密消息"(反查本地 Friend 缓存)
 *
 * Q3-H FCM token 注册的幂等重试: onNewToken 失败后会指数退避重试,确保 server 端永远拿到最新 token
 */
class FcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "FcmService"
        const val CHANNEL_ID = "securechat_messages"
        private const val CHANNEL_NAME = "新消息"
        // 占位通知 ID:WS catch-up 完成后由 NotificationHelper 升级文案,复用同一个 ID
        const val PLACEHOLDER_NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Q3-H FCM Token 刷新,幂等重试
     * - 首次失败 → 5s 重试 → 30s 重试 → 5min 重试,最多 3 次
     * - SDK push.register 内部会在 server 端做去重,不会因为多次调用产生脏数据
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: ${token.take(12)}…")
        scope.launch {
            val delays = longArrayOf(0L, 5_000L, 30_000L, 300_000L)
            for ((i, d) in delays.withIndex()) {
                if (d > 0) kotlinx.coroutines.delay(d)
                try {
                    val client = SecureChatClient.getInstance()
                    // 系统重启进程时 onNewToken 可能在 Activity 之前跑,先恢复 session 拿 JWT
                    runCatching { client.restoreSession() }
                    client.push.register(token)
                    Log.d(TAG, "FCM token registered (attempt ${i + 1})")
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "register attempt ${i + 1} failed: ${e.message}")
                }
            }
            Log.e(TAG, "FCM token register exhausted retries")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"]
        Log.d(TAG, "onMessageReceived type=$type")

        // 仅处理 new_msg 类型;未来如有 call_offer 推送可在此分流
        if (type != "new_msg") return

        // 1. 唤起前台服务拉离线消息(关键 — Doze/Frozen 状态下系统才会重启进程)
        MessagingForegroundService.wakeAndSync(this)

        // 2. 立刻显示中性占位通知
        //    - 不含会话 ID / 发送者(防推送通道关联分析)
        //    - WS catch-up 后 NotificationHelper.upgradeMessageNotification() 升级文案
        NotificationHelper.showPlaceholder(this)
    }

    /** 新消息通道 — IMPORTANCE_HIGH 让锁屏可见、有声音 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "加密消息推送"
            enableLights(true)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }
}
