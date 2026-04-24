package space.securechat.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape

// ══════════════════════════════════════════════════════════════
// DAO Message 三端统一视觉规范 — 对应 docs/DESIGN_TOKENS.md
// 修改前请先同步 DESIGN_TOKENS.md
// ══════════════════════════════════════════════════════════════

// ── 色彩 · 基础背景 ────────────────────────────────────────────
/** color.bg.base zinc-950 */
val DarkBg         = Color(0xFF09090B)
/** color.bg.surface zinc-900 */
val Surface1       = Color(0xFF18181B)
/** color.bg.surface-raised zinc-800 */
val Surface2       = Color(0xFF27272A)
/** color.bg.hover zinc-700 */
val SurfaceHover   = Color(0xFF3F3F46)

// ── 色彩 · 边框 ────────────────────────────────────────────────
/** color.border.default zinc-800 */
val BorderDefault  = Color(0xFF27272A)
/** color.border.strong zinc-600 */
val BorderStrong   = Color(0xFF52525B)

// ── 色彩 · 文字 ────────────────────────────────────────────────
/** color.text.primary zinc-50 */
val TextPrimary    = Color(0xFFFAFAFA)
/** color.text.secondary zinc-300 */
val TextSecondary  = Color(0xFFD4D4D8)
/** color.text.muted zinc-400 */
val TextMutedLight = Color(0xFFA1A1AA)
/** color.text.disabled zinc-500 */
val TextMuted      = Color(0xFF71717A)

// ── 色彩 · 品牌 ────────────────────────────────────────────────
/** color.brand.primary blue-500 */
val BrandPrimary   = Color(0xFF3B82F6)
/** color.brand.primary-hover blue-600 */
val BrandPrimaryHover = Color(0xFF2563EB)
/** color.brand.primary-text blue-400 (链接/辅助) */
val BrandPrimaryText = Color(0xFF60A5FA)
/** 别名保留: @deprecated 用 BrandPrimary 替代 */
val BlueAccent     = BrandPrimary

// ── 色彩 · 状态 ────────────────────────────────────────────────
/** color.status.danger red-500 */
val Danger         = Color(0xFFEF4444)
/** color.status.success green-500 */
val Success        = Color(0xFF22C55E)
/** color.status.success-text green-400 (E2EE 徽章) */
val SuccessText    = Color(0xFF4ADE80)
/** color.status.warning amber-400 */
val Warning        = Color(0xFFFBBF24)

// ── 间距 (4px 网格) ────────────────────────────────────────────
object Spacing {
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
}

// ── 圆角 ──────────────────────────────────────────────────────
object Radius {
    val sm: Shape = RoundedCornerShape(4.dp)
    val md: Shape = RoundedCornerShape(6.dp)
    val lg: Shape = RoundedCornerShape(8.dp)
    val xl: Shape = RoundedCornerShape(12.dp)
    val xxl: Shape = RoundedCornerShape(16.dp)
    val xxxl: Shape = RoundedCornerShape(24.dp)
    val full: Shape = CircleShape
}

// ── 字号 + 字重 ────────────────────────────────────────────────
object TextSize {
    val xs = 12.sp
    val sm = 14.sp    // 默认正文
    val base = 16.sp
    val lg = 18.sp
    val xl = 20.sp
    val xl2 = 24.sp
    val xl3 = 30.sp
    val xl4 = 36.sp
}

// ── 阴影 ──────────────────────────────────────────────────────
object Elevation {
    val sm = 1.dp
    val md = 3.dp
    val lg = 6.dp
    val xl = 8.dp
    val xxl = 12.dp
}

// ── Material ColorScheme 绑定 ──────────────────────────────────
private val AppColorScheme = darkColorScheme(
    background       = DarkBg,
    surface          = Surface1,
    surfaceVariant   = Surface2,
    primary          = BrandPrimary,
    onPrimary        = TextPrimary,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    error            = Danger,
    outline          = BorderDefault,
)

@Composable
fun SecureChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography(),
        content     = content
    )
}
