package com.trashworks.pigon

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfo(chatJson: JSONObject, onBackClicked: (leftGroup: Boolean) -> Unit) {
    val context = LocalContext.current;
    val scope = rememberCoroutineScope()

    val chatID = chatJson.getInt("chatid")
    var participants = remember { mutableStateListOf<JSONObject>() }

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(chatJson.getString("name")) },
                navigationIcon = {
                    IconButton(onClick = {
                        onBackClicked(false)
                    }) {
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
            //show participants
            LazyColumn(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
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

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    APIHandler.leaveGroup(chatID) { res ->
                        if (res.success) {
                            scope.launch {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                            onBackClicked(true)
                        } else {
                            scope.launch {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }) { Text("Leave group") }


        }
    }
}