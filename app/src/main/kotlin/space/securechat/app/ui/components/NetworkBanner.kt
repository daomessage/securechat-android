package space.securechat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckCircle
import kotlinx.coroutines.delay
import space.securechat.app.ui.theme.*
import space.securechat.sdk.messaging.WSTransport.NetworkState

/**
 * NetworkBanner · 网络状态横幅
 * 对齐 PWA NetworkBanner.tsx / iOS NetworkBanner.swift
 *
 * 行为:
 *  - disconnected → 红色横幅 "网络连接已断开"
 *  - connecting   → 黄色横幅 "正在重新连接..." + 旋转 spinner
 *  - connected    → 从非 connected 过来时, 显示绿色 "网络已恢复" 2 秒后隐藏
 *  - 稳定 connected → 不渲染
 *
 * 尺寸: 高 32dp (text-xs 12 + py-1.5), 贴顶部置于 TopBar 之下
 */
@Composable
fun NetworkBanner(state: NetworkState) {
    // 记住上一个状态 · 用于"恢复"过渡
    var prev by remember { mutableStateOf<NetworkState>(NetworkState.Connecting) }
    var showRecovered by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val wasDown = prev !is NetworkState.Connected && prev !is NetworkState.Connecting
        if (state is NetworkState.Connected && wasDown) {
            showRecovered = true
            delay(2000)
            showRecovered = false
        }
        prev = state
    }

    val visible = showRecovered ||
        state is NetworkState.Disconnected ||
        state is NetworkState.Connecting ||
        state is NetworkState.Kicked

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
    ) {
        when {
            showRecovered -> BannerContent(
                bg = Success,
                text = "网络已恢复",
                showSpinner = false,
            )
            state is NetworkState.Disconnected -> BannerContent(
                bg = Danger,
                text = "网络连接已断开,请检查网络设置",
                showSpinner = false,
            )
            state is NetworkState.Connecting -> BannerContent(
                bg = Warning,
                text = "正在重新连接...",
                showSpinner = true,
            )
            state is NetworkState.Kicked -> BannerContent(
                bg = Danger,
                text = "已在其他设备登录",
                showSpinner = false,
            )
            else -> {}
        }
    }
}

@Composable
private fun BannerContent(bg: Color, text: String, showSpinner: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = Spacing.s4, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = TextPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(Spacing.s2))
        }
        Text(
            text = text,
            color = TextPrimary,
            fontSize = TextSize.xs,
            fontWeight = FontWeight.Medium,
        )
    }
}
