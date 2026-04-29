package space.securechat.app.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.contacts.Friend
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppViewModel

/**
 * MessagesTab — 对标 Web: MessagesTab.tsx
 * 会话列表：展示所有已接受好友的会话条目
 */
/**
 * 单个会话的元数据(P1 加):
 * - lastText: 最近一条消息文本(用于副标题预览)
 * - lastTime: 最近一条消息时间(用于右上角时间戳)
 * - isVerified: 信任状态(用于头像角标信任徽章)
 */
private data class ConvMeta(
    val lastText: String? = null,
    val lastTime: Long = 0L,
    val isVerified: Boolean = false,
)

@Composable
fun MessagesTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var convMeta by remember { mutableStateOf<Map<String, ConvMeta>>(emptyMap()) }
    val unreadCounts by appViewModel.unreadCounts.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            val list = client.contacts.syncFriends()
            friends = list
            // 拉每个会话的最近消息 + 信任状态
            val meta = mutableMapOf<String, ConvMeta>()
            list.forEach { friend ->
                runCatching {
                    val recent = client.getHistory(friend.conversationId, limit = 1)
                    val lastMsg = recent.lastOrNull()
                    val trust = client.security.getTrustState(friend.aliasId)
                    val isVerified = trust is space.securechat.sdk.security.TrustState.Verified
                    meta[friend.conversationId] = ConvMeta(
                        lastText = lastMsg?.text,
                        lastTime = lastMsg?.time ?: 0L,
                        isVerified = isVerified,
                    )
                }
            }
            convMeta = meta
        } catch (_: Exception) {}
    }

    var deleteTarget by remember { mutableStateOf<Friend?>(null) }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("消息", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        if (friends.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💬", fontSize = 40.sp)
                    Text("还没有会话", color = TextMuted, fontSize = 16.sp)
                    Text("添加好友开始聊天", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn {
                items(friends, key = { it.conversationId }) { friend ->
                    ConversationRow(
                        friend = friend,
                        meta = convMeta[friend.conversationId] ?: ConvMeta(),
                        unread = unreadCounts[friend.conversationId] ?: 0,
                        onClick = {
                            appViewModel.setActiveChatId(friend.conversationId)
                            appViewModel.clearUnread(friend.conversationId)
                        },
                        onLongClick = { deleteTarget = friend }
                    )
                    Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }

    // 长按删除确认弹窗
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Surface1,
            title = { Text("删除会话", color = TextPrimary) },
            text = {
                Text(
                    "删除与 ${target.nickname} 的全部消息？此操作不可撤销。",
                    color = TextMuted, fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val convId = target.conversationId
                        deleteTarget = null
                        scope.launch {
                            try {
                                client.clearHistory(convId)
                                appViewModel.clearUnread(convId)
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = TextMuted) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    friend: Friend,
    meta: ConvMeta,
    unread: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 头像 + 信任徽章角标(对齐 PWA `absolute -bottom-1 -right-1`)
        // 用 Box overlay,左下角放盾形图标
        Box(modifier = Modifier.size(48.dp)) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(BlueAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friend.nickname.take(2).uppercase(),
                    color = BlueAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // 信任徽章 — 已核验绿盾,未核验黄盾(对齐 PWA MessagesTab.tsx:127-131)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(DarkBg)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (meta.isVerified) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                    contentDescription = null,
                    tint = if (meta.isVerified) Success else Warning,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 主区:nickname(主)+ 最近消息预览(副)
        // P1 决定:主标题用 nickname 不用 alias_id —— 微信/iMessage/Telegram 标准设计,
        //          alias_id 是机器标识,nickname 是用户起的可读名字,放主位用户认得出
        Column(Modifier.weight(1f)) {
            Text(
                friend.nickname.ifEmpty { "@${friend.aliasId}" },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // 副标题:最近消息预览 — 没消息时显示 alias_id 作为兜底标识
            Text(
                meta.lastText?.let { renderMessagePreview(it) } ?: "@${friend.aliasId}",
                color = if (unread > 0) TextSecondary else TextMuted,
                fontSize = 13.sp,
                fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 右侧:时间戳 + 未读徽章
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 时间戳(对齐 PWA `formatTime`)
            if (meta.lastTime > 0L) {
                Text(
                    formatRelativeTime(meta.lastTime),
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            } else {
                Spacer(Modifier.height(13.dp))  // 占位保证高度对齐
            }
            // 未读徽章
            if (unread > 0) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(BlueAccent)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else "$unread",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Spacer(Modifier.height(20.dp))  // 占位
            }
        }
    }
}

/**
 * 消息预览渲染 — 把媒体消息变成简短文字,跟 PWA `renderPreview` 保持一致
 * 对齐 PWA MessagesTab.tsx 那种 [图片] / [文件] / [语音] 的占位
 */
private fun renderMessagePreview(text: String): String {
    return when {
        text.startsWith("[img]") || text.startsWith("[image]") -> "📷 [图片]"
        text.startsWith("[file]") -> "📎 [文件]"
        text.startsWith("[voice]") -> "🎙 [语音]"
        text.startsWith("{") -> {
            // 尝试解析 JSON(SDK 1.0+ 用 {"type":"image"/"file"/"voice", ...} 序列化媒体)
            try {
                val json = org.json.JSONObject(text)
                when (json.optString("type")) {
                    "image" -> "📷 [图片]"
                    "file" -> "📎 ${json.optString("name", "[文件]")}"
                    "voice" -> "🎙 [语音]"
                    "retracted" -> "[消息已撤回]"
                    else -> text
                }
            } catch (_: Exception) {
                text
            }
        }
        else -> text
    }
}

/**
 * 简易相对时间格式化 — 跟 PWA formatTime 对齐:
 * - 今天:HH:mm
 * - 昨天:昨天
 * - 7 天内:周一~周日
 * - 更早:MM/dd
 */
private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = java.util.Calendar.getInstance()
    val msg = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val daysDiff = ((now.timeInMillis - msg.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt()

    return when {
        // 同一天
        now.get(java.util.Calendar.YEAR) == msg.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == msg.get(java.util.Calendar.DAY_OF_YEAR) -> {
            String.format("%02d:%02d",
                msg.get(java.util.Calendar.HOUR_OF_DAY),
                msg.get(java.util.Calendar.MINUTE))
        }
        daysDiff == 1 -> "昨天"
        daysDiff in 2..6 -> {
            // 周一~周日
            arrayOf("日","一","二","三","四","五","六")[
                msg.get(java.util.Calendar.DAY_OF_WEEK) - 1
            ].let { "周$it" }
        }
        else -> String.format("%d/%d",
            msg.get(java.util.Calendar.MONTH) + 1,
            msg.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
