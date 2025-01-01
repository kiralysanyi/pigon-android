package com.trashworks.pigon

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView


@OptIn(UnstableApi::class)
@Composable
fun LoadVideoFromUrl(
    videoUrl: String,
    modifier: Modifier = Modifier
        .fillMaxSize(),
    previewOnly: Boolean = false
) {
    val context = LocalContext.current
    val exoPlayer = ExoPlayer.Builder(context).build()
// Create a MediaSource
    // Create a data source factory.
    val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Cookie" to APIHandler.getCookies()))
// Create a progressive media source pointing to a stream uri.
    val mediaSource = ProgressiveMediaSource.Factory(factory)
        .createMediaSource(MediaItem.fromUri(videoUrl));
    // Set MediaSource to ExoPlayer
    LaunchedEffect(mediaSource) {
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()

    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Use AndroidView to embed an Android View (PlayerView) into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer

            }

        },
        modifier = modifier
    )
}