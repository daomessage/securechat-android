@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package space.securechat.app.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.contacts.Friend
import space.securechat.sdk.contacts.UserProfile
import space.securechat.app.ui.components.QrCodeImage
import space.securechat.app.ui.components.QrScannerLauncher
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppViewModel

/**
 * ContactsTab — 对标 Web: ContactsTab.tsx
 *
 * 面板切换：Friends（已接受）/ Requests（待处理）/ Add（搜索添加）
 */
@Composable
fun ContactsTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    // 全部好友记录 — 用 friendStatus / friendDirection 拆分展示
    var allRecords by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<UserProfile?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    val accepted = remember(allRecords) { allRecords.filter { it.friendStatus == "accepted" } }
    val pendingReceived = remember(allRecords) {
        allRecords.filter { it.friendStatus == "pending" && it.friendDirection == "received" }
    }
    val pendingSent = remember(allRecords) {
        allRecords.filter { it.friendStatus == "pending" && it.friendDirection == "sent" }
    }

    suspend fun reload() {
        try {
            client.contacts.refresh()
            allRecords = client.contacts.friends
            appViewModel.setPendingRequestCount(
                allRecords.count { it.friendStatus == "pending" && it.friendDirection == "received" }
            )
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { scope.launch { reload() } }

    var showMyQr by remember { mutableStateOf(false) }
    val userInfo by appViewModel.userInfo.collectAsState()

    // 扫码：返回 alias_id（兼容 securechat://add?aliasId=xxx 与裸 alias）
    val qrLauncher = QrScannerLauncher { rawText ->
        val alias = parseAliasFromQr(rawText)
        if (alias != null) {
            scope.launch {
                try {
                    client.contacts.sendFriendRequest(alias)
                    successMsg = "已向 @$alias 发送好友请求"
                    reload()
                } catch (e: Exception) { errorMsg = e.message ?: "失败" }
            }
        } else {
            errorMsg = "无效的二维码"
        }
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        // 顶栏 — 标题 + 扫码 + 我的二维码
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "联系人",
                color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { qrLauncher.launch() }) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "扫描二维码", tint = BlueAccent)
            }
            IconButton(onClick = { showMyQr = true }) {
                Icon(Icons.Default.QrCode2, contentDescription = "我的二维码", tint = BlueAccent)
            }
        }

        // 子 Tab — Friends / Requests / Add
        val tabs = listOf("好友", "请求", "添加")
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkBg,
            contentColor = BlueAccent,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = BlueAccent
                )
            }
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = {
                        if (idx == 1 && pendingReceived.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${pendingReceived.size}", fontSize = 10.sp) } }) {
                                Text(label, color = if (selectedSubTab == idx) BlueAccent else TextMuted)
                            }
                        } else {
                            Text(label, color = if (selectedSubTab == idx) BlueAccent else TextMuted)
                        }
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> FriendsList(
                friends = accepted,
                onOpenChat = { conv ->
                    appViewModel.setActiveChatId(conv)
                    appViewModel.clearUnread(conv)
                }
            )
            1 -> RequestsList(
                received = pendingReceived,
                sent = pendingSent,
                onAccept = { friendshipId ->
                    // 乐观更新:立刻把 UI 里那条 pending 标为 accepted,不等服务器 200
                    // 用户点完按钮 UI 立刻有反应,不用盯着转圈等两三秒
                    allRecords = allRecords.map {
                        if (it.friendshipId == friendshipId) it.copy(friendStatus = "accepted") else it
                    }
                    appViewModel.setPendingRequestCount(
                        allRecords.count { it.friendStatus == "pending" && it.friendDirection == "received" }
                    )
                    scope.launch {
                        try {
                            client.contacts.acceptFriendRequest(friendshipId)
                            successMsg = "已接受好友请求"
                            // 成功后再 reload 一次拿 conv_id 等服务端字段
                            reload()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "接受失败"
                            // 失败则回滚:重新 reload 拿回真实状态
                            reload()
                        }
                    }
                }
            )
            2 -> AddFriendPanel(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                result = searchResult,
                isSearching = isSearching,
                errorMsg = errorMsg,
                successMsg = successMsg,
                onSearch = {
                    isSearching = true; errorMsg = null; searchResult = null; successMsg = null
                    scope.launch {
                        try { searchResult = client.contacts.lookupUser(searchQuery.trim()) }
                        catch (e: Exception) { errorMsg = "找不到该用户" }
                        finally { isSearching = false }
                    }
                },
                onSendRequest = { aliasId ->
                    scope.launch {
                        try {
                            client.contacts.sendFriendRequest(aliasId)
                            successMsg = "好友请求已发送！"
                            searchResult = null
                            reload()
                        } catch (e: Exception) { errorMsg = e.message }
                    }
                }
            )
        }
    }

    if (showMyQr) {
        val payload = "securechat://add?aliasId=${userInfo.aliasId}"
        AlertDialog(
            onDismissRequest = { showMyQr = false },
            containerColor = Surface1,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("我的二维码", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.padding(8.dp).background(TextPrimary, RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        QrCodeImage(content = payload, sizeDp = 220)
                    }
                    Text(userInfo.nickname.ifEmpty { "我" }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("@${userInfo.aliasId}", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyQr = false }) {
                    Text("关闭", color = BlueAccent)
                }
            }
        )
    }
}

@Composable
private fun FriendsList(friends: List<Friend>, onOpenChat: (String) -> Unit) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("👥", fontSize = 40.sp)
                Text("还没有好友", color = TextMuted, fontSize = 16.sp)
                Text("到「添加」标签页查找联系人", color = TextMuted, fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(friends, key = { it.aliasId }) { friend ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(friend.conversationId) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(BlueAccent.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(friend.nickname.take(2).uppercase(), color = BlueAccent, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(friend.nickname, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("@${friend.aliasId}", color = TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
            }
        }
    }
}

@Composable
private fun AddFriendPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    result: UserProfile?,
    isSearching: Boolean,
    errorMsg: String?,
    successMsg: String?,
    onSearch: () -> Unit,
    onSendRequest: (String) -> Unit
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("按 Alias ID 搜索...", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                    focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                modifier = Modifier.height(56.dp)
            ) {
                if (isSearching) CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, contentDescription = "搜索", tint = TextPrimary)
            }
        }

        errorMsg?.let { Text(it, color = Danger, fontSize = 13.sp) }
        successMsg?.let { Text(it, color = Success, fontSize = 13.sp) }

        result?.let { user ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface1),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(BlueAccent.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.nickname.take(2).uppercase(), color = BlueAccent, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(user.nickname, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("@${user.aliasId}", color = TextMuted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onSendRequest(user.aliasId) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    ) { Text("添加") }
                }
            }
        }
    }
}

@Composable
private fun RequestsList(
    received: List<Friend>,
    sent: List<Friend>,
    onAccept: (Long) -> Unit
) {
    if (received.isEmpty() && sent.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📬", fontSize = 40.sp)
                Text("没有待处理的请求", color = TextMuted, fontSize = 16.sp)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (received.isNotEmpty()) {
            item {
                Text(
                    "收到的请求 (${received.size})",
                    color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(received, key = { it.friendshipId }) { req ->
                RequestRow(req, action = {
                    Button(
                        onClick = { onAccept(req.friendshipId) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("接受", fontSize = 13.sp) }
                })
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
            }
        }
        if (sent.isNotEmpty()) {
            item {
                Text(
                    "已发送 (${sent.size})",
                    color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(sent, key = { it.friendshipId }) { req ->
                RequestRow(req, action = {
                    Text("等待中", color = TextMuted, fontSize = 13.sp)
                })
                Divider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
            }
        }
    }
}

@Composable
private fun RequestRow(req: Friend, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(BlueAccent.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                req.nickname.take(2).uppercase().ifEmpty { "?" },
                color = BlueAccent, fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(req.nickname.ifEmpty { req.aliasId }, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("@${req.aliasId}", color = TextMuted, fontSize = 12.sp)
        }
        action()
    }
}

/**
 * 解析 QR 文本：
 *   - "securechat://add?aliasId=foo" → "foo"
 *   - "foo" 形如纯 alias → "foo"
 *   - 否则 null
 */
private fun parseAliasFromQr(raw: String): String? {
    val t = raw.trim()
    val prefix = "securechat://add?aliasId="
    if (t.startsWith(prefix)) {
        val after = t.removePrefix(prefix).substringBefore('&').trim()
        return after.ifBlank { null }
    }
    // 简单 alias 校验：3-30 个字母/数字/下划线
    return if (Regex("^[A-Za-z0-9_]{3,30}$").matches(t)) t else null
}
