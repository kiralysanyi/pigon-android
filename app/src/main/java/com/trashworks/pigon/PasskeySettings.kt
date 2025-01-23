package com.trashworks.pigon

import android.content.Context
import android.credentials.CreateCredentialException
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun CreatePasskey(scope: CoroutineScope, context: Context, finished: (success: Boolean) -> Unit) {
    scope.launch {

        APIHandler.getUserInfo { res ->
            val username =
                res.data.getJSONObject("data").getString("username") // Extract the username

            APIHandler.getChallenge { challenge ->
                Log.d("Challenge", challenge.toString())

                val requestJson = """{
            "challenge": "$challenge",
            "timeout": 60000,
            "rp": {
                "name": "pigon.ddns.net",
                "id": "pigon.ddns.net"
            },
            "user": {
                "id": "${username.hashCode()}",
                "name": "$username",
                "displayName": "$username"
            },
            "pubKeyCredParams": [
                {
                    "type": "public-key",
                    "alg": -7
                }
            ],
            "attestation": "none",
  "excludeCredentials": [
    {"id": "ghi789", "type": "public-key"},
    {"id": "jkl012", "type": "public-key"}
  ],
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "requireResidentKey": true,
    "residentKey": "required",
    "userVerification": "required"
  }
        }"""

                val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
                    requestJson = requestJson,
                    preferImmediatelyAvailableCredentials = true
                )

                val credentialManager = CredentialManager.create(context)

                // Use a coroutine to handle the asynchronous nature

                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            credentialManager.createCredential(
                                context,
                                createPublicKeyCredentialRequest
                            )
                        } as CreateCredentialResponse

                        // Extract the registration response JSON
                        val registrationResponseJson =
                            response.data.getString("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON")
                        if (registrationResponseJson != null) {
                            Log.d("RegistrationResponse", registrationResponseJson)
                        }

                        // Send the response to your backend
                        if (registrationResponseJson != null) {
                            APIHandler.registerPasskey(
                                registrationResponseJson,
                                challenge!!
                            ) { res ->
                                if (res.success) {
                                    finished(true)
                                } else {
                                    Log.e("Webauthn", res.message)
                                    finished(false)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Registration", e.toString())
                        finished(false)
                    }
                }
            }
        }


    }
}


@Composable
fun PasskeysCard() {
    Card(
        modifier = Modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Passkeys: A Better Way to Sign In",
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Benefits:",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "• Enhanced Security: Passkeys replace passwords with cryptographic keys, eliminating the risk of phishing and credential stuffing attacks.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "• Improved User Experience: Enjoy passwordless, seamless logins across all your devices.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun PasskeySettings(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current;
    var showDialog by remember { mutableStateOf(false) }
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
                    "Passkey Settings",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 25.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PasskeysCard()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Add Passkey",
                        modifier = Modifier
                            .padding(10.dp)
                            .weight(1f),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(onClick = {
                        //add key
                        CreatePasskey(scope, navController.context) { success ->
                            if (success) {
                                scope.launch {
                                    Toast.makeText(context, "Passkey added", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            } else {
                                scope.launch {
                                    Toast.makeText(
                                        context,
                                        "Failed to add passkey",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }, modifier = Modifier.padding(10.dp)) {
                        Text("Add")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Disable passkeys",
                        modifier = Modifier
                            .padding(10.dp)
                            .weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = {
                        showDialog = true;

                    }, modifier = Modifier.padding(10.dp)) {
                        Text("Disable")
                    }
                }
            }
            if (showDialog) {
                AlertDialogExample(
                    onDismissRequest = {showDialog = false},
                    onConfirmation = {
                        APIHandler.disablePasskeys { res ->
                            if (res.success) {
                                scope.launch {
                                    Toast.makeText(context, "Passkeys disabled", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                scope.launch {
                                    Toast.makeText(context, "Failed to disable passkeys", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showDialog = false

                        }
                    },
                    dialogText = "Do you really wan't to disable passkeys? This will invalidate all passkeys associated to this account",
                    dialogTitle = "Disable Passkeys"
                )
            }
        }
    }
}