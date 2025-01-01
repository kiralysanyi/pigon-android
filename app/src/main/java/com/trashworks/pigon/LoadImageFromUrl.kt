package com.trashworks.pigon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

// In-memory LRU cache to store images
val imageCache = LruCache<String, Bitmap>(200 * 1024 * 1024) // 200 MB cache

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

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            imageBitmap = imageCache.get(imageUrl);
            if (imageBitmap == null) {
                try {
                    val client = OkHttpClient()
                    val reqHeaders =
                        Headers.Builder().set("cookie", APIHandler.getCookies()).build()
                    val request = Request.Builder().url(imageUrl).headers(reqHeaders).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val inputStream: InputStream? = response.body?.byteStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        imageBitmap = bitmap
                        // Cache the image
                        bitmap?.let {
                            imageCache.put(imageUrl, it)
                        }
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