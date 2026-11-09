package com.rivers.rivpost.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
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
)

/**
 * One-to-one voice calls. The audio engine is the prebuilt one from Telegram X
 * (tgcalls + WebRTC); TDLib does the signalling.
 */
object Calls {
    private const val TAG = "RivCalls"
    val ui = MutableStateFlow<CallUi?>(null)

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
        if (ui.value?.ended == false) { ui.value = ui.value?.copy(minimized = false); return }
        if (!available()) { com.rivers.rivpost.ui.toast(act, t("Calls can't run on this phone") + "\n" + loadError); return }
        if (!askMic(act)) return
        ui.value = CallUi(acc.index, 0, userId, outgoing = true, status = t("Requesting…"))
        acc.scope.launch {
            when (val r = acc.client.createCall(userId = userId, protocol = protocol(), isVideo = false)) {
                is TdlResult.Success -> ui.value = ui.value?.copy(id = r.result.id)
                is TdlResult.Failure -> {
                    ui.value = ui.value?.copy(status = r.message.ifBlank { t("Call failed") }, ended = true)
                    closeLater()
                }
            }
        }
        CallService.start(act)
    }

    fun accept(act: Activity) {
        val u = ui.value ?: return
        if (!u.incomingRinging) return
        if (!available()) { com.rivers.rivpost.ui.toast(act, t("Calls can't run on this phone")); decline(); return }
        if (!askMic(act)) return
        stopRinging()
        ui.value = u.copy(incomingRinging = false, status = t("Connecting…"), minimized = false)
        CallService.start(act)
        val acc = Tg.get(u.acc)
        acc.scope.launch { acc.client.acceptCall(callId = u.id, protocol = protocol()) }
    }

    fun decline() = hangUp()

    fun hangUp() {
        val u = ui.value ?: return
        stopRinging()
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
        try { controller?.setMicDisabled(m) } catch (_: Throwable) { }
        ui.value = u.copy(muted = m)
    }

    fun toggleSpeaker() {
        val u = ui.value ?: return
        val s = !u.speaker
        setSpeaker(Tg.app, s)
        ui.value = u.copy(speaker = s)
        proximity(Tg.app, !s)
    }

    fun minimize(v: Boolean) { ui.value = ui.value?.copy(minimized = v) }

    // ------------------------------------------------------------------ TDLib updates

    fun onCall(acc: TgAccount, call: Call) {
        if (call.isVideo && controller == null && ui.value == null && !call.isOutgoing) {
            // Video calls aren't supported yet: let other devices answer.
            return
        }
        val cur = ui.value
        if (cur != null && !cur.ended && cur.id != 0 && cur.id != call.id) {
            // Busy with another call.
            if (!call.isOutgoing && call.state is CallStatePending) acc.scope.launch {
                acc.client.discardCall(callId = call.id, isDisconnected = false, inviteLink = "", duration = 0, isVideo = false, connectionId = 0)
            }
            return
        }
        when (val s = call.state) {
            is CallStatePending -> {
                if (!call.isOutgoing) {
                    if (!Prefs.voiceCalls || cur != null) return
                    ui.value = CallUi(acc.index, call.id, call.userId, outgoing = false, status = t("Incoming call"), incomingRinging = true)
                    startRinging(Tg.app)
                    CallNotif.showIncoming(Tg.app, acc, call.id, call.userId)
                } else {
                    val st = if (s.isReceived) t("Ringing…") else if (s.isCreated) t("Waiting…") else t("Requesting…")
                    ui.value = (cur ?: CallUi(acc.index, call.id, call.userId, true, st)).copy(id = call.id, status = st)
                    if (s.isReceived) startRingback()
                }
            }
            is CallStateExchangingKeys -> {
                stopRinging()
                ui.value = cur?.copy(id = call.id, status = t("Exchanging keys…"))
            }
            is CallStateReady -> {
                stopRinging()
                ui.value = cur?.copy(id = call.id, status = t("Connecting…"), emojis = s.emojis.toList())
                if (controller == null) acc.scope.launch { launchEngine(acc, call, s) }
            }
            is CallStateHangingUp -> ui.value = cur?.copy(status = t("Hanging up…"))
            is CallStateDiscarded -> {
                val why = when (s.reason) {
                    is CallDiscardReasonMissed -> if (call.isOutgoing) t("No answer") else t("Missed call")
                    is CallDiscardReasonDeclined -> if (call.isOutgoing) t("Line busy") else t("Call declined")
                    else -> t("Call ended")
                }
                if (cur?.id == call.id) finish(why)
            }
            is CallStateError -> if (cur?.id == call.id) finish(s.error.message.ifBlank { t("Call failed") })
        }
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

    private suspend fun launchEngine(acc: TgAccount, call: Call, s: CallStateReady) {
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
            0, false, null,
            false, false, true, false,
        )
        val options = CallOptions(networkType(ctx), false, 1, ui.value?.muted == true)
        val listener = object : ConnectionStateListener {
            override fun onConnectionStateChanged(newState: Int) {
                val u = ui.value ?: return
                when (newState) {
                    3 -> { // established
                        ui.value = u.copy(status = "", connectedAt = if (u.connectedAt == 0L) SystemClock.elapsedRealtime() else u.connectedAt)
                    }
                    4 -> { // failed
                        ui.value = u.copy(status = t("Connection failed"))
                        val conn = try { controller?.connectionId ?: 0L } catch (_: Throwable) { 0L }
                        acc.scope.launch {
                            acc.client.discardCall(callId = call.id, isDisconnected = true, inviteLink = "", duration = 0, isVideo = false, connectionId = conn)
                        }
                        finish(t("Connection failed"))
                    }
                    5 -> ui.value = u.copy(status = t("Reconnecting…"))
                    1, 2 -> if (u.connectedAt == 0L) ui.value = u.copy(status = t("Connecting…"))
                }
            }
            override fun onSignalBarCountChanged(newCount: Int) { ui.value = ui.value?.copy(bars = newCount) }
            override fun onStopped(finalStats: NetworkStats, debugLog: String?) { }
            override fun onRemoteMediaStateChanged(audioState: Int, videoState: Int) {
                val u = ui.value ?: return
                if (u.connectedAt > 0) ui.value = u.copy(status = if (audioState == 0) t("Their microphone is off") else "")
            }
            override fun onSignallingDataEmitted(data: ByteArray) {
                acc.scope.launch { acc.client.sendCallSignalingData(callId = call.id, data = data) }
            }
        }
        startAudio(ctx)
        var made: TgCallsController? = null
        for (v in s.protocol.libraryVersions) {
            if (v.isEmpty() || v !in versions) continue
            made = try { TgCallsController(config, options, listener, v) } catch (e: Throwable) { Log.e(TAG, "tgcalls $v", e); null }
            if (made != null) break
        }
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
        stopAudio(Tg.app)
        CallNotif.cancelIncoming(Tg.app)
        CallService.stop(Tg.app)
        val u = ui.value ?: return
        if (u.ended) return
        ui.value = u.copy(status = why, ended = true, incomingRinging = false, minimized = false)
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
        try {
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

    private fun startRinging(c: Context) {
        try {
            val am = am(c)
            if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
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
