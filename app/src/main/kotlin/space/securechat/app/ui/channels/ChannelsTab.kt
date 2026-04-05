package space.securechat.app.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.channels.ChannelInfo
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppViewModel

/**
 * ChannelsTab — 对标 Web: ChannelsTab.tsx
 * 我的频道 + 搜索发现 + 发帖
 */
@Composable
fun ChannelsTab(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var myChannels by remember { mutableStateOf<List<ChannelInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChannelInfo>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { myChannels = client.channels.getMine() } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Channels", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create", tint = BlueAccent)
            }
        }

        // 搜索栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search channels...", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Surface2, unfocusedBorderColor = Surface2,
                    focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        try { searchResults = client.channels.search(searchQuery) }
                        catch (_: Exception) {}
                        finally { isLoading = false }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
            }
        }

        val displayChannels = if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) searchResults else myChannels

        if (displayChannels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📢", fontSize = 40.sp)
                    Text("No channels yet", color = TextMuted, fontSize = 16.sp)
                    Text("Create or search for channels", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayChannels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        onClick = { appViewModel.setActiveChannelId(channel.id) }
                    )
                }
            }
        }
    }

    // 创建频道对话框
    if (showCreate) {
        CreateChannelDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, desc ->
                scope.launch {
                    try {
                        client.channels.create(name, desc)
                        myChannels = client.channels.getMine()
                    } catch (_: Exception) {}
                }
                showCreate = false
            }
        )
    }
}

@Composable
private fun ChannelRow(channel: ChannelInfo, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BlueAccent.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) { Text("📢", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(channel.description.take(60), color = TextMuted, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CreateChannelDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Create Channel", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Channel Name", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, desc) },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
