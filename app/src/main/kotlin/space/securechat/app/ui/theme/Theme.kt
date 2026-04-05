package space.securechat.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 色值（对标 Web 端 Tailwind 暗色调板）────────────────────────────────

/** 主背景 zinc-950 */
val DarkBg     = Color(0xFF09090B)
/** 卡片/输入框 zinc-900 */
val Surface1   = Color(0xFF18181B)
/** 边框 zinc-800 */
val Surface2   = Color(0xFF27272A)
/** 次要文字 zinc-500 */
val TextMuted  = Color(0xFF71717A)
/** 主色 blue-500 */
val BlueAccent = Color(0xFF3B82F6)
/** 危险色 red-500 */
val Danger     = Color(0xFFEF4444)
/** 成功色 green-500 */
val Success    = Color(0xFF22C55E)
/** 警告色 amber-400 */
val Warning    = Color(0xFFFBBF24)
/** 主文字白 */
val TextPrimary = Color(0xFFFAFAFA)

private val AppColorScheme = darkColorScheme(
    background       = DarkBg,
    surface          = Surface1,
    surfaceVariant   = Surface2,
    primary          = BlueAccent,
    onPrimary        = TextPrimary,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    error            = Danger,
    outline          = Surface2,
)

@Composable
fun SecureChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography(),
        content     = content
    )
}
