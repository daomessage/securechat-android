@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
    var isSubscribed by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var forSale by remember { mutableStateOf(false) }
    var salePrice by remember { mutableStateOf<Double?>(null) }
    var showBuyDialog by remember { mutableStateOf(false) }
    var buyOrder by remember { mutableStateOf<space.securechat.sdk.channels.ChannelTradeOrder?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            errorMsg = null
        }
    }

    LaunchedEffect(channelId) {
        try {
            val detail = client.channels.getDetail(channelId)
            channelName = detail.name
            isOwner = detail.role == "owner" || detail.role == "admin"
            isSubscribed = detail.isSubscribed == true
            forSale = detail.forSale == true
            salePrice = detail.salePrice
            posts = client.channels.getPosts(channelId)
        } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize()) {
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
                Text(if (isSubscribed) "Subscribed" else "Channel", color = TextMuted, fontSize = 12.sp)
            }
            // For-sale 购买按钮（owner 不显示）
            if (!isOwner && forSale) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                buyOrder = client.channels.buyChannel(channelId)
                                showBuyDialog = true
                            } catch (e: Exception) { errorMsg = e.message }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Buy ${salePrice?.toInt() ?: 0} USDT", color = TextPrimary, fontSize = 12.sp)
                }
            }
            // 订阅/退订 toggle
            if (!isOwner) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (isSubscribed) {
                                client.channels.unsubscribe(channelId)
                                isSubscribed = false
                            } else {
                                client.channels.subscribe(channelId)
                                isSubscribed = true
                            }
                        } catch (_: Exception) {}
                    }
                }) {
                    Text(
                        if (isSubscribed) "Unsubscribe" else "Subscribe",
                        color = if (isSubscribed) TextMuted else BlueAccent,
                        fontSize = 13.sp
                    )
                }
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
            var isMarkdown by remember { mutableStateOf(false) }
            var showPreview by remember { mutableStateOf(false) }

            Column(Modifier.fillMaxWidth().background(Surface1).padding(12.dp).imePadding()) {
                // Markdown 工具栏
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = isMarkdown,
                        onClick = { isMarkdown = !isMarkdown },
                        label = { Text("Markdown", fontSize = 11.sp) },
                        leadingIcon = if (isMarkdown) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                    if (isMarkdown) {
                        FilterChip(
                            selected = showPreview,
                            onClick = { showPreview = !showPreview },
                            label = { Text(if (showPreview) "编辑" else "预览", fontSize = 11.sp) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showPreview && isMarkdown) {
                        // 极简预览：# 标题、**粗体**、换行
                        Box(
                            Modifier.weight(1f).background(Surface2, RoundedCornerShape(12.dp)).padding(12.dp).heightIn(min = 56.dp)
                        ) {
                            Text(
                                renderMarkdownPreview(postInput),
                                color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = postInput,
                            onValueChange = { postInput = it },
                            placeholder = {
                                Text(
                                    if (isMarkdown) "# Title\\n**bold** *italic*\\n- list" else "Write a post...",
                                    color = TextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Surface2, unfocusedBorderColor = Surface2,
                                focusedContainerColor = Surface2, unfocusedContainerColor = Surface2
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = if (isMarkdown) 8 else 4,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val text = postInput.trim()
                            if (text.isBlank() || isPosting) return@Button
                            isPosting = true
                            scope.launch {
                                try {
                                    client.channels.post(channelId, text, if (isMarkdown) "markdown" else "text")
                                    postInput = ""
                                    showPreview = false
                                    posts = client.channels.getPosts(channelId)
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Failed to post"
                                } finally {
                                    isPosting = false
                                }
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
                } // Row
            } // Column
        }
    }
    if (showBuyDialog) {
        val o = buyOrder
        AlertDialog(
            onDismissRequest = { showBuyDialog = false; buyOrder = null },
            containerColor = Surface1,
            title = { Text("Buy Channel", color = TextPrimary) },
            text = {
                if (o == null) {
                    Text("Loading...", color = TextMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Price: ${o.priceUsdt} USDT", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Pay to: ${o.payTo}", color = TextMuted, fontSize = 12.sp)
                        Text("Expires: ${o.expiredAt}", color = TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBuyDialog = false; buyOrder = null }) {
                    Text("I've Paid", color = BlueAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuyDialog = false; buyOrder = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
    )
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

/**
 * 极简 Markdown 预览：支持 # 标题、**粗体**、换行。
 * 生产用建议接 Markwon 或 compose-markdown；此处满足"基础预览"需求。
 */
private fun renderMarkdownPreview(raw: String): String {
    if (raw.isBlank()) return "预览为空"
    val lines = raw.split("\n").map { line ->
        when {
            line.startsWith("### ")  -> "▸ ${line.removePrefix("### ")}"
            line.startsWith("## ")   -> "▸▸ ${line.removePrefix("## ")}"
            line.startsWith("# ")    -> "■ ${line.removePrefix("# ")}"
            line.startsWith("- ") || line.startsWith("* ") -> "• ${line.removePrefix(line.take(2))}"
            else -> line
        }
    }
    // 粗体 **xx** 简化为 "xx"（不支持富文本，仅文字显示）
    return lines.joinToString("\n").replace("**", "")
}
