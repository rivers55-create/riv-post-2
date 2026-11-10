package com.rivers.rivpost.calls

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.audio.CallAudioHooks
import java.io.OutputStream
import kotlin.math.sqrt

/** Per-person call options. */
object CallPeople {
    const val AUTO = "auto"; const val LOUD = "loud"; const val SPEAKER = "speaker"; const val REC = "rec"

    private fun json() = try { JSONObject(Prefs.callPeople) } catch (_: Throwable) { JSONObject() }

    fun has(userId: Long, flag: String): Boolean =
        json().optJSONObject(userId.toString())?.optBoolean(flag, false) == true

    fun set(userId: Long, flag: String, on: Boolean) {
        val j = json()
        val o = j.optJSONObject(userId.toString()) ?: JSONObject()
        o.put(flag, on)
        j.put(userId.toString(), o)
        Prefs.callPeople = j.toString()
    }

    fun ringtone(userId: Long): String = json().optJSONObject(userId.toString())?.optString("tone", "") ?: ""

    fun setRingtone(userId: Long, uri: String) {
        val j = json()
        val o = j.optJSONObject(userId.toString()) ?: JSONObject()
        o.put("tone", uri)
        j.put(userId.toString(), o)
        Prefs.callPeople = j.toString()
    }
}

/** Decodes an audio file and feeds it into the call (play into call, hold music). */
object CallInject {
    @Volatile private var job: Thread? = null
    @Volatile var playing = false
        private set

    fun stop() {
        playing = false
        job?.interrupt()
        job = null
        CallAudioHooks.injectClear()
    }

    /** uri == null plays a soft built-in hold tune. */
    fun play(c: Context, uri: Uri?, loop: Boolean) {
        stop()
        playing = true
        val t = Thread {
            try {
                do {
                    if (uri == null) tune() else decode(c, uri)
                } while (loop && playing)
            } catch (_: Throwable) { }
            playing = false
        }
        job = t
        t.start()
    }

    private fun push(s: ShortArray) {
        var off = 0
        while (off < s.size && playing) {
            val part = if (off == 0) s else s.copyOfRange(off, s.size)
            val k = CallAudioHooks.inject(part, part.size)
            off += k
            if (off < s.size) Thread.sleep(40)
        }
    }

    private fun tune() {
        val rate = CallAudioHooks.recordRate
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 880.0, 698.46)
        for (f in notes) {
            if (!playing) return
            val n = rate * 4 / 10
            val s = ShortArray(n) { i ->
                val env = minOf(1.0, i / 800.0) * minOf(1.0, (n - i) / 2400.0)
                (Math.sin(2 * Math.PI * f * i / rate) * 4500 * env).toInt().toShort()
            }
            push(s)
            push(ShortArray(rate / 10))
        }
    }

    private fun decode(c: Context, uri: Uri) {
        val ex = MediaExtractor()
        ex.setDataSource(c, uri, null)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inDone = false
        try {
            while (playing) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING) && nf.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT) return
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val sb = buf.asShortBuffer()
                    val frames = sb.remaining() / ch
                    val mono = ShortArray(frames) { f -> var sum = 0; for (k in 0 until ch) sum += sb.get(f * ch + k); (sum / ch).toShort() }
                    codec.releaseOutputBuffer(o, false)
                    push(resample(mono, srcRate, CallAudioHooks.recordRate))
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            try { codec.stop(); codec.release() } catch (_: Throwable) { }
            ex.release()
        }
    }

    private fun resample(s: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || s.isEmpty()) return s
        val n = (s.size.toLong() * to / from).toInt()
        return ShortArray(n) { i ->
            val pos = i.toDouble() * from / to
            val a = pos.toInt().coerceAtMost(s.size - 1); val b = (a + 1).coerceAtMost(s.size - 1)
            val f = pos - a
            (s[a] * (1 - f) + s[b] * f).toInt().toShort()
        }
    }
}

/** Live captions of the other person's voice (Android 13+, uses the phone's speech recognition). */
object CallCaptions {
    val lines = mutableStateListOf<String>()
    val transcript = StringBuilder()
    @Volatile private var running = false
    private var rec: SpeechRecognizer? = null
    private var out: OutputStream? = null
    private var rate = 48000

    fun supported(c: Context) = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(c)

    fun start(c: Context) {
        if (running || !supported(c)) return
        running = true
        lines.clear(); transcript.setLength(0)
        android.os.Handler(c.mainLooper).post { next(c) }
    }

    // Audio for the recognizer goes through this queue, so the call's playback thread never waits.
    private val queue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(50)
    private var feeder: Thread? = null

    fun stop() {
        running = false
        CallAudioHooks.playTap = null
        queue.clear()
        android.os.Handler(Tg.app.mainLooper).post {
            try { out?.close() } catch (_: Throwable) { }
            out = null
            try { rec?.destroy() } catch (_: Throwable) { }
            rec = null
            feeder?.interrupt(); feeder = null
        }
    }

    private fun startFeeder() {
        if (feeder != null) return
        val t = Thread {
            try {
                while (running) {
                    val b = queue.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                    try { out?.write(b) } catch (_: Throwable) { }
                }
            } catch (_: InterruptedException) { }
        }
        t.isDaemon = true
        feeder = t
        t.start()
    }

    private fun next(c: Context) {
        if (!running || Build.VERSION.SDK_INT < 33) return
        try { out?.close() } catch (_: Throwable) { }
        try { rec?.destroy() } catch (_: Throwable) { }
        val pipe = ParcelFileDescriptor.createPipe()
        val stream = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        out = stream
        rate = CallAudioHooks.playRate
        startFeeder()
        CallAudioHooks.playTap = CallAudioHooks.PlayTap { pcm, len, _, ch ->
            val m = if (ch == 1) pcm.copyOf(len) else ByteArray(len / ch).also { m ->
                for (i in 0 until len / (2 * ch)) { m[i * 2] = pcm[i * 2 * ch]; m[i * 2 + 1] = pcm[i * 2 * ch + 1] }
            }
            queue.offer(m) // drops audio if the recognizer falls behind
        }
        val r = SpeechRecognizer.createSpeechRecognizer(c)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!txt.isNullOrBlank()) add(txt)
                next(c)
            }
            override fun onError(error: Int) { android.os.Handler(c.mainLooper).postDelayed({ next(c) }, 300) }
            override fun onPartialResults(partialResults: Bundle?) { }
            override fun onReadyForSpeech(params: Bundle?) { }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onEndOfSpeech() { }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
        }
        try { r.startListening(i) } catch (_: Throwable) { running = false }
        try { pipe[0].close() } catch (_: Throwable) { }
    }

    private fun add(txt: String) {
        val u = Calls.ui.value ?: return
        val acc = Tg.get(u.acc)
        if (!Prefs.callTranslate) { push(txt); return }
        acc.scope.launch {
            val to = if (Lang.arabic) "ar" else Prefs.lang.takeIf { it.length == 2 } ?: "en"
            val r = acc.client.translateText(text = dev.g000sha256.tdl.dto.FormattedText(txt, emptyArray()), toLanguageCode = to, tone = "")
            push((r as? dev.g000sha256.tdl.TdlResult.Success)?.result?.text ?: txt)
        }
    }

    private fun push(s: String) {
        android.os.Handler(Tg.app.mainLooper).post {
            transcript.append(s).append('\n')
            lines.add(s)
            while (lines.size > 3) lines.removeAt(0)
        }
    }
}

/** Flip to silence, shake to hang up. */
object CallGestures : SensorEventListener {
    private var sm: SensorManager? = null
    private var shakes = 0
    private var lastShake = 0L

    fun start(c: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { start(c) }
            return
        }
        if (sm != null || (!Prefs.callFlip && !Prefs.callShake)) return
        val m = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val a = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        m.registerListener(this, a, SensorManager.SENSOR_DELAY_UI)
        sm = m
    }

    fun stop() { android.os.Handler(android.os.Looper.getMainLooper()).post { sm?.unregisterListener(this); sm = null } }

    override fun onSensorChanged(e: SensorEvent) {
        val u = Calls.ui.value ?: return
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        if (Prefs.callFlip && u.incomingRinging && z < -8.5f) Calls.silence()
        if (Prefs.callShake && u.connectedAt > 0 && !u.ended) {
            val g = sqrt(x * x + y * y + z * z)
            val now = SystemClock.elapsedRealtime()
            if (g > 25f) {
                if (now - lastShake > 1500) shakes = 0
                if (now - lastShake > 200) { shakes++; lastShake = now }
                if (shakes >= 3) { shakes = 0; Calls.hangUp() }
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) { }
}

/** Headset button: press to answer or mute, long press to hang up. */
object CallButtons {
    private var session: MediaSession? = null
    private var downAt = 0L

    fun start(c: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { start(c) }
            return
        }
        if (!Prefs.callHeadsetButton || session != null) return
        try {
            val s = MediaSession(c, "riv-call")
            s.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(i: Intent): Boolean {
                    val ev = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    ev ?: return false
                    if (ev.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && ev.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
                    if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) downAt = SystemClock.elapsedRealtime()
                    if (ev.action == KeyEvent.ACTION_UP) {
                        val long = SystemClock.elapsedRealtime() - downAt > 700
                        val u = Calls.ui.value ?: return true
                        when {
                            long -> Calls.hangUp()
                            u.incomingRinging -> Calls.acceptFromBackground(c)
                            else -> Calls.toggleMute()
                        }
                    }
                    return true
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).setActions(PlaybackState.ACTION_PLAY_PAUSE).build())
            s.isActive = true
            session = s
        } catch (_: Throwable) { }
    }

    fun stop() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { session?.release() } catch (_: Throwable) { }
            session = null
        }
    }
}

/** Missed-call notification and "call back later" reminders. */
object CallReminders {
    const val CH = "calls_missed"
    private const val ACTION_REMIND = "com.rivers.rivpost.CALL_REMIND"
    private const val ACTION_FIRE = "com.rivers.rivpost.CALL_REMIND_FIRE"

    private fun channel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            android.app.NotificationChannel(CH, t("Missed calls and reminders"), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun callIntent(c: Context, acc: Int, userId: Long, code: Int) = PendingIntent.getActivity(
        c, code,
        Intent(c, MainActivity::class.java).putExtra("call_user", userId).putExtra("acc", acc)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun remindIntent(c: Context, acc: Int, userId: Long, minutes: Int) = PendingIntent.getBroadcast(
        c, (userId % 100000).toInt() * 10 + minutes / 10,
        Intent(c, CallReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra("acc", acc).putExtra("user", userId).putExtra("min", minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun missed(c: Context, acc: Int, userId: Long) {
        if (!Prefs.callMissedNotif) return
        channel(c)
        val name = Tg.get(acc).userName(userId)
        val id = 7800 + (userId % 1000).toInt()
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t("Missed call"))
            .setContentText(name)
            .setAutoCancel(true)
            .setContentIntent(callIntent(c, acc, userId, id))
            .addAction(0, t("Call back"), callIntent(c, acc, userId, id + 1))
            .addAction(0, t("In 10 min"), remindIntent(c, acc, userId, 10))
            .addAction(0, t("In 1 hour"), remindIntent(c, acc, userId, 60))
            .build()
        try { c.getSystemService(NotificationManager::class.java).notify(id, n) } catch (_: Throwable) { }
    }

    fun schedule(c: Context, acc: Int, userId: Long, minutes: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, (userId % 100000).toInt(),
            Intent(c, CallReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra("acc", acc).putExtra("user", userId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi)
        com.rivers.rivpost.ui.toast(c, t("I'll remind you") + " · " + minutes + " " + t("min"))
    }

    fun fire(c: Context, acc: Int, userId: Long) {
        channel(c)
        val id = 7900 + (userId % 1000).toInt()
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t("Call back"))
            .setContentText(Tg.get(acc).userName(userId))
            .setAutoCancel(true)
            .setContentIntent(callIntent(c, acc, userId, id))
            .build()
        try { c.getSystemService(NotificationManager::class.java).notify(id, n) } catch (_: Throwable) { }
    }

    fun onReceive(c: Context, i: Intent) {
        val acc = i.getIntExtra("acc", 0); val user = i.getLongExtra("user", 0)
        when (i.action) {
            ACTION_REMIND -> {
                schedule(c, acc, user, i.getIntExtra("min", 10))
                c.getSystemService(NotificationManager::class.java).cancel(7800 + (user % 1000).toInt())
            }
            ACTION_FIRE -> fire(c, acc, user)
        }
    }
}

class CallReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Tg.accounts.value.isEmpty()) try { Tg.startAll() } catch (_: Throwable) { }
        CallReminders.onReceive(context, intent)
    }
}

// ---------------------------------------------------------------- phone system (car / Bluetooth)

object CallTelecom {
    var connection: RivConnection? = null

    private fun handle(c: Context) = PhoneAccountHandle(ComponentName(c, RivConnectionService::class.java), "rivpost")

    fun register(c: Context): Boolean {
        if (!Prefs.callTelecom) return false
        return try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.registerPhoneAccount(
                PhoneAccount.builder(handle(c), "Riv Post")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build(),
            )
            true
        } catch (_: Throwable) { false }
    }

    fun outgoing(c: Context, name: String) {
        if (!register(c)) return
        try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(c))
                putString("name", name)
            }
            tm.placeCall(Uri.fromParts("tel", name, null), extras)
        } catch (_: Throwable) { }
    }

    fun incoming(c: Context, name: String) {
        if (!register(c)) return
        try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.addNewIncomingCall(handle(c), Bundle().apply { putString("name", name) })
        } catch (_: Throwable) { }
    }

    fun active() { try { connection?.setActive() } catch (_: Throwable) { } }

    /** Routes the call to the speaker or earpiece through the phone system. Returns false if not linked. */
    @Suppress("DEPRECATION")
    fun speaker(on: Boolean): Boolean {
        val cn = connection ?: return false
        return try {
            val headset = cn.callAudioState?.supportedRouteMask?.let { m ->
                when {
                    m and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0 -> android.telecom.CallAudioState.ROUTE_BLUETOOTH
                    m and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0 -> android.telecom.CallAudioState.ROUTE_WIRED_HEADSET
                    else -> 0
                }
            } ?: 0
            cn.setAudioRoute(
                if (on) android.telecom.CallAudioState.ROUTE_SPEAKER
                else if (headset != 0) headset else android.telecom.CallAudioState.ROUTE_EARPIECE,
            )
            true
        } catch (_: Throwable) { false }
    }

    fun ended() {
        val cn = connection ?: return
        connection = null
        try { cn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); cn.destroy() } catch (_: Throwable) { }
    }
}

class RivConnection(private val name: String) : Connection() {
    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
        setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
        audioModeIsVoip = true
    }
    override fun onAnswer() { Calls.acceptFromBackground(com.rivers.rivpost.tg.Tg.app) }
    override fun onAnswer(videoState: Int) { onAnswer() }
    override fun onSilence() { Calls.silence() }
    override fun onReject() { Calls.hangUp() }
    override fun onDisconnect() { Calls.hangUp() }
    override fun onAbort() { Calls.hangUp() }
    override fun onHold() { Calls.setHold(true); setOnHold() }
    override fun onUnhold() { Calls.setHold(false); setActive() }
    override fun onShowIncomingCallUi() { }
}

class RivConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(h: PhoneAccountHandle?, r: ConnectionRequest?): Connection {
        val cn = RivConnection(r?.extras?.getString("name") ?: "Riv Post")
        cn.setDialing()
        CallTelecom.connection = cn
        return cn
    }

    override fun onCreateIncomingConnection(h: PhoneAccountHandle?, r: ConnectionRequest?): Connection {
        val cn = RivConnection(r?.extras?.getString("name") ?: "Riv Post")
        cn.setRinging()
        CallTelecom.connection = cn
        return cn
    }

    // The phone system refused (for example during a normal phone call). Riv Post keeps handling the call itself.
    override fun onCreateIncomingConnectionFailed(h: PhoneAccountHandle?, r: ConnectionRequest?) { CallTelecom.connection = null }
    override fun onCreateOutgoingConnectionFailed(h: PhoneAccountHandle?, r: ConnectionRequest?) { CallTelecom.connection = null }
}
