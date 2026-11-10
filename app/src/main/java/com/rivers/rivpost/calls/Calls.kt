package com.rivers.rivpost.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Call
import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallServerTypeTelegramReflector
import dev.g000sha256.tdl.dto.CallServerTypeWebrtc
import dev.g000sha256.tdl.dto.CallStateDiscarded
import dev.g000sha256.tdl.dto.CallStateError
import dev.g000sha256.tdl.dto.CallStateExchangingKeys
import dev.g000sha256.tdl.dto.CallStateHangingUp
import dev.g000sha256.tdl.dto.CallStatePending
import dev.g000sha256.tdl.dto.CallStateReady
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.OptionValueInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import org.webrtc.audio.CallAudioHooks
import org.thunderdog.challegram.N
import org.thunderdog.challegram.voip.CallConfiguration
import org.thunderdog.challegram.voip.CallOptions
import org.thunderdog.challegram.voip.ConnectionStateListener
import org.thunderdog.challegram.voip.NetworkStats
import org.thunderdog.challegram.voip.TgCallsController
import java.io.File

/** What the call screen shows. */
data class CallUi(
    val acc: Int,
    val id: Int,
    val userId: Long,
    val outgoing: Boolean,
    val status: String,
    val incomingRinging: Boolean = false,
    val emojis: List<String> = emptyList(),
    val connectedAt: Long = 0L,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val bars: Int = 4,
    val ended: Boolean = false,
    val minimized: Boolean = false,
    val hold: Boolean = false,
    val deafen: Boolean = false,
    val recording: Boolean = false,
    val playing: Boolean = false,
    val captions: Boolean = false,
    val weak: Boolean = false,
    val talkingMuted: Boolean = false,
    val silent: Boolean = false,
    val lastMsg: String = "",
    val waitingUser: Long = 0L,
    val waitingId: Int = 0,
    val waitingAcc: Int = 0,
    val redial: Int = 0,
    val notes: String = "",
)

/**
 * One-to-one voice calls. The audio engine is the prebuilt one from Telegram X
 * (tgcalls + WebRTC); TDLib does the signalling.
 */
object Calls {
    private const val TAG = "RivCalls"
    val ui = MutableStateFlow<CallUi?>(null)

    /** A finished call Telegram would like rated: account index to call id. */
    val rate = MutableStateFlow<Pair<Int, Int>?>(null)
    @Volatile private var lastDebug = ""

    fun sendRating(stars: Int, comment: String, problems: List<dev.g000sha256.tdl.dto.CallProblem>) {
        val (a, id) = rate.value ?: return
        rate.value = null
        val acc = Tg.get(a)
        acc.scope.launch {
            acc.client.sendCallRating(callId = dev.g000sha256.tdl.dto.InputCallDiscarded(id), rating = stars, comment = comment, problems = problems.toTypedArray())
        }
    }

    private var loaded: Boolean? = null
    var loadError: String = ""
        private set
    private var versions: Array<String> = emptyArray()

    private var controller: TgCallsController? = null
    private val pendingSignaling = mutableListOf<ByteArray>()
    private var ringtone: Ringtone? = null
    private var tone: ToneGenerator? = null
    private var proximity: PowerManager.WakeLock? = null
    private var savedMode = AudioManager.MODE_NORMAL

    /** Loads the native engine once. Returns false if this phone can't run it. */
    fun available(): Boolean {
        loaded?.let { return it }
        val ok = try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("cryptox")
            System.loadLibrary("sslx")
            System.loadLibrary("tgcallsjni")
            org.webrtc.ContextUtils.initialize(Tg.app.applicationContext)
            versions = N.getTgCallsVersions().filter { it != "11.0.0" }.toTypedArray()
            Log.i(TAG, "tgcalls versions: ${versions.joinToString()}")
            versions.isNotEmpty()
        } catch (e: Throwable) {
            loadError = e.toString()
            Log.e(TAG, "engine load failed", e)
            false
        }
        loaded = ok
        return ok
    }

    private fun protocol() = CallProtocol(
        udpP2p = true,
        udpReflector = true,
        minLayer = 65,
        maxLayer = 92,
        libraryVersions = versions,
    )

    private fun hasMic(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askMic(act: Activity): Boolean {
        if (hasMic(act)) return true
        act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4711)
        com.rivers.rivpost.ui.toast(act, t("Allow the microphone, then try again"))
        return false
    }

    // ------------------------------------------------------------------ actions

    fun start(act: Activity, acc: TgAccount, userId: Long) {
        if (!Prefs.voiceCalls) return
        if (ui.value?.ended == false) { mod { it.copy(minimized = false) }; return }
        if (!available()) { com.rivers.rivpost.ui.toast(act, t("Calls can't run on this phone") + "\n" + loadError); return }
        if (!askMic(act)) return
        if (Prefs.callWifiOnly && networkType(act) !in setOf(6, 7)) { com.rivers.rivpost.ui.toast(act, t("Wi-Fi only calls is on")); return }
        startInternal(act.applicationContext, acc, userId, Prefs.callRedial)
    }

    private fun startInternal(ctx: Context, acc: TgAccount, userId: Long, redial: Int) {
        ui.value = CallUi(acc.index, 0, userId, outgoing = true, status = t("Requesting…"), redial = redial)
        acc.scope.launch {
            when (val r = acc.client.createCall(userId = userId, protocol = protocol(), isVideo = false)) {
                is TdlResult.Success -> mod { it.copy(id = r.result.id) }
                is TdlResult.Failure -> {
                    ui.value = ui.value?.copy(status = r.message.ifBlank { t("Call failed") }, ended = true)
                    CallService.stop(ctx)
                    closeLater()
                }
            }
        }
        CallService.start(ctx)
        CallTelecom.outgoing(ctx, acc.userName(userId))
        CallButtons.start(ctx)
        CallGestures.start(ctx)
    }

    fun accept(act: Activity) {
        val u = ui.value ?: return
        if (!u.incomingRinging) return
        if (!available()) { com.rivers.rivpost.ui.toast(act, t("Calls can't run on this phone")); decline(); return }
        if (!askMic(act)) return
        acceptInternal(act)
    }

    /** From the headset button, the car or auto-answer. Opens the app if the mic still needs permission. */
    fun acceptFromBackground(c: Context) {
        val u = ui.value ?: return
        if (!u.incomingRinging) return
        if (!available()) return
        if (!hasMic(c)) {
            c.startActivity(Intent(c, com.rivers.rivpost.MainActivity::class.java).putExtra("call_accept", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        // Without the phone-system link, Android only gives the mic to calls answered while the app is visible.
        if (CallTelecom.connection == null) try {
            c.startActivity(Intent(c, com.rivers.rivpost.MainActivity::class.java).putExtra("call_open", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (_: Throwable) { }
        acceptInternal(c)
    }

    private fun acceptInternal(c: Context) {
        val u = ui.value ?: return
        stopRinging()
        CallNotif.cancelIncoming(c)
        mod { it.copy(incomingRinging = false, status = t("Connecting…"), minimized = false) }
        CallService.start(c)
        CallTelecom.active()
        val acc = Tg.get(u.acc)
        acc.scope.launch { acc.client.acceptCall(callId = u.id, protocol = protocol()) }
    }

    /** Stop the ringtone but keep the call ringing on screen. */
    fun silence() {
        stopRinging()
        mod { it.copy(silent = true) }
    }

    fun declineWithMessage(text: String) {
        val u = ui.value ?: return
        val acc = Tg.get(u.acc)
        hangUp()
        acc.scope.launch { sendText(acc, u.userId, text) }
    }

    private suspend fun sendText(acc: TgAccount, userId: Long, text: String) {
        val chat = acc.privateChat(userId) ?: return
        acc.client.sendMessage(
            chatId = chat,
            inputMessageContent = dev.g000sha256.tdl.dto.InputMessageText(
                text = dev.g000sha256.tdl.dto.FormattedText(text = text, entities = emptyArray()), linkPreviewOptions = null, clearDraft = false,
            ),
        )
    }

    fun sendChat(text: String) {
        val u = ui.value ?: return
        if (text.isBlank()) return
        val acc = Tg.get(u.acc)
        acc.scope.launch { sendText(acc, u.userId, text) }
    }

    /** Text lines for the call details dialog. */
    fun details(c: Context): List<Pair<String, String>> {
        val u = ui.value ?: return emptyList()
        val ctl = controller
        val st = try { ctl?.stats } catch (_: Throwable) { null }
        val net = when (networkType(c)) { 6 -> "Wi-Fi"; 7 -> "Ethernet"; 5 -> t("Mobile data"); 0 -> "—"; else -> t("Other") }
        fun mb(b: Long) = "%.2f MB".format(b / 1048576.0)
        return listOfNotNull(
            t("Network") to net,
            t("Signal") to "${u.bars}/4",
            t("Engine version") to (ctl?.version ?: "—"),
            t("Direct connection allowed") to (if (Prefs.callP2p) t("Yes") else t("No")),
            st?.let { t("Data used") to mb(it.bytesSentWifi + it.bytesRecvdWifi + it.bytesSentMobile + it.bytesRecvdMobile) },
            st?.let { t("Sent / received") to mb(it.bytesSentWifi + it.bytesSentMobile) + " / " + mb(it.bytesRecvdWifi + it.bytesRecvdMobile) },
            t("Data saving") to when (dataSaving(c)) { 2 -> t("Battery low"); 1 -> t("Mobile data"); else -> t("Off") },
            t("Encryption") to t("End to end"),
        )
    }

    fun setVoice(v: Int) { CallAudioHooks.voice = v }

    fun setNotes(n: String) { mod { it.copy(notes = n) } }

    fun setHold(on: Boolean) {
        val u = ui.value ?: return
        CallAudioHooks.hold = on
        if (on) {
            try { controller?.setMicDisabled(false) } catch (_: Throwable) { }
            CallInject.play(Tg.app, Prefs.callHoldMusic.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }, loop = true)
        } else {
            CallInject.stop()
            CallAudioHooks.injectMix = true
            try { controller?.setMicDisabled(u.muted || u.deafen) } catch (_: Throwable) { }
        }
        mod { it.copy(hold = on, playing = on) }
    }

    private fun restoreMic() {
        val u = ui.value ?: return
        if (u.hold) return
        CallAudioHooks.injectMix = true
        try { controller?.setMicDisabled(u.muted || u.deafen) } catch (_: Throwable) { }
    }

    fun toggleDeafen() {
        val u = ui.value ?: return
        val d = !u.deafen
        CallAudioHooks.deafen = d
        try { controller?.setMicDisabled(d || u.muted) } catch (_: Throwable) { }
        mod { it.copy(deafen = d) }
    }

    fun playIntoCall(uri: Uri?) {
        val u = ui.value ?: return
        if (uri == null) { CallInject.stop(); restoreMic(); mod { it.copy(playing = false) }; return }
        if (u.muted || u.deafen) try { controller?.setMicDisabled(false) } catch (_: Throwable) { }
        // While muted, send only the clip, never your real mic.
        CallAudioHooks.injectMix = !(u.muted || u.deafen)
        CallInject.play(Tg.app, uri, loop = false)
        mod { it.copy(playing = true) }
    }

    fun toggleRecord(c: Context) {
        val u = ui.value ?: return
        if (u.recording) { saveRecording(c); mod { it.copy(recording = false) }; return }
        try {
            val dir = File(c.getExternalFilesDir(null), "calls").apply { mkdirs() }
            val name = Tg.get(u.acc).userName(u.userId).replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(30)
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.US).format(java.util.Date())
            CallAudioHooks.startRecording(File(dir, "$name $stamp.wav"))
            mod { it.copy(recording = true) }
        } catch (e: Throwable) { com.rivers.rivpost.ui.toast(c, e.message ?: "") }
    }

    private fun saveRecording(c: Context) {
        val f = CallAudioHooks.stopRecording() ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, f.name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/wav")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Riv Post/Calls")
                }
                val uri = c.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                if (uri != null) {
                    c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                    f.delete()
                }
                android.os.Handler(c.mainLooper).post { com.rivers.rivpost.ui.toast(c, t("Recording saved to Downloads › Riv Post › Calls")) }
            } else android.os.Handler(c.mainLooper).post { com.rivers.rivpost.ui.toast(c, t("Recording saved") + "\n" + f.absolutePath) }
        } catch (_: Throwable) { }
    }

    fun toggleCaptions(c: Context) {
        val u = ui.value ?: return
        if (u.captions) { CallCaptions.stop(); mod { it.copy(captions = false) }; return }
        if (!CallCaptions.supported(c)) { com.rivers.rivpost.ui.toast(c, t("Captions need Android 13 or newer with speech recognition")); return }
        CallCaptions.start(c)
        mod { it.copy(captions = true) }
    }

    /** Ends the current call and answers the one that's waiting. */
    fun switchToWaiting(act: Activity) {
        val u = ui.value ?: return
        if (u.waitingId == 0) return
        val next = CallUi(u.waitingAcc, u.waitingId, u.waitingUser, outgoing = false, status = t("Incoming call"), incomingRinging = true)
        hangUp()
        ui.value = next
        accept(act)
    }

    fun declineWaiting() {
        val u = ui.value ?: return
        if (u.waitingId == 0) return
        val acc = Tg.get(u.waitingAcc)
        val id = u.waitingId
        acc.scope.launch { acc.client.discardCall(callId = id, isDisconnected = false, inviteLink = "", duration = 0, isVideo = false, connectionId = 0) }
        mod { it.copy(waitingId = 0, waitingUser = 0) }
    }

    fun decline() = hangUp()

    fun hangUp() {
        val u = ui.value ?: return
        stopRinging()
        if (u.redial > 0 && u.connectedAt == 0L) mod { it.copy(redial = 0) }
        val acc = Tg.get(u.acc)
        val duration = if (u.connectedAt > 0) ((SystemClock.elapsedRealtime() - u.connectedAt) / 1000).toInt() else 0
        val conn = try { controller?.connectionId ?: 0L } catch (_: Throwable) { 0L }
        if (u.id != 0) acc.scope.launch {
            acc.client.discardCall(callId = u.id, isDisconnected = false, inviteLink = "", duration = duration, isVideo = false, connectionId = conn)
        }
        finish(t("Call ended"))
    }

    fun toggleMute() {
        val u = ui.value ?: return
        val m = !u.muted
        try { if (!u.hold) controller?.setMicDisabled(m || u.deafen) } catch (_: Throwable) { }
        mod { it.copy(muted = m, talkingMuted = false) }
    }

    fun toggleSpeaker() {
        val u = ui.value ?: return
        val s = !u.speaker
        setSpeaker(Tg.app, s)
        mod { it.copy(speaker = s) }
        proximity(Tg.app, !s)
    }

    /** Changes the live call. Never brings an ended call back, even if another thread was slower. */
    private fun mod(f: (CallUi) -> CallUi) { ui.update { c -> if (c == null || c.ended) c else f(c) } }

    fun minimize(v: Boolean) { mod { it.copy(minimized = v) } }

    // ------------------------------------------------------------------ TDLib updates

    private fun inHours(from: Int, to: Int): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (from <= to) h in from until to else h >= from || h < to
    }

    private fun headsetConnected(c: Context): Boolean = try {
        am(c).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    } catch (_: Throwable) { false }

    private fun discard(acc: TgAccount, id: Int) = acc.scope.launch {
        acc.client.discardCall(callId = id, isDisconnected = false, inviteLink = "", duration = 0, isVideo = false, connectionId = 0)
    }

    fun onCall(acc: TgAccount, call: Call) {
        val ctx = Tg.app
        val cur = ui.value
        if (cur != null && cur.waitingId == call.id) {
            if (call.state is CallStateDiscarded || call.state is CallStateError) mod { it.copy(waitingId = 0, waitingUser = 0) }
            return
        }
        if (call.isVideo && controller == null && cur == null && !call.isOutgoing) return // video calls aren't supported yet
        if (cur != null && !cur.ended && cur.id != 0 && cur.id != call.id) {
            if (!call.isOutgoing && call.state is CallStatePending) {
                if (Prefs.callWaiting && cur.waitingId == 0 && !call.isVideo) {
                    mod { it.copy(waitingId = call.id, waitingUser = call.userId, waitingAcc = acc.index) }
                    vibrate(ctx, 300)
                } else discard(acc, call.id)
            }
            return
        }
        when (val s = call.state) {
            is CallStatePending -> {
                if (!call.isOutgoing) {
                    if (!Prefs.voiceCalls || (cur != null && !cur.ended)) return
                    val uid = call.userId
                    val loud = CallPeople.has(uid, CallPeople.LOUD)
                    val quiet = Prefs.callQuiet && inHours(Prefs.callQuietFrom, Prefs.callQuietTo) && !loud
                    if (quiet && Prefs.callQuietMode == "decline") {
                        discard(acc, call.id)
                        val msg = Prefs.callQuietMsg
                        if (msg.isNotBlank()) acc.scope.launch { sendText(acc, uid, msg) }
                        return
                    }
                    val silent = quiet || (Prefs.callUnknownSilent && acc.users[uid]?.isContact != true && !loud)
                    val last = if (Prefs.callLastMsg) try { com.rivers.rivpost.tg.Fmt.preview(acc, acc.chats[uid]?.lastMessage) } catch (_: Throwable) { "" } else ""
                    ui.value = CallUi(acc.index, call.id, uid, outgoing = false, status = t("Incoming call"), incomingRinging = true, silent = silent, lastMsg = last)
                    if (!silent) startRinging(ctx, CallPeople.ringtone(uid))
                    CallNotif.showIncoming(ctx, acc, call.id, uid)
                    CallTelecom.incoming(ctx, acc.userName(uid))
                    CallButtons.start(ctx)
                    CallGestures.start(ctx)
                    if (CallPeople.has(uid, CallPeople.AUTO) || (Prefs.callAutoHeadset && headsetConnected(ctx))) acc.scope.launch {
                        delay(2000)
                        if (ui.value?.id == call.id && ui.value?.incomingRinging == true) acceptFromBackground(ctx)
                    }
                } else {
                    val st = if (s.isReceived) t("Ringing…") else if (s.isCreated) t("Waiting…") else t("Requesting…")
                    ui.value = (cur ?: CallUi(acc.index, call.id, call.userId, true, st)).copy(id = call.id, status = st)
                    if (s.isReceived) startRingback()
                }
            }
            is CallStateExchangingKeys -> {
                stopRinging()
                mod { it.copy(id = call.id, status = t("Exchanging keys…")) }
            }
            is CallStateReady -> {
                stopRinging()
                mod { it.copy(id = call.id, status = t("Connecting…"), emojis = s.emojis.toList()) }
                if (controller == null) acc.scope.launch { launchEngine(acc, call, s) }
            }
            is CallStateHangingUp -> mod { it.copy(status = t("Hanging up…")) }
            is CallStateDiscarded -> {
                val missed = s.reason is CallDiscardReasonMissed
                val declined = s.reason is CallDiscardReasonDeclined
                val why = when {
                    missed -> if (call.isOutgoing) t("No answer") else t("Missed call")
                    declined -> if (call.isOutgoing) t("Line busy") else t("Call declined")
                    else -> t("Call ended")
                }
                // What Telegram asks for after a call, like the official apps send.
                if (s.needDebugInformation) acc.scope.launch {
                    delay(1500)
                    if (lastDebug.isNotBlank()) acc.client.sendCallDebugInformation(callId = dev.g000sha256.tdl.dto.InputCallDiscarded(call.id), debugInformation = lastDebug)
                }
                if (s.needLog) acc.scope.launch {
                    delay(1500)
                    val f = File(File(ctx.filesDir, "voip"), "last_call.log")
                    if (f.exists()) acc.client.sendCallLog(callId = dev.g000sha256.tdl.dto.InputCallDiscarded(call.id), logFile = dev.g000sha256.tdl.dto.InputFileLocal(f.absolutePath))
                }
                if (s.needRating && Prefs.callRateAsk && cur?.connectedAt?.let { it > 0 } == true) rate.value = acc.index to call.id
                if (cur?.id == call.id) {
                    val wasRinging = cur.incomingRinging
                    finish(why)
                    if (!call.isOutgoing && missed && wasRinging) CallReminders.missed(ctx, acc.index, call.userId)
                    if (call.isOutgoing && (missed || declined) && cur.redial > 0 && cur.connectedAt == 0L) redialLater(acc, call.userId, cur.redial - 1)
                }
            }
            is CallStateError -> if (cur?.id == call.id) finish(s.error.message.ifBlank { t("Call failed") })
        }
    }

    private fun redialLater(acc: TgAccount, userId: Long, left: Int) {
        acc.scope.launch {
            for (sec in 5 downTo 1) {
                delay(1000)
                val u = ui.value ?: return@launch
                if (!u.ended || u.userId != userId) return@launch
                mod { it.copy(status = t("Calling again in") + " $sec", redial = left + 1) }
            }
            val u = ui.value
            if (u != null && u.ended && u.userId == userId && u.redial > 0) {
                ui.value = null
                startInternal(Tg.app, acc, userId, left)
            }
        }
    }

    private fun monitor(acc: TgAccount, id: Int) {
        acc.scope.launch {
            var weakFor = 0
            var lastAlert = 0
            while (true) {
                delay(1000)
                val u = ui.value ?: break
                if (u.id != id || u.ended) break
                if (u.playing && !u.hold && !CallInject.playing) restoreMic()
                val talking = Prefs.callMuteReminder && u.muted && !u.hold && CallAudioHooks.micLevel > 0.15f
                weakFor = if (u.bars <= 1) weakFor + 1 else 0
                val weak = Prefs.callWeakWarn && weakFor >= 5
                var next = u
                if (talking != u.talkingMuted || weak != u.weak || u.playing != CallInject.playing) next = u.copy(talkingMuted = talking, weak = weak, playing = CallInject.playing || u.hold)
                if (next !== u) ui.value = next
                val alert = Prefs.callTimeAlert
                if (alert > 0 && u.connectedAt > 0) {
                    val mins = ((SystemClock.elapsedRealtime() - u.connectedAt) / 60_000).toInt()
                    if (mins > 0 && mins % alert == 0 && mins != lastAlert) {
                        lastAlert = mins
                        vibrate(Tg.app, 600)
                        android.os.Handler(Tg.app.mainLooper).post { com.rivers.rivpost.ui.toast(Tg.app, "$mins " + t("min")) }
                    }
                }
            }
        }
    }

    private fun onConnected(acc: TgAccount, u: CallUi) {
        if (Prefs.callVibrate) vibrate(Tg.app, 120)
        CallTelecom.active()
        if (CallPeople.has(u.userId, CallPeople.SPEAKER) && !u.speaker) toggleSpeaker()
        if (Prefs.callRecord && CallPeople.has(u.userId, CallPeople.REC)) toggleRecord(Tg.app)
        if (Prefs.callCaptions && CallCaptions.supported(Tg.app)) { CallCaptions.start(Tg.app); mod { it.copy(captions = true) } }
        monitor(acc, u.id)
    }

    private fun vibrate(c: Context, ms: Long) {
        try { (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Throwable) { }
    }

    fun onSignaling(callId: Int, data: ByteArray) {
        if (ui.value?.id != callId) return
        val c = controller
        if (c == null) synchronized(pendingSignaling) { pendingSignaling.add(data) }
        else try { c.handleIncomingSignalingData(data) } catch (e: Throwable) { Log.e(TAG, "signal", e) }
    }

    // ------------------------------------------------------------------ engine

    private suspend fun option(acc: TgAccount, name: String, def: Long): Long =
        ((acc.client.getOption(name = name) as? TdlResult.Success)?.result as? OptionValueInteger)?.value ?: def

    @Volatile private var engineStarting = false

    private suspend fun launchEngine(acc: TgAccount, call: Call, s: CallStateReady) {
        if (engineStarting || controller != null) return
        engineStarting = true
        try { launchEngineInner(acc, call, s) } finally { engineStarting = false }
    }

    private fun stillLive(id: Int) = ui.value.let { it != null && it.id == id && !it.ended }

    private suspend fun launchEngineInner(acc: TgAccount, call: Call, s: CallStateReady) {
        if (!available()) { hangUp(); return }
        val ctx = Tg.app
        val dir = File(ctx.filesDir, "voip").apply { mkdirs() }
        val state = TdApi.CallStateReady(
            TdApi.CallProtocol(s.protocol.udpP2p, s.protocol.udpReflector, s.protocol.minLayer, s.protocol.maxLayer, s.protocol.libraryVersions),
            s.servers.map { sv ->
                val type: TdApi.CallServerType = when (val ty = sv.type) {
                    is CallServerTypeTelegramReflector -> TdApi.CallServerTypeTelegramReflector(ty.peerTag, ty.isTcp)
                    is CallServerTypeWebrtc -> TdApi.CallServerTypeWebrtc(ty.username, ty.password, ty.supportsTurn, ty.supportsStun)
                    else -> TdApi.CallServerTypeWebrtc("", "", false, false)
                }
                TdApi.CallServer(sv.id, sv.ipAddress, sv.ipv6Address, sv.port, type)
            }.toTypedArray(),
            s.config, s.encryptionKey, s.emojis, s.allowP2p && Prefs.callP2p, s.isGroupCallSupported, s.customParameters,
        )
        val config = CallConfiguration(
            state, call.isOutgoing,
            File(dir, "persistent_state.json").absolutePath,
            File(dir, "last_call.log").absolutePath,
            File(dir, "last_call_stats.log").absolutePath,
            option(acc, "call_packet_timeout_ms", 10_000),
            option(acc, "call_connect_timeout_ms", 30_000),
            dataSaving(ctx), false, null,
            false, false, true, false,
        )
        if (!stillLive(call.id)) return
        val options = CallOptions(networkType(ctx), false, 1, ui.value?.muted == true)
        val listener = object : ConnectionStateListener {
            override fun onConnectionStateChanged(newState: Int) {
                val u = ui.value ?: return
                when (newState) {
                    3 -> { // established
                        val first = u.connectedAt == 0L
                        mod { it.copy(status = "", connectedAt = if (first) SystemClock.elapsedRealtime() else u.connectedAt) }
                        if (first) ui.value?.let { onConnected(acc, it) }
                    }
                    4 -> { // failed
                        mod { it.copy(status = t("Connection failed")) }
                        val conn = try { controller?.connectionId ?: 0L } catch (_: Throwable) { 0L }
                        acc.scope.launch {
                            acc.client.discardCall(callId = call.id, isDisconnected = true, inviteLink = "", duration = 0, isVideo = false, connectionId = conn)
                        }
                        finish(t("Connection failed"))
                    }
                    5 -> mod { it.copy(status = t("Reconnecting…")) }
                    1, 2 -> if (u.connectedAt == 0L) mod { it.copy(status = t("Connecting…")) }
                }
            }
            override fun onSignalBarCountChanged(newCount: Int) { mod { it.copy(bars = newCount) } }
            override fun onStopped(finalStats: NetworkStats, debugLog: String?) { lastDebug = debugLog ?: "" }
            override fun onRemoteMediaStateChanged(audioState: Int, videoState: Int) {
                val u = ui.value ?: return
                if (u.connectedAt > 0) mod { it.copy(status = if (audioState == 0) t("Their microphone is off") else "") }
            }
            override fun onSignallingDataEmitted(data: ByteArray) {
                acc.scope.launch { acc.client.sendCallSignalingData(callId = call.id, data = data) }
            }
        }
        CallAudioHooks.micGain = Prefs.callMicGain
        CallAudioHooks.playGain = Prefs.callPlayGain
        CallAudioHooks.bass = Prefs.callBass
        CallAudioHooks.treble = Prefs.callTreble
        CallAudioHooks.voice = 0
        CallAudioHooks.deafen = false; CallAudioHooks.hold = false
        startAudio(ctx)
        var made: TgCallsController? = null
        for (v in s.protocol.libraryVersions) {
            if (v.isEmpty() || v !in versions) continue
            made = try { TgCallsController(config, options, listener, v) } catch (e: Throwable) { Log.e(TAG, "tgcalls $v", e); null }
            if (made != null) break
        }
        if (made != null && !stillLive(call.id)) { try { made.destroy() } catch (_: Throwable) { }; stopAudio(ctx); return }
        if (made == null) {
            Log.e(TAG, "no matching tgcalls version: ${s.protocol.libraryVersions.joinToString()} vs ${versions.joinToString()}")
            hangUp()
            return
        }
        controller = made
        val queued = synchronized(pendingSignaling) { pendingSignaling.toList().also { pendingSignaling.clear() } }
        queued.forEach { try { made.handleIncomingSignalingData(it) } catch (_: Throwable) { } }
    }

    private fun finish(why: String) {
        stopRinging()
        val c = controller
        controller = null
        synchronized(pendingSignaling) { pendingSignaling.clear() }
        try { c?.destroy() } catch (e: Throwable) { Log.e(TAG, "destroy", e) }
        CallInject.stop()
        CallAudioHooks.hold = false; CallAudioHooks.deafen = false
        if (CallAudioHooks.isRecording()) saveRecording(Tg.app)
        val transcript = CallCaptions.transcript.toString().trim()
        CallCaptions.stop()
        CallGestures.stop()
        CallButtons.stop()
        CallTelecom.ended()
        ui.value?.let { u0 ->
            if (u0.ended) return@let
            if (u0.connectedAt > 0 && Prefs.callVibrate) vibrate(Tg.app, 120)
            val notes = u0.notes.trim()
            if (notes.isNotEmpty() || transcript.isNotEmpty()) {
                val acc = Tg.get(u0.acc)
                val me = acc.me.value?.id ?: 0L
                val txt = buildString {
                    append("📞 ").append(acc.userName(u0.userId)).append('\n')
                    if (notes.isNotEmpty()) append('\n').append(t("Notes")).append(":\n").append(notes).append('\n')
                    if (transcript.isNotEmpty()) append('\n').append(t("What they said")).append(":\n").append(transcript)
                }
                if (me != 0L) acc.scope.launch { sendText(acc, me, txt.take(4000)) }
            }
        }
        stopAudio(Tg.app)
        CallNotif.cancelIncoming(Tg.app)
        CallService.stop(Tg.app)
        var was: CallUi? = null
        ui.update { c -> if (c == null || c.ended) c else { was = c; c.copy(status = why, ended = true, incomingRinging = false, minimized = false) } }
        if (was == null) return
        closeLater()
    }

    private fun closeLater() {
        val cur = ui.value
        Tg.active.scope.launch {
            delay(1800)
            if (ui.value === cur || ui.value?.ended == true) ui.value = null
        }
    }

    // ------------------------------------------------------------------ audio

    private fun am(c: Context) = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun startAudio(c: Context) {
        val am = am(c)
        // With the phone-system link, Android sets the call audio mode itself.
        if (CallTelecom.connection == null) try {
            savedMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Throwable) { }
        setSpeaker(c, ui.value?.speaker == true)
        proximity(c, ui.value?.speaker != true)
    }

    private fun stopAudio(c: Context) {
        val am = am(c)
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            @Suppress("DEPRECATION") am.isSpeakerphoneOn = false
            am.mode = if (savedMode == AudioManager.MODE_IN_COMMUNICATION) AudioManager.MODE_NORMAL else savedMode
        } catch (_: Throwable) { }
        proximity(c, false)
    }

    private fun setSpeaker(c: Context, on: Boolean) {
        if (CallTelecom.speaker(on)) return
        val am = am(c)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val want = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val headset = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                val dev = if (!on && headset != null) headset else am.availableCommunicationDevices.firstOrNull { it.type == want }
                if (dev != null) am.setCommunicationDevice(dev) else am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION") am.isSpeakerphoneOn = on
            }
        } catch (_: Throwable) { }
    }

    private fun proximity(c: Context, on: Boolean) {
        try {
            if (on) {
                if (proximity == null) {
                    val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
                        proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "riv:call")
                }
                if (proximity?.isHeld == false) proximity?.acquire(4 * 60 * 60 * 1000L)
            } else if (proximity?.isHeld == true) proximity?.release()
        } catch (_: Throwable) { }
    }

    private fun startRinging(c: Context, tone: String = "") {
        try {
            val am = am(c)
            if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val r = RingtoneManager.getRingtone(c, if (tone.isNotBlank()) Uri.parse(tone) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                if (Build.VERSION.SDK_INT >= 28) r?.isLooping = true
                r?.play()
                ringtone = r
            }
            if (am.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 1000), 0))
            }
        } catch (_: Throwable) { }
    }

    private fun startRingback() {
        if (tone != null) return
        try {
            tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        } catch (_: Throwable) { }
    }

    private fun stopRinging() {
        try { ringtone?.stop() } catch (_: Throwable) { }
        ringtone = null
        try { tone?.stopTone(); tone?.release() } catch (_: Throwable) { }
        tone = null
        try { (Tg.app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Throwable) { }
    }

    private fun dataSaving(c: Context): Int {
        if (!Prefs.callSaver) return 0
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val level = try { bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Throwable) { 100 }
        return when {
            level in 1..15 && !bm.isCharging -> 2
            networkType(c) == 5 || networkType(c) == 8 -> 1
            else -> 0
        }
    }

    private fun networkType(c: Context): Int {
        return try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return 0
            when {
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 6
                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 7
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 5
                else -> 8
            }
        } catch (_: Throwable) { 0 }
    }
}
