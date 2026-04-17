package space.securechat.app

import android.app.Application
import space.securechat.app.call.CallManager
import space.securechat.sdk.SecureChatClient

class SecureChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureChatClient.init(this)
        // WebRTC PeerConnectionFactory 初始化（单例）
        CallManager.getInstance().init(this)
    }
}
