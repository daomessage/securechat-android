# SecureChat Android - Production Setup Guide

## Overview

This is a **Production-Ready** Android Jetpack Compose application for SecureChat, an end-to-end encrypted messaging platform using the `@daomessage_sdk/sdk-android`.

## Architecture

```
┌─────────────────────────────────────────────┐
│         Jetpack Compose UI Layer            │
│  (4 Tabs + Chat + Onboarding screens)       │
├─────────────────────────────────────────────┤
│         AppViewModel (StateFlow)            │
│         Global State Management             │
├─────────────────────────────────────────────┤
│    SecureChatClient (SDK Singleton)         │
│    ├─ Auth (Registration/Recovery)          │
│    ├─ Messaging (E2EE Send/Receive)         │
│    ├─ Contacts (Friends/Requests)           │
│    ├─ Channels (Public Groups)              │
│    ├─ Security (Safety Codes)               │
│    ├─ Media (Image/Voice/File Upload)       │
│    └─ Push (FCM)                            │
├─────────────────────────────────────────────┤
│     SQLite (Room) + IndexedDB               │
│     Local encrypted storage                 │
├─────────────────────────────────────────────┤
│    WebSocket (Relay Server)                 │
│    relay.daomessage.com:443                 │
└─────────────────────────────────────────────┘
```

## Project Structure

```
template-app-android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/space/securechat/app/
│   │   │   ├── MainActivity.kt              # 入口、Deep Link 处理
│   │   │   ├── SecureChatApp.kt             # Application、SDK 初始化
│   │   │   ├── AppNavigation.kt             # 路由容器
│   │   │   ├── viewmodel/
│   │   │   │   └── AppViewModel.kt          # 全局状态（Zustand 对标）
│   │   │   ├── ui/
│   │   │   │   ├── onboarding/              # Onboarding 流程
│   │   │   │   │   ├── WelcomeScreen.kt
│   │   │   │   │   ├── GenerateMnemonicScreen.kt
│   │   │   │   │   ├── ConfirmBackupScreen.kt
│   │   │   │   │   ├── SetNicknameScreen.kt
│   │   │   │   │   ├── RecoverScreen.kt
│   │   │   │   │   └── VanityShopScreen.kt
│   │   │   │   ├── main/
│   │   │   │   │   └── MainScreen.kt         # 4-Tab 主屏幕
│   │   │   │   ├── messages/
│   │   │   │   │   └── MessagesTab.kt        # 会话列表
│   │   │   │   ├── contacts/
│   │   │   │   │   └── ContactsTab.kt        # 好友列表 + 搜索
│   │   │   │   ├── channels/
│   │   │   │   │   ├── ChannelsTab.kt        # 频道列表
│   │   │   │   │   └── ChannelDetailScreen.kt # 频道详情
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatScreen.kt         # 聊天界面
│   │   │   │   │   ├── MessageBubble.kt      # 消息气泡组件
│   │   │   │   │   └── SecurityCodeDialog.kt # 安全码验证
│   │   │   │   ├── settings/
│   │   │   │   │   └── SettingsTab.kt        # 用户设置
│   │   │   │   ├── components/
│   │   │   │   │   └── ErrorSnackbar.kt      # 错误提示组件
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt              # 颜色定义
│   │   │   └── push/
│   │   │       └── FcmService.kt             # FCM 推送接收
│   │   ├── AndroidManifest.xml               # 权限、Deep Link、FCM
│   │   └── res/values/strings.xml            # 字符串资源
│   ├── google-services.json                  # Firebase 配置（从 Firebase Console 下载）
│   └── build.gradle.kts                      # 应用构建配置
├── build.gradle.kts                          # 根构建配置
├── settings.gradle.kts                       # 模块和 SDK composite build
├── gradle/libs.versions.toml                 # 依赖版本目录
├── app/proguard-rules.pro                    # 代码混淆规则
└── SETUP.md                                  # 本文件
```

## Prerequisites

1. **Android Studio** 2024.1 或更新版本
2. **Java 17+**
3. **Firebase Account** - 用于 FCM 推送
4. **Google Play Services** - 面向生产部署

## Setup Steps

### 1. 克隆 + 初始化 SDK

```bash
cd template-app-android

# 通过 composite build 自动加载 SDK
# settings.gradle.kts 中已配置：includeBuild("../sdk-android")
```

### 2. Firebase 配置

从 [Firebase Console](https://console.firebase.google.com/) 创建应用：

1. 创建新项目
2. 添加 Android 应用（Package: `space.securechat.app`）
3. 下载 `google-services.json` → `app/google-services.json`
4. 在 Firebase Console 启用 Cloud Messaging

### 3. 本地构建 + 运行

```bash
# 构建（开发版本）
./gradlew assembleDebug

# 安装到设备/模拟器
./gradlew installDebug

# 运行（自动打开 logcat）
./gradlew connectedAndroidTest
```

### 4. 签名 + 发布构建（Google Play）

生成 release keystore：

```bash
keytool -genkey -v -keystore keystore.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 -alias securechat
```

环境变量：

```bash
export KEYSTORE_PASSWORD="your_keystore_pass"
export KEY_ALIAS="securechat"
export KEY_PASSWORD="your_key_pass"
```

构建 AAB（用于 Play Store）：

```bash
./gradlew bundleRelease
```

## Feature Checklist

### Onboarding
- ✅ WelcomeScreen - 新账号/恢复选择
- ✅ GenerateMnemonicScreen - 12词展示 + 备份确认
- ✅ ConfirmBackupScreen - 随机 3 词验证
- ✅ VanityShopScreen - 靓号市场（可跳过）
- ✅ SetNicknameScreen - 注册完成
- ✅ RecoverScreen - 恢复账号

### Main App (4 Tabs)
- ✅ **MessagesTab** - 会话列表 + 未读数徽章
- ✅ **ContactsTab** - 好友列表 + 搜索添加 + 好友请求
- ✅ **ChannelsTab** - 频道列表 + 搜索 + 创建
- ✅ **SettingsTab** - 用户信息 + 推送 + 助记词 + 登出

### Chat
- ✅ 消息发送（文字）
- ✅ 消息撤回（仅自己）
- ✅ 引用回复
- ✅ Typing 指示器
- ✅ 图片上传（Photo Picker）
- ✅ 安全码验证（首次聊天）
- ✅ 网络状态 Banner
- ✅ 消息已读回执

### Push
- ✅ FCM Token 注册
- ✅ 推送接收 + 通知展示
- ✅ 通知点击 → 打开对应会话

## Security

### Encryption
- **消息加密**: AES-GCM (SDK 自动处理)
- **密钥派生**: BIP39 助记词 → Ed25519 + X25519
- **服务端**: 永不接触明文（Relay-as-a-Service）

### Key Storage
- **本地存储**: SQLite (Room) + IndexedDB 加密存储
- **助记词**: 仅在 Onboarding 阶段显示，不持久化到设备
- **Session Key**: 通过 E2EE 协议自动建立，存储在 Room DB

### Permissions (AndroidManifest.xml)
- `INTERNET` - 网络连接
- `POST_NOTIFICATIONS` - 推送通知
- `RECORD_AUDIO` - 语音消息录制
- `READ_MEDIA_IMAGES` - 图片选择器

## Performance Optimizations

### Compose
- ✅ `key { }` 防止消息列表重组抖动
- ✅ `remember { }` 缓存昂贵计算
- ✅ LazyColumn 分页加载历史
- ✅ Snackbar 错误提示（不阻塞 UI）

### Networking
- ✅ WebSocket 自动重连
- ✅ 消息离线队列（自动重发）
- ✅ Image 下载缓存 (Coil)

### Storage
- ✅ Room 异步查询
- ✅ IndexedDB 按会话分片
- ✅ 消息增量同步

## Debugging

### Logcat
```bash
./gradlew logcat | grep securechat
```

### Network Inspector
- Chrome DevTools → Remote Devices → WebSocket 检查

### Database
```bash
adb shell
cd /data/data/space.securechat.app/databases
sqlite3 securechat.db
```

## Release Checklist

- [ ] ProGuard/R8 代码混淆已启用 (`minifyEnabled = true`)
- [ ] Firebase `google-services.json` 已配置
- [ ] Keystore 已生成 (`keystore.jks`)
- [ ] App Icon + Launcher 图标已更新
- [ ] App 名称 + 描述已本地化
- [ ] 隐私政策 URL 已配置
- [ ] Privacy/Data Collection 合规审查完成
- [ ] 构建 Release AAB: `./gradlew bundleRelease`
- [ ] 上传到 Google Play Console
- [ ] Beta 测试（2-7天）
- [ ] 正式上线

## Troubleshooting

### WebSocket 连接超时
```
症状: "Reconnecting..." 一直显示
原因: 服务端不可达 / 网络问题
解决: 检查 relay.daomessage.com DNS 可达性
```

### 图片上传失败
```
症状: Image upload failed
原因: 会话密钥未初始化
解决: 确保已发送至少 1 条文字消息以建立会话
```

### FCM 推送不到达
```
症状: 推送通知不出现
原因: google-services.json 配置错误
解决: 重新下载 google-services.json from Firebase Console
```

## API Reference

### Main Classes

**SecureChatClient** (SDK 单例)
```kotlin
val client = SecureChatClient.getInstance()

// 注册
val aliasId = client.auth.registerAccount(mnemonic, nickname)

// 发送消息
client.sendMessage(convId, toAliasId, text, replyToId?)

// 发送图片
client.sendImage(convId, toAliasId, imageBytes, thumbnail?, replyToId?)

// 订阅事件
val unsub = client.on(EVENT_MESSAGE) { msg -> ... }

// 登出
client.logout()
```

**AppViewModel** (App 全局状态)
```kotlin
appViewModel.setRoute(AppRoute.MAIN)
appViewModel.setActiveChatId(convId)
appViewModel.setUserInfo(aliasId, nickname)
appViewModel.incrementUnread(convId)
appViewModel.clearUnread(convId)
```

## Further Reading

- [SecureChat Protocol Spec](../docs/architecture/隐私社交协议与基础架构-架构设计-V1.md)
- [SDK TypeScript Reference](../sdk-typescript/docs/)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Firebase Messaging](https://firebase.google.com/docs/cloud-messaging)

## License

SecureChat is Open Source (TBD)

---

**Last Updated**: 2026-04-15
**Status**: Production Ready ✅
