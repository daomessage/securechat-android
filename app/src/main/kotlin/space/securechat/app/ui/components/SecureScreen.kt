package space.securechat.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * SecureScreen — 在 composable 生命周期内对当前 Activity 加 FLAG_SECURE
 *
 * 效果：禁截屏、禁录屏、最近任务列表中显示为黑色。
 * 用于展示助记词 / 助记词输入 / 安全码 等敏感页面。
 *
 * 用法：
 *   @Composable
 *   fun GenerateMnemonicScreen(...) {
 *       SecureScreen()
 *       // ... 页面内容
 *   }
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    val activity = findActivity(context)
    DisposableEffect(activity) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private fun findActivity(context: android.content.Context): Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
