package space.securechat.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 把字符串编码成 QR 位图。失败返回 null。
 * 默认尺寸 512x512，纠错级别 M。
 */
fun encodeQrToBitmap(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

/** 在 Compose 里渲染一个 QR 码。content 为空时不显示。 */
@Composable
fun QrCodeImage(content: String, sizeDp: Int = 220) {
    val bmp = remember(content) { encodeQrToBitmap(content) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(sizeDp.dp)
        )
    } else {
        Box(modifier = Modifier.size(sizeDp.dp))
    }
}
