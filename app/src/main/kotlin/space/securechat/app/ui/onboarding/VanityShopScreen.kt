package space.securechat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.securechat.sdk.SecureChatClient
import space.securechat.sdk.vanity.VanityItem
import space.securechat.app.ui.theme.*
import space.securechat.app.viewmodel.AppRoute
import space.securechat.app.viewmodel.AppViewModel

/**
 * VanityShopScreen — 对标 Web: VanityShop.tsx
 * 注册流程中选靓号（可跳过）
 */
@Composable
fun VanityShopScreen(appViewModel: AppViewModel) {
    val client = SecureChatClient.getInstance()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VanityItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<VanityItem?>(null) }
    var purchaseUrl by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank()) return
        isSearching = true
        errorMsg = null
        scope.launch {
            try { results = client.vanity.search(query) }
            catch (e: Exception) { errorMsg = e.message }
            finally { isSearching = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Claim Your ID", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Choose a memorable alias", color = TextMuted, fontSize = 14.sp)
            }
            TextButton(onClick = { appViewModel.setRoute(AppRoute.SET_NICKNAME) }) {
                Text("Skip", color = TextMuted)
            }
        }

        // 搜索栏
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search alias...", color = TextMuted) },
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
                onClick = { search() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
            }
        }

        if (isSearching) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent, modifier = Modifier.size(32.dp))
            }
        }

        errorMsg?.let {
            Text(it, color = Danger, fontSize = 13.sp)
        }

        // 搜索结果
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { item ->
                VanityResultRow(
                    item = item,
                    selected = item.aliasId == selectedItem?.aliasId,
                    onBuy = {
                        scope.launch {
                            try {
                                val result = client.vanity.purchase(item.aliasId)
                                purchaseUrl = result.paymentUrl
                                selectedItem = item
                            } catch (e: Exception) { errorMsg = e.message }
                        }
                    }
                )
            }
        }

        // 支付链接（弹出提示）
        purchaseUrl?.let { url ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface1),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payment Ready", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Complete payment to claim @${selectedItem?.aliasId}", color = TextMuted, fontSize = 13.sp)
                    Text(url, color = BlueAccent, fontSize = 12.sp)
                    Button(
                        onClick = { appViewModel.setRoute(AppRoute.SET_NICKNAME) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue to Setup") }
                }
            }
        }
    }
}

@Composable
private fun VanityResultRow(item: VanityItem, selected: Boolean, onBuy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BlueAccent.copy(alpha = 0.1f) else Surface1
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("@${item.aliasId}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    when (item.status) {
                        "available" -> "Available"
                        "reserved" -> "Reserved"
                        else -> item.status
                    },
                    color = if (item.status == "available") Success else TextMuted,
                    fontSize = 12.sp
                )
            }
            if (item.status == "available") {
                Button(
                    onClick = onBuy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("$${item.priceUsdt} USDT", fontSize = 13.sp)
                }
            }
        }
    }
}
