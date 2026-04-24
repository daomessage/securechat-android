package space.securechat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.theme.*

/**
 * Primary / Secondary / Danger Button · 三端统一按钮组件
 * 对齐 docs/DESIGN_TOKENS.md
 *
 * 规格:
 *   高度 48dp (Material 默认 40, 我们用 48 对齐 PWA/iOS)
 *   圆角 radius.lg = 8dp
 *   水平内边距 space.6 = 24dp
 *   字号 16sp / 字重 Medium
 *   禁用态 alpha 50%
 */

private val ButtonHeight = 48.dp
private val ButtonRadius = 8.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(ButtonHeight),
        shape = Radius.lg,
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandPrimary,
            contentColor = TextPrimary,
            disabledContainerColor = BrandPrimary.copy(alpha = 0.5f),
            disabledContentColor = TextPrimary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = Spacing.s6),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(ButtonHeight),
        shape = Radius.lg,
        colors = ButtonDefaults.buttonColors(
            containerColor = Surface2,
            contentColor = TextPrimary,
            disabledContainerColor = Surface2.copy(alpha = 0.5f),
            disabledContentColor = TextPrimary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = Spacing.s6),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(ButtonHeight),
        shape = Radius.lg,
        colors = ButtonDefaults.buttonColors(
            containerColor = Danger,
            contentColor = TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.s6),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// OutlineButton (空底 + 描边) · 用于次要操作
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(ButtonHeight),
        shape = Radius.lg,
        border = BorderStroke(1.dp, BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextPrimary,
            disabledContentColor = TextPrimary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = Spacing.s6),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
