package com.rivers.rivpost.push

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.Tg
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.DeviceTokenFirebaseCloudMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Optional Firebase push: uses the Firebase project the user fills in Settings. */
object Push {
    var status by mutableStateOf("")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun app(c: Context): FirebaseApp? {
        if (Prefs.fbApiKey.isBlank() || Prefs.fbAppId.isBlank() || Prefs.fbProject.isBlank() || Prefs.fbSender.isBlank()) {
            status = "Fill in all four fields first"; return null
        }
        FirebaseApp.getApps(c).firstOrNull()?.let { return it }
        return try {
            FirebaseApp.initializeApp(
                c.applicationContext,
                FirebaseOptions.Builder().setApiKey(Prefs.fbApiKey).setApplicationId(Prefs.fbAppId)
                    .setProjectId(Prefs.fbProject).setGcmSenderId(Prefs.fbSender).build(),
            )
        } catch (e: Throwable) { status = "Firebase error: " + (e.message ?: ""); null }
    }

    fun start(c: Context) {
        if (!Prefs.fbOn) return
        app(c) ?: return
        status = "Getting token…"
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = task.result
                if (!task.isSuccessful || token.isNullOrBlank()) { status = "Couldn't get a token: " + (task.exception?.message ?: ""); return@addOnCompleteListener }
                register(token)
            }
        } catch (e: Throwable) { status = "Firebase error: " + (e.message ?: "") }
    }

    fun register(token: String) {
        scope.launch {
            val accs = Tg.accounts.value.values.filter { it.isReady }
            if (accs.isEmpty()) { status = "Sign in first"; return@launch }
            val ids = accs.map { it.myId }.filter { it != 0L }
            var ok = 0; var err = ""
            for (a in accs) {
                val r = a.client.registerDevice(deviceToken = DeviceTokenFirebaseCloudMessaging(token = token, encrypt = false), otherUserIds = ids.filter { it != a.myId }.toLongArray())
                if (r is TdlResult.Success) ok++ else if (r is TdlResult.Failure) err = r.message
            }
            status = if (ok > 0) "Registered with Telegram ($ok)" else "Telegram refused: $err"
        }
    }

    fun handle(c: Context, m: RemoteMessage) {
        val json = JSONObject()
        for ((k, v) in m.data) json.put(k, v)
        json.put("google.sent_time", m.sentTime)
        val payload = json.toString()
        if (Tg.accounts.value.isEmpty()) Tg.startAll()
        scope.launch { for (a in Tg.accounts.value.values) a.client.processPushNotification(payload = payload) }
    }
}

class RivPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) { if (Prefs.fbOn) Push.handle(this, message) }
    override fun onNewToken(token: String) { if (Prefs.fbOn) Push.register(token) }
}
