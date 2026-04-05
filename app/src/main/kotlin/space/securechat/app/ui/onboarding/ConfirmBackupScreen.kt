package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * ConfirmBackupScreen — 对标 Web: ConfirmBackup.tsx
 * 随机选 3 个词位，要求用户从选项中点选正确词（防截图备份）
 */
@Composable
fun ConfirmBackupScreen(appViewModel: AppViewModel) {
    val mnemonic by appViewModel.tempMnemonic.collectAsStateWithLifecycle()
    val words = remember(mnemonic) { mnemonic.split(" ") }

    // 随机生成 3 个测试位（idx 0-11）
    val testIndices = remember { (0..11).toList().shuffled().take(3).sorted() }
    var currentStep by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }

    // 每个步骤的干扰选项（正确词 + 3 个随机词，共 4 个）
    val options = remember(testIndices) {
        testIndices.map { idx ->
            val correct = words[idx]
            val pool = words.filter { it != correct }.shuffled().take(3) + correct
            pool.shuffled()
        }
    }

    if (currentStep >= testIndices.size) {
        // 全部通过，跳靓号页
        LaunchedEffect(Unit) { appViewModel.setRoute(AppRoute.VANITY_SHOP) }
        return
    }

    val currentIdx = testIndices[currentStep]

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TextButton(onClick = { appViewModel.setRoute(AppRoute.GENERATE_MNEMONIC) }) {
            Text("← Back", color = TextMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verify Your Backup", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Step ${currentStep + 1} of ${testIndices.size}",
                color = TextMuted, fontSize = 14.sp
            )
        }

        // 进度条
        LinearProgressIndicator(
            progress = { (currentStep.toFloat() + 1) / testIndices.size },
            modifier = Modifier.fillMaxWidth(),
            color = BlueAccent,
            trackColor = Surface2
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Select word #${currentIdx + 1}",
                    color = TextMuted, fontSize = 15.sp
                )

                // 4 个选项
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(120.dp)
                ) {
                    itemsIndexed(options[currentStep]) { _, option ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface2)
                                .border(1.dp, Surface2, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (option == words[currentIdx]) {
                                        error = false
                                        currentStep++
                                    } else {
                                        error = true
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (error) {
                    Text("Incorrect. Try again.", color = Danger, fontSize = 13.sp)
                }
            }
        }
    }
}
