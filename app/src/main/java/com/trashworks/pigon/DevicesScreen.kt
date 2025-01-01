package com.trashworks.pigon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Delete, contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            Button (
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun DevicesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var devices by remember {
        mutableStateOf(listOf<JSONObject>())
    }
    LaunchedEffect("") {
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

    var selectedDeviceId by remember { mutableStateOf("") }

    var openRemoveDialog by remember { mutableStateOf(false) }

    PigonTheme {
        Column(modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .height(84.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
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
                        },
                    tint = MaterialTheme.colorScheme.onTertiaryContainer


                )

                Text("Devices", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 25.sp)
            }

            //display devices
            LazyColumn(modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
            ) {
                items(devices) {device ->
                    val deviceInfo = JSONObject(device.getString("deviceInfo"));
                    var bg = MaterialTheme.colorScheme.secondary
                    var color = MaterialTheme.colorScheme.onSecondary
                    if (device.getBoolean("current")) {
                        bg = MaterialTheme.colorScheme.primary;
                        color = MaterialTheme.colorScheme.onPrimary
                    }
                    Row (modifier = Modifier
                        .padding(10.dp)
                        .background(bg)
                        .fillMaxWidth()
                        .clickable {
                            if (!device.getBoolean("current")) {
                                selectedDeviceId = device.getString("deviceID")
                                openRemoveDialog = true;
                            }

                        }
                    ) {
                        Text(deviceInfo.getString("deviceName"), color = color, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            if (openRemoveDialog) {
                AlertDialogExample(
                    onDismissRequest = { openRemoveDialog = false },
                    onConfirmation = {
                        openRemoveDialog = false
                        scope.launch {
                            APIHandler.removeDevice(selectedDeviceId, onResult = {res ->
                                scope.launch {
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

                            })
                        }
                    },
                    dialogTitle = "Remove Device",
                    dialogText = "Do you really want to remove this device?",
                )
            }


        }
    }

}