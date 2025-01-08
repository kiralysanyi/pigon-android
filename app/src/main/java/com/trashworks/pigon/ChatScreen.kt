package com.trashworks.pigon

import android.text.Html
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.sharp.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.trashworks.pigon.ui.theme.PigonTheme
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun decodeHTML(html: String): String {
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}

@Composable
fun ChatScreen(navController: NavController, chatInfo: String) {
    Log.d("Chatinfo", chatInfo)
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
                SocketConnection.socket.emit("setLastRead", JSONObject("""{"chatID": $chatID, messageID: ${msgData.getInt("messageID")}}"""))
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

    var openEditModal by remember { mutableStateOf(false) }


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
                if (!res.success) {
                    navController.navigate("loading_screen");
                } else {
                    userInfo = res.data.getJSONObject("data");
                    Log.d("UserInfo", userInfo.toString())
                    userInfoLoaded = true;
                }


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
                            var pfpID = 0;
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

                            )

                        }

                        Text(
                            text = chatJson.getString("name"),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 10.dp)

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
                                PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
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

        if (isImageViewerOpen == true) {
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

        if (isVideoViewerOpen == true) {
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

        if (openEditModal) {
            EditModal(chatJson, closeCallback = { openEditModal = false }, navController)
        }
    }
}

@Composable
fun EditModal(chatJson: JSONObject, closeCallback: () -> Unit, navController: NavController) {
    var participants = remember { mutableStateListOf<JSONObject>() }
    var scope = rememberCoroutineScope()


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