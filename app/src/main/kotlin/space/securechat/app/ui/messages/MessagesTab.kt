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
@Composable
fun MessagesTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    val unreadCounts by appViewModel.unreadCounts.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try { friends = client.contacts.syncFriends() } catch (_: Exception) {}
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
private fun ConversationRow(friend: Friend, unread: Int, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 头像
        Box(
            Modifier
                .size(48.dp)
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

        Column(Modifier.weight(1f)) {
            Text(
                friend.nickname,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                "@${friend.aliasId}",
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (unread > 0) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(BlueAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 99) "99+" else "$unread",
                    color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // 🔒 E2EE 标志
        Text("🔒", fontSize = 12.sp)
    }
}
