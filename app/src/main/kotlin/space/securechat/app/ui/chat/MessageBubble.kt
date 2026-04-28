package space.securechat.app.ui.chat

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                            replyPreview?.let { if (it.isMe) "你" else "回复" } ?: "回复",
                            color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            replyPreview?.text?.take(60) ?: "原始消息",
                            color = TextMuted, fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // 消息气泡 — 对齐 PWA `ChatWindow.tsx`:
            //   自己: bg-blue-600 (BlueAccent) — 一致 ✓
            //   对方: bg-zinc-800 (Surface2) + border-zinc-700/50 (BorderStrong copy)
            //   之前 Android 用了 Surface1(zinc-900),颜色比 PWA 更深,不一致
            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isMe) 16.dp else 4.dp,
                bottomEnd = if (msg.isMe) 4.dp else 16.dp
            )
            Box(
                Modifier
                    .clip(bubbleShape)
                    .then(
                        if (msg.isMe) Modifier
                        else Modifier.border(
                            width = 0.5.dp,
                            color = BorderStrong.copy(alpha = 0.5f),
                            shape = bubbleShape
                        )
                    )
                    .background(if (msg.isMe) BlueAccent else Surface2)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when {
                    isRetracted -> Text(
                        "消息已撤回",
                        color = TextMuted, fontSize = 15.sp, lineHeight = 22.sp
                    )
                    msg.msgType == "image" -> {
                        ImageMessageBubble(msg = msg)
                    }
                    msg.msgType == "file" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📎", fontSize = 18.sp)
                            Column {
                                Text(msg.caption ?: "文件", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("点击下载", color = TextMuted, fontSize = 11.sp)
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
                                "暂不支持的消息类型：${msg.msgType}",
                                color = TextMuted, fontSize = 13.sp
                            )
                            Text(
                                "请更新应用以查看此消息。",
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
                    // 状态图标设计(双端对齐 PWA):
                    // - sending:· 灰
                    // - sent / delivered:✓ 单勾灰 — 不区分"服务端收到"和"对方设备收到"
                    //   (跟 iMessage/Telegram 一样合并;之前 delivered 也用 ✓✓ 导致用户
                    //    误以为已读,实则只是已送达)
                    // - read:✓✓「已读」 亮蓝 — 跟上面拉开视觉差距
                    // - failed:❗ 红
                    val (symbol, withLabel) = when (msg.status) {
                        "sending"   -> "·" to false
                        "sent"      -> "✓" to false
                        "delivered" -> "✓" to false   // 和 sent 视觉一致
                        "read"      -> "✓✓" to true
                        "failed"    -> "❗" to false
                        else        -> "" to false
                    }
                    val color = when (msg.status) {
                        "read"   -> BlueAccent
                        "failed" -> Danger
                        else     -> TextMuted
                    }
                    Text(symbol, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (withLabel) {
                        Text("已读", color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
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
                isPlaying -> Icon(Icons.Default.Stop, contentDescription = "停止", tint = TextPrimary, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
        // SDK 序列化 voice 消息为 JSON: {"type":"voice","key":"...","durationMs":N}
        // 解析 msg.text 获取时长，兼容旧格式 "[voice]key|durationMs"
        val durText = try {
            val json = org.json.JSONObject(msg.text ?: "")
            val ms = json.optLong("durationMs", -1L)
            if (ms >= 0) "${(ms / 1000).toInt()}″" else null
        } catch (_: Exception) {
            // 旧格式兜底：[voice]key|durationMs
            msg.text?.removePrefix("[voice]")?.split("|")?.getOrNull(1)
                ?.toLongOrNull()?.let { "${(it / 1000).toInt()}″" }
        } ?: "语音"
        Text(durText, color = TextPrimary, fontSize = 14.sp)
        // 简易波形占位（静态）
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(6, 10, 8, 12, 6, 10, 8).forEach { h ->
                Box(Modifier.size(width = 2.dp, height = h.dp).background(TextPrimary.copy(alpha = 0.6f)))
            }
        }
    }
}


/**
 * ImageMessageBubble — E2EE 图片气泡
 * 流程: 首次 render 启动 异步下载+解密 → 写到 cache → Coil 辩识 file:// URI 渲染
 *         点击图片 → 全屏查看(Intent.ACTION_VIEW)
 */
@Composable
private fun ImageMessageBubble(msg: StoredMessage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cachedFile by remember(msg.id) { mutableStateOf<java.io.File?>(null) }
    var loading by remember(msg.id) { mutableStateOf(true) }
    var error by remember(msg.id) { mutableStateOf<String?>(null) }

    // 首次进入异步下载解密
    LaunchedEffect(msg.id) {
        val url = msg.mediaUrl
        if (url.isNullOrEmpty()) { error = "No URL"; loading = false; return@LaunchedEffect }
        val dir = java.io.File(context.cacheDir, "decrypted_images").apply { mkdirs() }
        val f = java.io.File(dir, "img_${msg.id}.bin")
        if (f.exists() && f.length() > 0) {
            cachedFile = f
            loading = false
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val client = space.securechat.sdk.SecureChatClient.getInstance()
                val bytes = client.downloadMedia(msg.conversationId, url)
                f.writeBytes(bytes)
                cachedFile = f
            } catch (e: Exception) {
                android.util.Log.e("ImageBubble", "download failed", e)
                error = e.message ?: "download failed"
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TextMuted.copy(alpha = 0.15f))
            .clickable(enabled = cachedFile != null) {
                val f = cachedFile ?: return@clickable
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🖨", fontSize = 24.sp)
                Text("图片无法显示", color = TextMuted, fontSize = 12.sp)
            }
            cachedFile != null -> coil.compose.AsyncImage(
                model = cachedFile!!,
                contentDescription = "encrypted image",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    }
}
