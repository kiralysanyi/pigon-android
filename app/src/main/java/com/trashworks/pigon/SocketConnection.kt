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


    var socketOptions = IO.Options().apply {
        extraHeaders = singletonMap("Cookie", singletonList(APIHandler.getCookies()))
        path = "/socketio"
    }
    var initialized = false;
    var incall = false;
    var acceptedCall = false;
    var socket: Socket = IO.socket("https://pigon.ddns.net", socketOptions);

    fun init(fullInit: Boolean? = false) {
        if (fullInit == true) {
            initialized = false;
        }
        if (!initialized) { // Check if the socket has been initialized before
            socketOptions = IO.Options().apply {
                extraHeaders = singletonMap("Cookie", singletonList(APIHandler.getCookies()))
                path = "/socketio"
            }
            socket = IO.socket("https://pigon.ddns.net", socketOptions)
            socket.connect()
            // ... (rest of the initialization code) ...
            initialized = true // Set initialized to true after initializing the socket
        } else if (!socket.connected()) {
            // If the socket is initialized but not connected, try to reconnect
            socket.connect()
        }
    }
}