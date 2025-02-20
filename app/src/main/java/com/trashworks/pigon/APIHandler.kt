@file:OptIn(DelicateCoroutinesApi::class)

package com.trashworks.pigon

import android.content.Context
import android.net.Uri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.os.Build
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class ReturnObject {
    public var message: String
    public var success: Boolean
    public var data: JSONObject
    public var dataArray: JSONArray

    constructor(
        success: Boolean,
        message: String,
        data: JSONObject = JSONObject(),
        dataArray: JSONArray = JSONArray()
    ) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.dataArray = dataArray;
    }
}


object APIHandler {
    private val json = "application/json".toMediaType();
    private val client = OkHttpClient()
    private val uri = "https://pigon.ddns.net";
    private var requestHeaders = Headers.Builder().set("Content-Type", "application/json").build();
    private lateinit var cookies: String;
    public var isLoggedIn = false;

    fun getCookies(context: Context? = null): String {
        if (isLoggedIn) {
            return cookies;
        } else {
            Log.e("APIHandler", "Failed to get cookies")
            if (context == null) {
                return "";
            } else {
                GlobalScope.launch {
                    val dsWrapper = DataStoreWrapper(context)
                    setCookies(dsWrapper.getString()!!, dsWrapper)
                }
                return cookies
            }

        }

    }

    fun leaveGroup(chatID: Int, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("chatid", chatID)
            }
            try {
                val request =
                    Request.Builder().url("$uri/api/v1/chat/leave").headers(requestHeaders)
                        .post(body.toString().toRequestBody()).build()

                val response = client.newCall(request).execute()

                val responseJson = JSONObject(response.body?.string())
                onResult(
                    ReturnObject(
                        responseJson.getBoolean("success"), responseJson.getString("message")
                    )
                )
            } catch (e: Exception) {
                Log.e("LeaveGroup", e.toString())
                onResult(ReturnObject(false, e.toString()))
            }

        }
    }

    fun submitFirebaseToken(token: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch {
            withContext(Dispatchers.IO) {

                val body = JSONObject().apply {
                    put("registrationToken", token)
                }
                val reqbody = body.toString().toRequestBody(contentType = json)
                val request =
                    Request.Builder().url("$uri/api/v1/firebase/register").headers(requestHeaders)
                        .post(reqbody).build()

                try {
                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string();
                    if (responseString != null) {
                        Log.d("Firebase reg", responseString)
                    }
                    val responseJson = JSONObject(responseString);
                    onResult(
                        ReturnObject(
                            responseJson.getBoolean("success"), responseJson.getString("message")
                        )
                    );
                } catch (e: Exception) {
                    onResult(ReturnObject(false, e.toString()))
                }


            }
        }

    }


    fun prepareCall(chatid: Int, onResult: (ReturnObject) -> Unit) {
        val request = Request.Builder().url("$uri/api/v1/chat/prepareCall").headers(requestHeaders)
            .post("""{"chatid": $chatid}""".toRequestBody()).build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string())
                Log.d("PrepareCall", responseJson.toString())
                onResult(
                    ReturnObject(
                        responseJson.getBoolean("succes"),
                        responseJson.getString("message"),
                        responseJson.getJSONObject("data")
                    )
                )
            } catch (e: Exception) {
                Log.e("PrepareCall", e.toString())
                onResult(ReturnObject(false, e.toString()))
            }


        }
    }

    suspend fun removeDevice(deviceID: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch {
            withContext(Dispatchers.IO) {

                val body = JSONObject().apply {
                    put("deviceID", deviceID)
                }
                val reqbody = body.toString().toRequestBody(contentType = json)
                val request =
                    Request.Builder().url("$uri/api/v1/auth/removedevice").headers(requestHeaders)
                        .delete(reqbody).build()

                try {
                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string();
                    if (responseString != null) {
                        Log.d("Removedevice", responseString)
                    }
                    val responseJson = JSONObject(responseString);
                    onResult(
                        ReturnObject(
                            responseJson.getBoolean("success"), responseJson.getString("message")
                        )
                    );
                } catch (e: Exception) {
                    onResult(ReturnObject(false, e.toString()))
                }


            }
        }
    }

    fun addUser(userIDs: List<Int>, chatID: Int, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch(Dispatchers.IO) {
            val body = """
                {
                    "chatid": $chatID,
                    "targetids": [${userIDs.joinToString(",")}]
                }
            """.trimIndent().toRequestBody()
            val request =
                Request.Builder().url("$uri/api/v1/chat/groupuser").headers(requestHeaders)
                    .post(body).build()

            try {
                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string());
                if (responseJson.getBoolean("success")) {
                    onResult(ReturnObject(true, "Applied changes"))
                } else {
                    onResult(ReturnObject(false, responseJson.getString("message")))
                }
            } catch (e: Exception) {
                onResult(ReturnObject(false, e.toString()))
            }


        }
    }

    fun removeUser(userID: Int, chatID: Int, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch(Dispatchers.IO) {
            val body = """
                {
                    "chatid": $chatID,
                    "targetid": $userID
                }
            """.trimIndent().toRequestBody()
            val request =
                Request.Builder().url("$uri/api/v1/chat/groupuser").headers(requestHeaders)
                    .delete(body).build()

            try {
                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string());
                if (responseJson.getBoolean("success")) {
                    onResult(ReturnObject(true, "Applied changes"))
                } else {
                    onResult(ReturnObject(false, responseJson.getString("message")))
                }
            } catch (e: Exception) {
                onResult(ReturnObject(false, e.toString()))
            }


        }
    }

    suspend fun getUserInfo(userID: Int? = null, onResult: suspend (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        // Launch a coroutine on the IO thread to make the network request
        var url = "$uri/api/v1/auth/userinfo"
        if (userID != null) {
            url = "$uri/api/v1/auth/userinfo?userID=$userID"
        }
        GlobalScope.launch(Dispatchers.IO) {
            val request = Request.Builder().url(url).headers(requestHeaders).get().build()

            try {
                val response = client.newCall(request).execute()
                val res = JSONObject(response.body?.string())

                if (!response.isSuccessful) {

                    // Switching back to the main thread to return the result
                    withContext(Dispatchers.Main) {
                        onResult(
                            ReturnObject(
                                res.getBoolean("success"), "Something went wrong...", res
                            )
                        )
                    }
                    return@launch
                }


                // Switching back to the main thread to return the result
                withContext(Dispatchers.Main) {

                    onResult(ReturnObject(true, "Successfully retrieved userinfo", data = res))
                }
            } catch (e: Exception) {
                // Handle error and send failure result
                withContext(Dispatchers.Main) {
                    onResult(ReturnObject(false, "Error: ${e.message}"))
                }
            }
        }
    }

    fun disablePasskeys(onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        GlobalScope.launch(Dispatchers.IO) {
            val request =
                Request.Builder().url("$uri/api/v1/auth/webauthn/passkeys").headers(requestHeaders)
                    .delete().build()

            try {
                val response = client.newCall(request).execute()
                val responseString = response.body?.string();
                val responseJson = JSONObject(responseString);
                onResult(
                    ReturnObject(
                        responseJson.getBoolean("success"), responseJson.getString("message")
                    )
                )
            } catch (e: Exception) {
                Log.e("DisablePasskeys", e.toString())
                onResult(ReturnObject(false, e.toString()))
            }

        }

    }

    fun registerPasskey(registration: String, challenge: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }
        val body = JSONObject().apply {
            put("registration", JSONObject(registration))
            put("challenge", challenge)
        }
        Log.d("Webauthn", "$body")
        GlobalScope.launch(Dispatchers.IO) {
            val request =
                Request.Builder().url("$uri/api/v1/auth/webauthn/register").headers(requestHeaders)
                    .post(body.toString().toRequestBody()).build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string();
            Log.d("Webauthn", responseString!!)
            val responseJson = JSONObject(responseString);
            onResult(
                ReturnObject(
                    responseJson.getBoolean("success"), responseJson.getString("message")
                )
            )

            try {

            } catch (e: Exception) {
                Log.e("Webauthn-APIHandler", e.toString())
                onResult(ReturnObject(false, e.toString()))
            }
        }
    }

    fun getDeviceId(onResult: (String) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            getDevices { res ->
                if (res.success) {
                    for (i in 0..<res.dataArray.length()) {
                        if (res.dataArray.getJSONObject(i).getBoolean("current")) {
                            GlobalScope.launch(Dispatchers.Main) {
                                onResult(res.dataArray.getJSONObject(i).getString("deviceID"))
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    suspend fun getDevices(onResult: suspend (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        withContext(Dispatchers.IO) {
            val request =
                Request.Builder().url("$uri/api/v1/auth/devices").headers(requestHeaders).get()
                    .build()

            try {
                val response = client.newCall(request).execute()
                val responseString = response.body?.string();
                val responseJson = JSONObject(responseString);
                if (responseJson.getBoolean("success")) {
                    onResult(
                        ReturnObject(
                            true,
                            "Retrieved devices info successfully",
                            dataArray = responseJson.getJSONArray("data")
                        )
                    )
                } else {
                    onResult(ReturnObject(false, "Failed to retrieve devices info"));
                }
            } catch (e: Exception) {
                Log.e("GetDevices", e.toString())
                onResult(ReturnObject(false, "Failed to retrieve devices info"));
                //TODO(Notify the user about the error)
            }

        }
    }

    suspend fun checkIfTokenValid(onResult: suspend (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        withContext(Dispatchers.IO) {
            getUserInfo { res ->

                if (!res.success) {
                    onResult(ReturnObject(res.success, "User not logged in"))
                } else {
                    onResult(ReturnObject(res.success, "User logged in"))
                }
            }
        }
    }

    fun searchUsers(onResult: (ReturnObject) -> Unit, query: String) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        try {
            val request = Request.Builder().url("$uri/api/v1/auth/search?search=$query")
                .headers(requestHeaders).get().build()

            GlobalScope.launch(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val stringResponse = response.body?.string()
                val jsonResponse = JSONObject(stringResponse)

                onResult(
                    ReturnObject(
                        jsonResponse.getBoolean("success"),
                        jsonResponse.getString("message"),
                        dataArray = JSONArray(jsonResponse.getString("data"))
                    )
                )
            }

        } catch (e: Exception) {
            Log.e("User search", e.toString())
        }
    }

    fun postExtraInfo(fullname: String, bio: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."))
            return
        }

        val dataObject = JSONObject()
        dataObject.put("fullname", fullname)
        dataObject.put("bio", bio)

        val requestBodyObject = JSONObject()
        requestBodyObject.put("data", dataObject)

        val requestBody = requestBodyObject.toString().toRequestBody()

        val request = Request.Builder().url("$uri/api/v1/auth/extrainfo").headers(requestHeaders)
            .post(requestBody).build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string())

                if (responseJson.getBoolean("success")) {
                    onResult(ReturnObject(true, responseJson.getString("message")))
                } else {
                    onResult(ReturnObject(false, responseJson.getString("message")))
                }
            } catch (e: Exception) {
                onResult(ReturnObject(false, e.toString()))
            }
        }
    }

    fun getExtraInfo(userID: Int, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val request = Request.Builder().url("$uri/api/v1/auth/extrainfo?userID=$userID")
            .headers(requestHeaders).get().build()


        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()

                val responseJson = JSONObject(response.body?.string())
                if (responseJson.getBoolean("success")) {
                    onResult(
                        ReturnObject(
                            true, "Retrieved extra info", data = responseJson.getJSONObject("data")
                        )
                    )
                } else {
                    onResult(ReturnObject(false, responseJson.getString("message")))
                }
            } catch (e: Exception) {
                onResult(ReturnObject(false, e.toString()))
            }
        }

    }


    fun deleteGroup(chatid: Int, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val body = JSONObject().apply {
            put("chatid", chatid)
        }

        val request = Request.Builder().url("$uri/api/v1/chat/group").headers(requestHeaders)
            .delete(body.toString().toRequestBody()).build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()

                val responseJson = JSONObject(response.body?.string())

                Log.d("Delete group", responseJson.toString())
                onResult(
                    ReturnObject(
                        responseJson.getBoolean("success"), responseJson.getString("message")
                    )
                )
            } catch (e: Exception) {
                Log.e("Delete group", e.toString())
                onResult(ReturnObject(false, e.toString()));
            }

        }
    }

    fun deleteUser(username: String, password: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val body = JSONObject().apply {
            put("password", password)
            put("username", username)
        }

        val request = Request.Builder().url("$uri/api/v1/auth/delete").headers(requestHeaders)
            .delete(body.toString().toRequestBody()).build()


        GlobalScope.launch(Dispatchers.IO) {

            try {

                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string())
                val responseDataObject = responseJson.getJSONObject("data")
                withContext(Dispatchers.Main) {
                    onResult(
                        ReturnObject(
                            responseJson.getBoolean("success"),
                            responseDataObject.getString("message")
                        )
                    )
                }


            } catch (e: Exception) {
                Log.e("Delete user", e.toString())
                withContext(Dispatchers.Main) {
                    onResult(ReturnObject(false, e.toString()))
                }
            }

        }


    }

    fun newChat(
        onResult: (ReturnObject) -> Unit,
        participants: JSONArray,
        isGroupChat: Boolean,
        chatName: String? = null
    ) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val body = JSONObject();
        body.put("isGroupChat", isGroupChat);
        if (isGroupChat && chatName != null) {
            body.put("chatName", chatName)
        }

        body.put("participants", participants)

        val request = Request.Builder().url("$uri/api/v1/chat/create").headers(requestHeaders)
            .post(body.toString().toRequestBody()).build()

        try {
            GlobalScope.launch(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val stringResponse = response.body?.string()
                val jsonResponse = JSONObject(stringResponse)
                onResult(
                    ReturnObject(
                        jsonResponse.getBoolean("success"), jsonResponse.getString("message")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Create chat", e.toString())
            onResult(ReturnObject(false, e.toString()))
        }


    }

    fun getChallenge(onResult: (String?) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            val request =
                Request.Builder().url("$uri/api/v1/auth/webauthn/challenge").headers(requestHeaders)
                    .get().build()

            try {
                val response = client.newCall(request).execute()
                onResult(response.body?.string()?.let { JSONObject(it).getString("challenge") })
            } catch (e: Exception) {
                Log.e("challenge", e.toString())
            }

        }
    }

    fun authWebauthn(payload: String, challenge: String, onResult: (ReturnObject) -> Unit) {
        val body = JSONObject();
        body.put("deviceName", "${Build.BRAND} ${Build.MODEL}")
        body.put("challenge", challenge)
        body.put("authentication", JSONObject(payload))


        val request =
            Request.Builder().url("$uri/api/v1/auth/webauthn/auth").headers(requestHeaders)
                .post(body.toString().toRequestBody()).build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.headers["Set-Cookie"] != null) {
                    isLoggedIn = true;
                    cookies = response.headers["Set-Cookie"].toString()
                }
                val stringResponse = response.body?.string()
                Log.d("Webauthn", stringResponse.toString())
                val jsonResponse = JSONObject(stringResponse)
                onResult(
                    ReturnObject(
                        jsonResponse.getBoolean("success"), jsonResponse.getString("message")
                    )
                )
            } catch (e: Exception) {
                Log.e("Webauthn", e.toString())
                onResult(ReturnObject(false, e.toString()));
            }


        }

    }

    suspend fun getMessages(chatID: Int, page: Int = 1, onResult: (ReturnObject) -> Unit) {
        Log.d("GetMessages", chatID.toString())
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val url = "$uri/api/v1/chat/messages?chatid=$chatID&page=$page";

        // Launch a coroutine on the IO thread to make the network request
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).headers(requestHeaders).get().build()

            try {
                val response = client.newCall(request).execute()


                if (!response.isSuccessful) {
                    val res = JSONObject(response.body?.string())
                    // Switching back to the main thread to return the result
                    withContext(Dispatchers.Main) {
                        onResult(
                            ReturnObject(
                                res.getBoolean("success"), "Something went wrong...", res
                            )
                        )
                    }

                }


                val res = JSONArray(response.body?.string())

                onResult(ReturnObject(true, "Successfully retrieved messages", dataArray = res))

            } catch (e: Exception) {
                // Handle error and send failure result
                Log.e("ERROR bLyaT", e.toString())
                withContext(Dispatchers.Main) {
                    onResult(ReturnObject(false, "Error: ${e.message}"))
                }
            }
        }


    }

    suspend fun setCookies(cookiestring: String, dsWrapper: DataStoreWrapper? = null) {
        Log.d("Setcookie", cookiestring)
        if (dsWrapper != null) {
            dsWrapper.saveString(cookiestring)
        }
        Log.d("Cookie: ", cookiestring)
        cookies = cookiestring;
        requestHeaders =
            Headers.Builder().set("Content-Type", "application/json").set("Cookie", cookiestring)
                .build();
        Log.d("Request headers set: ", requestHeaders.toString())
        isLoggedIn = true;

        if (!SocketConnection.initialized) {
            SocketConnection.init();
        }
    }

    // Helper function to convert Uri to File
    fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "temp_file_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return file
    }

    fun registerUser(username: String, password: String, onResult: (ReturnObject) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            val request = Request.Builder().url("$uri/api/v1/auth/register").headers(requestHeaders)
                .post("""{"username": "$username", "password": "$password"}""".toRequestBody())
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseString = response.body?.string();
                Log.d("Register", "$responseString")
                val responseJson = JSONObject(responseString)
                val responseData = responseJson.getJSONObject("data")

                GlobalScope.launch(Dispatchers.Main) {
                    onResult(
                        ReturnObject(
                            responseJson.getBoolean("success"), responseData.getString("message")
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e("Register", e.toString())
                GlobalScope.launch(Dispatchers.Main) {
                    onResult(ReturnObject(false, "Error: ${e.message}"))
                }

            }
        }
    }

    fun registerPeer(callid: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val request = Request.Builder().url("$uri/api/v1/chat/registerPeer").headers(requestHeaders)
            .post("""{"callid": "$callid"}""".toRequestBody()).build()

        try {
            GlobalScope.launch(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string())
                onResult(
                    ReturnObject(
                        responseJson.getBoolean("success"), responseJson.getString("message")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("RegisterPeer", e.toString())
            onResult(ReturnObject(false, e.toString()))
        }
    }

    fun getPeers(callid: String, onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        val request = Request.Builder().headers(requestHeaders)
            .url("$uri/api/v1/chat/getPeerIDs?callid=$callid").get().build()

        try {
            GlobalScope.launch(Dispatchers.IO) {
                val result = client.newCall(request).execute()
                val resultJson = JSONObject(result.body?.string())
                GlobalScope.launch(Dispatchers.Main) {
                    onResult(
                        ReturnObject(
                            resultJson.getBoolean("success"), "", resultJson.getJSONObject("data")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("getPeers", e.toString())
            onResult(ReturnObject(false, e.toString()))
        }
    }

    suspend fun logout(dsWrapper: DataStoreWrapper, onResult: (Boolean) -> Unit) {
        if (!isLoggedIn) {
            onResult(false);
        }

        withContext(Dispatchers.IO) {
            val request =
                Request.Builder().url("$uri/api/v1/auth/logout").headers(requestHeaders).get()
                    .build()

            try {
                val response = client.newCall(request).execute()
                val responseString = response.body?.string()
                val responseJson = JSONObject(responseString);

                if (responseJson.getBoolean("success") == true) {
                    if (responseString != null) {
                        Log.d("Logout", responseString)
                    }
                    isLoggedIn = false;
                    dsWrapper.saveString("");
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }


                } else {
                    if (responseString != null) {
                        Log.e("Logout", responseString)
                    }
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e("Logout", e.toString())
                onResult(false)
            }
        }
    }

    suspend fun uploadPfp(
        location: Uri, onResult: (ReturnObject) -> Unit, context: Context
    ) {
        if (!isLoggedIn) {
            return;
        }

        withContext(Dispatchers.IO) {
            val file = uriToFile(context, location)

            // Create MultipartBody.Part for the file
            val fileRequestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("image", file.name, fileRequestBody)

            // Build the MultipartBody
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addPart(filePart) // Add the file
                .build()

            // Create OkHttp client and request

            val request = Request.Builder().url("$uri/api/v1/auth/pfp").post(multipartBody)
                .headers(requestHeaders).build()

            // Execute the request
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.body?.string()?.let {
                        Log.e("PFP", it)
                        onResult(ReturnObject(false, it))
                    }
                } else {
                    val responseString = response.body?.string();
                    val resJson = JSONObject(responseString)
                    Log.d("PFP", resJson.toString())
                    onResult(
                        ReturnObject(
                            true, "Successfully uploaded profile picture", data = resJson
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e("PFP_error", e.toString())
            }

        }
    }

    suspend fun uploadToCdn(
        location: Uri, onResult: (filename: String) -> Unit, chatID: Int, context: Context
    ) {
        if (!isLoggedIn) {
            return;
        }

        withContext(Dispatchers.IO) {
            val file = uriToFile(context, location)

            // Create MultipartBody.Part for the file
            val fileRequestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)

            // Build the MultipartBody
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM).addPart(
                MultipartBody.Part.createFormData(
                    "chatid", chatID.toString()
                )
            ) // Add chatId
                .addPart(filePart) // Add the file
                .build()

            // Create OkHttp client and request

            val request = Request.Builder().url("$uri/api/v1/chat/cdn").post(multipartBody)
                .headers(requestHeaders).build()

            // Execute the request
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.body?.string()?.let { Log.e("CDN", it) }
                } else {
                    val responseString = response.body?.string();
                    val resJson = JSONObject(responseString)
                    Log.d("CDN", resJson.toString())
                    onResult("/api/v1/chat/cdn?filename=" + resJson.getString("filename"))
                }

            } catch (e: Exception) {
                Log.e("CDN_error", e.toString())
            }

        }
    }

    suspend fun getChats(onResult: (ReturnObject) -> Unit) {
        if (!isLoggedIn) {
            onResult(ReturnObject(false, "You have to log in first."));
            return;
        }

        return withContext(Dispatchers.IO) {

            // Launch a coroutine on the IO thread to make the network request
            val request =
                Request.Builder().url("$uri/api/v1/chat/chats").headers(requestHeaders).get()
                    .build()

            try {
                val response = client.newCall(request).execute()

                // Parse the response body as a JSONObject
                val res = JSONObject(response.body?.string())
                if (!response.isSuccessful) {
                    // Switching back to the main thread to return the result
                    withContext(Dispatchers.Main) {
                        onResult(
                            ReturnObject(
                                res.getBoolean("success"),
                                res.getJSONObject("data").getString("message"),
                                res
                            )
                        )
                    }

                }


                // Switching back to the main thread to return the result
                withContext(Dispatchers.Main) {

                    if (res.getBoolean("success") == true) {
                        onResult(ReturnObject(true, "Successfully retrieved chat list", res))
                    }

                }
            } catch (e: Exception) {
                Log.e("BLYAT", e.toString())
                // Handle error and send failure result
                withContext(Dispatchers.Main) {
                    onResult(ReturnObject(false, "Error: ${e.message}"))
                }
            }
        }


    }

    fun login(username: String, password: String, onResult: (ReturnObject) -> Unit) {
        // Launch a coroutine on the IO thread to make the network request
        GlobalScope.launch(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("deviceName", "${Build.BRAND} ${Build.MODEL}")
            }
            val reqbody = body.toString().toRequestBody(contentType = json)
            val request = Request.Builder().url("$uri/api/v1/auth/login").headers(requestHeaders)
                .post(reqbody).build()

            try {
                val response = client.newCall(request).execute()
                // Get the cookies
                cookies = response.headers["Set-Cookie"].toString()
                requestHeaders =
                    Headers.Builder().set("Content-Type", "application/json").set("Cookie", cookies)
                        .build();
                // Parse the response body as a JSONObject
                val res = JSONObject(response.body?.string())
                if (!response.isSuccessful) {
                    // Switching back to the main thread to return the result
                    withContext(Dispatchers.Main) {
                        onResult(
                            ReturnObject(
                                res.getBoolean("success"),
                                res.getJSONObject("data").getString("message"),
                                res
                            )
                        )
                    }
                    return@launch
                }


                // Switching back to the main thread to return the result
                withContext(Dispatchers.Main) {
                    isLoggedIn = true;
                    onResult(ReturnObject(res.getBoolean("success"), res.toString(), res))
                }
            } catch (e: Exception) {
                // Handle error and send failure result
                withContext(Dispatchers.Main) {
                    onResult(ReturnObject(false, "Error: ${e.message}"))
                }
            }
        }
    }
}