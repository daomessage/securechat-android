package space.securechat.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * VoiceRecorder — 封装 MediaRecorder，录 m4a 语音
 *
 * 用法：
 *   val rec = VoiceRecorder(context)
 *   rec.start()          // 开始录
 *   val (bytes, ms) = rec.stop()   // 停止，拿 bytes + 时长
 *
 * 对标 PWA: template-app-pwa/src/components/chat/ChatInputBar.tsx MediaRecorder
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0

    /** 开始录音。失败抛异常。 */
    fun start(): Boolean {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(file.absolutePath)
        return try {
            r.prepare()
            r.start()
            recorder = r
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            r.release()
            recorder = null
            outputFile = null
            false
        }
    }

    /** 返回已录的秒数 */
    fun elapsedSec(): Int {
        if (startTimeMs == 0L) return 0
        return ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
    }

    /**
     * 停止录音
     * @return (audioBytes, durationMs) 或 null（如录制失败或时长过短 < 1s）
     */
    fun stop(save: Boolean = true): Pair<ByteArray, Long>? {
        val r = recorder ?: return null
        val file = outputFile
        val durationMs = System.currentTimeMillis() - startTimeMs
        try {
            r.stop()
        } catch (_: Exception) {
            // 录制时长过短会抛异常
            r.release()
            recorder = null
            outputFile = null
            startTimeMs = 0
            file?.delete()
            return null
        }
        r.release()
        recorder = null
        outputFile = null
        startTimeMs = 0

        if (!save || file == null || !file.exists() || durationMs < 1000) {
            file?.delete()
            return null
        }
        val bytes = try { file.readBytes() } catch (_: Exception) { null }
        file.delete()
        return bytes?.let { Pair(it, durationMs) }
    }

    /** 取消录制（不保存）*/
    fun cancel() {
        stop(save = false)
    }

    fun isRecording(): Boolean = recorder != null
}
