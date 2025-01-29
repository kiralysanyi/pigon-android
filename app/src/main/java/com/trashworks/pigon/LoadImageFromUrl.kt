package com.trashworks.pigon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


fun getCurrentDateTime(): String {
    val currentDateTime = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return currentDateTime.format(formatter)
}

fun parseDateFromString(dateString: String): LocalDateTime {
    // Define the format of the input string
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    // Parse the string into a LocalDateTime object
    return LocalDateTime.parse(dateString, formatter)
}

fun timePassed(dateTime1: String, dateTime2: String): Duration {
    // Define the format of the date strings
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Parse the date-time strings into LocalDateTime objects
    val parsedDateTime1 = LocalDateTime.parse(dateTime1, formatter)
    val parsedDateTime2 = LocalDateTime.parse(dateTime2, formatter)

    // Calculate the duration between the two dates
    val duration = Duration.between(parsedDateTime1, parsedDateTime2)

    // Build the human-readable string
    return duration;
}

fun loadImageFromDiskCache(context: Context, url: String): Bitmap? {
    val cacheUpdateStoreFile = File(context.cacheDir, "cachedate")
    val file = File(context.cacheDir, url.hashCode().toString())
    if (url.contains("/api/v1/auth/pfp") && file.exists() && cacheUpdateStoreFile.exists()) {
        val lastUpdate = cacheUpdateStoreFile.readText()
        val duration = timePassed(lastUpdate, getCurrentDateTime())
        if (duration.toHours() > 1) {
            Log.d("Cache", "cache expired: ${file.name}")
            file.delete()
            return null;
        }
    }

    if (!cacheUpdateStoreFile.exists() && file.exists()) {
        file.delete()
        return null
    }

    if (!file.exists()) {
        return null;
    }

    val bitmap = BitmapFactory.decodeFile(file.path);
    if (bitmap == null) {
        file.delete()
        Log.e("Cache", "Cahce error: decodeFile returned null for ${file.path}")
    }
    return bitmap;
}

fun clearDiskCache(context: Context) {
    val cacheDir = context.cacheDir
    val files = cacheDir.listFiles()
    for (file in files) {
        file.delete()
    }
}

fun saveImageToDiskCache(context: Context, url: String, bitmap: Bitmap) {

    val file = File(context.cacheDir, url.hashCode().toString())
    try {
        if (!file.exists()) {
            if (url.contains("/api/v1/auth/pfp")) {
                val cacheUpdateStoreFile = File(context.cacheDir, "cachedate")
                cacheUpdateStoreFile.writeText(getCurrentDateTime())
            }

            val fileOutputStream = FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close();
        } else {
            Log.d("Cache", "Cache file exists aborting saving")
        }


    } catch (e: Exception) {
        Log.e("Cache", e.toString())
    }
}

@Composable
fun LoadImageFromUrl(
    imageUrl: String, modifier: Modifier = Modifier
        .width(64.dp)
        .height(64.dp)
        .clip(
            RoundedCornerShape(64.dp)
        )
) {

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current.applicationContext
    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {


            imageBitmap = loadImageFromDiskCache(context, imageUrl)

            if (imageBitmap == null) {
                try {


                    val client = OkHttpClient.Builder()

                        .build()

                    val reqHeaders =
                        Headers.Builder().set("cookie", APIHandler.getCookies()).build()
                    val request = Request.Builder()
                        .url(imageUrl)
                        .headers(reqHeaders)
                        .build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val inputStream: InputStream? = response.body?.byteStream()

                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (inputStream != null) {
                            when {
                                bitmap != null -> {
                                    saveImageToDiskCache(context, imageUrl, bitmap)
                                }
                            }
                        }
                        imageBitmap = bitmap
                        inputStream?.close()
                    } else {
                        errorMessage = "Error: Unable to download image"
                    }
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.toString()}"
                }
            }

        }

    }

    Box(
        modifier = modifier
    ) {
        when {
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = "Downloaded Image",
                    modifier = Modifier.fillMaxSize()
                )
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                CircularProgressIndicator()
            }
        }
    }
}