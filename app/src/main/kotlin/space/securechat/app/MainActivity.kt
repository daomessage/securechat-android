package space.securechat.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import space.securechat.app.ui.theme.SecureChatTheme
import space.securechat.app.viewmodel.AppViewModel
import space.securechat.app.AppNavigation

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 关键:让 Activity 能在锁屏上方显示 + 自动亮屏
        // 来电 fullScreenIntent 拉起 MainActivity 时,如果不设这两个 flag,
        // 系统只会跳到锁屏页,不会显示 Activity 本身,用户必须解锁后才能看到来电 UI
        // 这是国内 IM(微信/钉钉)在国产 ROM 上锁屏来电亮屏的标配。
        enableShowOnLockScreen()

        // 处理通知 Deep Link（冷启动 intent）
        handleIntent(intent)

        setContent {
            SecureChatTheme {
                AppNavigation(appViewModel = appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 锁屏来电场景:已存在的 Activity 被 onNewIntent 唤起时,
        // 也要保证锁屏属性是 ON(用户上一次进入可能没有 incoming_call_id)
        if (intent.hasExtra("incoming_call_id")) {
            enableShowOnLockScreen()
            // 主动请求解除锁屏(不需要密码,只是把锁屏 UI 收起)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && km.isKeyguardLocked) {
                km.requestDismissKeyguard(this, null)
            }
        }
        handleIntent(intent)
    }

    /**
     * 让 Activity 在锁屏上方显示 + 拉起时点亮屏幕。
     * Android 8.1+ 用 setShowWhenLocked / setTurnScreenOn(推荐),
     * 老版本回退到 WindowManager.LayoutParams flag。
     */
    @Suppress("DEPRECATION")
    private fun enableShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun handleIntent(intent: Intent?) {
        // securechat://chat?conv_id=c-xxx
        intent?.data?.let { uri ->
            val convId = uri.getQueryParameter("conv_id")
            if (!convId.isNullOrEmpty()) {
                appViewModel.setActiveChatId(convId)
            }
        }
        // FCM data payload（从通知点击进来）
        intent?.getStringExtra("conv_id")?.let { convId ->
            if (convId.isNotEmpty()) appViewModel.setActiveChatId(convId)
        }
    }
}
