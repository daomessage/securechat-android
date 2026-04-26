package space.securechat.app.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import space.securechat.app.MainActivity
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.messaging.StoredMessage

/**
 * NotificationHelper — 新消息通知占位与升级
 *
 * 🔒 零知识链路:
 * 阶段 1 (FcmService 收到推送时): showPlaceholder()
 *   - 通知文案: "DAO Message · 🔒 您有一条新加密消息"
 *   - 点击进 MainActivity (无 conv_id, 进默认 Tab)
 *
 * 阶段 2 (WS 拉离线后, SDK 触发 EVENT_MESSAGE): upgradeMessageNotification()
 *   - 反查本地 Friend 缓存: aliasId → nickname
 *   - 通知文案: "@nickname · 🔒 加密消息"
 *   - 点击进 MainActivity 并跳到对应会话
 *   - 通知 ID 仍用 conv_id.hashCode(),同会话多条消息合并显示
 */
object NotificationHelper {

    /** 阶段 1: FCM 唤起时的占位通知 */
    fun showPlaceholder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, FcmService.PLACEHOLDER_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, FcmService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("DAO Message")
            .setContentText("🔒 您有一条新加密消息")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FcmService.PLACEHOLDER_NOTIFICATION_ID, notification)
    }

    /**
     * 阶段 2: WS 拉到具体消息后调,升级通知文案
     *
     * 调用方:AppNavigation EVENT_MESSAGE listener (仅当应用在后台 / 锁屏时调)
     * 应用前台时不要弹通知 — 用户已经在看消息列表,弹通知是骚扰
     */
    fun upgradeMessageNotification(context: Context, msg: StoredMessage) {
        // 占位通知由具体会话通知接管;清掉占位避免堆积
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(FcmService.PLACEHOLDER_NOTIFICATION_ID)

        // 反查 Friend 拿 nickname (零知识:仅查本地,不联网)
        val client = SecureChatClient.getInstance()
        val friend = client.contacts.friends.firstOrNull {
            it.conversationId == msg.conversationId
        }
        val title = friend?.let { "@${it.nickname.ifBlank { it.aliasId }}" } ?: "DAO Message"

        // 点击通知 deeplink 到对应会话
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conv_id", msg.conversationId)
        }
        val pi = PendingIntent.getActivity(
            context, msg.conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FcmService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText("🔒 加密消息")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(msg.conversationId.hashCode(), notification)
    }
}
