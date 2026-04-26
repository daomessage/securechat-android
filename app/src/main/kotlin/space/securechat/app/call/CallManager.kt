package space.securechat.app.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import space.securechat.sdk.SecureChatClient
import java.util.UUID

/**
 * CallManager — 通话状态机 + WebRTC 媒体集成（stream-webrtc-android）
 *
 * 信令走 SDK.sendSignalFrame / EVENT_SIGNAL；媒体走 PeerConnection。
 * 对标 PWA: template-app-pwa/src/components/chat/CallScreen.tsx
 *
 * ⚠️ 需要在 Activity 里 init(context) 一次，注入 Application context 给 EglBase
 *    和 PeerConnectionFactory。
 */
class CallManager private constructor() {

    enum class State { IDLE, OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }
    enum class Mode { AUDIO, VIDEO }

    data class CallInfo(
        val callId: String,
        val remoteAlias: String,
        val isCaller: Boolean,
        val mode: Mode,
        val startedAtMs: Long = System.currentTimeMillis()
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _info = MutableStateFlow<CallInfo?>(null)
    val info: StateFlow<CallInfo?> = _info.asStateFlow()

    // 本地/远端视频流（UI 层订阅后用 SurfaceViewRenderer.addSink 渲染）
    private val _localStream = MutableStateFlow<MediaStream?>(null)
    val localStream: StateFlow<MediaStream?> = _localStream.asStateFlow()

    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream.asStateFlow()

    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _cameraMuted = MutableStateFlow(false)
    val cameraMuted: StateFlow<Boolean> = _cameraMuted.asStateFlow()

    private var unsubSignal: (() -> Unit)? = null
    private var noAnswerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client get() = SecureChatClient.getInstance()

    // WebRTC 核心对象
    private var appContext: Context? = null
    var eglBase: EglBase? = null
        private set
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // ICE 服务器配置 — 运行时从 /api/v1/calls/ice-config 拉取（Cloudflare Realtime TURN）
    // 仅在拉取失败时降级为 Google 公共 STUN
    private suspend fun fetchIceServers(): List<PeerConnection.IceServer> {
        val turnConfig = try { client.fetchTurnConfig() } catch (e: Exception) { null }
        if (turnConfig != null) {
            val servers = turnConfig.iceServers.mapNotNull { srv ->
                @Suppress("UNCHECKED_CAST")
                val urls = (srv["urls"] as? List<String>) ?: return@mapNotNull null
                val username   = srv["username"]   as? String
                val credential = srv["credential"] as? String
                val builder = PeerConnection.IceServer.builder(urls)
                if (username != null && credential != null) {
                    builder.setUsername(username).setPassword(credential)
                }
                builder.createIceServer()
            }
            if (servers.isNotEmpty()) {
                android.util.Log.d("CallManager", "ICE: CF TURN 凭证获取成功，节点数: ${servers.size}")
                return servers
            }
        }
        android.util.Log.w("CallManager", "ICE: 拉取失败，降级为 Google STUN")
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    // 缓存对端传入的 ICE 候选（若 remote description 还没设就先缓存）
    private val pendingIce = mutableListOf<IceCandidate>()

    // 缓存 call_offer SDP（被叫方点"接听"前 peerConnection 尚未创建，需先缓存）
    private var pendingOfferSdp: String? = null

    // ─── 音频路由 ─────────────────────────────────────────────────────────────
    // WebRTC 音频默认走 STREAM_VOICE_CALL，必须把 AudioManager 切到 IN_COMMUNICATION
    // 模式才能正常播放远端音频；否则会被路由到听筒小喇叭或音量为 0 → 表面上 "无声音"。
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerOn: Boolean = false
    private var savedMicMuted: Boolean = false
    private var audioConfigured: Boolean = false

    private fun configureAudioForCall(speakerOn: Boolean) {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!audioConfigured) {
            savedAudioMode = am.mode
            savedSpeakerOn = am.isSpeakerphoneOn
            savedMicMuted = am.isMicrophoneMute
            audioConfigured = true
        }
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isMicrophoneMute = false
        am.isSpeakerphoneOn = speakerOn
        Log.d("CallManager", "audio routing: MODE_IN_COMMUNICATION, speaker=$speakerOn")
    }

    private fun restoreAudioAfterCall() {
        if (!audioConfigured) return
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = savedAudioMode
        am.isSpeakerphoneOn = savedSpeakerOn
        am.isMicrophoneMute = savedMicMuted
        audioConfigured = false
        Log.d("CallManager", "audio routing restored")
    }

    fun init(context: Context) {
        if (peerConnectionFactory != null) return
        appContext = context.applicationContext

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()
        val encoder = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
    }

    fun start() {
        if (unsubSignal != null) return
        val listener: (Map<String, Any?>) -> Unit = { frame -> handleIncoming(frame) }
        unsubSignal = client.on(SecureChatClient.EVENT_SIGNAL, listener)
    }

    fun stop() {
        unsubSignal?.invoke()
        unsubSignal = null
        teardownPeer()
        _state.value = State.IDLE
        _info.value = null
    }

    // ────────────────────────────────── 对外 API ──────────────────────────

    fun call(remoteAlias: String, mode: Mode) {
        val callId = UUID.randomUUID().toString()
        _info.value = CallInfo(callId, remoteAlias, isCaller = true, mode = mode)
        _state.value = State.OUTGOING

        scope.launch {
            createPeerConnection()
            attachLocalMedia(mode)
            createAndSendOffer(mode)
        }

        client.sendSignalFrame(mapOf(
            "type"     to "call_invite",
            "to"       to remoteAlias,
            "call_id"  to callId,
            "mode"     to mode.name.lowercase(),
            "crypto_v" to 1,
        ))

        noAnswerJob?.cancel()
        noAnswerJob = scope.launch {
            delay(30_000)
            if (_state.value == State.OUTGOING) hangup()
        }
    }

    fun answer() {
        noAnswerJob?.cancel()
        val info = _info.value ?: return
        if (_state.value != State.INCOMING) return
        _state.value = State.CONNECTING

        scope.launch {
            createPeerConnection()
            attachLocalMedia(info.mode)

            // 显式指定 OfferToReceiveVideo，确保视频来电被叫方协商出视频 track
            val answerConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo",
                    if (info.mode == Mode.VIDEO) "true" else "false"))
            }

            val createAndSendAnswer: () -> Unit = {
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp ?: return
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                        client.sendSignalFrame(mapOf(
                            "type"     to "call_answer",
                            "to"       to info.remoteAlias,
                            "call_id"  to info.callId,
                            "sdp"      to sdp.description,
                            "sdp_type" to "answer",
                            "crypto_v" to 1,
                        ))
                        scope.launch { _state.value = State.CONNECTED }
                    }
                }, answerConstraints)
            }

            // 应用缓存的 offer SDP（call_offer 可能在 peerConnection 创建前到达）
            // setRemoteDescription 是异步的：createAnswer 必须等 remote 已 set 才能正确生成
            // OfferToReceiveVideo 协商。同时 ICE flush 也必须等 remote set 完成。
            val offerSdp = pendingOfferSdp
            if (offerSdp != null) {
                pendingOfferSdp = null
                peerConnection?.setRemoteDescription(
                    FlushAfterRemoteDescObserver {
                        flushPendingIce()
                        createAndSendAnswer()
                    },
                    SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                )
            } else {
                // 兼容路径：handleIncoming 已 setRemoteDescription 的情况
                createAndSendAnswer()
                flushPendingIce()
            }
        }
    }

    fun reject() {
        noAnswerJob?.cancel()
        val info = _info.value ?: return
        client.sendSignalFrame(mapOf(
            "type"     to "call_reject",
            "to"       to info.remoteAlias,
            "call_id"  to info.callId,
            "crypto_v" to 1,
        ))
        teardownPeer()
        _state.value = State.ENDED
        _info.value = null
        _state.value = State.IDLE
    }

    fun hangup() {
        noAnswerJob?.cancel()
        val info = _info.value
        if (info != null) {
            client.sendSignalFrame(mapOf(
                "type"     to "call_hangup",
                "to"       to info.remoteAlias,
                "call_id"  to info.callId,
                "crypto_v" to 1,
            ))
        }
        teardownPeer()
        _state.value = State.ENDED
        _info.value = null
        _state.value = State.IDLE
    }

    fun toggleMic() {
        val newVal = !_micMuted.value
        _micMuted.value = newVal
        localAudioTrack?.setEnabled(!newVal)
    }

    fun toggleCamera() {
        val newVal = !_cameraMuted.value
        _cameraMuted.value = newVal
        localVideoTrack?.setEnabled(!newVal)
    }

    // ────────────────────────────────── 内部 ──────────────────────────────

    private suspend fun createPeerConnection() {
        val factory = peerConnectionFactory ?: run {
            Log.e("CallManager", "PeerConnectionFactory not initialized — call init(context) first")
            return
        }
        val iceServers = fetchIceServers()
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val info = _info.value ?: return
                // PWA handleICE 期望 candidate 字段是 RTCIceCandidateInit 对象
                // { candidate: string, sdpMid: string|null, sdpMLineIndex: number }
                // 与 PWA 自己发的 e.candidate.toJSON() 格式一致
                client.sendSignalFrame(mapOf(
                    "type"     to "call_ice",
                    "to"       to info.remoteAlias,
                    "call_id"  to info.callId,
                    "candidate" to mapOf(
                        "candidate"     to candidate.sdp,
                        "sdpMid"        to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                    ),
                    "crypto_v" to 1,
                ))
            }
            override fun onAddStream(stream: MediaStream?) {
                // Unified Plan 下 onAddStream 不可靠，由 onTrack 处理
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                // Unified Plan：每个 track 独立触发，重建远端 MediaStream
                transceiver ?: return
                val track = transceiver.receiver?.track() ?: return
                scope.launch(Dispatchers.Main) {
                    val factory = peerConnectionFactory ?: return@launch
                    val existing = _remoteStream.value
                    val stream = if (existing != null) existing
                    else {
                        val s = factory.createLocalMediaStream("remote0")
                        _remoteStream.value = s
                        s
                    }
                    when (track) {
                        is AudioTrack -> {
                            if (stream.audioTracks.none { it.id() == track.id() })
                                stream.addTrack(track)
                        }
                        is VideoTrack -> {
                            if (stream.videoTracks.none { it.id() == track.id() })
                                stream.addTrack(track)
                        }
                    }
                    // 触发 StateFlow 更新让 UI 重新收到 stream
                    _remoteStream.value = stream
                    Log.d("CallManager", "onTrack: kind=${track.kind()} remoteStream " +
                        "audio=${stream.audioTracks.size} video=${stream.videoTracks.size}")
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d("CallManager", "PC state: $newState")
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    // 音视频都默认开扬声器；如果只是语音通话用户也希望听到外放
                    val mode = _info.value?.mode ?: Mode.AUDIO
                    scope.launch { configureAudioForCall(speakerOn = true) }
                }
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.FAILED ||
                    newState == PeerConnection.IceConnectionState.CLOSED) {
                    scope.launch { hangup() }
                }
            }
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    private fun attachLocalMedia(mode: Mode) {
        val factory = peerConnectionFactory ?: return
        val pc = peerConnection ?: return
        val ctx = appContext ?: return

        // Audio
        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        val audioTrack = factory.createAudioTrack("audio0", audioSource)
        audioTrack.setEnabled(true)
        pc.addTrack(audioTrack, listOf("stream0"))
        localAudioTrack = audioTrack

        if (mode == Mode.VIDEO) {
            val capturer = createCameraCapturer(ctx) ?: return
            val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext)
            val videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceHelper, ctx, videoSource.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            val videoTrack = factory.createVideoTrack("video0", videoSource)
            videoTrack.setEnabled(true)
            pc.addTrack(videoTrack, listOf("stream0"))
            localVideoTrack = videoTrack
            videoCapturer = capturer
            surfaceTextureHelper = surfaceHelper

            // 组装一个 MediaStream 便于 UI 渲染本地预览
            val localStream = factory.createLocalMediaStream("stream0")
            localStream.addTrack(videoTrack)
            localStream.addTrack(audioTrack)
            _localStream.value = localStream
        }
    }

    private fun createCameraCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        for (name in enumerator.deviceNames) {
            return enumerator.createCapturer(name, null)
        }
        return null
    }

    private fun createAndSendOffer(mode: Mode) {
        val info = _info.value ?: return
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                client.sendSignalFrame(mapOf(
                    "type"     to "call_offer",
                    "to"       to info.remoteAlias,
                    "call_id"  to info.callId,
                    "sdp"      to sdp.description,
                    "sdp_type" to "offer",
                    "mode"     to mode.name.lowercase(),
                    "crypto_v" to 1,
                ))
            }
        }, MediaConstraints())
    }

    private fun teardownPeer() {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        peerConnection?.close()
        peerConnection = null
        _localStream.value = null
        _remoteStream.value = null
        _micMuted.value = false
        _cameraMuted.value = false
        pendingIce.clear()
        pendingOfferSdp = null
        // 恢复系统音频路由（mode + speaker + mic）
        restoreAudioAfterCall()
    }

    private fun flushPendingIce() {
        val pc = peerConnection ?: return
        for (c in pendingIce) pc.addIceCandidate(c)
        pendingIce.clear()
    }

    private fun handleIncoming(frame: Map<String, Any?>) {
        val type = frame["type"] as? String ?: return
        val from = frame["from"] as? String ?: return
        val callId = frame["call_id"] as? String ?: return
        when (type) {
            "call_invite" -> {
                if (_state.value != State.IDLE) {
                    client.sendSignalFrame(mapOf(
                        "type" to "call_reject", "to" to from,
                        "call_id" to callId, "crypto_v" to 1
                    ))
                    return
                }
                val mode = if ((frame["mode"] as? String) == "video") Mode.VIDEO else Mode.AUDIO
                _info.value = CallInfo(callId, from, isCaller = false, mode = mode)
                _state.value = State.INCOMING
            }
            "call_offer" -> {
                val sdpStr = frame["sdp"] as? String ?: return
                val pc = peerConnection
                if (pc != null) {
                    // peerConnection 已创建（不常见，但兼容）
                    // 必须等 setRemoteDescription 完成后再 flush ICE，否则视频 m-line 的候选会被丢
                    pc.setRemoteDescription(
                        FlushAfterRemoteDescObserver { flushPendingIce() },
                        SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                    )
                } else {
                    // 被叫方点接听前 peerConnection 还未创建，缓存 SDP
                    Log.d("CallManager", "call_offer 到达但 peerConnection 未创建，缓存 SDP")
                    pendingOfferSdp = sdpStr

                    // PWA SDK 1.0.20+ 直接发 call_offer，不再单独发 call_invite。
                    // 如果当前还在 IDLE，必须把状态切到 INCOMING 才会弹来电界面。
                    if (_state.value == State.IDLE) {
                        // 从 SDP 嗅探是否含 video m-line，决定模式
                        val mode = if (sdpStr.contains("\nm=video ") || sdpStr.startsWith("m=video ")) {
                            Mode.VIDEO
                        } else {
                            Mode.AUDIO
                        }
                        _info.value = CallInfo(callId, from, isCaller = false, mode = mode)
                        _state.value = State.INCOMING
                    } else if (_state.value != State.INCOMING) {
                        // 非 IDLE 也非 INCOMING（比如正在通话中）→ 拒绝
                        client.sendSignalFrame(mapOf(
                            "type" to "call_reject", "to" to from,
                            "call_id" to callId, "crypto_v" to 1
                        ))
                    }
                    // 已经是 INCOMING（call_invite 先到 + call_offer 紧随）→ 仅缓存 SDP
                }
            }
            "call_answer" -> {
                val sdpStr = frame["sdp"] as? String
                if (sdpStr != null) {
                    // 必须等 setRemoteDescription 完成后再 flush ICE，否则视频 m-line 的候选会被丢
                    peerConnection?.setRemoteDescription(
                        FlushAfterRemoteDescObserver {
                            if (_state.value == State.OUTGOING) {
                                _state.value = State.CONNECTED
                            }
                            flushPendingIce()
                        },
                        SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                    )
                } else if (_state.value == State.OUTGOING) {
                    _state.value = State.CONNECTED
                }
            }
            "call_reject", "call_hangup" -> {
                teardownPeer()
                _state.value = State.ENDED
                _info.value = null
                _state.value = State.IDLE
            }
            "call_ice" -> {
                // PWA 发来格式: candidate = { candidate, sdpMid, sdpMLineIndex }
                // 兼容旧格式: candidate = 字符串 + sdp_mid + sdp_mline
                @Suppress("UNCHECKED_CAST")
                val candObj = frame["candidate"]
                val sdp: String
                val sdpMid: String
                val sdpMLine: Int
                if (candObj is Map<*, *>) {
                    sdp     = (candObj["candidate"] as? String) ?: return
                    sdpMid  = (candObj["sdpMid"] as? String) ?: ""
                    sdpMLine = (candObj["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                } else {
                    sdp     = (candObj as? String) ?: return
                    sdpMid  = frame["sdp_mid"] as? String ?: ""
                    sdpMLine = (frame["sdp_mline"] as? Number)?.toInt() ?: 0
                }
                val c = IceCandidate(sdpMid, sdpMLine, sdp)
                val pc = peerConnection
                if (pc != null && pc.remoteDescription != null) {
                    pc.addIceCandidate(c)
                } else {
                    pendingIce.add(c)
                }
            }
            else -> {}
        }
    }

    companion object {
        @Volatile private var instance: CallManager? = null
        fun getInstance(): CallManager = instance ?: synchronized(this) {
            instance ?: CallManager().also { instance = it }
        }
    }
}

/** 偷懒的 SdpObserver 实现 */
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

/**
 * setRemoteDescription 完成后再执行回调（通常是 flushPendingIce）。
 *
 * setRemoteDescription 是异步的，用空 SimpleSdpObserver 后立刻 addIceCandidate
 * 会在 remote description 还没真正应用时打入候选，部分候选会被丢弃 —
 * 视频通道因为 m-line 比纯音频多、对 ICE 完整性更敏感，最先出现"音通了视频黑屏"。
 */
class FlushAfterRemoteDescObserver(private val onDone: () -> Unit) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() { onDone() }
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) { Log.w("CallManager", "setRemoteDescription failed: $p0") }
}
