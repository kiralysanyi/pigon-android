package com.trashworks.pigon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DevicesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var devices by remember {
        mutableStateOf(listOf<JSONObject>())
    }
    LaunchedEffect(Unit) {
        APIHandler.getDevices { res ->
            if (res.success) {
                devices = res.dataArray.let { jsonArray ->
                    List(jsonArray.length()) { index ->
                        jsonArray.getJSONObject(index)
                    }
                }
            }
        }
    }

    PigonTheme {
        Column(modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .height(84.dp)
                    .statusBarsPadding()
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
                    tint = MaterialTheme.colorScheme.onTertiaryContainer


                )
            }

            //display devices
            LazyColumn(modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
            ) {
                items(devices) {device ->
                    val deviceInfo = JSONObject(device.getString("deviceInfo"));
                    Row (modifier = Modifier
                        .padding(10.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .fillMaxWidth()
                    ) {
                        Text(deviceInfo.getString("deviceName"), color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

}