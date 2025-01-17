package com.trashworks.pigon

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoRenderer(videoTrack: VideoTrack, eglBase: EglBase, modifier: Modifier) {
    // Remember the SurfaceViewRenderer to manage its lifecycle
    val context = LocalContext.current
    val surfaceViewRenderer = remember { SurfaceViewRenderer(context) }

    // Side-effects to initialize and dispose of the SurfaceViewRenderer
    DisposableEffect(Unit) {
        surfaceViewRenderer.init(eglBase.eglBaseContext, null)
        videoTrack.addSink(surfaceViewRenderer)

        onDispose {
            videoTrack.removeSink(surfaceViewRenderer)
            surfaceViewRenderer.release()
        }
    }

    // AndroidView to display the SurfaceViewRenderer in Compose
    AndroidView(
        factory = { surfaceViewRenderer },
        modifier = modifier
    )
}