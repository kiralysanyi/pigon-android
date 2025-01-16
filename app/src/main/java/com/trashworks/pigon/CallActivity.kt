package com.trashworks.pigon

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext

class CallActivity : ComponentActivity() {

    private var callService: CallService? = null
    private var isBound = false

    private var onServiceConnected: () -> Unit = {Log.d("CallUI", "SAASdAWDAWDAWDwdawdawfsgrkoawuioef")};

    // Connection to the service
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("CallUI", "Service connected")
            val binder = service as CallService.LocalBinder
            callService = binder.getService()
            isBound = true
            onServiceConnected();
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            callService = null
            isBound = false
            Log.d("CallUI", "Service disconnected")
        }
    }

    override fun onDestroy() {
        callService?.stopSelf();
        super.onDestroy()
        unregisterReceiver(closeReceiver)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.trashworks.ACTION_CLOSE_ACTIVITY") {
                finish() // Close the activity
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        Intent(this, CallService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        val filter = IntentFilter("com.trashworks.ACTION_CLOSE_ACTIVITY")
        registerReceiver(closeReceiver, filter, RECEIVER_EXPORTED)
        super.onCreate(savedInstanceState)
        val activityContext = this;

        val callInfo = intent.getStringExtra("callInfo")
        val isInitiator = intent.getBooleanExtra("isInitiator", false);
        val displayName = intent.getStringExtra("displayName");


        Log.d("CallUI", "Ready to load")
        onServiceConnected = {
            Log.d("CallUI", "Setting content")
            setContent {

                if (callInfo != null) {
                    CallScreen(
                        callInfo, isInitiator, displayName,
                        onEnded = {
                            //call ended
                            SocketConnection.socket.disconnect();
                            activityContext.finish()
                        },
                        callService
                    )
                }
            }

        }
    }
}