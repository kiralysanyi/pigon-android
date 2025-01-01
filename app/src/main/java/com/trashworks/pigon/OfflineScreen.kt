package com.trashworks.pigon

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.delay

@Composable
fun ConnectionChecker(
    context: Context,
    navController: NavController
) {

    // on below line creating a variable
    // for connection status.
    var connectionType by remember { mutableStateOf("NONE") }

    LaunchedEffect(connectionType) {
        Log.d("ConnectionInfo", connectionType)
        delay(1000)
        if (connectionType == "NONE") {
            navController.navigate("offline_screen")
        } else {
            navController.navigate("loading_screen")
        }
    }


    // A Thread that will continuously
    // monitor the Connection Type
    Thread(Runnable {
        while (true) {


            // Invoking the Connectivity Manager
            val cm =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Fetching the Network Information
            val netInfo = cm.allNetworkInfo

            // on below line finding if connection
            // type is wifi or mobile data.
            var isConnected = false;
            for (ni in netInfo) {

                if (ni.typeName.equals("WIFI", ignoreCase = true) || ni.typeName.equals(
                        "MOBILE",
                        ignoreCase = true
                    )
                ) {
                    if (ni.isConnected) {
                        isConnected = true;
                        connectionType = ni.typeName;

                        break;
                    }
                }
            }
            if (isConnected == false) {
                connectionType = "NONE"
            }

        }
    }).start() // Starting the thread


}

@Composable
fun OfflineScreen(navController: NavController) {
    PigonTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Please check your internet connection", color = MaterialTheme.colorScheme.onBackground)
        }
    }

}