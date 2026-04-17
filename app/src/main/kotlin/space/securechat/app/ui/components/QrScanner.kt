package space.securechat.app.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

/**
 * 通用 QR 扫描启动器（基于 zxing-android-embedded）
 *
 * 用法：
 * ```
 * val launcher = QrScannerLauncher { text -> ... }
 * Button(onClick = { launcher.launch() }) { Text("Scan") }
 * ```
 */
class QrLauncherHandle internal constructor(private val internalLaunch: () -> Unit) {
    fun launch() = internalLaunch()
}

@Composable
fun QrScannerLauncher(onResult: (String) -> Unit): QrLauncherHandle {
    val launcher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result: ScanIntentResult ->
        val content = result.contents
        if (!content.isNullOrBlank()) {
            onResult(content)
        }
    }
    val handle = remember {
        QrLauncherHandle {
            val opts = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan a SecureChat QR code")
                setBeepEnabled(false)
                setOrientationLocked(true)
                setCaptureActivity(CaptureActivity::class.java)
            }
            launcher.launch(opts)
        }
    }
    return handle
}
