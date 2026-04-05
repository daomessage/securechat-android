package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * GenerateMnemonicScreen — 对标 Web: GenerateMnemonic.tsx
 * 展示 12 词助记词，要求用户备份后再继续
 */
@Composable
fun GenerateMnemonicScreen(appViewModel: AppViewModel) {
    val mnemonic by appViewModel.tempMnemonic.collectAsStateWithLifecycle()
    val words = remember(mnemonic) { mnemonic.split(" ") }
    var checkedBackup by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 顶部 back
        TextButton(onClick = { appViewModel.setRoute(AppRoute.WELCOME) }) {
            Text("← Back", color = TextMuted)
        }

        Text("Your Recovery Phrase", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // 警告
        Card(
            colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️", fontSize = 14.sp)
                Text(
                    "Write down these 12 words in order. Never share them with anyone. " +
                    "They are the only way to recover your account.",
                    color = Danger,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        // 12 词宫格
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(words) { index, word ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface1),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "${index + 1}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            word,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 确认勾选
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checkedBackup,
                onCheckedChange = { checkedBackup = it },
                colors = CheckboxDefaults.colors(checkedColor = BlueAccent)
            )
            Text("I've safely written down my recovery phrase", color = TextMuted, fontSize = 14.sp)
        }

        Button(
            onClick = { appViewModel.setRoute(AppRoute.CONFIRM_BACKUP) },
            enabled = checkedBackup,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
