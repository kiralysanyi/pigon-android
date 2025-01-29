package com.trashworks.pigon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
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
import com.google.android.datatransport.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

fun requestOverlayPermission(context: Context) {
    if (!hasOverlayPermission(context)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}

fun checkAndRequestPermissions(activity: Activity) {
    val permissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
        Manifest.permission.MANAGE_OWN_CALLS
    )

    val permissionsToRequest = mutableListOf<String>()
    for (permission in permissions) {
        if (ContextCompat.checkSelfPermission(
                activity,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission)
        }
    }

    if (permissionsToRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(
            activity,
            permissionsToRequest.toTypedArray(),
            69
        )
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        checkAndRequestPermissions(this);

        super.onCreate(savedInstanceState)
        val activityContext = this;

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RequestNotificationPermission(LocalContext.current, this)
            }

            requestOverlayPermission(LocalContext.current)


            PigonAppNavGraph(activityContext, this)
        }
    }
}

fun RequestNotificationPermission(context: Context, activity: MainActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Toast.makeText(
                context,
                "Please grant appear on top permission to receive calls in the background",
                Toast.LENGTH_LONG
            ).show()
            GlobalScope.launch {
                delay(1000L)
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
        }
    }
}


@Serializable
data class Chat(val chatInfo: String)

@Serializable
data class Group(val chatInfo: String? = null)

@Serializable
data class Call(
    val callInfo: String,
    val isInitiator: Boolean = false,
    val displayName: String? = null
)

@Composable
fun PigonAppNavGraph(activityContext: Context, activity: MainActivity) {

    val dsWrapper = DataStoreWrapper(context = LocalContext.current.applicationContext)
    val navController = rememberNavController()
    val context = LocalContext.current


    NavHost(
        navController = navController,
        startDestination = "loading_screen",
        enterTransition = { defaultEnterTransition() },
        exitTransition = { defaultExitTransition() },
        popEnterTransition = { defaultPopEnterTransition() },
        popExitTransition = { defaultPopExitTransition() },
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable("loading_screen") {
            LoadingScreen(navController = navController, dsWrapper)
        }
        composable("login_screen") {
            LoginScreen(navController = navController, dsWrapper, context, activityContext)
        }

        composable("userinfo_settings") {
            UserinfoSettings(navController);
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

        composable("newgroup_screen") {
            NewGroupScreen(navController)
        }

        composable("passkey_settings") {
            PasskeySettings(navController)
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
            ChatScreen(navController, chat.chatInfo, activity);
        }
    }


    ConnectionChecker(LocalContext.current, navController)
}

private fun defaultEnterTransition(): EnterTransition {
    return slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(500))
}

private fun defaultExitTransition(): ExitTransition {
    return slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(500))
}

private fun defaultPopEnterTransition(): EnterTransition {
    return slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(500))
}

private fun defaultPopExitTransition(): ExitTransition {
    return slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(500))
}

fun getEula(): String {
    return """End-User License Agreement (EULA) for Pigon

Last Updated: 2025.01.19

This End-User License Agreement ("EULA") is a legal agreement between you ("User") and [Your Company Name] ("Company") for the use of the Pigon messaging application ("App"). By downloading, installing, or using the App, you agree to be bound by the terms of this EULA. If you do not agree to these terms, do not use the App.

1. License Grant

The Company grants you a non-exclusive, non-transferable, revocable license to use the App for personal, non-commercial purposes in accordance with this EULA.

2. Data Collection and Usage

2.1 Crash and Performance Monitoring

The App uses Firebase Crashlytics to collect data about app crashes and performance issues. This data helps us improve the App’s stability and functionality.

2.2 Analytics

The App uses Google Analytics to collect anonymized data about usage patterns and user interactions to enhance the user experience and guide future development.

2.3 Message Access

While the App is designed to provide private messaging, messages may be accessible to the Company on the server side if needed for:

Investigating abuse or misuse of the App.

Ensuring compliance with legal obligations or responding to valid legal requests.

For more details, please review our Privacy Policy [insert link if available].

3. User Responsibilities

You agree to:

Use the App only for lawful purposes.

Not use the App to harass, defame, or harm others.

Maintain the confidentiality of your account credentials.

4. Restrictions

You may not:

Reverse engineer, decompile, or disassemble the App.

Use the App to transmit malicious software or unauthorized content.

5. Termination

The Company reserves the right to terminate your access to the App at any time for any reason, including but not limited to breach of this EULA.

6. Disclaimer of Warranties

The App is provided "as is" without warranties of any kind, either express or implied, including but not limited to implied warranties of merchantability or fitness for a particular purpose.

7. Limitation of Liability

To the maximum extent permitted by law, the Company shall not be liable for any damages arising out of the use or inability to use the App, including but not limited to incidental, consequential, or indirect damages.

8. Governing Law

This EULA shall be governed by and construed in accordance with the laws of [Your Country/State], without regard to its conflict of law principles.

9. Changes to this EULA

The Company reserves the right to update or modify this EULA at any time. Continued use of the App after changes are made constitutes your acceptance of the updated terms.

10. Contact Information

If you have any questions or concerns about this EULA, please contact us at kiralysandor1986@gmail.com.

By using Pigon, you acknowledge that you have read, understood, and agree to be bound by this EULA."""
}


@Composable
fun LoadingScreen(navController: NavController, dsWrapper: DataStoreWrapper) {
    val scope = rememberCoroutineScope()
    var eula by remember { mutableStateOf(false) }
    var showEula by remember { mutableStateOf(false) }
    LaunchedEffect(eula) {
        if (dsWrapper.hasString("eula") && !eula) {
            if (dsWrapper.getString("eula") == "true") {
                eula = true;
            } else {
                showEula = true;
            }
        }

        if (!dsWrapper.hasString("eula")) {
            showEula = true;
        }

        if (eula) {
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
                                SocketConnection.init()
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

        if (showEula) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
            ) {
                Text(
                    "Before using pigon you have to accept our eula!",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 30.sp
                )

                Text(
                    getEula(),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .weight(1f),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        scope.launch {
                            dsWrapper.saveString("true", "eula")
                            eula = true;
                        }

                    }) {
                        Text("Accept")
                    }
                }
            }
        }

    }

}
