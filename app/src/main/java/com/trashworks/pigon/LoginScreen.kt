package com.trashworks.pigon

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.navigation.NavController
import com.trashworks.pigon.ui.theme.PigonTheme
import kotlinx.coroutines.launch


@Composable
fun LoginScreen(
    navController: NavController,
    dsWrapper: DataStoreWrapper,
    context: Context,
    activityContext: Context
) {
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


    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun handleLogin() {
        keyboardController?.hide()
        isLoading = true;
        APIHandler.login(username, password, onResult = { res ->
            if (res.success == true) {
                coroutineScope.launch {
                    dsWrapper.saveString(APIHandler.getCookies())
                }
                SocketConnection.init(true);
                navController.navigate("main_screen")
            } else {
                dasMessage = res.message;
            }

            isLoading = false;

        })
    }



    PigonTheme {
        val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            //background image
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = "Login Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    dasMessage,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                OutlinedTextField(value = username,
                    label = { Text("Username"); },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        Log.d("Keyboard", "Focus password")
                        focusRequester.requestFocus()
                    }),
                    onValueChange = { newVal ->
                        username = newVal
                    })

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                OutlinedTextField(value = password,
                    label = { Text("Password"); },
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        handleLogin()
                    }),
                    onValueChange = { newVal ->
                        password = newVal
                    })

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Button(
                    onClick = {
                        handleLogin()
                    }, modifier = Modifier.width(120.dp)
                ) {
                    if (isLoading == true) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Login")
                    }

                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Button(
                    onClick = {
                        val credentialManager = CredentialManager.create(context);
                        // Request JSON (replace with your WebAuthn JSON request)
                        APIHandler.getChallenge { challenge ->
                            Log.d("Challenge", challenge.toString())
                            val requestJson = """{
                "challenge": "$challenge",
                "timeout": 60000,
                "rpId": "pigon.ddns.net",
                "allowCredentials": [],
                "userVerification": "preferred"
                }"""
                            // Get passkey from the user's public key credential provider.
                            val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
                                requestJson = requestJson
                            )

                            val getCredRequest = GetCredentialRequest(
                                listOf(getPublicKeyCredentialOption)
                            )

                            coroutineScope.launch {
                                try {
                                    val result = credentialManager.getCredential(
                                        activityContext, getCredRequest
                                    );

                                    // Handle the successfully returned credential.
                                    val credential = result.credential

                                    when (credential) {
                                        is PublicKeyCredential -> {
                                            val responseJson = credential.authenticationResponseJson
                                            if (challenge != null) {
                                                APIHandler.authWebauthn(responseJson,
                                                    challenge,
                                                    { res ->
                                                        if (res.success) {
                                                            coroutineScope.launch {
                                                                APIHandler.setCookies(
                                                                    APIHandler.getCookies(),
                                                                    dsWrapper
                                                                )
                                                                SocketConnection.init(true)
                                                                navController.navigate("main_screen")
                                                            }
                                                        } else {
                                                            dasMessage = res.message;
                                                        }
                                                    })
                                            } else {
                                                Log.e("Webauthn", "Challenge is null")
                                            }
                                        }

                                        else -> {
                                            // Catch any unrecognized credential type here.
                                            Log.e("Login", "Unexpected type of credential")
                                        }
                                    }

                                } catch (e: Exception) {
                                    Log.e("Login", e.toString())
                                }
                            }

                        }

                    }, modifier = Modifier.width(150.dp)
                ) {
                    Text("Use passkey", color = MaterialTheme.colorScheme.onSecondary)
                }
            }
        }

    }
}
