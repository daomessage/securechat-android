package space.securechat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import space.securechat.app.MainActivity

/**
 * 来电前台服务
 *
 * Q3 修复 + 通话方面:
 * - phoneCall 类型享受系统最高优先级,即使应用已被冻结也能拉起
 * - fullScreenIntent 全屏拉起来电界面(Android 14+ 需 USE_FULL_SCREEN_INTENT)
 * - 独立通知频道 IMPORTANCE_HIGH + 系统默认铃声
 *
 * 启动时机:FcmService 收到 call_offer 推送 / WS 收到 call_offer 帧
 * 停止时机:用户接听 / 拒绝 / 错过 / 主叫挂断
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallFgService"
        private const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "securechat_calls"
        const val CHANNEL_NAME = "来电"

        const val ACTION_INCOMING = "space.securechat.app.action.CALL_INCOMING"
        const val ACTION_DISMISS  = "space.securechat.app.action.CALL_DISMISS"

        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_FROM    = "from"
        const val EXTRA_MODE    = "mode"  // "audio" / "video"

        fun showIncoming(context: Context, callId: String, from: String, mode: String) {
            val intent = Intent(context, CallForegroundService::class.java)
                .setAction(ACTION_INCOMING)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java)
                .setAction(ACTION_DISMISS)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createCallChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                Log.d(TAG, "ACTION_DISMISS")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_INCOMING -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
                val from   = intent.getStringExtra(EXTRA_FROM).orEmpty()
                val mode   = intent.getStringExtra(EXTRA_MODE) ?: "audio"
                Log.d(TAG, "ACTION_INCOMING from=$from mode=$mode")

                val notification = buildIncomingCallNotification(callId, from, mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            else -> {
                // 异常路径:无 action 直接停止
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildIncomingCallNotification(callId: String, from: String, mode: String): Notification {
        // 全屏 Intent:在锁屏时直接拉起来电界面
        // 普通 Intent:用户在使用其他 app 时点通知跳转
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incoming_call_id", callId)
            putExtra("incoming_from", from)
            putExtra("incoming_mode", mode)
            setData(android.net.Uri.parse("securechat://call?id=$callId"))
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (mode == "video") "📹 视频来电" else "📞 语音来电"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText("@$from")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "来电通知,可在系统设置中调整"
            setSound(ringtoneUri, audioAttrs)
            enableLights(true)
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
