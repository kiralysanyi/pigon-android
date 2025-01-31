package com.trashworks.pigon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun ImageViewer(imageViewerSource: String, onDismiss: () -> Unit) {

    var offsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        label = "overlayOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .offset { IntOffset(0, animatedOffsetY.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 200) { // Threshold to dismiss
                            onDismiss()
                        } else {
                            offsetY = 0f // Reset position if not swiped enough
                        }
                    }
                ) { _, dragAmount ->
                    offsetY += dragAmount
                }
            }
    ) {
        Icon(
            Icons.Rounded.Close,
            "Close image viewer",
            modifier = Modifier
                .width(64.dp)
                .height(64.dp)
                .clickable {
                    onDismiss();
                }
            ,
            tint = MaterialTheme.colorScheme.onBackground
        )
        LoadImageFromUrl(
            imageViewerSource,
            Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

@Composable
fun VideoViewer(videoViewerSource: String, onDismiss: () -> Unit) {
    var offsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        label = "overlayOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .offset { IntOffset(0, animatedOffsetY.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 200) { // Threshold to dismiss
                            onDismiss()
                        } else {
                            offsetY = 0f // Reset position if not swiped enough
                        }
                    }
                ) { _, dragAmount ->
                    offsetY += dragAmount
                }
            }
    ) {
        Icon(
            Icons.Rounded.Close,
            "Close video viewer",
            modifier = Modifier
                .width(64.dp)
                .height(64.dp)
                .clickable {
                    onDismiss()
                },
            tint = MaterialTheme.colorScheme.onBackground
        )
        LoadVideoFromUrl(
            videoViewerSource,
            Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}