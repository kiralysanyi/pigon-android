package com.trashworks.pigon

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.trashworks.pigon.SocketConnection.socket
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList
import java.util.Queue
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ProximityScreenOff(context: Context) {
    var isNear by remember { mutableStateOf(false) }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var wakeLock by remember { mutableStateOf<PowerManager.WakeLock?>(null) }

    // Access the proximity sensor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    isNear = it.values[0] < proximitySensor!!.maximumRange
                    updateScreenState(powerManager, wakeLock, isNear) { newWakeLock ->
                        wakeLock = newWakeLock
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Register the sensor listener
        proximitySensor?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        onDispose {
            // Unregister the sensor listener
            sensorManager.unregisterListener(sensorEventListener)
            wakeLock?.release()
        }
    }
}

private fun updateScreenState(
    powerManager: PowerManager,
    wakeLock: PowerManager.WakeLock?,
    isNear: Boolean,
    updateWakeLock: (PowerManager.WakeLock?) -> Unit
) {
    if (isNear) {
        if (wakeLock == null || !wakeLock.isHeld) {
            val newWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "MyApp:ProximityScreenOff"
            )
            newWakeLock.acquire() // Turn off the screen
            updateWakeLock(newWakeLock)
        }
    } else {
        wakeLock?.release() // Turn the screen back on
        updateWakeLock(null)
    }
}


fun initializePeerConnectionFactory(context: Context): PeerConnectionFactory {
    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
    )

    val options = PeerConnectionFactory.Options()
    val encoderFactory = DefaultVideoEncoderFactory(
        EglBase.create().eglBaseContext, true, true
    )
    val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

    return PeerConnectionFactory.builder()
        .setOptions(options)
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .createPeerConnectionFactory()
}

fun sendOffer(peerConnection: PeerConnection?, peerID: String?) {
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




@Composable
fun CallScreen(navController: NavController, callInfo: String, isInitiator: Boolean = false, displayName: String? = null) {
    val callJson = JSONObject(callInfo)
    val callID = callJson.getString("callid")
    var callAccepted by remember { mutableStateOf(isInitiator) }
    val context = LocalContext.current;
    var deviceID: String? by remember { mutableStateOf(null) }
    var peerID: String? by remember { mutableStateOf(null) }
    var initiator: String? by remember { mutableStateOf(null) }
    var peerRegistered: Boolean by remember { mutableStateOf(false) }
    var audioSource: AudioSource? by remember { mutableStateOf(null) }
    val audioManager by remember { mutableStateOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager) }
    val initialMessageQueue by remember { mutableStateOf(LinkedList<JSONObject>()) }
    var preInit by remember { mutableStateOf(true) };

    var seconds by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf(0) }

    ProximityScreenOff(LocalContext.current)

    // Coroutine to update the timer every second
    LaunchedEffect(preInit) {
        while (!preInit) {
            delay(1000L) // Wait for 1 second
            seconds++
            if (seconds == 60) {
                seconds = 0
                minutes++
            }
            if (minutes == 60) {
                minutes = 0
                hours++
            }
        }
    }

    LaunchedEffect("") {
        socket.on("relay", { args ->
            Log.d("Relay", args[0].toString())
            if (preInit) {
                initialMessageQueue.add(JSONObject(args[0].toString()));
            }
        })
    }

    var isSpeaker by remember { mutableStateOf(false) }

    LaunchedEffect(isSpeaker) {
        audioManager.isSpeakerphoneOn = isSpeaker;
    }

    LaunchedEffect(callAccepted) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (callAccepted) {
            APIHandler.registerPeer(callID, { res ->
                if (res.success) {
                    peerRegistered = true;
                }
            })
        }
    }

    LaunchedEffect(callAccepted, deviceID, peerRegistered) {

        if (deviceID == null && !peerRegistered) {
            APIHandler.getDeviceId { res ->
                deviceID = res;
                Log.d("Call", "DeviceID: $deviceID")
            }
        }
        if (callAccepted && deviceID != null && peerRegistered) {
            APIHandler.getPeers(callID, { res ->
                if (res.success) {
                    //start
                    Log.d("Call", "Peers info: ${res.data.toString()}")
                    peerID = res.data.getJSONArray("peers").getString(0);
                    initiator = res.data.getString("initiator")
                }
            })

        }
    }

    var peerConnection: PeerConnection? by remember { mutableStateOf(null) }

    fun InitAudio(peerConnection: PeerConnection?) {
        // Initialize WebRTC dependencies (if not already done)
        val eglBase = EglBase.create()
        val peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
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
    }


    LaunchedEffect(initiator, deviceID, peerID, peerRegistered) {
        if (deviceID != null && peerID != null && peerRegistered && initiator != null) {
            val peerConnectionFactory = initializePeerConnectionFactory(context)
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
                    val sdp =
                        SessionDescription(
                            SessionDescription.Type.OFFER,
                            JSONObject(data.getString("offer")).getString("sdp")
                        )
                    setRemote(sdp)
                    createAnswer()
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
                                audioSource?.dispose()
                                peerConnection?.close()
                                SocketConnection.incall = false;
                                SocketConnection.acceptedCall = false;
                                navController.navigate("main_screen")
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
                        mediaStreams?.forEach { mediaStream ->
                            // Iterate through the audio tracks in the MediaStream
                            mediaStream.audioTracks.forEach { audioTrack ->
                                // Enable the audio track
                                audioTrack.setEnabled(true)

                                // Optionally log or debug
                                Log.d("WebRTC", "Audio track added: ${audioTrack.id()}")

                                // WebRTC automatically plays the audio through the device speakers
                            }
                        }
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
            InitAudio(peerConnection)

            if (isInitiator) {
                sendOffer(peerConnection, peerID)
            }
        }
    }

    PigonTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            if (!callAccepted) {
                Text(
                    callJson.getString("username"),
                    modifier = Modifier
                        .padding(top = 80.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    Button(onClick = {
                        SocketConnection.socket.emit(
                            "response$callID",
                            JSONObject("""{"accepted": false, "reason": "Declined"}""")
                        )
                        SocketConnection.incall = false;
                        navController.navigate("main_screen");

                    }) {
                        Text("Decline")
                    }
                    Button(onClick = {
                        //Accept call
                        SocketConnection.acceptedCall = true;
                        socket.emit(
                            "response$callID",
                            JSONObject("""{"accepted": true, "reason": "Why not"}""")
                        )
                        callAccepted = true;
                    }) {

                        Text("Accept")
                    }

                }
            } else {
                //render call UI
                Column(
                    modifier = Modifier
                        .padding(top = 80.dp)
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier
                            .padding(50.dp),
                        text = displayName ?: callJson.getString("username"),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 40.sp
                    )

                    Text(
                        text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }




                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    Icon(Icons.Filled.PhoneDisabled,"Hang up", tint = MaterialTheme.colorScheme.error, modifier = Modifier
                        .clickable {
                            //hangup
                            SocketConnection.socket.off("relay")
                            audioSource?.dispose()
                            peerConnection?.close()
                            SocketConnection.incall = false;
                            SocketConnection.acceptedCall = false;
                            navController.navigate("main_screen")
                        }
                        .height(50.dp)
                        .width(50.dp)
                    )

                    var speakerIcon = Icons.AutoMirrored.Filled.VolumeUp

                    if (isSpeaker) {
                        speakerIcon = Icons.AutoMirrored.Filled.VolumeDown
                    }

                    Icon(speakerIcon, "Speaker phone toggle", modifier = Modifier
                        .clickable {
                            isSpeaker = !isSpeaker
                        }
                        .height(50.dp)
                        .width(50.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

            }
        }
    }
}