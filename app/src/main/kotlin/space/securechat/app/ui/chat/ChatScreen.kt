package space.securechat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.messaging.StoredMessage
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChatScreen — 对标 Web: ChatWindow.tsx
 *
 * 功能：
 *   - E2EE 文字发送 / 撤回 / 引用回复
 *   - Typing 指示器
 *   - 图片选择发送（Photo Picker）
 *   - 网络状态 Banner
 *   - 长按消息弹出操作菜单（撤回 / 引用 / 复制）
 *
 * 🔒 SDK 自动：加解密 / WebSocket / 持久化
 * 👤 App：UI 渲染 / 事件监听
 */
@Composable
fun ChatScreen(
    convId: String,
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<StoredMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }         // 对方正在输入
    var replyToMsg by remember { mutableStateOf<StoredMessage?>(null) }
    var selectedMsg by remember { mutableStateOf<StoredMessage?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showMsgDetails by remember { mutableStateOf(false) }
    var networkConnected by remember { mutableStateOf(client.isConnected) }
    var friendNickname by remember { mutableStateOf("") }
    var friendAliasId by remember { mutableStateOf("") }
    var trustState by remember { mutableStateOf<space.securechat.sdk.security.TrustState>(space.securechat.sdk.security.TrustState.Unverified) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var securityDisplayCode by remember { mutableStateOf("") }
    var myEcdhPub by remember { mutableStateOf<ByteArray?>(null) }
    var theirEcdhPub by remember { mutableStateOf<ByteArray?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }
    val pageSize = 30
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun loadTrustAndCode() {
        if (friendAliasId.isBlank()) return
        try {
            trustState = client.security.getTrustState(friendAliasId)
            val triple = client.getSecurityFingerprint(convId)
            securityDisplayCode = triple.first.displayCode
            myEcdhPub = triple.second
            theirEcdhPub = triple.third
        } catch (_: Exception) {}
    }

    // 初始化：加载历史消息 + 获取好友信息
    LaunchedEffect(convId) {
        messages = client.getHistory(convId, limit = pageSize)
        hasMoreHistory = messages.size == pageSize
        // 找对应好友
        try {
            val friends = client.contacts.syncFriends()
            friends.find { it.conversationId == convId }?.let {
                friendNickname = it.nickname
                friendAliasId = it.aliasId
            }
        } catch (_: Exception) {}
        // 加载信任状态 + 安全码
        loadTrustAndCode()
        // 滚到底部
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    // 订阅新消息
    DisposableEffect(convId) {
        val msgListener: (StoredMessage) -> Unit = { msg ->
            if (msg.conversationId == convId) {
                // 去重:同一 id 已存在则替换(SDK 可能因乐观写入 + ack 回插发出两次同 id 事件),
                // 否则追加。避免 LazyColumn key 重复导致的 IllegalArgumentException 崩溃。
                messages = if (messages.any { it.id == msg.id }) {
                    messages.map { if (it.id == msg.id) msg else it }
                } else {
                    messages + msg
                }
                scope.launch { listState.animateScrollToItem(messages.size - 1) }
                // 发送已读回执:仅当 (1) 是对方发来的消息 (2) Activity 处于 RESUMED(用户真在看)
                // 否则会出现"自己发出去的消息立刻变已读"或"在后台消息却变已读"
                val isInForeground = lifecycleOwner.lifecycle.currentState
                    .isAtLeast(Lifecycle.State.RESUMED)
                if (!msg.isMe && isInForeground && friendAliasId.isNotEmpty()) {
                    client.markAsRead(convId, msg.seq ?: 0L, friendAliasId)
                }
            }
        }
        val unsub = client.on(SecureChatClient.EVENT_MESSAGE, msgListener)
        onDispose { unsub() }
    }

    // 订阅 status_change（更新本地消息的发送/送达/已读图标）
    DisposableEffect(convId) {
        val statusListener: (String, String) -> Unit = { msgId, newStatus ->
            messages = messages.map { m ->
                if (m.id == msgId) m.copy(status = newStatus) else m
            }
        }
        val unsub = client.on(SecureChatClient.EVENT_STATUS_CHANGE, statusListener)
        onDispose { unsub() }
    }

    // 订阅 typing
    DisposableEffect(convId) {
        val typingListener: (String, String) -> Unit = { alias, conv ->
            if (conv == convId) {
                isTyping = true
                scope.launch {
                    delay(3000)
                    isTyping = false
                }
            }
        }
        val unsub = client.on(SecureChatClient.EVENT_TYPING, typingListener)
        onDispose { unsub() }
    }

    // 网络状态
    LaunchedEffect(Unit) {
        client.networkState.collect { state ->
            networkConnected = state is space.securechat.sdk.messaging.WSTransport.NetworkState.Connected
        }
    }

    // 图片选择器（PickVisualMedia / ImageOnly）
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
                        ?: return@launch
                    if (friendAliasId.isNotBlank()) {
                        client.sendImage(convId, friendAliasId, bytes)
                        // 重新拉一次历史以显示新发的图片
                        messages = client.getHistory(convId, limit = pageSize.coerceAtLeast(messages.size))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "sendImage failed", e)
                }
            }
        }
    }

    // 文件选择器（任意 MIME）
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(fileUri)?.readBytes()
                        ?: return@launch
                    val name = context.contentResolver.query(fileUri, null, null, null, null)?.use { c ->
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && nameIdx >= 0) c.getString(nameIdx) else null
                    } ?: fileUri.lastPathSegment ?: "file"
                    if (friendAliasId.isNotBlank()) {
                        client.sendFile(convId, friendAliasId, bytes, name)
                        messages = client.getHistory(convId, limit = pageSize.coerceAtLeast(messages.size))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "sendFile failed", e)
                }
            }
        }
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    val voiceRecorder = remember { space.securechat.app.voice.VoiceRecorder(context) }
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showVoiceRecorder = true
    }

    // 视频通话需要 CAMERA + RECORD_AUDIO 运行时权限。
    // 不申请 → WebRTC Camera2Session 抛 SecurityException 静默失败 →
    // capturer 没产视频帧 → 对端 inbound video bytes=0(线上现象:有声没视频)。
    val callMgrForLauncher = space.securechat.app.call.CallManager.getInstance()
    val videoCallTargetAlias = remember { mutableStateOf<String?>(null) }
    val videoCallPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraOk = result[android.Manifest.permission.CAMERA] == true
        val micOk = result[android.Manifest.permission.RECORD_AUDIO] == true
        val target = videoCallTargetAlias.value
        videoCallTargetAlias.value = null
        if (cameraOk && micOk && !target.isNullOrBlank()) {
            callMgrForLauncher.call(target, space.securechat.app.call.CallManager.Mode.VIDEO)
        }
    }
    val audioCallPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = videoCallTargetAlias.value
        videoCallTargetAlias.value = null
        if (granted && !target.isNullOrBlank()) {
            callMgrForLauncher.call(target, space.securechat.app.call.CallManager.Mode.AUDIO)
        }
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {

        // 网络 Banner
        if (!networkConnected) {
            Box(
                Modifier.fillMaxWidth().background(Warning.copy(alpha = 0.15f)).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠ 重新连接中...", color = Warning, fontSize = 12.sp)
            }
        }

        // 顶栏
        Row(
            Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(BlueAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friendNickname.take(2).uppercase().ifEmpty { "?" },
                    color = BlueAccent, fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f)) {
                Text(friendNickname.ifEmpty { "加载中..." }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isTyping) "正在输入..." else "🔒 端到端加密",
                    color = if (isTyping) Success else TextMuted,
                    fontSize = 12.sp
                )
            }
            // 通话按钮
            IconButton(onClick = {
                if (friendAliasId.isBlank()) return@IconButton
                val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (micGranted) {
                    callMgrForLauncher.call(friendAliasId, space.securechat.app.call.CallManager.Mode.AUDIO)
                } else {
                    videoCallTargetAlias.value = friendAliasId
                    audioCallPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Icon(Icons.Default.Phone, contentDescription = "语音通话", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {
                if (friendAliasId.isBlank()) return@IconButton
                val cameraGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (cameraGranted && micGranted) {
                    callMgrForLauncher.call(friendAliasId, space.securechat.app.call.CallManager.Mode.VIDEO)
                } else {
                    videoCallTargetAlias.value = friendAliasId
                    videoCallPermLauncher.launch(arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO
                    ))
                }
            }) {
                Icon(Icons.Default.Videocam, contentDescription = "视频通话", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            // 信任状态图标 — 点击弹安全码 Modal
            val isVerified = trustState is space.securechat.sdk.security.TrustState.Verified
            IconButton(onClick = { showSecurityDialog = true }) {
                Icon(
                    imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                    contentDescription = if (isVerified) "已验证" else "未验证",
                    tint = if (isVerified) Success else Warning
                )
            }
        }

        // 加载更多 — 共享逻辑(自动触发 + 占位 spinner 都用这个)
        // 对齐 PWA Virtuoso `startReached` 自动触发,不再让用户手动点按钮
        val loadMore: () -> Unit = lm@{
            if (isLoadingMore || !hasMoreHistory) return@lm
            val oldest = messages.minByOrNull { it.time } ?: return@lm
            isLoadingMore = true
            scope.launch {
                try {
                    val older = client.getHistory(convId, limit = pageSize, before = oldest.time)
                    if (older.isEmpty()) {
                        hasMoreHistory = false
                    } else {
                        val byId = (older + messages).associateBy { it.id }.values
                            .sortedBy { it.time }
                        messages = byId.toList()
                        hasMoreHistory = older.size == pageSize
                    }
                } catch (_: Exception) {}
                isLoadingMore = false
            }
        }

        // 自动触发:第一个可见 item 索引 ≤ 1(顶部 spinner 或第一条消息可见)且不在 loading 中
        // 等价于 PWA Virtuoso `startReached` 事件
        LaunchedEffect(listState, hasMoreHistory) {
            snapshotFlow {
                listState.firstVisibleItemIndex
            }.collect { idx ->
                if (idx <= 1 && hasMoreHistory && !isLoadingMore && messages.isNotEmpty()) {
                    loadMore()
                }
            }
        }

        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 顶部加载占位:loading 中显示 spinner;hasMore 中显示透明占位(给 firstVisibleIndex 留入口)
            if (hasMoreHistory && messages.isNotEmpty()) {
                item("load_more") {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                color = BlueAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                            )
                        } else {
                            Text("向上滚动加载更早的消息", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                val replyPreview = remember(msg.replyToId, messages) {
                    msg.replyToId?.let { id -> messages.firstOrNull { it.id == id } }
                }
                MessageBubble(
                    msg = msg,
                    replyPreview = replyPreview,
                    onLongPress = {
                        selectedMsg = msg
                        showActionMenu = true
                    }
                )
            }
        }

        // 引用回复预览
        replyToMsg?.let { reply ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface1)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.width(3.dp).height(36.dp).background(BlueAccent, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    Text("回复", color = BlueAccent, fontSize = 11.sp)
                    Text(reply.text, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { replyToMsg = null }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "取消", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface1)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 附件按钮 — 弹出 图/文件/语音 菜单
            Box {
                IconButton(
                    onClick = { showAttachMenu = !showAttachMenu },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加附件", tint = TextMuted)
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("照片") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("文件") },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            filePicker.launch("*/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("语音") },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            val perm = android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO
                                ) == perm
                            ) {
                                showVoiceRecorder = true
                            } else {
                                audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }

            // 共享发送函数:Enter 键和发送按钮都走这里(避免逻辑漂移)
            // 对齐 PWA `ChatInputBar.tsx` 的 onKeyDown Enter / onClick 双触发设计
            val performSend: () -> Unit = perform@{
                val text = inputText.trim()
                if (text.isBlank() || friendAliasId.isEmpty()) return@perform
                inputText = ""
                val replyId = replyToMsg?.id
                replyToMsg = null
                scope.launch {
                    try {
                        client.sendMessage(convId, friendAliasId, text, replyId)
                        // P2-H: limit 必须 ≥ 当前消息数 + 1,否则发完新消息后
                        // getHistory 用默认 limit 截断,长会话会丢历史。
                        messages = client.getHistory(convId, limit = pageSize.coerceAtLeast(messages.size + 1))
                        listState.animateScrollToItem(messages.size - 1)
                    } catch (_: Exception) {}
                }
            }

            // 输入框 — 对齐 PWA:
            //   1. 焦点边框 BlueAccent(对齐 PWA `border-blue-500`)
            //   2. ImeAction.Send + KeyboardActions 触发回车发送
            //      (PWA 是 onKeyDown Enter && !shift,Android 物理键盘 Enter / 软键盘 Send 等价)
            OutlinedTextField(
                value = inputText,
                onValueChange = { text ->
                    inputText = text
                    if (friendAliasId.isNotEmpty()) client.sendTyping(convId, friendAliasId)
                },
                placeholder = { Text("输入消息...", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = Surface2,
                    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { performSend() }
                ),
                modifier = Modifier.weight(1f)
            )

            // 发送按钮 — 32dp(对齐 PWA `w-8 h-8`),图标 16dp
            IconButton(
                onClick = performSend,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) BlueAccent else Surface2)
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }

    // 长按操作菜单
    if (showActionMenu && selectedMsg != null) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false; selectedMsg = null },
            containerColor = Surface1,
            title = { Text("消息操作", color = TextPrimary) },
            text = {
                Column {
                    if (selectedMsg!!.isMe) {
                        TextButton(onClick = {
                            scope.launch {
                                try { client.retractMessage(selectedMsg!!.id, friendAliasId, convId) } catch (_: Exception) {}
                            }
                            showActionMenu = false
                        }) { Text("撤回", color = Danger) }
                    }
                    TextButton(onClick = {
                        replyToMsg = selectedMsg
                        showActionMenu = false
                    }) { Text("回复", color = TextPrimary) }
                    TextButton(onClick = {
                        selectedMsg?.text?.let { clipboard.setText(AnnotatedString(it)) }
                        showActionMenu = false
                        selectedMsg = null
                    }) { Text("复制", color = TextPrimary) }
                    TextButton(onClick = {
                        showMsgDetails = true
                        showActionMenu = false
                    }) { Text("详情", color = TextPrimary) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionMenu = false; selectedMsg = null }) {
                    Text("取消", color = TextMuted)
                }
            }
        )
    }

    // 语音录制 Dialog
    if (showVoiceRecorder) {
        VoiceRecorderDialog(
            recorder = voiceRecorder,
            onCancel = {
                voiceRecorder.cancel()
                showVoiceRecorder = false
            },
            onSend = { bytes, durationMs ->
                showVoiceRecorder = false
                if (friendAliasId.isNotBlank()) {
                    scope.launch {
                        try {
                            client.sendVoice(convId, friendAliasId, bytes, durationMs)
                            messages = client.getHistory(convId, limit = pageSize.coerceAtLeast(messages.size))
                        } catch (e: Exception) {
                            android.util.Log.e("ChatScreen", "sendVoice failed", e)
                        }
                    }
                }
            }
        )
    }

    // 消息详情 Dialog
    if (showMsgDetails && selectedMsg != null) {
        val msg = selectedMsg!!
        val detailTime = remember(msg.time) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date(msg.time))
        }
        AlertDialog(
            onDismissRequest = { showMsgDetails = false; selectedMsg = null },
            containerColor = Surface1,
            title = { Text("消息详情", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("ID", msg.id)
                    DetailRow("时间", detailTime)
                    DetailRow("状态", msg.status)
                    DetailRow("发送方", if (msg.isMe) "我" else (msg.fromAliasId ?: friendAliasId))
                    DetailRow("类型", msg.msgType ?: "text")
                    msg.replyToId?.let { DetailRow("回复", it) }
                    msg.seq?.let { DetailRow("序列号", "$it") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMsgDetails = false; selectedMsg = null }) {
                    Text("关闭", color = BlueAccent)
                }
            }
        )
    }

    // 安全码 Dialog
    if (showSecurityDialog && securityDisplayCode.isNotEmpty()) {
        SecurityCodeDialog(
            securityCode = securityDisplayCode,
            friendNickname = friendNickname.ifEmpty { friendAliasId },
            onDismiss = { showSecurityDialog = false },
            onMarkVerified = {
                val mine = myEcdhPub
                val theirs = theirEcdhPub
                if (friendAliasId.isNotBlank() && mine != null && theirs != null) {
                    scope.launch {
                        try {
                            client.security.markAsVerified(friendAliasId, mine, theirs)
                            trustState = client.security.getTrustState(friendAliasId)
                        } catch (_: Exception) {}
                    }
                }
                showSecurityDialog = false
            }
        )
    }
}

// ── 消息气泡 ──────────────────────────────────────────────────────────────
// MessageBubble 已拆到 MessageBubble.kt（同包），此处由 import 解析至公有版本。

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 64.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun VoiceRecorderDialog(
    recorder: space.securechat.app.voice.VoiceRecorder,
    onCancel: () -> Unit,
    onSend: (ByteArray, Long) -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        recorder.start()
        while (recorder.isRecording()) {
            elapsed = recorder.elapsedSec()
            delay(500)
        }
    }
    val timeStr = "%d:%02d".format(elapsed / 60, elapsed % 60)
    AlertDialog(
        onDismissRequest = {}, // 不允许点外部关闭
        containerColor = Surface1,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 闪烁红点
                Box(Modifier.size(10.dp).clip(CircleShape).background(Danger))
                Text("录音中...", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(48.dp))
                Text(timeStr, color = TextPrimary, fontSize = 22.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("点发送分享语音，取消则丢弃", color = TextMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = recorder.stop(save = true)
                    if (result != null) onSend(result.first, result.second)
                    else onCancel()
                },
                enabled = elapsed >= 1,
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text("发送") }
        },
        dismissButton = {
            TextButton(onClick = {
                recorder.cancel()
                onCancel()
            }) { Text("取消", color = TextMuted) }
        }
    )
}
