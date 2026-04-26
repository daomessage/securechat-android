package space.securechat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import space.securechat.app.MainActivity
import space.securechat.app.R
import space.securechat.sdk.SecureChatClient

/**
 * 消息同步前台服务
 *
 * Q3 修复:Android 进程切后台 → Doze / Cached / Frozen 状态下,
 * Application 持有的 WebSocket 会被系统 freeze,导致消息收不到。
 *
 * 解法:把 WS 维护权交给 ForegroundService(dataSync 类型),
 * 系统不会 freeze 显示前台通知的服务。
 *
 * 启动时机:登录成功后(MainActivity restoreSession 完成或注册完成)
 * 停止时机:用户主动登出 / 应用被关闭
 */
class MessagingForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var reconnectJob: Job? = null

    companion object {
        private const val TAG = "MsgFgService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "securechat_foreground"
        private const val CHANNEL_NAME = "DAO Message 在线"

        const val ACTION_START = "space.securechat.app.action.START_MESSAGING"
        const val ACTION_STOP  = "space.securechat.app.action.STOP_MESSAGING"
        const val ACTION_WAKE_AND_SYNC = "space.securechat.app.action.WAKE_AND_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, MessagingForegroundService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            // Q3 调试:打印调用栈,确定 stop 是从哪里来的
            Log.w(TAG, "stop() 被调用,调用栈:", Throwable("stop() called"))
            val intent = Intent(context, MessagingForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** FCM 收到消息后调用:启动服务并触发短时同步 */
        fun wakeAndSync(context: Context) {
            val intent = Intent(context, MessagingForegroundService::class.java)
                .setAction(ACTION_WAKE_AND_SYNC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须立刻 startForeground,否则 5s 内会被系统抛 ForegroundServiceDidNotStartInTimeException
        startForegroundCompat()

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP")
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_WAKE_AND_SYNC -> {
                Log.d(TAG, "ACTION_WAKE_AND_SYNC: FCM 唤起,加锁拉离线消息")
                acquireWakeLockBriefly()
                ensureConnected()
            }
            else -> {
                Log.d(TAG, "ACTION_START / 重启")
                ensureConnected()
            }
        }
        // START_STICKY:进程被杀后系统会尝试重启服务(Intent 为 null,走 else 分支重连 WS)
        return START_STICKY
    }

    /**
     * Q3-E 确保 WS 已连接;断了就发起重连
     *
     * 关键路径(系统 START_STICKY 重启服务):
     *   Application.onCreate → SecureChatClient.init(已就位)
     *   但 Activity 没有起来,意味着 restoreSession 还没跑过 → 没有 JWT → connect 失败
     * 所以这里必须自己先 restoreSession() 一次(幂等,SDK 内部会检查是否已恢复)
     *
     * SDK 内部已有指数退避重连,这里只做兜底
     */
    private fun ensureConnected() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            try {
                val client = SecureChatClient.getInstance()
                // 先确保 session 已恢复(系统重启进程的兜底)
                runCatching { client.restoreSession() }
                    .onFailure { Log.w(TAG, "restoreSession 失败: ${it.message}") }
                if (!client.isConnected) {
                    Log.d(TAG, "WS 未连接,发起 connect()")
                    runCatching { client.connect() }
                        .onFailure { Log.w(TAG, "connect 失败: ${it.message}") }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureConnected error: ${e.message}")
            }
        }
    }

    /**
     * Doze 模式下临时持锁 60s,确保 FCM 唤起后 WS 重连 + 离线消息拉取能跑完
     * 不能长时间持锁(影响电池),60s 已经足够 SDK 完成 catch-up
     */
    private fun acquireWakeLockBriefly() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DAOMessage:FCMWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60_000L)  // 60s 自动释放
            }
        } catch (e: Exception) {
            Log.w(TAG, "wakeLock 获取失败: ${e.message}")
        }
    }

    private fun startForegroundCompat() {
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+:必须显式声明 foregroundServiceType
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("DAO Message 在线")
            .setContentText("🔒 端到端加密 · 后台保持连接")
            .setOngoing(true)
            // LOW:不在锁屏显示,不发声,只在通知抽屉里有一条;用户体验最低打扰
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pi)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持后台连接的常驻通知,可在系统设置中关闭"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopSelfSafely() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            reconnectJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (_: Exception) { /* ignore */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        scope.cancel()
        Log.d(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
