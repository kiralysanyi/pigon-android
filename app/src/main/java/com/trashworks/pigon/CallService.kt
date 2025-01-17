package com.trashworks.pigon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.geometry.isEmpty
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import com.trashworks.pigon.SocketConnection.socket
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.LinkedList


class CallService : Service() {

    private fun initializePeerConnectionFactory(context: Context): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )


        val options = PeerConnectionFactory.Options()
        val eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = SoftwareVideoDecoderFactory()

        return PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun sendOffer(peerConnection: PeerConnection?, peerID: String?) {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    // Set local description
                    peerConnection.setLocalDescription(this, it)
                    // Emit offer to the signaling server
                    val offerJson = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", it.description)
                    }
                    socket.emit(
                        "relay",
                        JSONObject("""{"deviceID": "$peerID", "data": {"type": "offer", "offer": $offerJson}}""")
                    )

                }
            }

            override fun onSetSuccess() {
                // Log or handle successful local description set
                Log.d("WebRTC", "Local description set successfully")
            }

            override fun onCreateFailure(error: String?) {
                // Handle the error of offer creation failure
                Log.e("WebRTC", "Offer creation failed: $error")
            }

            override fun onSetFailure(error: String?) {
                // Handle the error of setting the local description failure
                Log.e("WebRTC", "Failed to set local description: $error")
            }
        }, MediaConstraints())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, CallActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentTitle("Ongoing Call")
            .setContentText("Tap to return to call")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your icon
            .setContentIntent(pendingIntent)

            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "call_channel"
    }

    private val binder = LocalBinder()

    // Binder to return the service instance
    inner class LocalBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private var wakeLock: PowerManager.WakeLock? = null;
    override fun onDestroy() {
        super.onDestroy()
        videoCapturer?.stopCapture()
        wakeLock?.release()
    }

    override fun onCreate() {
        if (!SocketConnection.initialized) {
            //initialize API and SocketConnection
            val dsWrapper = DataStoreWrapper(applicationContext);
            GlobalScope.launch(Dispatchers.IO) {
                dsWrapper.getString()?.let { APIHandler.setCookies(it, dsWrapper) }
                SocketConnection.init();
            }
        }

        super.onCreate()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CallService:WakeLock"
        )
        wakeLock?.acquire() // Acquire the wake lock

        // Create a notification channel (for Android Oreo and above)
        createNotificationChannel()

        // Create the notification
        val notification = createNotification()

        // Start foreground service
        startForeground(NOTIFICATION_ID, notification)

        context = this;
    }

    private var callJson: JSONObject? = null;
    private var callID: String? = null;
    private var audioSource: AudioSource? = null;
    private var videoSource: VideoSource? = null;
    private var peerRegistered = false;
    private var initiator: String? = null;
    private var peerID: String? = null;
    private var deviceID: String? = null;
    private var context: Context? = null;
    private var callAccepted = false;
    private var peerConnection: PeerConnection? = null;
    private val initialMessageQueue = LinkedList<JSONObject>();
    private var preInit = true;
    private var audioManager: AudioManager? = null
    private var isInitiator = false;

    fun hangUP() {
        videoCapturer?.stopCapture()
        SocketConnection.socket.off("relay")
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        SocketConnection.incall = false;
        SocketConnection.acceptedCall = false;
        this.stopSelf()
    }

    fun setData(
        isinit: Boolean,
        callInfo: String,
        peerid: String,
        deviceid: String,
        initiatorid: String
    ) {
        callJson = JSONObject(callInfo)
        isInitiator = isinit;
        deviceID = deviceid;
        peerID = peerid;
        initiator = initiatorid;
    }

    fun setSpeaker(isSpeakerPhone: Boolean) {
        audioManager?.isSpeakerphoneOn = isSpeakerPhone
    }

    fun preInit() {
        SocketConnection.init()
        socket.on("relay", { args ->
            Log.d("Relay_preinit", args[0].toString())
            if (preInit) {
                initialMessageQueue.add(JSONObject(args[0].toString()));
            }
        })
    }

    fun InitAudio() {

        audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager;

        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                // Handle audio focus changes here
                Log.d("CallService", "Focus changed: ${focusChange}")
            }
            .build()

        val result = audioManager?.requestAudioFocus(audioFocusRequest)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d("CallService", "Audio focus request granted")
            Log.d("WebRTC", "Initializing audio")
            // Initialize WebRTC dependencies (if not already done)
            val eglBase = EglBase.create()
            val peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglBase.eglBaseContext,
                        true,
                        true
                    )
                )
                .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
                .createPeerConnectionFactory()

            // Step 1: Add an audio transceiver to predefine the track
            val audioTransceiver = peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
            )

            // Step 2: Create an AudioSource with constraints
            val audioConstraints = MediaConstraints().apply {
                optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }

            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)

            // Step 3: Create an AudioTrack
            val audioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK_ID", audioSource)

            // Step 4: Set the audio track to the transceiver (instead of directly adding it)
            audioTransceiver?.sender?.setTrack(audioTrack, false)

            // Step 5: Enable the audio track
            audioTrack.setEnabled(true)

            // Optional logging for debugging
            Log.d("WebRTC", "Audio track initialized and added to transceiver")
        } else {
            // Handle audio focus request denial
            Log.e("CallService", "Audio focus request denied")
        }


    }


    var onLocalVideo: ((VideoTrack) -> Unit)? = null
    var onLocalVideoEnded: (() -> Unit)? = null
    var onRemoteVideo: ((VideoTrack) -> Unit)? = null
    var onRemoteVideoEnded: (() -> Unit)? = null

    private fun createCameraCapturer(enumerator: CameraEnumerator): CameraVideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // Try to find a front-facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // If no front-facing camera is found, try to find a back-facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // If no camera is found, return null
        return null
    }

    private var videoCapturer: VideoCapturer? = null;
    private var isCameraEnabled = false;
    private var videoTrack: VideoTrack? = null;
    private var videoSender: RtpSender? = null;
    private fun InitVideo() {
        Log.d("WebRTC", "Initializing video")
        // Initialize WebRTC dependencies (if not already done)
        val eglBase = EglBase.create()
        val peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .createPeerConnectionFactory()

        videoSource = peerConnectionFactory.createVideoSource(false)

        videoCapturer = createCameraCapturer(Camera1Enumerator(false)) // or Camera2Enumerator
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
            context,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(1280, 720, 30) // Adjust resolution and FPS as needed

        videoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK_ID", videoSource)


        videoTrack?.setEnabled(true)
        Log.d("WebRTC", "Video track state: ${videoTrack?.state()}")
        videoSender = peerConnection?.addTrack(videoTrack)

        onLocalVideo?.let { it(videoTrack!!) }




        // Optional logging for debugging
        Log.d("WebRTC", "Audio track initialized and added to transceiver")
    }

    private fun StopVideo() {
        peerConnection?.removeTrack(videoSender);
        videoCapturer?.stopCapture()
        onLocalVideoEnded?.let { it() }
    }


    fun ToggleCamera() {
        if (isCameraEnabled) {
            isCameraEnabled = false;
            //disable camera
            StopVideo()

        } else {
            isCameraEnabled = true;
            //enable camera
            InitVideo()
        }
    }

    public fun InitWebRTC(onEnded: () -> Unit) {
        Log.d("WebRTC", "Initializing webrtc")
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        if (deviceID != null && peerID != null && initiator != null) {
            val peerConnectionFactory = initializePeerConnectionFactory(context!!)
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            }


            var negotiationAllowed = false;

            if (isInitiator) {
                negotiationAllowed = true;
            }

            val negotiationQueue = LinkedList<Runnable>()

            // Flag to track negotiation state
            var isNegotiating = false

            fun processNegotiationQueue() {
                if (isNegotiating || negotiationQueue.isEmpty()) {
                    return
                }

                isNegotiating = true
                val task = negotiationQueue.poll()
                task?.run()

                // After the task is completed, process the next item in the queue
                isNegotiating = false
                processNegotiationQueue()
            }

            fun negotiationNeeded() {
                if (negotiationAllowed) {
                    sendOffer(peerConnection, peerID)
                } else {
                    Log.d("WebRTC", "Negotiation not allowed yet")
                }
            }

            // Set remote description
            fun setRemote(sdp: SessionDescription) {
                if (peerConnection == null) {
                    Log.e("WebRTC", "setRemote will fail because peerConnection is null")
                }
                peerConnection?.setRemoteDescription(
                    object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            // Not relevant for setRemoteDescription; leave unimplemented or log if needed
                            Log.d(
                                "WebRTC",
                                "onCreateSuccess called unexpectedly for setRemoteDescription"
                            )
                        }

                        override fun onSetSuccess() {
                            // Successfully set the remote description
                            Log.d("WebRTC", "Remote description set successfully: ${sdp.type}")
                        }

                        override fun onCreateFailure(error: String?) {
                            // Not relevant for setRemoteDescription; leave unimplemented or log if needed
                            Log.e(
                                "WebRTC",
                                "onCreateFailure called unexpectedly for setRemoteDescription: $error"
                            )
                        }

                        override fun onSetFailure(error: String?) {
                            // Handle the failure to set the remote description
                            Log.e("WebRTC", "Failed to set remote description: $error")
                            // You can implement further error recovery logic here
                        }
                    },
                    sdp
                )
            }

            fun createAnswer() {
                if (peerConnection == null) {
                    Log.e("WebRTC", "createAnswer will fail because peerConnection is null")
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp?.let {
                            // Set the local description
                            peerConnection!!.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(sdp: SessionDescription?) {
                                    // Not relevant for setLocalDescription
                                }

                                override fun onSetSuccess() {
                                    Log.d(
                                        "WebRTC",
                                        "Local description set successfully: ${it.type}"
                                    )
                                    // Emit the answer to the signaling server
                                    Log.d("WebRTC", "Answer: $peerID")
                                    val answerJson = JSONObject().apply {
                                        put("deviceID", peerID)
                                        put(
                                            "data",
                                            JSONObject().apply {
                                                put("type", "answer")
                                                put(
                                                    "answer",
                                                    JSONObject("""{"type": "answer", "sdp": "${it.description}"}""")
                                                )
                                            }
                                        )
                                    }
                                    SocketConnection.socket.emit("relay", answerJson)
                                    negotiationAllowed = true;
                                }

                                override fun onCreateFailure(error: String?) {
                                    // Not relevant for setLocalDescription
                                }

                                override fun onSetFailure(error: String?) {
                                    Log.e("WebRTC", "Failed to set local description: $error")
                                }
                            }, it)
                        }
                    }

                    override fun onSetSuccess() {
                        // Not relevant for createAnswer
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e("WebRTC", "Failed to create answer: $error")
                    }

                    override fun onSetFailure(error: String?) {
                        // Not relevant for createAnswer
                    }
                }, MediaConstraints())
            }

            fun handlePayload(payload: JSONObject) {
                val senderID = payload.getString("senderID");
                val data = payload.getJSONObject("data")
                Log.d("WebRTC", "Processing: ${data.toString()}")

                if (data.getString("type") == "offer") {
                    val sdp = SessionDescription(
                        SessionDescription.Type.OFFER,
                        JSONObject(data.getString("offer")).getString("sdp")
                    )
                    negotiationQueue.add {
                        if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE) {
                            setRemote(sdp)
                            createAnswer()
                        } else {
                            Log.w(
                                "WebRTC",
                                "Ignoring offer due to invalid PeerConnection state: ${peerConnection?.signalingState()}"
                            )
                        }
                    }
                    processNegotiationQueue()
                }

                if (data.getString("type") == "candidate" && data.getString("candidate") != "null") {
                    val candidateJSON = JSONObject(data.getString("candidate"))
                    val sdpMid = candidateJSON.getString("sdpMid")
                    val sdpMLineIndex = candidateJSON.getInt("sdpMLineIndex")
                    val sdp = candidateJSON.getString("candidate")
                    peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }

                if (data.getString("type") == "answer") {
                    val answerJSON = JSONObject(data.getString("answer"))
                    setRemote(
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            answerJSON.getString("sdp")
                        )
                    )
                }
            }

            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                        Log.d("WebRTC", "Signaling state changed: $state")
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        Log.d("WebRTC", "ICE connection state changed: $state")
                        if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                            Log.w("WebRTC", "Peer disconnected")
                            GlobalScope.launch(Dispatchers.Main) {
                                hangUP();
                                onEnded();
                            }
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        Log.d("WebRTC", "ICE connection receiving changed: $receiving")
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                        Log.d("WebRTC", "ICE gathering state changed: $state")
                    }

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate?.let {
                            Log.d("WebRTC", "ICE Candidate: ${it.sdp}")
                            val candidateJson = JSONObject().apply {
                                put("deviceID", peerID)
                                put(
                                    "data",
                                    JSONObject().apply {
                                        put("type", "candidate")
                                        put("candidate", JSONObject().apply {
                                            put("candidate", it.sdp)
                                            put("sdpMid", it.sdpMid)
                                            put("sdpMLineIndex", it.sdpMLineIndex)
                                        })

                                    }
                                )
                            }
                            SocketConnection.socket.emit("relay", candidateJson)
                        }
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                        candidates?.let {
                            Log.d(
                                "WebRTC",
                                "ICE Candidates removed: ${it.joinToString(", ") { candidate -> candidate.sdp }}"
                            )
                        }
                    }

                    override fun onAddTrack(
                        receiver: RtpReceiver?,
                        mediaStreams: Array<out MediaStream>?
                    ) {
                        Log.d("WebRTC", "Track added by remote")

                        // Handle audio or video track from the RtpReceiver directly
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            track.setEnabled(true)
                            Log.d("WebRTC", "Video track added: ${track.id()} State: ${track.state()}")

                            // Pass the track to your rendering logic
                            onRemoteVideo?.let {
                                Log.d("WebRTC", "Rendering remote video track")
                                it(track)
                            } ?: Log.e("WebRTC", "onRemoteVideo is not set!")
                        } else if (track is AudioTrack) {
                            track.setEnabled(true)
                            Log.d("WebRTC", "Audio track added: ${track.id()}")
                        } else {
                            Log.e("WebRTC", "Unknown track type")
                        }
                    }

                    override fun onRemoveTrack(receiver: RtpReceiver?) {
                        onRemoteVideoEnded?.let { it() }
                        super.onRemoveTrack(receiver)
                    }

                    override fun onAddStream(stream: MediaStream?) {
                        stream?.let {
                            Log.d(
                                "WebRTC",
                                "MediaStream added with ${it.videoTracks.size} video tracks and ${it.audioTracks.size} audio tracks"
                            )
                            // Handle the remote stream, e.g., attach it to a video view.
                        }
                    }

                    override fun onRemoveStream(stream: MediaStream?) {
                        Log.d("WebRTC", "MediaStream removed")
                        // Handle stream removal if necessary.
                        onRemoteVideoEnded?.let { it() }
                    }

                    override fun onDataChannel(dataChannel: DataChannel?) {
                        dataChannel?.let {
                            Log.d("WebRTC", "DataChannel opened: ${it.label()}")
                            // Set up the data channel for sending/receiving messages.
                        }
                    }

                    override fun onRenegotiationNeeded() {
                        Log.d("WebRTC", "Renegotiation needed")
                        // Trigger renegotiation if required.
                        negotiationNeeded();
                    }
                }
            )

            if (preInit) {
                preInit = false;
                while (initialMessageQueue.isNotEmpty()) {
                    val payload = initialMessageQueue.poll();
                    if (payload != null) {
                        Log.d("WebRTC", "Loading from queue: ${payload.toString()}")
                    }
                    if (payload != null) {
                        handlePayload(payload)
                    }
                }
                negotiationAllowed = true;
                negotiationNeeded()
            }

            socket.on("relay", { args ->
                val payload = JSONObject(args[0].toString());
                handlePayload(payload)
            })


            //initialize audio
            InitAudio()

            if (isInitiator) {
                sendOffer(peerConnection, peerID)
            }
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }
}