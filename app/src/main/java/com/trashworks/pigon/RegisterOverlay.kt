package com.trashworks.pigon

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RegisterOverlay(onDismiss: () -> Unit) {

    var username by remember { mutableStateOf("") }
    var password1 by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    val context = LocalContext.current

    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        //background image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Login Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(32.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                "Register an account on pigon",
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            OutlinedTextField(value = username,
                label = { Text("Username"); },
                singleLine = true,
                onValueChange = { newVal ->
                    username = newVal
                })

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            OutlinedTextField(value = password1,
                label = { Text("Password"); },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                onValueChange = { newVal ->
                    password1 = newVal
                })

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            OutlinedTextField(value = password2,
                label = { Text("Confirm Password"); },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                onValueChange = { newVal ->
                    password2 = newVal
                })

            Spacer(
                modifier = Modifier.height(16.dp)
            )


            Button(onClick = {
                //register user

                if (password1 != password2) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                APIHandler.registerUser(username, password1) { res ->
                    if (res.success) {
                        Toast.makeText(context, "Account created", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Create Account")
            }

            OutlinedButton(onClick = {
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    }

}