package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.keys.KeyDerivation
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * RecoverScreen — 对标 Web: Recover.tsx
 * 输入 12 词助记词恢复账号 → 直接 loginWithMnemonic 进 Main。
 *
 * 流程:
 *   1. validateMnemonic 校验 12 词
 *   2. client.auth.loginWithMnemonic(mnemonic):
 *        - 尝试 register;若服务端 409 说明已注册,从 response 拿 uuid+alias_id
 *        - 用私钥做 auth challenge/verify 拿 JWT
 *        - 持久化 identity 到 Room
 *   3. connect() 建 WebSocket
 *   4. 跳 Main
 *
 * 不再经过 SetNickname 页 — 恢复场景用户本来就有旧昵称,服务端已有记录。
 * 想改昵称去「设置」页改。
 */
@Composable
fun RecoverScreen(appViewModel: AppViewModel) {
    // 敏感屏：禁截屏/录屏
    space.securechat.app.ui.components.SecureScreen()
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun restore() {
        val mnemonic = input.trim().lowercase()
        if (!KeyDerivation.validateMnemonic(mnemonic)) {
            errorMsg = "助记词无效，请检查你的 12 个单词。"
            return
        }
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                // 🔒 SDK:loginWithMnemonic = register-or-409-auth,一步到位(SDK 已存 identity 到 Room)
                val aliasId = client.auth.loginWithMnemonic(mnemonic)

                // 从 SDK 读已持久化的身份(含服务端已存的 nickname)
                val (_, nickname) = client.auth.restoreSession() ?: (aliasId to "已恢复用户")

                // 👤 App:更新全局状态
                appViewModel.setUserInfo(aliasId, nickname)

                // 🔒 SDK:建立 WebSocket
                client.connect()
                appViewModel.setSdkReady(true)

                // 清内存中的助记词
                appViewModel.setTempMnemonic("")

                appViewModel.setRoute(AppRoute.MAIN)
            } catch (e: Exception) {
                errorMsg = "恢复失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TextButton(onClick = { appViewModel.setRoute(AppRoute.WELCOME) }) {
            Text("← 返回", color = TextMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("恢复账户", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "请输入你的 12 个助记词，单词之间以空格分隔。",
                color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("助记词", color = TextMuted) },
            placeholder = { Text("单词1 单词2 单词3 … 单词12", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
            ),
            shape = RoundedCornerShape(12.dp),
            minLines = 3,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let {
            Text(it, color = Danger, fontSize = 13.sp)
        }

        // 校验提示
        val wordCount = input.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
        if (input.isNotBlank()) {
            Text(
                "已输入 $wordCount / 12 个单词",
                color = if (wordCount == 12) Success else TextMuted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { restore() },
            enabled = wordCount == 12 && !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("恢复账户", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
