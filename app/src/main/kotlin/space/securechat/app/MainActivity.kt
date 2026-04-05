package space.securechat.app

import android.content.Intent
import android.os.Bundle
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
        handleIntent(intent)
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
