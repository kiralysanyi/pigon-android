package com.trashworks.pigon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        val activityContext = this;

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RequestNotificationPermission(LocalContext.current, this)
            }
            PigonAppNavGraph(activityContext, this)
        }
    }
}

fun RequestNotificationPermission(context: Context, activity: MainActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}


@Serializable
data class Chat(val chatInfo: String)

@Serializable
data class Group(val chatInfo: String? = null)

@Serializable
data class Call(val callInfo: String, val isInitiator: Boolean = false, val displayName: String? = null)

@Composable
fun PigonAppNavGraph(activityContext: Context, activity: MainActivity) {

    val dsWrapper = DataStoreWrapper(context = LocalContext.current.applicationContext)
    val navController = rememberNavController()
    val context = LocalContext.current


    NavHost(
        navController = navController,
        startDestination = "loading_screen",
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable("loading_screen") {
            LoadingScreen(navController = navController, dsWrapper)
        }
        composable("login_screen") {
            LoginScreen(navController = navController, dsWrapper, context, activityContext)
        }

        composable("devices_screen") {
            DevicesScreen(navController = navController)
        }

        composable("offline_screen") {
            OfflineScreen(navController)
        }

        composable("newchat_screen") {
            NewChatScreen(navController)
        }

        composable<Call> { backStackEntry ->
            BackHandler(true) {
                //do nothing bruh
            }
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            val call: Call = backStackEntry.toRoute()
            CallScreen(navController, call.callInfo, call.isInitiator, call.displayName)

        }

        composable<Group> { backStackEntry ->
            val group: Group = backStackEntry.toRoute()
            NewGroupScreen(navController, group.chatInfo)
        }

        composable(
            route = "main_screen"
        ) {
            BackHandler(true) {
                //do nothing bruh
            }
            MainScreen(navController = navController, dsWrapper)
        }

        composable<Chat> { backStackEntry ->
            val chat: Chat = backStackEntry.toRoute();
            ChatScreen(navController, chat.chatInfo);
        }
    }


    ConnectionChecker(LocalContext.current, navController)
}



@Composable
fun LoadingScreen(navController: NavController, dsWrapper: DataStoreWrapper) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (dsWrapper.hasString()) {
            val cookies = dsWrapper.getString();
            if (cookies != null) {
                Log.d("Setcookie", "setting cookies")
                APIHandler.setCookies(cookies)
                scope.launch {
                    APIHandler.getUserInfo { res ->
                        if (!res.success) {
                            navController.navigate("login_screen")
                        } else {
                            if (!SocketConnection.initialized) {
                                SocketConnection.init(navController)
                            }
                            navController.navigate("main_screen")
                        }
                    }
                }

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