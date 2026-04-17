package space.securechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.theme.Danger
import space.securechat.app.ui.theme.Surface1
import space.securechat.app.ui.theme.TextPrimary
import space.securechat.app.ui.theme.Success

/**
 * ErrorSnackbar — 错误提示条（全屏底部）
 *
 * 用法：
 * ```kotlin
 * if (error != null) {
 *     ErrorSnackbar(message = error!!, onDismiss = { error = null })
 * }
 * ```
 */
@Composable
fun ErrorSnackbar(message: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Danger.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                message,
                color = Danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Danger, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * SuccessSnackbar — 成功提示
 */
@Composable
fun SuccessSnackbar(message: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                shape = RoundedCornerShape(8.dp),
                color = Surface1.copy(alpha = 0.95f)
            )
            .border(1.dp, Success.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                message,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
