package com.trashworks.pigon

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun NewGroupScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(JSONArray()) }
    val scope = rememberCoroutineScope()
    var participants = remember { mutableStateListOf<String>() };
    var userData by remember { mutableStateOf(JSONObject()) }
    var gotUserData by remember { mutableStateOf(false) }
    var openNameDialog by remember { mutableStateOf(false) }

    var groupname by remember { mutableStateOf("") }
    LaunchedEffect("") {
        scope.launch {
            APIHandler.getUserInfo { res ->
                userData = res.data.getJSONObject("data")
                gotUserData = true;

            }
        }
    }

    PigonTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .height(84.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Go back to chats",
                    modifier = Modifier
                        .height(50.dp)
                        .padding(7.dp)
                        .width(50.dp)
                        .clickable {
                            navController.navigate("main_screen")
                        }
                        .align(Alignment.BottomStart),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer

                )

                Text(
                    "New Group",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 25.sp
                )

                val isButtonEnabled = participants.isNotEmpty()

                Button(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enabled = isButtonEnabled,
                    onClick = {
                        //create group
                        openNameDialog = true;
                    }) {
                    Text("Create")
                }
            }
            //added users
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                if (gotUserData) {
                    item {
                        //the user creating the group

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text(
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                text = userData.getString("username"),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            LoadImageFromUrl(
                                "https://pigon.ddns.net/api/v1/auth/pfp?id=${
                                    userData.getInt(
                                        "id"
                                    )
                                }"
                            )
                        }
                    }
                }
                items(participants.count()) { i ->
                    val participant = JSONObject(participants.get(i));
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            participants.removeAt(i)
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                            text = participant.getString("username"),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LoadImageFromUrl(
                            "https://pigon.ddns.net/api/v1/auth/pfp?id=${
                                participant.getInt(
                                    "id"
                                )
                            }"
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                TextField(
                    value = searchQuery,
                    label = { Text("Search") },
                    onValueChange = { newVal -> searchQuery = newVal },
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Search, "search", tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .width(50.dp)
                        .height(50.dp)
                        .clickable {
                            scope.launch {
                                APIHandler.searchUsers(query = searchQuery, onResult = { res ->
                                    if (res.success) {
                                        searchResults = res.dataArray
                                    }
                                })
                            }
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (i in 0..<searchResults.length()) {
                    val result = JSONObject(searchResults[i].toString());
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                Log.d("Newgroup", result.toString())
                                //add user to group
                                if (gotUserData) {
                                    if ((result.getInt("id") != userData.getInt("id")) && !participants.contains(
                                            result.toString()
                                        )
                                    ) {
                                        participants.add(result.toString())
                                    }

                                }

                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadImageFromUrl(
                            "https://pigon.ddns.net/api/v1/auth/pfp?id=${
                                result.getInt(
                                    "id"
                                )
                            }"
                        )
                        Text(
                            result.getString("username"),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

        }
    }

    if (openNameDialog) {
        Dialog(onDismissRequest = { openNameDialog = false }) {
            PigonTheme {
                Card(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .height(200.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Create Group",
                            modifier = Modifier.align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextField(
                            value = groupname,
                            label = { Text("Group Name") },
                            onValueChange = { newVal -> groupname = newVal },
                            singleLine = true
                        )

                        Button(
                            modifier = Modifier
                                .align(Alignment.BottomStart), onClick = {
                                //Cancel
                                openNameDialog = false;
                            }, colors = ButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Cancel") }

                        Button(modifier = Modifier
                            .align(Alignment.BottomEnd),
                            onClick = {
                                //add
                                scope.launch {
                                    var users = JSONArray();
                                    users.put(userData.getInt("id"));
                                    for (participant in participants) {
                                        users.put(JSONObject(participant).getInt("id"))
                                    }
                                    APIHandler.newChat(isGroupChat = true, chatName = groupname, participants = users, onResult = {res ->
                                        if (res.success) {
                                           scope.launch(Dispatchers.Main) {
                                               navController.navigate("main_screen");
                                           }
                                        } else {
                                            Log.e("New group", res.message)
                                        }
                                    })
                                }
                            },
                            enabled = groupname.isNotEmpty()
                        ) { Text("Create") }
                    }
                }
            }
        }
    }
}