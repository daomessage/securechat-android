package space.securechat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.theme.*

/**
 * SecurityCodeDialog — 安全码验证 Modal
 *
 * 首次与新联系人聊天时弹出，展示 60 位安全码用于面对面或通话核验身份
 * 防止中间人攻击（MITM）
 */
@Composable
fun SecurityCodeDialog(
    securityCode: String,
    friendNickname: String,
    onDismiss: () -> Unit,
    onMarkVerified: () -> Unit = onDismiss,
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "验证安全码",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 说明文字
                Text(
                    "请与 $friendNickname 当面或通过通话核对此安全码，确保与正确的人通信。",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // 安全码展示（6x10 格式）
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 分行显示（每行10个数字）
                        repeat(6) { lineIdx ->
                            val startIdx = lineIdx * 10
                            val endIdx = minOf(startIdx + 10, securityCode.length)
                            val line = securityCode.substring(startIdx, endIdx)

                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BlueAccent,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                // 复制按钮
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(securityCode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("复制安全码", color = TextPrimary)
                }

                // 警告
                Card(
                    colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("⚠️", fontSize = 14.sp)
                        Text(
                            "请勿忽略安全码。如果不匹配，请立即联系我们。",
                            color = Danger,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onMarkVerified,
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("已核对", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后", color = TextMuted)
            }
        }
    )
}
