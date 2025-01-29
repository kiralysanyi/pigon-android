package com.trashworks.pigon

import android.util.Log
import android.widget.Space
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun UserProfileForm(
    onCancel: () -> Unit,
    onSubmit: (String, String) -> Unit,
    currentUserinfo: JSONObject? = null
) {
    var fullName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (currentUserinfo != null) {
            fullName = currentUserinfo.getString("fullname")
            bio = currentUserinfo.getString("bio")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = { onSubmit(fullName, bio) }, modifier = Modifier.weight(1f)) {
                Text("Submit")
            }
        }
    }
}

@Composable
fun UserinfoSettings(navController: NavController) {
    var userinfo by remember { mutableStateOf<JSONObject?>(null) }
    var extrainfo by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showEditModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            APIHandler.getUserInfo { res ->
                if (res.success) {
                    userinfo = res.data.getJSONObject("data")
                    Log.d("Userinfo", userinfo.toString());
                    APIHandler.getExtraInfo(userinfo?.getInt("id")!!) { extrainfoResponse ->
                        if (extrainfoResponse.success) {
                            extrainfo = extrainfoResponse.data;
                            Log.d("Extrainfo", extrainfo.toString());
                        }
                    }
                } else {
                    scope.launch {
                        Toast.makeText(context, "Failed to get user info", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
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
                    "Edit Userinfo",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 25.sp
                )
            }

            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    Log.d("Selected media", uri.toString())
                    if (uri != null) {
                        scope.launch {
                            APIHandler.uploadPfp(uri, onResult = { res ->
                                if (res.success) {
                                    scope.launch {
                                        clearDiskCache(context)
                                        Toast.makeText(
                                            context,
                                            "Profile picture updated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        navController.navigate("userinfo_settings")
                                    }
                                } else {
                                    scope.launch {
                                        Toast.makeText(
                                            context,
                                            "Failed to update profile picture",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }, context)
                        }

                    }
                }



            if (userinfo != null || extrainfo != null) {
                //content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=${userinfo?.getInt("id")}",
                        modifier = Modifier
                            .width(128.dp)
                            .height(128.dp)
                            .clip(
                                RoundedCornerShape(128.dp)
                            )
                            .clickable {
                                launcher.launch(
                                    PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            })
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        userinfo?.getString("username") ?: "Loading...",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        extrainfo?.getString("fullname") ?: "Full name not set",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        extrainfo?.getString("bio") ?: "Bio not set",
                        color = MaterialTheme.colorScheme.onBackground
                    );
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showEditModal = true;
                        }
                    ) {
                        Text("Edit")
                    }
                }
            }


        }

        AnimatedVisibility(
            visible = showEditModal,
            enter = slideInVertically(
                initialOffsetY = { it }, // Starts off-screen to the left
                animationSpec = tween(durationMillis = 500) // Animation duration
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }, // Slides out to the left
                animationSpec = tween(durationMillis = 500)
            )
        ) {
            UserProfileForm(
                onCancel = { showEditModal = false },
                currentUserinfo = extrainfo,
                onSubmit = { fullName, bio ->
                    APIHandler.postExtraInfo(fullName, bio) { res ->
                        if (res.success) {
                            scope.launch {
                                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT)
                                    .show()
                                navController.navigate("userinfo_settings")
                            }
                        } else {
                            scope.launch {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                })
        }
    }
}