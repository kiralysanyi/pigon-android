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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun NewChatScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(JSONArray()) }
    val scope = rememberCoroutineScope()

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
                    "New Chat",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 25.sp
                )
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
                Icon(Icons.Default.Search, "search", tint = MaterialTheme.colorScheme.onSurface,
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
                                Log.d("Newchat", result.toString())
                                scope.launch {
                                    APIHandler.getUserInfo { res ->
                                        if (res.success) {
                                            Log.d("Userinfo", res.data.toString())
                                            val usrid = JSONObject(res.data.getString("data")).getInt("id")
                                            APIHandler.newChat(
                                                isGroupChat = false,
                                                participants = JSONArray("[$usrid, ${result.getInt("id")}]"),
                                                onResult = { res ->
                                                    if (res.success) {
                                                        scope.launch {
                                                            withContext(Dispatchers.Main) {
                                                                navController.navigate("main_screen")
                                                            }
                                                        }

                                                    } else {
                                                        Log.e("Newchat", res.message)
                                                    }
                                                })
                                        } else {
                                            Log.e("Newchat_userinfo", res.message)
                                        }
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
}