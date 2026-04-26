package space.securechat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import space.securechat.app.call.CallManager
import space.securechat.app.ui.theme.*

/**
 * CallScreen — 全屏通话覆盖层（带 WebRTC 视频渲染）
 *
 * 对标 PWA: template-app-pwa/src/components/chat/CallScreen.tsx
 */
@Composable
fun CallScreen(callManager: CallManager) {
    val state by callManager.state.collectAsState()
    val info by callManager.info.collectAsState()
    val localStream by callManager.localStream.collectAsState()
    val remoteStream by callManager.remoteStream.collectAsState()
    val micMuted by callManager.micMuted.collectAsState()
    val cameraMuted by callManager.cameraMuted.collectAsState()

    if (state == CallManager.State.IDLE || info == null) return
    val current = info ?: return

    var elapsedSec by remember(current.callId, state) { mutableIntStateOf(0) }
    LaunchedEffect(state, current.callId) {
        if (state == CallManager.State.CONNECTED) {
            elapsedSec = 0
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
    }
    val durationStr = remember(elapsedSec) {
        "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    }
    val isVideo = current.mode == CallManager.Mode.VIDEO

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // —— 远端视频（全屏）
        // 关键：AndroidView factory 只在创建时跑一次。remoteStream 是异步到达的 StateFlow,
        // 所以必须把 renderer 引用提到 Compose state, 用 LaunchedEffect(remoteStream) 显式
        // addSink/removeSink, 否则 onTrack 后视频 track 不会被挂到 SurfaceViewRenderer。
        if (isVideo) {
            val remoteRenderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            VideoSinkBinder(remoteStream, remoteRenderer.value)
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(callManager.eglBase?.eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        remoteRenderer.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 音频通话 / 视频未连接前 — 大头像
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(140.dp).clip(CircleShape).background(BlueAccent.copy(0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        current.remoteAlias.take(2).uppercase(),
                        color = TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("@${current.remoteAlias}", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // —— 本地小窗（video 通话时右上角）
        if (isVideo) {
            val localRenderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            VideoSinkBinder(localStream, localRenderer.value)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(callManager.eglBase?.eglBaseContext, null)
                            setEnableHardwareScaler(true)
                            setMirror(true)
                            localRenderer.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // —— 顶部状态
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when (state) {
                    CallManager.State.OUTGOING   -> "Calling…"
                    CallManager.State.INCOMING   -> "Incoming ${current.mode.name.lowercase()} call"
                    CallManager.State.CONNECTING -> "Connecting…"
                    CallManager.State.CONNECTED  -> durationStr
                    CallManager.State.ENDED      -> "Ended"
                    else -> ""
                },
                color = TextPrimary, fontSize = 16.sp
            )
            Text("🔒 End-to-end encrypted", color = TextMuted, fontSize = 12.sp)
        }

        // —— 底部操作
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                CallManager.State.INCOMING -> {
                    CallActionButton(
                        icon = Icons.Default.CallEnd, color = Danger, label = "Decline",
                        onClick = { callManager.reject() }
                    )
                    CallActionButton(
                        icon = Icons.Default.Call, color = Success, label = "Accept",
                        onClick = { callManager.answer() }
                    )
                }
                CallManager.State.CONNECTED, CallManager.State.CONNECTING, CallManager.State.OUTGOING -> {
                    CallActionButton(
                        icon = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        color = if (micMuted) Surface2 else Color.White.copy(alpha = 0.2f),
                        label = if (micMuted) "Unmute" else "Mute",
                        onClick = { callManager.toggleMic() }
                    )
                    CallActionButton(
                        icon = Icons.Default.CallEnd, color = Danger, label = "Hang up",
                        onClick = { callManager.hangup() }
                    )
                    if (isVideo) {
                        CallActionButton(
                            icon = if (cameraMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            color = if (cameraMuted) Surface2 else Color.White.copy(alpha = 0.2f),
                            label = if (cameraMuted) "Cam On" else "Cam Off",
                            onClick = { callManager.toggleCamera() }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * 把 stream 的第一条 videoTrack 挂到 renderer。
 * 当 stream 或 renderer 任一变化时,先 remove 旧 sink 再 add 新 sink。
 * 离开时自动 release renderer 防泄漏。
 */
@Composable
private fun VideoSinkBinder(stream: MediaStream?, renderer: SurfaceViewRenderer?) {
    DisposableEffect(stream, renderer) {
        val track: VideoTrack? = stream?.videoTracks?.firstOrNull()
        if (renderer != null && track != null) {
            try { track.addSink(renderer) } catch (_: Throwable) {}
        }
        onDispose {
            if (renderer != null && track != null) {
                try { track.removeSink(renderer) } catch (_: Throwable) {}
            }
        }
    }
    DisposableEffect(renderer) {
        onDispose {
            try { renderer?.release() } catch (_: Throwable) {}
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(color)
        ) {
            Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}
