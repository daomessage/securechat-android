package space.securechat.app.visual

/*
 * Paparazzi · 三端视觉回归 (Android 部分)
 *
 * 默认 disabled, 启用步骤见 docs/VISUAL_REGRESSION_SETUP.md.
 *
 * 启用后取消下面注释 + 在 app/build.gradle.kts 加 `id("app.cash.paparazzi")`.
 *
 * 跑法:
 *   ./gradlew :app:recordPaparazziDebug    # 录制基线
 *   ./gradlew :app:verifyPaparazziDebug    # PR 时验证
 */

// import app.cash.paparazzi.DeviceConfig
// import app.cash.paparazzi.Paparazzi
// import androidx.compose.foundation.background
// import androidx.compose.foundation.layout.*
// import androidx.compose.runtime.Composable
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.unit.dp
// import org.junit.Rule
// import org.junit.Test
// import space.securechat.app.ui.components.Avatar
// import space.securechat.app.ui.components.AvatarSize
// import space.securechat.app.ui.components.PrimaryButton
// import space.securechat.app.ui.components.SecondaryButton
// import space.securechat.app.ui.components.DangerButton
// import space.securechat.app.ui.theme.DarkBg
// import space.securechat.app.ui.theme.SecureChatTheme
//
// class ComponentSnapshotTest {
//     @get:Rule
//     val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)
//
//     private fun snapshot(name: String, content: @Composable () -> Unit) {
//         paparazzi.snapshot(name = name) {
//             SecureChatTheme {
//                 Box(Modifier.background(DarkBg).padding(16.dp)) {
//                     content()
//                 }
//             }
//         }
//     }
//
//     @Test fun `avatar all sizes`() = snapshot("avatar-sizes") {
//         Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//             Avatar(text = "AB", size = AvatarSize.SM)
//             Avatar(text = "CD", size = AvatarSize.MD)
//             Avatar(text = "EF", size = AvatarSize.LG)
//             Avatar(text = "GH", size = AvatarSize.XL)
//         }
//     }
//
//     @Test fun `primary button`() = snapshot("button-primary") {
//         PrimaryButton(text = "创建新账户", onClick = {})
//     }
//
//     @Test fun `secondary button`() = snapshot("button-secondary") {
//         SecondaryButton(text = "恢复已有账户", onClick = {})
//     }
//
//     @Test fun `danger button`() = snapshot("button-danger") {
//         DangerButton(text = "挂断", onClick = {})
//     }
// }
