package com.rivers.rivpost.music

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A song that lives in a Telegram message. */
data class Track(
    val acc: Int,
    val chatId: Long,
    val msgId: Long,
    val date: Int,
    val file: File,
    val title: String,
    val artist: String,
    val duration: Int,
    val mime: String,
    val fileName: String,
    val cover: File?,
) {
    val key: String get() = file.remote.uniqueId.ifBlank { "$chatId:$msgId" }
    val ext: String get() = fileName.substringAfterLast('.', "").uppercase().ifBlank {
        when {
            mime.contains("flac") -> "FLAC"; mime.contains("mpeg") -> "MP3"; mime.contains("ogg") -> "OGG"
            mime.contains("mp4") || mime.contains("aac") -> "M4A"; mime.contains("wav") -> "WAV"; else -> "AUDIO"
        }
    }

    companion object {
        private val audioExt = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "ape", "aiff", "dsf")

        fun from(accIndex: Int, m: Message): Track? = when (val c = m.content) {
            is MessageAudio -> {
                val a = c.audio
                Track(
                    accIndex, m.chatId, m.id, m.date, a.audio,
                    a.title.ifBlank { a.fileName.substringBeforeLast('.').ifBlank { "Unknown title" } },
                    a.performer.ifBlank { "Unknown artist" }, a.duration, a.mimeType, a.fileName,
                    a.albumCoverThumbnail?.file ?: a.externalAlbumCovers.firstOrNull()?.file,
                )
            }
            is MessageDocument -> {
                val d = c.document
                val ext = d.fileName.substringAfterLast('.', "").lowercase()
                if (d.mimeType.startsWith("audio/") || ext in audioExt) {
                    val base = d.fileName.substringBeforeLast('.')
                    val parts = base.split(" - ", limit = 2)
                    Track(
                        accIndex, m.chatId, m.id, m.date, d.document,
                        if (parts.size == 2) parts[1].trim() else base.ifBlank { "Unknown title" },
                        if (parts.size == 2) parts[0].trim() else "Unknown artist",
                        0, d.mimeType, d.fileName, d.thumbnail?.file,
                    )
                } else null
            }
            else -> null
        }
    }
}

/** The music player: its own queue, shuffle, repeat, sleep timer, speed and equalizer. */
object Music {
    lateinit var app: Context
        private set
    var exo: ExoPlayer? = null
        private set
    private val scope = MainScope()

    var queue by mutableStateOf(listOf<Track>())
        private set
    var index by mutableIntStateOf(-1)
        private set
    val current: Track? get() = queue.getOrNull(index)
    var isPlaying by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(0) // 0 off, 1 all, 2 one
        private set
    var sleepEnd by mutableLongStateOf(0L)
        private set
    var sleepAfterTrack by mutableStateOf(false)
    var format by mutableStateOf("")
        private set
    var outputKind by mutableStateOf("speaker")
        private set
    var outputName by mutableStateOf("")
        private set
    var playerOpen by mutableStateOf(false)
    var page by mutableIntStateOf(0) // 0 playing, 1 lyrics, 2 queue
    var error by mutableStateOf<String?>(null)

    private var order: List<Int> = emptyList()
    private var sleepJob: Job? = null
    private var playJob: Job? = null
    var equalizer: Equalizer? = null
        private set
    var bassBoost: BassBoost? = null
        private set

    val position: Long get() = exo?.currentPosition ?: 0L
    val duration: Long get() = exo?.duration?.takeIf { it > 0 } ?: ((current?.duration ?: 0) * 1000L)

    fun init(c: Context) {
        if (exo != null) return
        app = c.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onTracksChanged(tracks: Tracks) { format = describe(tracks) }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                // "repeat all" keeps the player looping one item; we move to the next song ourselves
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && repeat == 1) scope.launch {
                    if (sleepAfterTrack) { sleepAfterTrack = false; exo?.pause() } else next()
                }
            }
        })
        exo = p
        applySpeed()
        setupEffects(p.audioSessionId)
        updateOutput()
    }

    // ---------- queue ----------

    fun play(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        init(Tg.app)
        queue = tracks
        rebuildOrder(start)
        playIndex(start.coerceIn(0, tracks.lastIndex))
    }

    fun playIndex(i: Int) {
        val t = queue.getOrNull(i) ?: return
        index = i
        error = null
        abClear()
        playJob?.cancel()
        playJob = scope.launch {
            loading = true
            val path = ensureLocal(t)
            loading = false
            if (path == null) { error = "Couldn't download this song"; return@launch }
            val coverPath = t.cover?.let { ensureLocal(t.acc, it) }
            val md = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
                .apply { if (coverPath != null) setArtworkUri(Uri.fromFile(java.io.File(coverPath))) }
                .build()
            val item = MediaItem.Builder().setMediaId(t.key).setUri(Uri.fromFile(java.io.File(path))).setMediaMetadata(md).build()
            val p = exo ?: return@launch
            p.setMediaItem(item)
            p.prepare()
            p.play()
            startService()
            prefetchNext()
        }
    }

    private suspend fun ensureLocal(t: Track): String? = ensureLocal(t.acc, t.file)

    private suspend fun ensureLocal(accIndex: Int, f: File): String? {
        val a: TgAccount = Tg.accounts.value[accIndex] ?: return null
        if (f.local.isDownloadingCompleted && f.local.path.isNotEmpty() && java.io.File(f.local.path).exists()) return f.local.path
        val r = a.client.downloadFile(fileId = f.id, priority = 32, offset = 0, limit = 0, synchronous = true)
        val res = (r as? TdlResult.Success)?.result ?: return null
        return res.local.path.takeIf { res.local.isDownloadingCompleted && it.isNotEmpty() }
    }

    private fun onWifi(): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && !cm.isActiveNetworkMetered && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (_: Throwable) { false }

    private fun prefetchNext() {
        if (Prefs.prefetchWifiOnly && !onWifi()) return
        val n = nextIndex(false) ?: return
        val t = queue.getOrNull(n) ?: return
        val a = Tg.accounts.value[t.acc] ?: return
        a.download(t.file, 4)
    }

    private fun rebuildOrder(start: Int) {
        order = if (shuffle) (listOf(start) + queue.indices.filter { it != start }.shuffled()) else queue.indices.toList()
    }

    private fun nextIndex(wrap: Boolean): Int? {
        if (queue.isEmpty()) return null
        val pos = order.indexOf(index)
        val n = pos + 1
        return when {
            n < order.size -> order[n]
            wrap || repeat == 1 -> order.firstOrNull()
            else -> null
        }
    }

    fun next() { nextIndex(true)?.let { playIndex(it) } }

    fun previous() {
        if (position > 3000) { exo?.seekTo(0); return }
        val pos = order.indexOf(index)
        val p = if (pos > 0) order[pos - 1] else order.lastOrNull() ?: return
        playIndex(p)
    }

    private fun onEnded() {
        if (sleepAfterTrack) { sleepAfterTrack = false; exo?.pause(); return }
        if (repeat == 2) { exo?.seekTo(0); exo?.play(); return }
        val n = nextIndex(false)
        if (n != null) playIndex(n)
    }

    fun toggle() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    fun seekTo(ms: Long) { exo?.seekTo(ms.coerceAtLeast(0)) }
    fun seekBy(ms: Long) { seekTo(position + ms) }

    /** Flip the queue order, keeping the current song playing. */
    fun reverseQueue() {
        if (queue.size < 2) return
        val cur = queue.getOrNull(index)
        queue = queue.reversed()
        index = if (cur != null) queue.indexOf(cur).coerceAtLeast(0) else 0
        rebuildOrder(index)
    }

    fun toggleShuffle() = applyShuffle(!shuffle)

    fun applyShuffle(on: Boolean) {
        shuffle = on
        rebuildOrder(index.coerceAtLeast(0))
        exo?.shuffleModeEnabled = on   // so the phone's media panel shows it
    }

    fun cycleRepeat() = applyRepeat((repeat + 1) % 3)

    /** 0 off, 1 all, 2 one. The phone's media panel reads it from the player. */
    fun applyRepeat(r: Int) {
        repeat = r
        exo?.repeatMode = when (r) { 1 -> Player.REPEAT_MODE_ALL; 2 -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }

    fun jumpTo(i: Int) { playIndex(i) }

    fun removeAt(i: Int) {
        if (i == index || i !in queue.indices) return
        val cur = current
        queue = queue.toMutableList().also { it.removeAt(i) }
        index = queue.indexOf(cur)
        rebuildOrder(index)
    }

    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val cur = current
        val l = queue.toMutableList()
        val x = l.removeAt(from)
        l.add(to, x)
        queue = l
        index = queue.indexOf(cur)
        rebuildOrder(index)
    }

    fun playNext(t: Track) {
        if (queue.isEmpty()) { play(listOf(t), 0); return }
        val l = queue.toMutableList()
        l.add(index + 1, t)
        val cur = current
        queue = l
        index = queue.indexOf(cur)
        rebuildOrder(index)
    }

    fun addToQueue(t: Track) {
        if (queue.isEmpty()) { play(listOf(t), 0); return }
        queue = queue + t
        rebuildOrder(index)
    }

    fun stop() {
        exo?.stop()
        queue = emptyList()
        index = -1
        playerOpen = false
        cancelSleep()
    }

    // ---------- A-B repeat ----------
    var abA by mutableLongStateOf(-1L)
        private set
    var abB by mutableLongStateOf(-1L)
        private set
    private var abJob: Job? = null

    /** Tap once = set A, twice = set B (loop starts), third time = off. */
    fun abTap() {
        when {
            abA < 0 -> { abA = position }
            abB < 0 -> {
                if (position <= abA + 500) return
                abB = position
                abJob?.cancel()
                abJob = scope.launch {
                    while (isActive && abB > 0) {
                        if ((exo?.currentPosition ?: 0) >= abB) exo?.seekTo(abA)
                        delay(120)
                    }
                }
            }
            else -> abClear()
        }
    }

    fun abClear() { abA = -1; abB = -1; abJob?.cancel(); abJob = null }

    // ---------- hidden songs ----------
    fun isHidden(t: Track): Boolean = t.key in hiddenSet()
    fun hiddenSet(): Set<String> = Prefs.hiddenSongs.split("\n").filter { it.isNotBlank() }.toSet()
    fun hide(keys: Collection<String>) { Prefs.hiddenSongs = (hiddenSet() + keys).joinToString("\n") }
    fun unhide(key: String) { Prefs.hiddenSongs = (hiddenSet() - key).joinToString("\n") }
    fun hideChat(chatId: Long) { Prefs.hiddenChats = Prefs.toggleId(Prefs.hiddenChats, chatId) }
    fun isChatHidden(chatId: Long): Boolean = chatId in Prefs.idSet(Prefs.hiddenChats)
    fun visible(t: Track): Boolean =
        !isHidden(t) && !isChatHidden(t.chatId) && !(Prefs.hideShort && t.duration in 1 until Prefs.minSongSecs)

    // ---------- playlists ----------
    fun playlists(): List<Pair<String, List<String>>> = Prefs.playlists.split("\n").filter { it.contains('|') }.map { l ->
        l.substringBefore('|') to l.substringAfter('|').split(',').filter { it.isNotBlank() }
    }
    private fun savePlaylists(list: List<Pair<String, List<String>>>) {
        Prefs.playlists = list.joinToString("\n") { (n, k) -> n.replace("|", " ").replace("\n", " ") + "|" + k.joinToString(",") }
    }
    fun newPlaylist(name: String) { if (name.isNotBlank() && playlists().none { it.first == name }) savePlaylists(playlists() + (name.trim() to emptyList())) }
    fun deletePlaylist(name: String) = savePlaylists(playlists().filter { it.first != name })
    fun addToPlaylist(name: String, keys: Collection<String>) =
        savePlaylists(playlists().map { if (it.first == name) it.first to (it.second + keys).distinct() else it })
    fun removeFromPlaylist(name: String, key: String) =
        savePlaylists(playlists().map { if (it.first == name) it.first to (it.second - key) else it })

    // ---------- favorites ----------

    fun isFavorite(t: Track): Boolean = Prefs.favorites.split("\n").contains(t.key)
    fun toggleFavorite(t: Track) {
        val set = Prefs.favorites.split("\n").filter { it.isNotBlank() }.toMutableList()
        if (!set.remove(t.key)) set.add(t.key)
        Prefs.favorites = set.joinToString("\n")
    }

    // ---------- sleep, speed, effects ----------

    fun sleepIn(minutes: Int) {
        cancelSleep()
        sleepEnd = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            while (isActive && System.currentTimeMillis() < sleepEnd) delay(1000)
            if (isActive) { exo?.pause(); sleepEnd = 0L }
        }
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEnd = 0L
        sleepAfterTrack = false
    }

    fun applySpeed() {
        exo?.playbackParameters = PlaybackParameters(Prefs.mSpeed.coerceIn(0.25f, 3f), Prefs.mPitch.coerceIn(0.5f, 2f))
    }

    private fun setupEffects(session: Int) {
        try {
            equalizer = Equalizer(0, session).apply { enabled = Prefs.eqOn }
            val levels = Prefs.eqLevels.split(",").mapNotNull { it.toShortOrNull() }
            val eq = equalizer
            if (eq != null && levels.size == eq.numberOfBands.toInt()) {
                levels.forEachIndexed { i, l -> eq.setBandLevel(i.toShort(), l) }
            }
        } catch (_: Throwable) { equalizer = null }
        try {
            bassBoost = BassBoost(0, session).apply {
                enabled = Prefs.eqOn && Prefs.bass > 0
                if (strengthSupported) setStrength(Prefs.bass.toShort())
            }
        } catch (_: Throwable) { bassBoost = null }
    }

    fun setEq(on: Boolean) {
        Prefs.eqOn = on
        try { equalizer?.enabled = on } catch (_: Throwable) { }
        try { bassBoost?.enabled = on && Prefs.bass > 0 } catch (_: Throwable) { }
    }

    fun setBand(band: Int, level: Short) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(band.toShort(), level)
            Prefs.eqLevels = (0 until eq.numberOfBands).joinToString(",") { eq.getBandLevel(it.toShort()).toString() }
        } catch (_: Throwable) { }
    }

    fun usePreset(p: Int) {
        val eq = equalizer ?: return
        try {
            eq.usePreset(p.toShort())
            Prefs.eqLevels = (0 until eq.numberOfBands).joinToString(",") { eq.getBandLevel(it.toShort()).toString() }
        } catch (_: Throwable) { }
    }

    fun setBass(v: Int) {
        Prefs.bass = v
        try {
            bassBoost?.let { b ->
                b.enabled = Prefs.eqOn && v > 0
                if (b.strengthSupported) b.setStrength(v.toShort())
            }
        } catch (_: Throwable) { }
    }

    // ---------- output and format ----------

    fun updateOutput() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outs = try { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() } catch (_: Throwable) { emptyList() }
        val bt = mutableSetOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= 31) { bt += AudioDeviceInfo.TYPE_BLE_HEADSET; bt += AudioDeviceInfo.TYPE_BLE_SPEAKER }
        val wired = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
        val b = outs.firstOrNull { it.type in bt }
        val w = outs.firstOrNull { it.type in wired }
        when {
            b != null -> { outputKind = "bt"; outputName = b.productName?.toString().orEmpty() }
            w != null -> { outputKind = "wired"; outputName = "" }
            else -> { outputKind = "speaker"; outputName = "" }
        }
    }

    fun openOutputSwitcher(c: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            val ok = try { android.media.MediaRouter2.getInstance(c).showSystemOutputSwitcher() } catch (_: Throwable) { false }
            if (ok) return
        }
        try {
            c.startActivity(
                Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                    .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", c.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            try { c.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
        }
    }

    private fun describe(tracks: Tracks): String {
        val t = current ?: return ""
        var codec = t.ext
        var rate = 0
        var bits = 0
        var kbps = 0
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO || g.length == 0) continue
            val f = g.getTrackFormat(0)
            rate = f.sampleRate
            bits = when (f.pcmEncoding) {
                C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_24BIT -> 24; C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32; else -> 0
            }
            if (f.bitrate > 0) kbps = f.bitrate / 1000
            val m = f.sampleMimeType ?: ""
            codec = when {
                m.contains("flac") -> "FLAC"; m.contains("mpeg") -> "MP3"; m.contains("opus") -> "OPUS"
                m.contains("vorbis") -> "OGG"; m.contains("mp4a") -> "AAC"; m.contains("alac") -> "ALAC"
                m.contains("raw") -> "WAV"; else -> codec
            }
        }
        if (kbps == 0 && t.duration > 0 && t.file.size > 0 && codec != "FLAC" && codec != "WAV") kbps = (t.file.size * 8 / 1000 / t.duration).toInt()
        val parts = mutableListOf(codec)
        if (bits > 0) parts += "$bits-bit"
        if (kbps > 0 && bits == 0) parts += "$kbps kbps"
        if (rate > 0) parts += (if (rate % 1000 == 0) "${rate / 1000}" else "%.1f".format(rate / 1000f)) + " kHz"
        return parts.joinToString(" · ")
    }

    private fun startService() {
        try { app.startService(Intent(app, MusicService::class.java)) } catch (_: Throwable) { }
    }

    // ---------- loading songs from Telegram ----------

    /** All songs in one chat (oldest first). */
    suspend fun chatTracks(a: TgAccount, chatId: Long): List<Track> {
        val out = ArrayList<Track>()
        var from = 0L
        for (round in 0 until 6) {
            val r = a.client.searchChatMessages(
                chatId = chatId, query = "", fromMessageId = from, offset = 0, limit = 100, filter = SearchMessagesFilterAudio(),
            )
            val found = (r as? TdlResult.Success)?.result ?: break
            if (found.messages.isEmpty()) break
            for (m in found.messages) Track.from(a.index, m)?.let { out.add(it) }
            from = found.nextFromMessageId
            if (from == 0L) break
        }
        return out.distinctBy { it.key }.sortedBy { it.date }
    }

    /** Plays a song from a chat, with the rest of the chat's songs as the queue. */
    fun playFromChat(a: TgAccount, m: Message) {
        val t = Track.from(a.index, m) ?: return
        init(Tg.app)
        play(listOf(t), 0)
        scope.launch {
            val all = chatTracks(a, m.chatId)
            val i = all.indexOfFirst { it.msgId == m.id }
            if (i >= 0 && current?.msgId == m.id) {
                queue = all
                index = i
                rebuildOrder(i)
            }
        }
    }
}
