package com.trashworks.pigon

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ChatScreen(navController: NavController, chatInfo: String) {
    val chatJson = JSONObject(chatInfo)
    val chatID = chatJson.getInt("chatid");
    var userInfo by remember {
        mutableStateOf(JSONObject())
    }

    var userInfoLoaded by remember {
        mutableStateOf(false);
    }

    var messagesLoaded by remember {
        mutableStateOf(false)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var isImageViewerOpen by remember {
        mutableStateOf(false)
    }

    var imageViewerSource by remember {
        mutableStateOf("")
    }

    var isVideoViewerOpen by remember {
        mutableStateOf(false)
    }

    var videoViewerSource by remember {
        mutableStateOf("")
    }

    var page by remember {
        mutableStateOf(1)
    }

    var messages by remember { mutableStateOf(listOf<JSONObject>()) }

    val scope = rememberCoroutineScope();
    var inputmsg by remember {
        mutableStateOf("")
    }

    val listState = rememberLazyListState()

    DisposableEffect("") {
        Log.d("Is socket initialized???", SocketConnection.initialized.toString())
        val listener = Emitter.Listener { args ->
            val msgData = JSONObject(args[0].toString());
            msgData.put("senderid", msgData.getInt("senderID"))
            msgData.remove("senderID");
            Log.d("msgdata", msgData.toString() + "Chatid: $chatID");
            if (msgData.getInt("chatID") == chatID) {
                //add message to messages;
                messages = listOf(msgData) + messages
                Log.d("AAAAAAAAAAAAAA", messages.toString())
                scope.launch {
                    listState.scrollToItem(0);
                }
            }
        }
        SocketConnection.socket.on("message", listener)

        onDispose {
            SocketConnection.socket.off("message", listener)
        }
    }

    var isAtTop by remember { mutableStateOf(false) }

    var reachedLastPage by remember {
        mutableStateOf(false)
    }


    val context = LocalContext.current.applicationContext;

    LaunchedEffect(isAtTop) {
        if (isAtTop && !isLoading && !reachedLastPage) {
            isLoading = true;
            scope.launch {
                page++;
                APIHandler.getMessages(chatID, page = page) { res ->
                    Log.d("Fing", res.dataArray.toString())
                    Log.d("Fing", res.data.toString())
                    if (res.dataArray.length() == 0) {
                        reachedLastPage = true;
                    }
                    messages += res.dataArray.let { jsonArray ->
                        List(jsonArray.length()) { index ->
                            jsonArray.getJSONObject(index)
                        }
                    }

                    isLoading = false;

                }
            }
        }
    }

    // Observe scroll state
    LaunchedEffect(listState.firstVisibleItemIndex) {

        snapshotFlow {
            val isLastItemVisible =
                listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.count() == listState.layoutInfo.totalItemsCount
            isLastItemVisible
        }
            .distinctUntilChanged() // Avoid redundant updates
            .collect { isAtTop = it }
    }

    LaunchedEffect(Unit) {


        scope.launch {
            APIHandler.getUserInfo { res ->
                userInfo = res.data.getJSONObject("data");
                Log.d("UserInfo", userInfo.toString())
                userInfoLoaded = true;

            }
        }

        if (!messagesLoaded) {
            scope.launch {
                APIHandler.getMessages(chatID, page = 1) { res ->
                    Log.d("Fing", res.dataArray.toString())
                    Log.d("Fing", res.data.toString())
                    messages = res.dataArray.let { jsonArray ->
                        List(jsonArray.length()) { index ->
                            jsonArray.getJSONObject(index)
                        }
                    }

                    messagesLoaded = true;
                    isLoading = false;

                    scope.launch {
                        listState.scrollToItem(0);
                    }
                }
            }

        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            Log.d("Selected media", uri.toString())
            if (uri != null) {
                scope.launch {
                    APIHandler.uploadToCdn(
                        location = uri, chatID = chatID,
                        onResult = { filename ->
                            Log.d("Sendfile", filename)
                            SocketConnection.socket.emit(
                                "message",
                                JSONObject("{\"chatID\": $chatID, \"message\": {\"type\": \"image\", \"content\": \"$filename\"}}")
                            )
                        },
                        context = context,
                    )
                }

            }
        }

    PigonTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .height(64.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Go back to chats",
                    modifier = Modifier
                        .height(50.dp)
                        .padding(7.dp)
                        .width(50.dp)
                        .clickable {
                            navController.navigate("main_screen")
                        },
                    tint = MaterialTheme.colorScheme.onSurface


                )
                if (userInfoLoaded == true) {
                    if (chatJson.getInt("groupchat") == 0) {
                        //display pfp if not groupchat
                        val participants = chatJson.getJSONArray("participants")
                        var pfpID = 0;
                        if (participants.length() == 2) {
                            for (i in 0..<participants.length()) {
                                if (participants.get(i) != userInfo.getInt("id")) {
                                    pfpID = participants.getInt(i);
                                }
                            }
                        }
                        LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=$pfpID&smol=true")

                    }

                    Text(
                        text = chatJson.getString("name"),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }


            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background),
                state = listState,
                reverseLayout = true,
                horizontalAlignment = Alignment.Start
            ) {
                if (messagesLoaded == true && userInfoLoaded) {
                    //render messages
                    Log.d("AAAA", messages.toString())
                    items(messages) { message ->
                        val msgData = JSONObject(message.getString("message"));

                        var bg = MaterialTheme.colorScheme.secondaryContainer;
                        var color = MaterialTheme.colorScheme.onSecondaryContainer;

                        var senderID = 0;
                        try {
                            senderID = message.getInt("senderid")
                        } catch (e: Exception) {
                            senderID = message.getInt("senderID")
                        }


                        if (senderID == userInfo.getInt("id")) {
                            bg = MaterialTheme.colorScheme.primaryContainer
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        var senderName = "";
                        try {
                            senderName = message.getString("username");
                        } catch (e: Exception) {
                            senderName = message.getString("senderName")
                        }

                        var read = true;

                        try {
                            read = message.getBoolean("read");
                            Log.d("Read", read.toString())
                        } catch (e: Exception) {
                            Log.e("AA", e.toString())
                            read = true;
                        }

                        if (read == false) {
                            bg = MaterialTheme.colorScheme.primary;
                            color = MaterialTheme.colorScheme.onPrimary;
                        }

                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .clip(
                                    RoundedCornerShape(16.dp)
                                )
                                .widthIn(max = 250.dp)
                                .background(bg)

                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                            ) {
                                LoadImageFromUrl(
                                    "https://pigon.ddns.net/api/v1/auth/pfp?id=${
                                        message.getInt(
                                            "senderid"
                                        )
                                    }&smol=true",
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                )

                                Text(
                                    text = senderName,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(start = 10.dp)
                                )

                            }

                            Row {

                                if (msgData.getString("type") == "text") {
                                    Text(
                                        msgData.getString("content"),
                                        color = color,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }

                                if (msgData.getString("type") == "image") {
                                    val imgUrl =
                                        "https://pigon.ddns.net/" + msgData.getString("content");
                                    Log.d("Loadimage", imgUrl)
                                    LoadImageFromUrl(
                                        imgUrl,
                                        modifier = Modifier
                                            .heightIn(max = 200.dp)
                                            .clickable {
                                                Log.d("ImageViewer", "Opening image viewer")
                                                imageViewerSource = imgUrl;
                                                isImageViewerOpen = true;
                                            })
                                }
                                if (msgData.getString("type") == "video") {
                                    val videoUrl =
                                        "https://pigon.ddns.net/" + msgData.getString("content");
                                    Button(onClick = {
                                        videoViewerSource = videoUrl
                                        isVideoViewerOpen = true
                                    }, modifier = Modifier) {
                                        Text("Play Video")
                                    }
                                }

                            }
                        }


                    }
                }

            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Icon(
                    Icons.Rounded.Add, "Addicon", modifier = Modifier
                        .width(50.dp)
                        .height(50.dp)
                        .clickable {
                            launcher.launch(
                                PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    tint = MaterialTheme.colorScheme.onBackground
                )
                TextField(value = inputmsg, label = { Text("Message"); }, modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), onValueChange = { newVal ->
                    inputmsg = newVal
                })
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send icon",
                    modifier = Modifier
                        .width(50.dp)
                        .height(50.dp)
                        .padding(10.dp)
                        .clickable {
                            //send message
                            SocketConnection.socket.emit(
                                "message",
                                JSONObject("{\"chatID\": $chatID, \"message\": {\"type\": \"text\", \"content\": \"$inputmsg\"}}")
                            )
                            inputmsg = "";
                        },
                    tint = MaterialTheme.colorScheme.onBackground
                )

            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (isImageViewerOpen == true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Icon(
                    Icons.Rounded.Close, "Close image viewer", modifier = Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clickable {
                            isImageViewerOpen = false
                        },
                    tint = MaterialTheme.colorScheme.onBackground
                )
                LoadImageFromUrl(
                    imageViewerSource, Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }

        if (isVideoViewerOpen == true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Icon(
                    Icons.Rounded.Close, "Close video viewer", modifier = Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clickable {
                            isVideoViewerOpen = false
                        },
                    tint = MaterialTheme.colorScheme.onBackground
                )
                LoadVideoFromUrl(
                    videoViewerSource, Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}