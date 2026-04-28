package space.securechat.app.service

import android.app.ActivityOptions
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
import android.os.PowerManager
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

    /**
     * WakeLock 强制亮屏 — 来电时即便用户没开「锁屏显示 / 全屏通知」权限,
     * 屏幕也会强制亮起,用户能听到铃声 + 看到锁屏来电卡片。
     * 这是国内 IM(微信/钉钉)在 MIUI/华为/OPPO 上的通用做法,因为
     * 国产 ROM 默认对第三方 app 的 setFullScreenIntent 静默降级。
     *
     * 行为:
     * - ACTION_INCOMING 时 acquire(SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE)
     * - 30 秒自动释放(铃声超时基本就够)
     * - ACTION_DISMISS 时立即释放
     */
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "CallFgService"
        private const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "securechat_calls"
        const val CHANNEL_NAME = "来电"
        private const val WAKE_LOCK_TAG = "SecureChat:incoming-call"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L

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
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_INCOMING -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
                val from   = intent.getStringExtra(EXTRA_FROM).orEmpty()
                val mode   = intent.getStringExtra(EXTRA_MODE) ?: "audio"
                Log.d(TAG, "ACTION_INCOMING from=$from mode=$mode")

                // 第一时间 acquire WakeLock 强制亮屏(即便系统给我们的 fullScreenIntent 降级了)
                acquireScreenWakeLock()

                // 必须先 startForeground(把自己变成前台 phoneCall 类型),才能拥有 BAL 豁免
                val notification = buildIncomingCallNotification(callId, from, mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                // 关键:phoneCall 类型 FGS 享受 BAL (Background Activity Launch) 豁免,
                // 可以在用户用别的 app 时直接拉起 MainActivity 全屏来电界面。
                // 这是微信 / 钉钉 / 系统电话在解锁状态下的标准做法,
                // 否则 setFullScreenIntent 会被系统降级为 heads-up notification。
                // 锁屏场景已经被 fullScreenIntent + setShowWhenLocked 覆盖,不冲突。
                launchIncomingCallActivity(callId, from, mode)
            }
            else -> {
                // 异常路径:无 action 直接停止
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    /**
     * 直接拉起 MainActivity 显示来电界面。
     *
     * Android 14 BAL 加严要点(实测 logcat 暴露):
     *
     * 1. 直接 startActivity(intent) → 拦截
     *    `autoOptInReason: notPendingIntent`
     *
     * 2. PendingIntent.send() 不带 ActivityOptions → 仍然拦截
     *    `isPendingIntent: true / balRequireOptInByPendingIntentCreator: true /
     *     resultIfPiCreatorAllowsBal: BAL_BLOCK`
     *    虽然 ALLOW_BAL 标记都对了,但 Android 14 加了新闸门 —
     *    同 UID 的 PendingIntent 默认不放行 BAL,必须显式 opt-in。
     *
     * 3. 正确写法 (Android 14+):
     *    PendingIntent.send(context, code, intent,
     *        null, null, null,
     *        ActivityOptions.makeBasic()
     *            .setPendingIntentBackgroundActivityStartMode(
     *                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
     *            ).toBundle()
     *    )
     *    创建方明确 opt-in 后,sameUid 路径才会被 ALLOW。
     *
     * 这是微信/钉钉/系统电话在 Android 14 上的标准做法,缺一不可。
     */
    private fun launchIncomingCallActivity(callId: String, from: String, mode: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("incoming_call_id", callId)
                putExtra("incoming_from", from)
                putExtra("incoming_mode", mode)
                data = android.net.Uri.parse("securechat://call?id=$callId")
            }
            val pi = PendingIntent.getActivity(
                this,
                callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Android 14+ 必须 opt-in,否则 sameUid PendingIntent 仍被 BAL_BLOCK
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            } else {
                null
            }
            pi.send(this, 0, null, null, null, null, options)
            Log.d(TAG, "launchIncomingCallActivity: PendingIntent sent (BAL opt-in)")
        } catch (e: Exception) {
            // BAL 失败时不阻塞 — fullScreenIntent / heads-up notification 还在
            Log.w(TAG, "launchIncomingCallActivity failed: ${e.message}")
        }
    }

    /**
     * 申请 SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP:
     * - SCREEN_BRIGHT:整个屏幕亮起(不是 dim)
     * - ACQUIRE_CAUSES_WAKEUP:即使在锁屏 / 熄屏状态也立即亮屏
     * - ON_AFTER_RELEASE:释放后短暂保持亮屏,避免立刻又熄屏
     *
     * 30 秒自动 release,与典型来电铃声时长匹配
     */
    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "WakeLock acquired (${WAKE_LOCK_TIMEOUT_MS}ms)")
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release failed: ${e.message}")
        }
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
