package space.securechat.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.securechat.app.MainActivity
import space.securechat.sdk.SecureChatClient

/**
 * FcmService — FCM 推送接收
 *
 * ⚠️ E2EE 原则：服务端只推送 conv_id，不推明文
 * 通知点击 → Intent 携带 conv_id → MainActivity.handleIntent() → 打开对应会话
 */
class FcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val CHANNEL_ID = "securechat_messages"
    private val CHANNEL_NAME = "Messages"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /** FCM Token 刷新，自动同步到服务端 */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            try { SecureChatClient.getInstance().push.register(token) }
            catch (_: Exception) {}
        }
    }

    /**
     * 收到推送：展示本地通知，点击打开对应会话
     *
     * data payload: { "type": "new_msg", "conv_id": "c-xxx", "sender": "@alice" }
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val convId  = message.data["conv_id"]  ?: return
        val sender  = message.data["sender"]   ?: "SecureChat"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conv_id", convId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, convId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText("🔒 New encrypted message")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(convId.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
