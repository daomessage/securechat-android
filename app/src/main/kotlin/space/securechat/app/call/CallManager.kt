package space.securechat.app.call

import android.content.Context
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

    // ICE 服务器配置 — 默认 Google STUN；生产应换成自家 TURN
    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    // 缓存对端传入的 ICE 候选（若 remote description 还没设就先缓存）
    private val pendingIce = mutableListOf<IceCandidate>()

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

        createPeerConnection()
        attachLocalMedia(mode)
        createAndSendOffer(mode)

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

        createPeerConnection()
        attachLocalMedia(info.mode)
        // 对端 offer 应在 handleIncoming 时已 setRemoteDescription，这里直接 createAnswer
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
                // flush pending ICE
                flushPendingIce()
            }
        }, MediaConstraints())
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

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: run {
            Log.e("CallManager", "PeerConnectionFactory not initialized — call init(context) first")
            return
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val info = _info.value ?: return
                client.sendSignalFrame(mapOf(
                    "type"       to "call_ice",
                    "to"         to info.remoteAlias,
                    "call_id"    to info.callId,
                    "candidate"  to candidate.sdp,
                    "sdp_mid"    to candidate.sdpMid,
                    "sdp_mline"  to candidate.sdpMLineIndex,
                    "crypto_v"   to 1,
                ))
            }
            override fun onAddStream(stream: MediaStream?) {
                _remoteStream.value = stream
            }
            override fun onRemoveStream(stream: MediaStream?) {
                _remoteStream.value = null
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d("CallManager", "PC state: $newState")
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
            override fun onTrack(transceiver: RtpTransceiver?) {}
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
                peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                )
            }
            "call_answer" -> {
                val sdpStr = frame["sdp"] as? String
                if (sdpStr != null) {
                    peerConnection?.setRemoteDescription(
                        SimpleSdpObserver(),
                        SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                    )
                }
                if (_state.value == State.OUTGOING) {
                    _state.value = State.CONNECTED
                    flushPendingIce()
                }
            }
            "call_reject", "call_hangup" -> {
                teardownPeer()
                _state.value = State.ENDED
                _info.value = null
                _state.value = State.IDLE
            }
            "call_ice" -> {
                val sdp = frame["candidate"] as? String ?: return
                val sdpMid = frame["sdp_mid"] as? String ?: ""
                val sdpMLine = (frame["sdp_mline"] as? Number)?.toInt() ?: 0
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
