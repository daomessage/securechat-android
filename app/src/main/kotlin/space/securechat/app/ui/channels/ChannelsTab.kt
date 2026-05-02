package space.securechat.app.ui.channels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.channels.ChannelInfo
import space.securechat.sdk.channels.ChannelTradeOrder
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppViewModel

/**
 * ChannelsTab — 对标 Web: ChannelsTab.tsx
 *
 * 设计对齐:
 * - 顶栏标题「频道广场」+ Hash 图标
 * - Segmented 2-Tab(我的订阅 / 发现频道)
 * - 仅在「发现频道」显示搜索栏,输入即搜(防抖 400ms)
 * - 频道卡片:管理徽章 / 出售标签 / 我的订阅右侧 ✓
 * - FAB(右下角悬浮)创建频道
 * - 配额支付自动轮询 vanity.orderStatus,confirmed 自动重试创建
 */
@Composable
fun ChannelsTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf("mine") } // "mine" | "discover"
    var query by remember { mutableStateOf("") }

    var channels by remember { mutableStateOf<List<ChannelInfo>>(emptyList()) }
    var loadingData by remember { mutableStateOf(false) }

    var showCreate by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    // 配额支付
    var showQuotaPayment by remember { mutableStateOf(false) }
    var quotaOrder by remember { mutableStateOf<ChannelTradeOrder?>(null) }
    var pendingCreateName by remember { mutableStateOf("") }
    var pendingCreateDesc by remember { mutableStateOf("") }

    // 加载数据(初次或 tab/query 变化)
    suspend fun loadChannels() {
        loadingData = true
        try {
            if (tab == "mine") {
                channels = client.channels.getMine()
            } else {
                if (query.isBlank()) {
                    channels = emptyList()
                } else {
                    channels = client.channels.search(query.trim())
                }
            }
        } catch (_: Exception) {
        } finally {
            loadingData = false
        }
    }

    // tab 切换:重置 query + channels + 重新加载
    LaunchedEffect(tab) {
        query = ""
        channels = emptyList()
        loadChannels()
    }

    // 防抖搜索(仅 discover)
    LaunchedEffect(query, tab) {
        if (tab != "discover") return@LaunchedEffect
        delay(400)
        loadChannels()
    }

    // 配额支付轮询(每 3s 查一次,confirmed 自动关弹窗 + 重试创建)
    LaunchedEffect(showQuotaPayment, quotaOrder) {
        val order = quotaOrder ?: return@LaunchedEffect
        if (!showQuotaPayment) return@LaunchedEffect
        while (showQuotaPayment) {
            delay(3000)
            try {
                val st = client.vanity.orderStatus(order.orderId)
                if (st == "confirmed") {
                    showQuotaPayment = false
                    quotaOrder = null
                    // 自动重试创建
                    if (pendingCreateName.isNotBlank()) {
                        try {
                            client.channels.create(pendingCreateName, pendingCreateDesc)
                            channels = client.channels.getMine()
                        } catch (e: Exception) {
                            createError = e.message
                        } finally {
                            pendingCreateName = ""
                            pendingCreateDesc = ""
                        }
                    }
                    break
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(Modifier.fillMaxSize().background(DarkBg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Header & Tabs ──
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                    Text("频道广场", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Segmented Tab
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .padding(4.dp)
                ) {
                    SegmentedTabButton(
                        text = "我的订阅",
                        selected = tab == "mine",
                        onClick = { tab = "mine" },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedTabButton(
                        text = "发现频道",
                        selected = tab == "discover",
                        onClick = { tab = "discover" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 搜索栏(仅 discover)──
            if (tab == "discover") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索频道关键字...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BlueAccent.copy(0.5f), unfocusedBorderColor = Surface2,
                        focusedContainerColor = Surface1, unfocusedContainerColor = Surface1.copy(0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── 列表区 ──
            Box(Modifier.fillMaxSize()) {
                when {
                    loadingData -> {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = BlueAccent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("正在加载频道...", color = TextMuted, fontSize = 13.sp)
                        }
                    }

                    channels.isEmpty() -> {
                        EmptyState(tab = tab, query = query, onGoDiscover = { tab = "discover" })
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(channels, key = { it.id }) { ch ->
                                ChannelRow(
                                    channel = ch,
                                    isMineTab = tab == "mine",
                                    onClick = { appViewModel.setActiveChannelId(ch.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── FAB 创建频道 ──
        FloatingActionButton(
            onClick = { showCreate = true },
            containerColor = BlueAccent,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "创建频道", modifier = Modifier.size(24.dp))
        }
    }

    // ── 创建频道 Dialog ──
    if (showCreate) {
        CreateChannelDialog(
            title = newTitle, onTitleChange = { newTitle = it },
            desc = newDesc, onDescChange = { newDesc = it },
            creating = creating,
            onDismiss = {
                if (!creating) {
                    showCreate = false
                    newTitle = ""
                    newDesc = ""
                }
            },
            onCreate = {
                if (newTitle.isBlank()) return@CreateChannelDialog
                creating = true
                scope.launch {
                    try {
                        val channelId = client.channels.create(newTitle.trim(), newDesc.trim())
                        appViewModel.setActiveChannelId(channelId)
                        showCreate = false
                        newTitle = ""
                        newDesc = ""
                        // 刷新我的订阅列表
                        if (tab == "mine") loadChannels()
                    } catch (e: Exception) {
                        val msg = e.message.orEmpty()
                        if (msg.contains("QUOTA", ignoreCase = true)) {
                            // 配额满 — 保留 name/desc,关创建弹窗,开支付弹窗
                            pendingCreateName = newTitle.trim()
                            pendingCreateDesc = newDesc.trim()
                            showCreate = false
                            try {
                                quotaOrder = client.channels.buyQuota()
                                showQuotaPayment = true
                                newTitle = ""
                                newDesc = ""
                            } catch (e2: Exception) {
                                createError = "发起支付失败: ${e2.message}"
                            }
                        } else {
                            createError = msg.ifBlank { "创建失败" }
                        }
                    } finally {
                        creating = false
                    }
                }
            }
        )
    }

    // ── 配额支付 Dialog ──
    if (showQuotaPayment && quotaOrder != null) {
        QuotaPaymentDialog(
            order = quotaOrder!!,
            onDismiss = {
                showQuotaPayment = false
                quotaOrder = null
                pendingCreateName = ""
                pendingCreateDesc = ""
                tab = "mine"
                scope.launch { loadChannels() }
            }
        )
    }

    // ── 错误 Toast ──
    createError?.let { msg ->
        AlertDialog(
            onDismissRequest = { createError = null },
            containerColor = Surface1,
            title = { Text("出错了", color = TextPrimary) },
            text = { Text(msg, color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { createError = null }) { Text("确定", color = BlueAccent) }
            }
        )
    }
}

@Composable
private fun SegmentedTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "bg")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2.copy(alpha = bgAlpha))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) TextPrimary else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelInfo,
    isMineTab: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1.copy(alpha = 0.4f))
            .border(1.dp, Surface2, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .border(1.dp, Surface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tag, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }

        // Title + Description
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    channel.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (channel.role == "owner") {
                    RoleBadge(text = "管理", color = BlueAccent)
                }
                if (channel.forSale == true) {
                    SaleBadge(price = channel.salePrice ?: 0.0)
                }
            }
            Text(
                channel.description.ifBlank { "暂无介绍" },
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        // 我的订阅:右侧 ✓
        if (isMineTab) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RoleBadge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SaleBadge(price: Double) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Warning.copy(alpha = 0.15f))
            .border(1.dp, Warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Warning, modifier = Modifier.size(10.dp))
        val priceText = if (price == price.toLong().toDouble()) price.toLong().toString() else "%.2f".format(price)
        Text("$priceText USDT", color = Warning, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(tab: String, query: String, onGoDiscover: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Surface1.copy(alpha = 0.5f))
                .border(1.dp, Surface2, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (tab == "mine") Icons.Default.Tag else Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Surface2,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        if (tab == "mine") {
            Text("暂无订阅", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("去发现页面寻找感兴趣的内容吧", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onGoDiscover,
                colors = ButtonDefaults.textButtonColors(containerColor = BlueAccent.copy(alpha = 0.1f), contentColor = BlueAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("去发现", fontSize = 12.sp)
            }
        } else {
            Text(
                if (query.isNotBlank()) "查无此频道" else "输入关键字探索",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CreateChannelDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    desc: String,
    onDescChange: (String) -> Unit,
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                }
                Text("开通频道", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("频道名称 *", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = title, onValueChange = onTitleChange,
                        placeholder = { Text("例如：每日技术资讯", color = TextMuted.copy(0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("频道简介", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = desc, onValueChange = onDescChange,
                        placeholder = { Text("一句话介绍你的频道...", color = TextMuted.copy(0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = !creating && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueAccent,
                    contentColor = Color.White,
                    disabledContainerColor = Surface2,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("立即开通")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("取消", color = TextMuted)
            }
        }
    )
}

@Composable
private fun QuotaPaymentDialog(
    order: ChannelTradeOrder,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Warning.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Warning, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("扩充频道配额", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "您的免费频道额度已达上限。",
                    color = TextMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                val priceText = if (order.priceUsdt == order.priceUsdt.toLong().toDouble())
                    order.priceUsdt.toLong().toString()
                else
                    "%.2f".format(order.priceUsdt)
                Text(
                    "支付 $priceText USDT (TRC-20) 永久增加 1 个频道创建席位。",
                    color = TextMuted, fontSize = 13.sp
                )

                Spacer(Modifier.height(16.dp))

                // 收款地址
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, Surface2, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("向此地址付款", color = TextMuted, fontSize = 11.sp)
                    Text(
                        order.payTo,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        color = TextMuted,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    Text(
                        "等待链上确认中 (约 1-3 分钟)...",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(containerColor = Surface1, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("稍后查看")
            }
        }
    )
}
