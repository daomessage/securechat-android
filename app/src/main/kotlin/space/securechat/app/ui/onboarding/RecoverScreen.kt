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
 * 输入 12 词助记词恢复账号（直接调用 restoreSession 或验证助记词后注册）
 *
 * 流程：validateMnemonic → 存 tempMnemonic → 跳 set_nickname
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
            errorMsg = "Invalid mnemonic. Please check your 12 words."
            return
        }
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                // 助记词合法 → 存入内存，走 set_nickname 完成注册/登录
                appViewModel.setTempMnemonic(mnemonic)
                appViewModel.setRoute(AppRoute.SET_NICKNAME)
            } catch (e: Exception) {
                errorMsg = "Recovery failed: ${e.message}"
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
            Text("← Back", color = TextMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Restore Account", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Enter your 12-word recovery phrase, separated by spaces.",
                color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Recovery Phrase", color = TextMuted) },
            placeholder = { Text("word1 word2 word3 ... word12", color = TextMuted) },
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
                "$wordCount / 12 words",
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
                Text("Restore Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
