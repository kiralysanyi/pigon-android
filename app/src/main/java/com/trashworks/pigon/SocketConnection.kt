package com.trashworks.pigon
import android.util.Log
import androidx.navigation.NavController
import  io.socket.client.IO
import  com.trashworks.pigon.APIHandler
import io.socket.client.Socket
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections.singletonList
import java.util.Collections.singletonMap

object SocketConnection {


    val socketOptions = IO.Options().apply {
        extraHeaders = singletonMap("Cookie", singletonList(APIHandler.getCookies()))
        path = "/socketio"
    }
    var initialized = false;
    var incall = false;
    var acceptedCall = false;
    val socket: Socket = IO.socket("https://pigon.ddns.net", socketOptions);
    @OptIn(DelicateCoroutinesApi::class)
    fun init() {
        Log.d("Socket", "Initializing socketio connection")
        socket.on("error") { args ->
            Log.e("Socket error", args.joinToString(", "))
        }

        socket.on("connect", {
            Log.d("Socket", "Connected to socketio host")
        })
        socket.connect()

        initialized = true;
    }
}