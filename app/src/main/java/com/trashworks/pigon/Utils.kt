package com.trashworks.pigon

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker.checkPermission
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Determines if the given Uri points to an image or a video.
 *
 * @param context The context used to access the ContentResolver.
 * @param uri The Uri to check.
 * @return A string indicating whether the Uri points to an "image", "video", or "unknown".
 */
fun getMediaType(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: run {
        // Fallback to guessing based on the file extension if MIME type is null
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    return when {
        mimeType?.startsWith("image/") == true -> "image"
        mimeType?.startsWith("video/") == true -> "video"
        else -> "unknown"
    }
}

fun formatIsoDateTime(isoString: String? = null): String {
    if (isoString == null) {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault()) // Use device time zone

        return formatter.format(Instant.now())
    }
    val instant = Instant.parse(isoString) // Parse ISO string
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault()) // Use device time zone
    return formatter.format(instant)
}

const val CHANNEL_ID = "chat_notification_channel"

@SuppressLint("MissingPermission")
fun sendNotification(
    context: Context,
    notificationId: Int,
    title: String,
    message: String,
    smallIcon: Int = R.mipmap.ic_launcher // Default icon
) {
    // Create Notification Channel (Only for Android 8.0+)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, "My Notifications", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notification Channel Description" }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    // Intent to open MainActivity when notification is tapped
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE // Required for API 31+
    )

    // Build and send the notification
    val notificationBuilder =
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(smallIcon)
            .setContentTitle(title).setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Open activity on click
            .setAutoCancel(true) // Dismiss when tapped


    try {
        NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
    } catch (e: Exception) {
        Log.e("Notifhandler", "Error sending notification: ${e.message}")
        Log.d("Notifhandler", "User probabbly denied notification permission")
    }
}