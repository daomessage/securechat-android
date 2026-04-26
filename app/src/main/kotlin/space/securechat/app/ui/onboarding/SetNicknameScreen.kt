package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * SetNicknameScreen — 对标 Web: SetNickname.tsx
 * 完成注册：读助记词 → SDK.registerAccount() → connect() → Main
 *
 * 🔒 SDK 自动：PoW 计算 → 公钥上传 → JWT 获取 → Room DB 持久化
 * 👤 App：UI 状态更新 → 跳转 Main
 */
@Composable
fun SetNicknameScreen(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    val mnemonic by appViewModel.tempMnemonic.collectAsStateWithLifecycle()
    
    var nickname by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun register() {
        if (nickname.isBlank() || isLoading) return
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                // 🔒 SDK 全自动：PoW + 密钥派生 + HTTP 注册 + JWT
                val aliasId = client.auth.registerAccount(mnemonic, nickname.trim())

                // 👤 App：更新全局状态
                appViewModel.setUserInfo(aliasId, nickname.trim())

                // 🔒 SDK：建立 WebSocket
                client.connect()
                appViewModel.setSdkReady(true)

                // 清除内存中的助记词（不再需要）
                appViewModel.setTempMnemonic("")

                appViewModel.setRoute(AppRoute.MAIN)
            } catch (e: Exception) {
                errorMsg = "注册失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TextButton(onClick = { appViewModel.setRoute(AppRoute.VANITY_SHOP) }) {
                Text("← 返回", color = TextMuted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择显示昵称", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "这是其他联系人看到的名称，之后可随时修改。",
                    color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
            }

            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 30) nickname = it },
                label = { Text("显示昵称", color = TextMuted) },
                placeholder = { Text("例如：小明", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                    focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${nickname.length}/30", color = TextMuted) }
            )

            errorMsg?.let {
                Text(it, color = Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { register() },
                enabled = nickname.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("创建账户", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
