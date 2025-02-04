package com.trashworks.pigon

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    senderName: String,
    senderID: Int,
    type: String,
    content: String,
    time: String,
    isCurrentUser: Boolean,
    isRead: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = { /* Do nothing */ },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "Cancel message"
            )
        ,
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isCurrentUser) {
            LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=$senderID", modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(32.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        var msgbg = MaterialTheme.colorScheme.surface

        if (!isRead) {
            msgbg = MaterialTheme.colorScheme.surfaceBright
        }

        Column(
            modifier = Modifier
                .background(
                    if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer
                    else msgbg,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .widthIn(max = 200.dp)
        ) {
            if (!isCurrentUser) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (type == "text") {
                Text(
                    text = decodeHTML(content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (type == "image") {
                val imgUrl =
                    "https://pigon.ddns.net/$content";
                Log.d("Loadimage", imgUrl)
                LoadImageFromUrl(imgUrl,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .clickable {
                            Log.d("ImageViewer", "Opening image viewer")
                            onClick()
                        })
            }
            if (type == "video") {
                Button(onClick = {
                    onClick()
                }, modifier = Modifier) {
                    Text("Play Video")
                }
            }



            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}
