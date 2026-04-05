package space.securechat.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AppViewModel — 全局状态（对标 Zustand appStore.ts）
 *
 * 包含：路由、用户信息、Tab 状态、活跃会话/频道、未读计数
 */
class AppViewModel : ViewModel() {

    // ── 路由（对标 AppRoute）──────────────────────────────────────────────
    private val _route = MutableStateFlow(AppRoute.LOADING)
    val route: StateFlow<AppRoute> = _route.asStateFlow()
    fun setRoute(r: AppRoute) { _route.value = r }

    // ── 注册流程临时助记词（仅在内存，不持久化）──────────────────────────
    private val _tempMnemonic = MutableStateFlow("")
    val tempMnemonic: StateFlow<String> = _tempMnemonic.asStateFlow()
    fun setTempMnemonic(m: String) { _tempMnemonic.value = m }

    // ── SDK 就绪（WebSocket 连接后置 true）───────────────────────────────
    private val _sdkReady = MutableStateFlow(false)
    val sdkReady: StateFlow<Boolean> = _sdkReady.asStateFlow()
    fun setSdkReady(r: Boolean) { _sdkReady.value = r }

    // ── 用户信息（UI 展示用）─────────────────────────────────────────────
    private val _userInfo = MutableStateFlow(UserInfo())
    val userInfo: StateFlow<UserInfo> = _userInfo.asStateFlow()
    fun setUserInfo(aliasId: String, nickname: String) {
        _userInfo.value = UserInfo(aliasId, nickname)
    }

    // ── 主界面底部 Tab────────────────────────────────────────────────────
    private val _activeTab = MutableStateFlow(MainTab.MESSAGES)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()
    fun setActiveTab(t: MainTab) { _activeTab.value = t }

    // ── 活跃会话 ID（打开聊天窗口时设置）──────────────────────────────── 
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()
    fun setActiveChatId(id: String?) { _activeChatId.value = id }

    // ── 活跃频道 ID ────────────────────────────────────────────────────── 
    private val _activeChannelId = MutableStateFlow<String?>(null)
    val activeChannelId: StateFlow<String?> = _activeChannelId.asStateFlow()
    fun setActiveChannelId(id: String?) { _activeChannelId.value = id }

    // ── 待处理好友申请数（联系人 Tab 红点）───────────────────────────────
    private val _pendingRequestCount = MutableStateFlow(0)
    val pendingRequestCount: StateFlow<Int> = _pendingRequestCount.asStateFlow()
    fun setPendingRequestCount(n: Int) { _pendingRequestCount.value = n }

    // ── 未读消息计数（按会话 ID）─────────────────────────────────────────
    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    fun incrementUnread(convId: String) {
        _unreadCounts.update { counts ->
            counts + (convId to (counts[convId] ?: 0) + 1)
        }
    }

    fun clearUnread(convId: String) {
        _unreadCounts.update { it - convId }
    }

    val totalUnread: Int get() = _unreadCounts.value.values.sum()
}

// ── 数据类型 ──────────────────────────────────────────────────────────────

enum class AppRoute {
    LOADING,
    WELCOME,
    GENERATE_MNEMONIC,
    CONFIRM_BACKUP,
    VANITY_SHOP,
    SET_NICKNAME,
    RECOVER,
    MAIN
}

enum class MainTab { MESSAGES, CHANNELS, CONTACTS, SETTINGS }

data class UserInfo(
    val aliasId: String = "",
    val nickname: String = ""
)
