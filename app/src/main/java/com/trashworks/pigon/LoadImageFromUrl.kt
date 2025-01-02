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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit


fun loadImageFromDiskCache(context: Context, url: String): Bitmap? {
    val file = File(context.cacheDir, url.hashCode().toString())
    if (!file.exists()) {
        Log.d("Cache","Cache does not exits: ${file.path}")
        return  null;
    }
    val inputStream = FileInputStream(file.path);
    return BitmapFactory.decodeStream(inputStream);
}

fun saveImageToDiskCache(context: Context, url: String, inputStream: InputStream) {
    val file = File(context.cacheDir, url.hashCode().toString())
    try {
        val output = FileOutputStream(file)
        output.write(inputStream.read())
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
                            saveImageToDiskCache(context, imageUrl, inputStream)
                        }
                        imageBitmap = bitmap


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