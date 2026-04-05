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
import androidx.compose.runtime.*
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
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var pendingFriends by remember { mutableStateOf<List<space.securechat.sdk.http.FriendProfile>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<UserProfile?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        friends = client.contacts.syncFriends()
        // 获取待处理申请
        try {
            val resp = client.contacts.syncFriends() // 从 friends 过滤 pending
            val all = space.securechat.sdk.SecureChatClient.getInstance()
                .let { c ->
                    // 直接拿全部好友（含 pending）
                    client.contacts.syncFriends()
                }
        } catch (_: Exception) {}
        appViewModel.setPendingRequestCount(pendingFriends.size)
    }

    LaunchedEffect(Unit) { scope.launch { reload() } }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        // 顶栏
        Text(
            "Contacts",
            color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // 子 Tab
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkBg,
            contentColor = BlueAccent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = BlueAccent
                )
            }
        ) {
            listOf("Friends", "Add").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = { Text(label, color = if (selectedSubTab == idx) BlueAccent else TextMuted) }
                )
            }
        }

        when (selectedSubTab) {
            0 -> FriendsList(
                friends = friends,
                onOpenChat = { conv ->
                    appViewModel.setActiveChatId(conv)
                    appViewModel.clearUnread(conv)
                }
            )
            1 -> AddFriendPanel(
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
                        catch (e: Exception) { errorMsg = "User not found" }
                        finally { isSearching = false }
                    }
                },
                onSendRequest = { aliasId ->
                    scope.launch {
                        try {
                            client.contacts.sendFriendRequest(aliasId)
                            successMsg = "Friend request sent!"
                            searchResult = null
                        } catch (e: Exception) { errorMsg = e.message }
                    }
                }
            )
        }
    }
}

@Composable
private fun FriendsList(friends: List<Friend>, onOpenChat: (String) -> Unit) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("👥", fontSize = 40.sp)
                Text("No friends yet", color = TextMuted, fontSize = 16.sp)
                Text("Use the Add tab to find contacts", color = TextMuted, fontSize = 13.sp)
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
                HorizontalDivider(color = Surface2, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
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
                placeholder = { Text("Search by Alias ID...", color = TextMuted) },
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
                else Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
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
                    ) { Text("Add") }
                }
            }
        }
    }
}
