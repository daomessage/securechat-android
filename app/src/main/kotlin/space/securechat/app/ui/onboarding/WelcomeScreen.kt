package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import space.securechat.app.ui.components.PrimaryButton
import space.securechat.app.ui.components.SecondaryButton
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel
import space.securechat.sdk.keys.KeyDerivation

/**
 * WelcomeScreen — 对齐 Web Welcome.tsx / iOS WelcomeView.swift (B 路线 2026-04-24)
 *
 * 三端视觉规范 docs/DESIGN_TOKENS.md · docs/UI_PARITY_REPORT.md P0-1
 * - gradient 标题 (blue → violet → purple) 替代老的 🔒 Logo 盒
 * - 副标题和 PWA 一致: "零知识端到端加密通讯 · 由你掌控的去中心化即时通讯"
 * - 主按钮 PrimaryButton / 次按钮 SecondaryButton
 * - 底部链接占位 (Android 里简化为静态说明, 因为没有 "install" 概念)
 */
@Composable
fun WelcomeScreen(appViewModel: AppViewModel) {
    // 品牌渐变 · blue-400 → violet-400 → purple-400 (对齐 PWA text gradient)
    val titleBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF60A5FA),    // blue-400
            Color(0xFFA78BFA),    // violet-400
            Color(0xFFC084FC),    // purple-400
        )
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            modifier = Modifier.padding(Spacing.s6),
        ) {
            // 标题区 (无 Logo 盒, 用 gradient 文字传达品牌)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                Text(
                    text = "DAO Message",
                    style = TextStyle(
                        brush = titleBrush,
                        fontSize = TextSize.xl4,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "零知识端到端加密通讯 · 由你掌控的去中心化即时通讯",
                    color = TextMutedLight,
                    fontSize = TextSize.sm,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }

            // 按钮区
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                PrimaryButton(
                    text = "创建新账户",
                    onClick = {
                        val mnemonic = KeyDerivation.newMnemonic()
                        appViewModel.setTempMnemonic(mnemonic)
                        appViewModel.setRoute(AppRoute.GENERATE_MNEMONIC)
                    },
                )
                SecondaryButton(
                    text = "恢复已有账户",
                    onClick = { appViewModel.setRoute(AppRoute.RECOVER) },
                )
            }

            // 底部说明 (Android 不需要 PWA 的 "安装到主屏" 链接, 保持空间一致)
            Text(
                text = "v1.0 · 由 DAO MESSAGE 协议驱动",
                color = TextMuted,
                fontSize = TextSize.xs,
            )
        }
    }
}
