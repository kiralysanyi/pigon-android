package com.trashworks.pigon

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.trashworks.pigon.ui.theme.PigonTheme
import io.socket.emitter.Emitter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

fun removeIntFromJSONArray(jsonArray: JSONArray, valueToRemove: Int): JSONArray {
    val resultArray = JSONArray()
    for (i in 0 until jsonArray.length()) {
        val item = jsonArray.optInt(i, Int.MIN_VALUE) // Safely get the integer value
        if (item != valueToRemove) {
            resultArray.put(item)
        }
    }
    return resultArray
}

@Composable
fun MainScreen(navController: NavController, dsWrapper: DataStoreWrapper) {
    // Define a state to hold the chat list
    var chats by remember { mutableStateOf(listOf<JSONObject>()) }
    // Loading state
    var isLoading by remember { mutableStateOf(true) }
    var scope = rememberCoroutineScope()

    var userDataLoaded by remember { mutableStateOf(false) }
    var userData by remember { mutableStateOf(JSONObject()) }

    DisposableEffect("") {
        scope.launch {
            APIHandler.getUserInfo { res ->

                if (!res.success) {
                    navController.navigate("loading_screen")
                    return@getUserInfo;
                }
                userData = res.data.getJSONObject("data");
                userDataLoaded = true;
            }
        }

        scope.launch {
            val token = Firebase.messaging.token.await();

            APIHandler.submitFirebaseToken(token, onResult = {res ->
                Log.d("MainScreen", "Submitted firebase token with result: " + res.message)
            })
        }

        val listener = Emitter.Listener {
            scope.launch {
                APIHandler.getChats { res ->
                    if (res.success) {
                        // Update chats state with the fetched data
                        chats = res.data.getJSONArray("data").let { jsonArray ->
                            List(jsonArray.length()) { index ->
                                jsonArray.getJSONObject(index)
                            }
                        }
                    } else {
                        Log.e("Fetch chats", res.message)
                    }
                    // Set loading to false once data is fetched
                    isLoading = false
                }
            }
        }

        SocketConnection.socket.on("newchat", listener)

        SocketConnection.socket.on("message", listener)

        onDispose {
            SocketConnection.socket.off("message", listener)
            SocketConnection.socket.off("newchat", listener)
        }
    }

    // Use LaunchedEffect to call the API once when the composable is first composed
    LaunchedEffect(Unit) {
        if (isLoading == true) {
            scope.launch {
                APIHandler.checkIfTokenValid { res ->
                    if (!res.success) {
                        navController.navigate("login_screen")
                    }
                }
            }
        }


        scope.launch {
            APIHandler.getChats { res ->
                if (res.success) {
                    // Update chats state with the fetched data
                    chats = res.data.getJSONArray("data").let { jsonArray ->
                        List(jsonArray.length()) { index ->
                            jsonArray.getJSONObject(index)
                        }
                    }
                } else {
                    Log.e("Fetch chats", res.message)
                }
                // Set loading to false once data is fetched
                isLoading = false
            }
        }

    }



    val insets = WindowInsets.systemBars.union(WindowInsets.ime)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current

    // Show a loading state or the actual UI
    if (isLoading) {
        // Display loading indicator
        PigonTheme {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

    } else {
        // Display the chats once data is loaded using LazyColumn
        PigonTheme {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        if (userDataLoaded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=${userData.getInt("id")}", modifier = Modifier
                                    .height(100.dp)
                                    .width(100.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                )
                                Text(userData.getString("username"), fontSize = 30.sp)
                            }

                        }

                        HorizontalDivider()
                        NavigationDrawerItem(
                            label = { Text(text = "Logout") },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    APIHandler.logout(dsWrapper, onResult = {success ->
                                        if (success) {
                                            navController.navigate("loading_screen")
                                        } else {
                                            Toast.makeText(context, "Failed to log out", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            }
                        )
                        NavigationDrawerItem(
                            label = { Text(text = "Devices") },
                            selected = false,
                            onClick = {
                                navController.navigate("devices_screen")
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text(text = "Passkey Settings") },
                            selected = false,
                            onClick = {
                                navController.navigate("passkey_settings")
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text(text = "User information") },
                            selected = false,
                            onClick = {
                                navController.navigate("userinfo_settings")
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text(text = "New Chat") },
                            selected = false,
                            onClick = {
                                navController.navigate("newchat_screen")
                            }
                        )
                        NavigationDrawerItem(
                            label = { Text(text = "New Group Chat") },
                            selected = false,
                            onClick = {
                                navController.navigate("newgroup_screen")
                            }
                        )
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()

                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(4.dp)
                            .statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Menu, "Menu icon", modifier = Modifier
                            .height(56.dp)
                            .width(56.dp)
                            .clickable {
                                scope.launch {
                                    drawerState.apply {
                                        if (isClosed) open() else close()
                                    }
                                }
                            }
                            .align(Alignment.BottomStart),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text("Chats", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 25.sp)
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        items(chats) { chat ->

                            var bg = MaterialTheme.colorScheme.secondaryContainer
                            var color = MaterialTheme.colorScheme.onSecondaryContainer

                            if (chat.getBoolean("hasUnreadMessages")) {
                                bg = MaterialTheme.colorScheme.primaryContainer
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            }
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .clip(RoundedCornerShape(64.dp))
                                .background(bg)
                                .clickable {
                                    //open chat
                                    navController.navigate(Chat(chatInfo = chat.toString()))
                                },
                                verticalAlignment = Alignment.CenterVertically


                            ) {
                                if (chat.getInt("groupchat") == 0) {
                                    val participants = chat.getJSONArray("participants");



                                    if (participants.length() == 1) {
                                        LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=0&smol=true")
                                    } else {
                                        if (userDataLoaded) {
                                            val usrID = removeIntFromJSONArray(participants, userData.getInt("id")).getInt(0)
                                            LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=$usrID&smol=true")
                                        }
                                    }

                                } else {
                                    LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=0&smol=true")
                                }

                                Text(
                                    text = chat.getString("name"),
                                    color = color,
                                    modifier = Modifier.padding(start = 16.dp), // Optional: Add padding for spacing
                                    fontSize = 20.sp
                                )
                            }

                        }
                    }
                }
            }
        }
    }
}