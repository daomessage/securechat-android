package space.securechat.app.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.http.ChannelPost
import space.securechat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChannelDetailScreen — 对标 Web: ChannelDetail.tsx
 * 频道帖子列表 + 发帖（仅频道 owner 可发）
 */
@Composable
fun ChannelDetailScreen(channelId: String, onBack: () -> Unit) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var channelName by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<ChannelPost>>(emptyList()) }
    var postInput by remember { mutableStateOf("") }
    var isOwner by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) {
        try {
            val detail = client.channels.getDetail(channelId)
            channelName = detail.name
            isOwner = detail.role == "owner" || detail.role == "admin"
            posts = client.channels.getPosts(channelId)
        } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(channelName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Channel", color = TextMuted, fontSize = 12.sp)
            }
        }

        // 帖子列表
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(posts, key = { it.id }) { post ->
                PostCard(post)
            }
            if (posts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No posts yet", color = TextMuted)
                    }
                }
            }
        }

        // 发帖输入（仅 owner）
        if (isOwner) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface1)
                    .padding(12.dp)
                    .imePadding(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = postInput,
                    onValueChange = { postInput = it },
                    placeholder = { Text("Write a post...", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Surface2, unfocusedBorderColor = Surface2,
                        focusedContainerColor = Surface2, unfocusedContainerColor = Surface2
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val text = postInput.trim()
                        if (text.isBlank() || isPosting) return@Button
                        isPosting = true
                        scope.launch {
                            try {
                                client.channels.post(channelId, text)
                                postInput = ""
                                posts = client.channels.getPosts(channelId)
                            } catch (_: Exception) {}
                            finally { isPosting = false }
                        }
                    },
                    enabled = postInput.isNotBlank() && !isPosting,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isPosting) CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, contentDescription = "Post", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: ChannelPost) {
    val time = remember(post.created_at) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(post.created_at) ?: Date()
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
        } catch (_: Exception) { post.created_at }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("@${post.author_alias_id}", color = BlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("·", color = TextMuted, fontSize = 12.sp)
                Text(time, color = TextMuted, fontSize = 12.sp)
            }
            Text(post.content, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
