package space.securechat.app

import android.app.Application
import space.securechat.sdk.SecureChatClient

class SecureChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureChatClient.init(this)
    }
}
