package com.trashworks.pigon

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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

fun formatIsoDateTime(isoString: String): String {
    val instant = Instant.parse(isoString) // Parse ISO string
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault()) // Use device time zone
    return formatter.format(instant)
}