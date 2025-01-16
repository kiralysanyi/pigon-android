package com.trashworks.pigon

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationManager

import android.app.NotificationChannel

import android.app.PendingIntent

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build

import androidx.core.app.NotificationCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject


class PushNotificationService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("New Token", token)


        APIHandler.submitFirebaseToken(token, { res ->
            Log.d("FIREBASAAAAA", res.message)
        })
    }


    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("NotifHandler", "Received data: ${message.data.toString()}")

        if (message.data["type"] == "message") {
            // Check if message contains a notification payload.
            var messageID = 0;
            message.data.let { messageID = it["messageID"]?.toInt() ?: 0; }


            message.messageType?.let { Log.d("Message type", it) }
            message.data?.let {
                Log.d("NotifHandler", "Message Notification Body: ${it["body"]}")
                sendNotification(it["title"].toString(), it["body"].toString(), messageID)
            }
        }

        if (message.data["type"] == "cancel") {
            // Check if message contains a notification payload.
            var messageID = 0;
            var isCancelRequest = false;
            message.data.let {
                messageID = it["messageID"]?.toInt() ?: 0; isCancelRequest =
                (it["type"] == "cancel")
            }

            Log.d("NotifHandler", "cancel:$isCancelRequest msgid:$messageID")
            if (isCancelRequest) {
                cancelNotification(messageID)
                return;
            }
        }

        if (!APIHandler.isLoggedIn) {
            //initialize API and SocketConnection
            val dsWrapper = DataStoreWrapper(applicationContext);
            GlobalScope.launch {
                dsWrapper.getString()?.let { APIHandler.setCookies(it, dsWrapper) }
            }
        }

        if (message.data["type"] == "cancelcall") {
            if (!SocketConnection.acceptedCall) {
                val intent = Intent("com.trashworks.ACTION_CLOSE_ACTIVITY")
                sendBroadcast(intent)
                SocketConnection.incall = false;
            }
        }

        if (message.data["type"] == "call") {
            //display incoming call notification
            val callid = message.data["callid"];
            val username = message.data["username"];
            val chatid = message.data["chatid"];
            val callInfo = JSONObject().apply {
                put("callid", callid)
                put("username", username)
                put("chatid", chatid)
            }

            if (SocketConnection.initialized && SocketConnection.incall) {
                val responseJson = JSONObject()
                responseJson.apply {
                    put("callid", callid)
                    put("accepted", false)
                    put("reason", "Busy")
                }
                SocketConnection.socket.emit(
                    "answercall",
                    responseJson
                )
                return;
            }

            val intent = Intent(applicationContext, CallActivity::class.java)
            intent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("callInfo", callInfo.toString())
                putExtra("isInitiator", false)
                putExtra("displayName", username)
            }
            startActivity(intent)
            SocketConnection.incall = true;

        }
    }

    private fun cancelNotification(notificationId: Int) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(notificationId)
    }

    private fun sendNotification(messageTitle: String, messageBody: String, notificationId: Int) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val requestCode = 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(messageTitle)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chats",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())

    }
}