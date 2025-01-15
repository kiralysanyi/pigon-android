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

@Composable
fun KeepAppRunning(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var wakeLock by remember { mutableStateOf<PowerManager.WakeLock?>(null) }

    // Acquire the wake lock
    LaunchedEffect(Unit) {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp:KeepRunning"
            )
            wakeLock?.acquire() // Acquire the wake lock
        }
    }

    // Release the wake lock when no longer needed
    DisposableEffect(Unit) {
        onDispose {
            wakeLock?.release()
        }
    }
}

@Composable
fun CallScreen(callInfo: String, isInitiator: Boolean = false, displayName: String? = null, onEnded: () -> Unit, callService: CallService? = null) {
    val callJson = JSONObject(callInfo)
    var callAccepted by remember { mutableStateOf(isInitiator) }
    val context = LocalContext.current;
    var peerRegistered by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf(0) }
    var callID by remember { mutableStateOf(callJson.getString("callid")) }
    var deviceID: String? by remember { mutableStateOf(null) }
    var peerID: String? by remember { mutableStateOf(null) }
    var initiator: String? by remember { mutableStateOf(null) }

    ProximityScreenOff(LocalContext.current)
    KeepAppRunning(LocalContext.current)

    var isSpeaker by remember { mutableStateOf(false) }

    LaunchedEffect(callAccepted) {
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




    LaunchedEffect(initiator, deviceID, peerID, peerRegistered) {
        Log.d("WebRTC", "Init: $initiator, $deviceID, $peerID, $peerRegistered")
        if (peerID != null && deviceID != null && peerRegistered && initiator != null) {
            callService?.setData(isInitiator, callInfo, peerID!!, deviceID!!, initiator!!);
            callService?.InitWebRTC(isInitiator, onEnded)
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
                        val responseJson = JSONObject()
                        responseJson.apply {
                            put("callid", callID)
                            put("accepted", false)
                            put("reason", "Declined")
                        }
                        SocketConnection.socket.emit(
                            "answercall",
                            responseJson
                        )
                        SocketConnection.incall = false;
                        onEnded();

                    }) {
                        Text("Decline")
                    }
                    Button(onClick = {
                        //Accept call
                        SocketConnection.acceptedCall = true;
                        val responseJson = JSONObject()
                        responseJson.apply {
                            put("callid", callID)
                            put("accepted", true)
                            put("reason", "Why not?")
                        }
                        SocketConnection.socket.emit(
                            "answercall",
                            responseJson
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
                            callService?.hangUP()
                            onEnded();
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