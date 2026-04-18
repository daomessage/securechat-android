package space.securechat.app.ui.chat

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.securechat.sdk.messaging.StoredMessage
import space.securechat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * MessageBubble — 单条消息气泡
 *
 * 功能：
 *   - 自己右侧蓝色，对方左侧灰色
 *   - 消息状态图标（·/✓/✓✓）
 *   - 引用回复预览
 *   - 撤回提示
 *   - 长按触发操作菜单
 */
@Composable
fun MessageBubble(
    msg: StoredMessage,
    onLongPress: () -> Unit,
    replyPreview: StoredMessage? = null,
) {
    val isRetracted = msg.msgType == "retracted"
    val timeStr = remember(msg.time) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.time))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // 引用回复预览
            if (msg.replyToId != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.width(2.dp).height(28.dp).background(BlueAccent, RoundedCornerShape(1.dp)))
                    Column {
                        Text(
                            replyPreview?.let { if (it.isMe) "You" else "Reply" } ?: "Reply",
                            color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            replyPreview?.text?.take(60) ?: "Original message",
                            color = TextMuted, fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // 消息气泡
            Box(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (msg.isMe) 16.dp else 4.dp,
                            bottomEnd = if (msg.isMe) 4.dp else 16.dp
                        )
                    )
                    .background(if (msg.isMe) BlueAccent else Surface1)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when {
                    isRetracted -> Text(
                        "消息已撤回",
                        color = TextMuted, fontSize = 15.sp, lineHeight = 22.sp
                    )
                    msg.msgType == "image" -> {
                        // 图片占位（实际下载可走 client.downloadMedia）
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🖼️", fontSize = 18.sp)
                            Text("Image", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                    msg.msgType == "file" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📎", fontSize = 18.sp)
                            Column {
                                Text(msg.caption ?: "File", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Tap to download", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    msg.msgType == "voice" -> {
                        VoiceMessagePlayer(msg = msg)
                    }
                    msg.msgType == null || msg.msgType == "text" -> Text(
                        text = msg.text,
                        color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp
                    )
                    else -> {
                        // 未知消息类型 — 协议降级提示（§4.2 / §11）
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Unsupported message type: ${msg.msgType}",
                                color = TextMuted, fontSize = 13.sp
                            )
                            Text(
                                "Please update the app to view this message.",
                                color = BlueAccent, fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 时间 + 状态
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(timeStr, color = TextMuted, fontSize = 10.sp)
                if (msg.isMe) {
                    Text(
                        when (msg.status) {
                            "sending"   -> "·"
                            "sent"      -> "✓"
                            "delivered" -> "✓✓"
                            "read"      -> "✓✓"
                            "failed"    -> "❗"
                            else        -> ""
                        },
                        color = when (msg.status) {
                            "read"   -> BlueAccent
                            "failed" -> Danger
                            else     -> TextMuted
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * VoiceMessagePlayer — 点击播放语音气泡。
 * 播放流程：首次点击下载并解密 → 缓存到临时文件 → MediaPlayer 播放
 */
@Composable
private fun VoiceMessagePlayer(msg: StoredMessage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(msg.id) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable {
            val url = msg.mediaUrl ?: return@clickable
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
                return@clickable
            }
            // 如果 MediaPlayer 已准备好（之前播过），直接恢复
            mediaPlayer?.let {
                it.start()
                isPlaying = true
                return@clickable
            }
            isLoading = true
            scope.launch {
                try {
                    val client = space.securechat.sdk.SecureChatClient.getInstance()
                    val bytes = client.downloadMedia(msg.conversationId, url)
                    val file = java.io.File(context.cacheDir, "voice_play_${msg.id}.m4a")
                    file.writeBytes(bytes)
                    val mp = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        start()
                    }
                    mediaPlayer = mp
                    isPlaying = true
                } catch (e: Exception) {
                    android.util.Log.e("VoicePlayer", "play failed", e)
                } finally {
                    isLoading = false
                }
            }
        }.padding(4.dp)
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(TextPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                isPlaying -> Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TextPrimary, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
        val durText = msg.caption?.toLongOrNull()?.let {
            val s = (it / 1000).toInt()
            "${s}″"
        } ?: "Voice"
        Text(durText, color = TextPrimary, fontSize = 14.sp)
        // 简易波形占位（静态）
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(6, 10, 8, 12, 6, 10, 8).forEach { h ->
                Box(Modifier.size(width = 2.dp, height = h.dp).background(TextPrimary.copy(alpha = 0.6f)))
            }
        }
    }
}
