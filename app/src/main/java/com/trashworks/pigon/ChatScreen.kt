package com.trashworks.pigon

import android.content.Intent
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.sharp.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.navigation.NavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.trashworks.pigon.ui.theme.PigonTheme
import io.socket.emitter.Emitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun decodeHTML(html: String): String {
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}

@Composable
fun ChatScreen(navController: NavController, chatInfo: String, activityContext: MainActivity) {
    val chatJson = JSONObject(chatInfo)
    val chatID = chatJson.getInt("chatid");
    var userInfo by remember {
        mutableStateOf(JSONObject())
    }

    var userInfoLoaded by remember {
        mutableStateOf(false);
    }

    var showEditButton by remember { mutableStateOf(false) }

    if (userInfoLoaded) {
        if (userInfo.getInt("id") == chatJson.getInt("initiator") && chatJson.getInt("groupchat") == 1) {
            showEditButton = true;
        }
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

    var showProfileInfo by remember { mutableStateOf(false) }

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
            if (msgData.getInt("chatID") == chatID) {
                SocketConnection.socket.emit(
                    "setLastRead",
                    JSONObject("""{"chatID": $chatID, messageID: ${msgData.getInt("messageID")}}""")
                )
                //add message to messages;
                messages = listOf(msgData) + messages

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

    var openEditModal by remember { mutableStateOf(false) }

    var isCalling by remember { mutableStateOf(false) }
    var callResponseReason by remember { mutableStateOf("") }

    var callJson: JSONObject? by remember { mutableStateOf(null) }

    val context = LocalContext.current.applicationContext;
    var pfpID by remember { mutableStateOf(0) }

    LaunchedEffect(isAtTop) {
        if (isAtTop && !isLoading && !reachedLastPage) {
            isLoading = true;
            scope.launch {
                page++;
                APIHandler.getMessages(chatID, page = page) { res ->
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

    var showSendingOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {


        scope.launch {
            APIHandler.getUserInfo { res ->
                if (!res.success) {
                    navController.navigate("loading_screen");
                } else {
                    userInfo = res.data.getJSONObject("data");

                    userInfoLoaded = true;
                }


            }
        }

        if (!messagesLoaded) {
            scope.launch {
                APIHandler.getMessages(chatID, page = 1) { res ->

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
                    showSendingOverlay = true;
                    APIHandler.uploadToCdn(
                        location = uri, chatID = chatID,
                        onResult = { filename ->
                            Log.d("Sendfile", filename)
                            SocketConnection.socket.emit(
                                "message",
                                JSONObject(
                                    "{\"chatID\": $chatID, \"message\": {\"type\": \"${
                                        getMediaType(
                                            context,
                                            uri
                                        )
                                    }\", \"content\": \"$filename\"}}"
                                )
                            )
                            showSendingOverlay = false;
                        },
                        context = context,
                    )
                }

            }
        }

    PigonTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .heightIn(min = 84.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Go back to chats",
                    modifier = Modifier
                        .height(50.dp)
                        .padding(5.dp)
                        .width(50.dp)
                        .clickable {
                            navController.navigate("main_screen")
                        }
                        .align(Alignment.BottomStart),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer


                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (userInfoLoaded == true) {
                        if (chatJson.getInt("groupchat") == 0) {
                            //display pfp if not groupchat
                            val participants = chatJson.getJSONArray("participants")
                            if (participants.length() == 2) {
                                for (i in 0..<participants.length()) {
                                    if (participants.get(i) != userInfo.getInt("id")) {
                                        pfpID = participants.getInt(i);
                                    }
                                }
                            }
                            LoadImageFromUrl(
                                "https://pigon.ddns.net/api/v1/auth/pfp?id=$pfpID&smol=true",
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(50.dp)
                                    .padding(5.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .clickable {
                                        showProfileInfo = true;
                                    }

                            )

                        }

                        Text(
                            text = chatJson.getString("name"),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 10.dp)
                                .clickable {
                                    showProfileInfo = true;

                                }

                        )
                    }
                }

                if (showEditButton) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit group",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .height(45.dp)
                            .padding(5.dp)
                            .width(45.dp)
                            .clickable {
                                //open group editing thing
                                openEditModal = true;
                            }
                    )
                }

                if (!showEditButton && chatJson.getInt("groupchat") == 0) {
                    Icon(
                        Icons.Default.Call,
                        "Call user",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .height(45.dp)
                            .padding(5.dp)
                            .width(45.dp)
                            .clickable {
                                //start call
                                APIHandler.prepareCall(chatID, onResult = { res ->
                                    isCalling = true;
                                    callResponseReason = "Calling..."
                                    if (res.success) {
                                        SocketConnection.incall = true;
                                        callJson = res.data;
                                        SocketConnection.socket.once(
                                            "callresponse${
                                                res.data.getString(
                                                    "callid"
                                                )
                                            }", { args ->
                                                val response = JSONObject(args[0].toString());
                                                if (!response.getBoolean("accepted")) {
                                                    Log.d(
                                                        "Call",
                                                        "Declined call: ${response.getString("reason")}"
                                                    )
                                                    callResponseReason =
                                                        response.getString("reason");
                                                    scope.launch {
                                                        delay(2000L)
                                                        isCalling = false;
                                                        SocketConnection.incall = false;
                                                        callJson = null;
                                                    }
                                                } else {
                                                    GlobalScope.launch(Dispatchers.Main) {
                                                        //open call activity res.data.toString(), isInitiator = true, chatJson.getString("name")
                                                        val intent = Intent(
                                                            context,
                                                            CallActivity::class.java
                                                        )
                                                        intent.apply {
                                                            flags =
                                                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                            putExtra(
                                                                "callInfo",
                                                                res.data.toString()
                                                            )
                                                            putExtra("isInitiator", true)
                                                            putExtra(
                                                                "displayName",
                                                                chatJson.getString("name")
                                                            )
                                                        }
                                                        context.startActivity(intent)
                                                        SocketConnection.incall = true;
                                                        activityContext.finish()
                                                    }
                                                }
                                            })
                                        SocketConnection.socket.emit("call", res.data)
                                    } else {
                                        isCalling = false;
                                    }
                                })
                            }
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
                                        decodeHTML(msgData.getString("content")),
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add, "Addicon", modifier = Modifier
                        .width(50.dp)
                        .height(50.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .clickable {
                            launcher.launch(
                                PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    tint = MaterialTheme.colorScheme.onSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (inputmsg == "") {
                        Text(
                            "Message", modifier = Modifier
                                .padding(start = 10.dp)
                                .align(Alignment.CenterStart),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    BasicTextField(value = inputmsg, modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 66.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),

                        onValueChange = { newVal ->
                            inputmsg = newVal

                        })
                    Icon(
                        Icons.AutoMirrored.Sharp.Send,
                        contentDescription = "Send icon",
                        modifier = Modifier
                            .width(50.dp)
                            .height(50.dp)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .align(Alignment.CenterEnd)
                            .clickable {
                                //send message
                                if (inputmsg.trim() != "") {
                                    SocketConnection.socket.emit(
                                        "message",
                                        JSONObject("{\"chatID\": $chatID, \"message\": {\"type\": \"text\", \"content\": \"${inputmsg.trim()}\"}}")
                                    )
                                    inputmsg = "";
                                }
                            },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }


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

        AnimatedVisibility(
            visible = isImageViewerOpen,
            enter = slideInVertically(
                initialOffsetY = { it }, // Starts off-screen to the left
                animationSpec = tween(durationMillis = 500) // Animation duration
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }, // Slides out to the left
                animationSpec = tween(durationMillis = 500)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
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

        AnimatedVisibility(
            visible = isVideoViewerOpen,
            enter = slideInVertically(
                initialOffsetY = { it }, // Starts off-screen to the left
                animationSpec = tween(durationMillis = 500) // Animation duration
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }, // Slides out to the left
                animationSpec = tween(durationMillis = 500)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
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

        AnimatedVisibility(
            visible = openEditModal,
            enter = slideInVertically(
                initialOffsetY = { it }, // Starts off-screen to the left
                animationSpec = tween(durationMillis = 500) // Animation duration
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }, // Slides out to the left
                animationSpec = tween(durationMillis = 500)
            )
        ) {
            EditModal(chatJson, closeCallback = { openEditModal = false }, navController)
        }

        if (isCalling) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
            ) {

                Text(
                    callResponseReason, fontSize = 30.sp, modifier = Modifier
                        .padding(top = 30.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Button(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    onClick = {
                        SocketConnection.socket.emit("cancelcall", callJson);
                        callJson = null;
                        SocketConnection.incall = false;
                        isCalling = false;
                    }
                ) {
                    Text("Cancel")
                }
            }
        }

        if (showProfileInfo && chatJson.getInt("groupchat") == 0) {
            ProfileViewer(pfpID, chatJson) { showProfileInfo = false }
        }

        if (showProfileInfo && chatJson.getInt("groupchat") == 1) {
            GroupInfo(chatJson) { leftGroup ->
                if (leftGroup) {
                    scope.launch { navController.navigate("main_screen"); }
                } else {
                    showProfileInfo = false
                }
            }
        }
        if (showSendingOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("Sending file", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun EditModal(chatJson: JSONObject, closeCallback: () -> Unit, navController: NavController) {
    var participants = remember { mutableStateListOf<JSONObject>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current


    LaunchedEffect("") {
        val users = JSONArray(chatJson.getString("participants"))
        for (i in 0..<users.length()) {
            scope.launch {
                APIHandler.getUserInfo(users.getInt(i)) { res ->
                    val data = res.data.getJSONObject("data");
                    data.put("id", users[i]);
                    participants.add(data);
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Icon(
            Icons.Rounded.Close, "Close editor", modifier = Modifier
                .width(64.dp)
                .height(64.dp)
                .clickable {
                    closeCallback()
                },
            tint = MaterialTheme.colorScheme.onBackground
        )

        Text(
            chatJson.getString("name"),
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp
        )

        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .align(Alignment.Center)
                .padding(10.dp)
                .fillMaxWidth()

        ) {
            items(participants) { participant ->
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadImageFromUrl(
                        "https://pigon.ddns.net/api/v1/auth/pfp?id=${
                            participant.getInt(
                                "id"
                            )
                        }"
                    )
                    Text(
                        participant.getString("username"),
                        color = MaterialTheme.colorScheme.onSurface
                    );
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
        ) {
            Button(
                onClick = {
                    //adduser
                    navController.navigate(Group(chatInfo = chatJson.toString()))
                },
                modifier = Modifier
                    .padding(8.dp)
            )
            {
                Text("Add/Remove participants")
            }

            Button(
                onClick = {
                    //delete group
                    APIHandler.deleteGroup(chatid = chatJson.getInt("chatid")) { res ->
                        if (res.success) {
                            scope.launch {
                                navController.navigate("main_screen")
                            }
                        } else {
                            Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }, colors = ButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Text("Delete group")
            }
        }


    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileViewer(userID: Int, chatJson: JSONObject, onBackClicked: () -> Unit) {
    var extraInfo by remember { mutableStateOf<JSONObject?>(null) }

    val context = LocalContext.current;
    val scope = rememberCoroutineScope()

    LaunchedEffect("") {
        APIHandler.getExtraInfo(userID) { res ->
            if (res.success) {
                extraInfo = res.data;
            } else {
                scope.launch {
                    Log.e(
                        "ProfileViewer",
                        "Failed to load extra info for this user: ${res.message}"
                    )
                    Toast.makeText(
                        context,
                        "Failed to load extra info for this user: ${res.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(chatJson.getString("name")) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Placeholder for profile image
            LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=$userID&smol=true")

            Spacer(modifier = Modifier.height(16.dp))

            if (extraInfo != null) {
                Text(
                    extraInfo!!.getString("fullname"),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    decodeHTML(extraInfo!!.getString("bio").replace("\n", "<br>")),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}