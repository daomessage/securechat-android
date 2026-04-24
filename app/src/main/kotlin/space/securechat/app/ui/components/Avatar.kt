package space.securechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.theme.TextPrimary

/**
 * Avatar · 三端统一头像组件 (对齐 docs/DESIGN_TOKENS.md)
 *
 * 基础背景: gradient 蓝 → 紫
 * 圆形, 字号 = size * 0.4
 */

enum class AvatarSize(val dp: Dp, val fontSp: Int) {
    SM(32.dp, 13),    // 列表项
    MD(48.dp, 19),    // 聊天头像 / 顶栏
    LG(80.dp, 32),    // Welcome / 资料卡
    XL(96.dp, 38),    // 特殊场景
}

// 三端一致的 gradient · blue-500 → violet-500 → purple-500
private val AvatarGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF3B82F6),   // blue-500
        Color(0xFF8B5CF6),   // violet-500
        Color(0xFFA855F7),   // purple-500
    )
)

@Composable
fun Avatar(
    text: String,
    size: AvatarSize = AvatarSize.MD,
    modifier: Modifier = Modifier,
) {
    val letter = text.take(2).uppercase()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(AvatarGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = TextPrimary,
            fontSize = size.fontSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
