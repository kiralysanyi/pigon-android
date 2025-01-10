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
    fun init(navController: NavController) {
        Log.d("Socket", "Initializing socketio connection")
        socket.on("error") { args ->
            Log.e("Socket error", args.joinToString(", "))
        }

        socket.on("connect", {
            Log.d("Socket", "Connected to socketio host")
        })
        socket.connect()

        socket.on("incomingcall", {args ->

            val data = JSONObject(args[0].toString());
            val callid = data.getString("callid")
            if (incall == true) {
                socket.emit("response$callid", JSONObject("""{"accepted": false, "reason": "Busy"}"""))
            }
            incall = true;
            GlobalScope.launch(Dispatchers.Main) {
                navController.navigate(Call(callInfo = data.toString()))
            }
            Log.d("Call", "Incoming: ${data.toString()}")
            socket.once("cancelledcall", {args ->
                val data = JSONObject(args[0].toString());
                GlobalScope.launch(Dispatchers.Main) {
                    navController.navigate("main_screen")
                }

                if (data.getString("callid") == callid) {
                    Log.d("Call", "Cancelled call: $callid")
                }

            })

            socket.once("acceptedcall", {
                if (acceptedCall == false) {
                    incall = false;
                    GlobalScope.launch(Dispatchers.Main) {
                        navController.navigate("main_screen")
                    }
                }

            })
        })

        initialized = true;
    }
}