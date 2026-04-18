package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel
import space.securechat.sdk.keys.KeyDerivation
import space.securechat.app.ui.theme.*

/**
 * WelcomeScreen — 对标 Web: Welcome.tsx
 *
 * 👤 App 路由决策：Register（→ generate_mnemonic）或 Recover（→ recover）
 */
@Composable
fun WelcomeScreen(appViewModel: AppViewModel) {
    Box(
        Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // Logo 区
            Box(
                Modifier
                    .size(80.dp)
                    .background(BlueAccent.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = 36.sp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "SecureChat",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Your keys. Your privacy.\nEnd-to-end encrypted.",
                    color = TextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        val mnemonic = KeyDerivation.newMnemonic()
                        appViewModel.setTempMnemonic(mnemonic)
                        appViewModel.setRoute(AppRoute.GENERATE_MNEMONIC)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                ) {
                    Text("Create New Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { appViewModel.setRoute(AppRoute.RECOVER) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Surface2)
                ) {
                    Text("Restore from Mnemonic", fontSize = 16.sp)
                }
            }

            Text(
                "v1.0.0 · Powered by SecureChat Protocol",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
