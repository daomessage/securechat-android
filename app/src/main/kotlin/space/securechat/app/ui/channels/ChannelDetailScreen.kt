@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package space.securechat.app.ui.channels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.channels.ChannelTradeOrder
import space.securechat.sdk.http.ChannelPost
import space.securechat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChannelDetailScreen — 对标 PWA: ChannelDetail.tsx
 *
 * 13 处对齐：
 *  1. 顶栏副标题改为 description
 *  2. Owner 角标
 *  3. 挂牌中标识
 *  4. 订阅胶囊按钮 + BellRing/BellOff 图标
 *  5. 全屏 Markdown 撰文器（点击底部"发布广播"打开）
 *  6. Owner 底部双按钮：发布广播 + 挂牌出售
 *  7. 买家底部独立购买按钮
 *  8. ListForSaleDialog
 *  9. ChannelPaymentDialog（倒计时 + 复制 + 自动轮询）
 * 10. 空态文案
 * 11. Markdown 文章角标
 * 12. PostCard：内容前置，作者+时间在底部
 * 13. WebSocket 实时推送（订阅 events.channelPost）
 */
@Composable
fun ChannelDetailScreen(channelId: String, onBack: () -> Unit) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()

    var channelName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<ChannelPost>>(emptyList()) }
    var isOwner by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }
    var subbing by remember { mutableStateOf(false) }
    var forSale by remember { mutableStateOf(false) }
    var salePrice by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 三个浮层状态
    var showComposer by remember { mutableStateOf(false) }
    var showListForSale by remember { mutableStateOf(false) }
    var listing by remember { mutableStateOf(false) }
    var tradeOrder by remember { mutableStateOf<ChannelTradeOrder?>(null) }
    var buying by remember { mutableStateOf(false) }
    var sendingArticle by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    suspend fun loadData() {
        try {
            val detail = client.channels.getDetail(channelId)
            channelName = detail.name
            description = detail.description
            isOwner = detail.role == "owner" || detail.role == "admin"
            isSubscribed = detail.isSubscribed == true
            forSale = detail.forSale == true
            salePrice = detail.salePrice
            posts = client.channels.getPosts(channelId).reversed()
            loadError = null
            // 滚到底
            if (posts.isNotEmpty()) {
                listState.scrollToItem(posts.size - 1)
            }
        } catch (e: Exception) {
            loadError = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(channelId) { loadData() }

    // 实时推送：监听 events.channelPost，命中本频道就 refresh
    LaunchedEffect(channelId) {
        client.events.channelPost.collect { ev ->
            if (ev.channelId == channelId) {
                try {
                    posts = client.channels.getPosts(channelId).reversed()
                    if (posts.isNotEmpty()) {
                        listState.animateScrollToItem(posts.size - 1)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ─── 全屏撰文器分支 ──────────────
    if (showComposer) {
        ArticleComposerScreen(
            publishing = sendingArticle,
            onClose = { showComposer = false },
            onPublish = { content ->
                if (content.isBlank() || sendingArticle) return@ArticleComposerScreen
                sendingArticle = true
                scope.launch {
                    try {
                        client.channels.post(channelId, content, "markdown")
                        showComposer = false
                        loadData()
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "发布失败")
                    } finally {
                        sendingArticle = false
                    }
                }
            }
        )
        return
    }

    // ─── 主页面 ──────────────────
    Box(Modifier.fillMaxSize().background(DarkBg)) {
        if (loading && posts.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = TextMuted, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("加载频道中...", color = TextMuted, fontSize = 13.sp)
            }
            return@Box
        }

        if (loadError != null && posts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(loadError!!, color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2)
                ) { Text("返回", color = TextPrimary) }
            }
            return@Box
        }

        Column(Modifier.fillMaxSize()) {
            // ─── 顶栏 ────────────
            Row(
                Modifier.fillMaxWidth().background(DarkBg).padding(horizontal = 4.dp, vertical = 10.dp)
                    .border(0.dp, BorderDefault),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextMutedLight)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channelName.ifBlank { "频道资料" },
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isOwner) {
                            Spacer(Modifier.width(8.dp))
                            OwnerBadge()
                        }
                    }
                    Text(
                        description.ifBlank { "暂无简介" },
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    if (forSale) {
                        Spacer(Modifier.height(4.dp))
                        ForSaleBadge(price = salePrice)
                    }
                }
                if (!isOwner) {
                    SubscribeToggleButton(
                        isSubscribed = isSubscribed,
                        loading = subbing,
                        onClick = {
                            subbing = true
                            scope.launch {
                                try {
                                    if (isSubscribed) {
                                        client.channels.unsubscribe(channelId)
                                        isSubscribed = false
                                    } else {
                                        client.channels.subscribe(channelId)
                                        isSubscribed = true
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "操作失败")
                                } finally {
                                    subbing = false
                                }
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Divider(color = BorderDefault, thickness = 1.dp)

            // ─── 帖子列表 ────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (posts.isEmpty()) {
                    item { EmptyChannelState() }
                } else {
                    items(posts, key = { it.id }) { post ->
                        PostCard(post)
                    }
                }
            }

            // ─── 底部操作区 ────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Owner：发布广播 + 挂牌出售
                if (isOwner) {
                    Button(
                        onClick = { showComposer = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("发布广播", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    if (!forSale) {
                        OutlinedButton(
                            onClick = { showListForSale = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface1),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Sell, contentDescription = null, tint = Warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("挂牌出售此频道", color = Warning, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
                // 买家：购买按钮
                if (!isOwner && forSale) {
                    Button(
                        onClick = {
                            buying = true
                            scope.launch {
                                try {
                                    tradeOrder = client.channels.buyChannel(channelId)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "下单失败")
                                } finally {
                                    buying = false
                                }
                            }
                        },
                        enabled = !buying,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success, disabledContainerColor = Surface2),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (buying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "购买此频道 · ${salePrice?.toInt() ?: 0} USDT",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ─── Snackbar ────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }

    // ─── 挂牌出售弹窗 ────────────
    if (showListForSale) {
        ListForSaleDialog(
            channelName = channelName,
            submitting = listing,
            onClose = { if (!listing) showListForSale = false },
            onSubmit = { price ->
                listing = true
                scope.launch {
                    try {
                        client.channels.listForSale(channelId, price)
                        showListForSale = false
                        loadData()
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "挂牌失败")
                    } finally {
                        listing = false
                    }
                }
            }
        )
    }

    // ─── 购买支付弹窗 ────────────
    val order = tradeOrder
    if (order != null) {
        ChannelPaymentDialog(
            order = order,
            channelName = channelName,
            onClose = { tradeOrder = null },
            onConfirmed = {
                tradeOrder = null
                scope.launch { loadData() }
            }
        )
    }
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  顶栏小组件
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
@Composable
private fun OwnerBadge() {
    Box(
        modifier = Modifier
            .background(BrandPrimary.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .border(1.dp, BrandPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("OWNER", color = BrandPrimaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ForSaleBadge(price: Double?) {
    Row(
        modifier = Modifier
            .background(Warning.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, Warning.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Sell, contentDescription = null, tint = Warning, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "出售中 · ${price?.toInt() ?: 0} USDT",
            color = Warning, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SubscribeToggleButton(
    isSubscribed: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSubscribed) Surface1 else BrandPrimary
    val fg = if (isSubscribed) TextMutedLight else Color.White
    val borderColor = if (isSubscribed) BorderDefault else Color.Transparent
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(enabled = !loading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        } else {
            Icon(
                if (isSubscribed) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            if (isSubscribed) "已订阅" else "订阅",
            color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  PostCard — 内容前置，作者+时间在底部
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
@Composable
private fun PostCard(post: ChannelPost) {
    val timeText = remember(post.created_at) { formatPostTime(post.created_at) }
    val isMarkdown = post.type == "markdown"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .border(1.dp, BorderDefault.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Markdown 文章角标
        if (isMarkdown) {
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Article, contentDescription = null, tint = BrandPrimaryText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("文章", color = BrandPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Divider(color = BorderDefault.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(bottom = 10.dp))
        }

        // 内容
        Text(
            renderMarkdownPreview(post.content),
            color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp
        )

        // 分隔线 + author + time（底部）
        Spacer(Modifier.height(12.dp))
        Divider(color = BorderDefault.copy(alpha = 0.5f), thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDefault.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    "来自: ${post.author_alias_id}",
                    color = TextMutedLight, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                timeText,
                color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyChannelState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Surface1, CircleShape)
                .border(1.dp, BorderDefault, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("📭", fontSize = 28.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("暂无频道广播", color = TextMutedLight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("这里是被时间遗忘的角落", color = TextMuted, fontSize = 11.sp)
    }
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  全屏 Markdown 撰文器
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
@Composable
private fun ArticleComposerScreen(
    publishing: Boolean,
    onClose: () -> Unit,
    onPublish: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        // 顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, enabled = !publishing) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextMutedLight)
            }
            Text("撰写文章", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            // 编辑 / 预览 切换
            Row(
                modifier = Modifier
                    .background(Surface1, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDefault.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                ComposerToggle("编辑", icon = Icons.Default.Edit, selected = !showPreview, accent = false) {
                    showPreview = false
                }
                ComposerToggle("预览", icon = Icons.Default.RemoveRedEye, selected = showPreview, accent = true) {
                    showPreview = true
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (content.trim().isNotEmpty()) onPublish(content.trim()) },
                enabled = content.trim().isNotEmpty() && !publishing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, disabledContainerColor = Surface2),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (publishing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("发布", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Divider(color = BorderDefault, thickness = 1.dp)

        // 主体编辑/预览区
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (!showPreview) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface1.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "MARKDOWN 编辑",
                            color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(BrandPrimary),
                        decorationBox = { inner ->
                            if (content.isEmpty()) {
                                Text(
                                    "# 文章标题\n\n在此撰写您的频道文章…\n\n支持基础 Markdown 语法：\n- **粗体**、*斜体*\n- 标题、列表",
                                    color = TextMuted.copy(alpha = 0.6f),
                                    fontSize = 15.sp, lineHeight = 24.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            inner()
                        }
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface1.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("实时预览", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        if (content.trim().isEmpty()) {
                            Text("开始输入后这里会显示预览…", color = TextMuted, fontSize = 13.sp)
                        } else {
                            Text(
                                renderMarkdownPreview(content),
                                color = TextPrimary, fontSize = 15.sp, lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
        }

        // 底部状态栏
        Divider(color = BorderDefault, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface1.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${content.length} 字符 · ${content.split('\n').size} 行",
                color = TextMuted, fontSize = 9.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(Danger, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("免打扰广播 · Markdown", color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ComposerToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        selected && accent -> BrandPrimary
        selected -> SurfaceHover
        else -> Color.Transparent
    }
    val fg = if (selected) Color.White else TextMuted
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  挂牌出售 Dialog
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
@Composable
private fun ListForSaleDialog(
    channelName: String,
    submitting: Boolean,
    onClose: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface1,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Warning.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Sell, contentDescription = null, tint = Warning, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("挂牌出售", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        channelName, color = TextMuted, fontSize = 10.sp, maxLines = 1
                    )
                }
            }
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "📢 挂牌后频道将在搜索结果中展示售价标签，其他用户可直接购买。",
                            color = TextMutedLight, fontSize = 11.sp, lineHeight = 16.sp
                        )
                        Text(
                            "⚠️ 交易完成后，频道所有权将自动转移给买家（你将降为普通订阅者）。",
                            color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("售价 (USDT) *", color = TextMutedLight, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { v -> if (v.all { it.isDigit() }) price = v },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：100", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Warning, unfocusedBorderColor = BorderDefault,
                        focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                        cursorColor = Warning
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = price.toIntOrNull() ?: 0
                    if (v > 0) onSubmit(v)
                },
                enabled = (price.toIntOrNull() ?: 0) > 0 && !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Warning, disabledContainerColor = Surface2),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (submitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("确认挂牌", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !submitting) {
                Text("取消", color = TextMutedLight)
            }
        }
    )
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  购买支付 Dialog（倒计时 + 复制 + 自动轮询）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
@Composable
private fun ChannelPaymentDialog(
    order: ChannelTradeOrder,
    channelName: String,
    onClose: () -> Unit,
    onConfirmed: () -> Unit
) {
    val ctx = LocalContext.current
    val client = SecureChatClient.getInstance()

    var countdown by remember { mutableStateOf(formatCountdown(order.expiredAt)) }
    var polling by remember { mutableStateOf(false) }
    var pollError by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val expiredAt = order.expiredAt

    // 1秒倒计时
    LaunchedEffect(expiredAt) {
        while (true) {
            countdown = formatCountdown(expiredAt)
            delay(1000)
        }
    }

    // 3秒轮询订单状态
    LaunchedEffect(polling) {
        if (!polling) return@LaunchedEffect
        while (polling) {
            delay(3000)
            try {
                val st = client.vanity.orderStatus(order.orderId)
                if (st == "confirmed") {
                    polling = false
                    onConfirmed()
                    return@LaunchedEffect
                } else if (st == "expired") {
                    polling = false
                    pollError = "订单已过期，请重新购买。"
                    return@LaunchedEffect
                }
            } catch (_: Exception) {
                // 网络异常继续轮询
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface1,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Success.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("购买频道", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("USDT-TRC20 链上支付", color = TextMuted, fontSize = 9.sp)
                }
            }
        },
        text = {
            Column {
                // 频道信息卡
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Success.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .border(1.dp, Success.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("你将获得的频道", color = SuccessText, fontSize = 11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(channelName, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text("${order.priceUsdt} USDT", color = TextMutedLight, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))

                // 收款地址
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "TRON (TRC-20) 收款地址",
                        color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            order.payTo,
                            color = TextSecondary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("TRON Address", order.payTo))
                                copied = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                tint = if (copied) SuccessText else TextMutedLight,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("请向此地址转入恰好 ", color = TextMuted, fontSize = 9.sp)
                        Text("${order.priceUsdt} USDT", color = Warning, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 倒计时
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("订单将在 ", color = TextMuted, fontSize = 11.sp)
                    Text(
                        countdown,
                        color = if (countdown == "已过期") Danger else Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(" 后过期", color = TextMuted, fontSize = 11.sp)
                }

                // 错误
                if (pollError != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Danger.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .border(1.dp, Danger.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(pollError!!, color = Danger, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "支付确认后频道所有权将自动转移到你的账户",
                    color = TextMuted, fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (!polling) {
                Button(
                    onClick = { polling = true; pollError = null },
                    enabled = countdown != "已过期",
                    colors = ButtonDefaults.buttonColors(containerColor = Success, disabledContainerColor = Surface2),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("我已付款，等待确认", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Success.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = SuccessText, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在确认链上支付...", color = SuccessText, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            if (!polling) {
                TextButton(onClick = onClose) {
                    Text("取消", color = TextMutedLight)
                }
            }
        }
    )
}

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  工具函数
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
private fun formatPostTime(raw: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = sdf.parse(raw) ?: Date()
    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
} catch (_: Exception) { raw }

private fun formatCountdown(expiresAt: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val target = sdf.parse(expiresAt)?.time ?: 0L
        val diff = target - System.currentTimeMillis()
        if (diff <= 0) return "已过期"
        val m = diff / 60000
        val s = (diff % 60000) / 1000
        "${m}:${s.toString().padStart(2, '0')}"
    } catch (_: Exception) { "—" }
}

/**
 * 极简 Markdown 预览：支持 # 标题、**粗体**、列表、换行。
 */
private fun renderMarkdownPreview(raw: String): String {
    if (raw.isBlank()) return ""
    val lines = raw.split("\n").map { line ->
        when {
            line.startsWith("### ") -> "▸ ${line.removePrefix("### ")}"
            line.startsWith("## ")  -> "▸▸ ${line.removePrefix("## ")}"
            line.startsWith("# ")   -> "■ ${line.removePrefix("# ")}"
            line.startsWith("- ") || line.startsWith("* ") -> "• ${line.drop(2)}"
            else -> line
        }
    }
    return lines.joinToString("\n").replace("**", "")
}
