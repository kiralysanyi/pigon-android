package com.trashworks.pigon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trashworks.pigon.ui.theme.PigonTheme
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            PigonAppNavGraph()
        }
    }
}


@Serializable
data class Chat(val chatInfo: String)

@Composable
fun PigonAppNavGraph() {
    val dsWrapper = DataStoreWrapper(context = LocalContext.current.applicationContext)
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "loading_screen",
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable("loading_screen") {
            LoadingScreen(navController = navController, dsWrapper)
        }
        composable("login_screen") {
            LoginScreen(navController = navController, dsWrapper)
        }
        composable(
            route = "main_screen"
        ) {
            MainScreen(navController = navController)
        }

        composable<Chat> { backStackEntry ->
            val chat: Chat = backStackEntry.toRoute();
            ChatScreen(navController, chat.chatInfo);
        }
    }
}


@Composable
fun LoginScreen(navController: NavController, dsWrapper: DataStoreWrapper) {
    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var dasMessage by remember {
        mutableStateOf("Welcome to pigon!")
    }

    val coroutineScope = rememberCoroutineScope();


    PigonTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                dasMessage,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            TextField(value = username, label = { Text("Username"); }, onValueChange = { newVal ->
                username = newVal
            })


            TextField(
                value = password,
                label = { Text("Password"); },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                onValueChange = { newVal ->
                    password = newVal
                })

            Button(onClick = {
                isLoading = true;
                APIHandler.login(username, password, onResult = { res ->
                    if (res.success == true) {
                        coroutineScope.launch {
                            dsWrapper.saveString(APIHandler.getCookies())
                        }
                        navController.navigate("main_screen")
                    } else {
                        dasMessage = res.message;
                    }

                    isLoading = false;

                })

            }) {
                if (isLoading == true) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Login")
                }

            }
        }

    }
}

@Composable
fun LoadingScreen(navController: NavController, dsWrapper: DataStoreWrapper) {
    LaunchedEffect(Unit) {
        if (dsWrapper.hasString()) {
            val cookies = dsWrapper.getString();
            if (cookies != null) {
                Log.d("Setcookie", "setting cookies")
                APIHandler.setCookies(cookies)
                navController.navigate("main_screen")
            } else {
                navController.navigate("login_screen")
            }

        } else {

            navController.navigate("login_screen")
        }
    }

    PigonTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

}




@Composable
fun MainScreen(navController: NavController) {
    // Define a state to hold the chat list
    var chats by remember { mutableStateOf(listOf<JSONObject>()) }
    // Loading state
    var isLoading by remember { mutableStateOf(true) }

    var scope = rememberCoroutineScope()
    // Use LaunchedEffect to call the API once when the composable is first composed
    LaunchedEffect(Unit) {
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

    // Show a loading state or the actual UI
    if (isLoading) {
        // Display loading indicator
        Text("Loading...", color = MaterialTheme.colorScheme.onBackground)
    } else {
        // Display the chats once data is loaded using LazyColumn
        PigonTheme {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(chats) { chat ->

                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            //open chat

                            navController.navigate(Chat(chatInfo = chat.toString()))
                        }) {
                        if (chat.getInt("groupchat") == 0) {
                            val participants = chat.getJSONArray("participants");
                            Log.d("Kecske", participants.toString())
                            val usrID = participants.get(participants.length() - 1);
                            LoadImageFromUrl("https://pigon.ddns.net/api/v1/auth/pfp?id=$usrID&smol=true")
                        }

                        Text(
                            chat.getString("name"),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(16.dp) // Optional: Add padding for spacing
                        )
                    }

                }
            }
        }
    }
}