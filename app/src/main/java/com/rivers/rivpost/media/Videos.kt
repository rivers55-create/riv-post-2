package com.rivers.rivpost.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideo
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatWebp
import kotlinx.coroutines.runBlocking
import kotlin.math.min

/** A video that lives in a Telegram message. */
data class VideoItem(
    val acc: Int,
    val chatId: Long,
    val msgId: Long,
    val date: Int,
    val file: File,
    val title: String,
    val duration: Int,
    val width: Int,
    val height: Int,
    val thumb: File?,
    val mini: ByteArray?,
    val mime: String,
) {
    val key: String get() = "$chatId:$msgId"

    companion object {
        private val videoExt = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "ts", "3gp")

        fun from(accIndex: Int, m: Message): VideoItem? = when (val c = m.content) {
            is MessageVideo -> {
                val v = c.video
                val th = v.thumbnail?.takeIf { it.format is ThumbnailFormatJpeg || it.format is ThumbnailFormatWebp }?.file
                VideoItem(
                    accIndex, m.chatId, m.id, m.date, v.video,
                    c.caption.text.lineSequence().firstOrNull()?.take(80)?.ifBlank { null }
                        ?: v.fileName.substringBeforeLast('.').ifBlank { "Video" },
                    v.duration, v.width, v.height, th, v.minithumbnail?.data, v.mimeType,
                )
            }
            is MessageDocument -> {
                val d = c.document
                val ext = d.fileName.substringAfterLast('.', "").lowercase()
                if (d.mimeType.startsWith("video/") || ext in videoExt) {
                    val th = d.thumbnail?.takeIf { it.format is ThumbnailFormatJpeg || it.format is ThumbnailFormatWebp }?.file
                    VideoItem(
                        accIndex, m.chatId, m.id, m.date, d.document,
                        d.fileName.substringBeforeLast('.').ifBlank { c.caption.text.take(80).ifBlank { "Video" } },
                        0, 0, 0, th, d.minithumbnail?.data, d.mimeType.ifBlank { "video/mp4" },
                    )
                } else null
            }
            is MessageAnimation -> null
            else -> null
        }
    }
}

/** The Netflix-style library: My List, Continue watching, and one row per chosen chat. */
object VideoLib {
    var mine by mutableStateOf(listOf<VideoItem>())
        private set
    var rows by mutableStateOf(listOf<Pair<Long, List<VideoItem>>>())
        private set
    var loading by mutableStateOf(false)
        private set
    var version by mutableStateOf(0)
        private set

    private fun sp(c: Context) = c.getSharedPreferences("riv_video", Context.MODE_PRIVATE)

    fun myKeys(): List<String> = Prefs.videoList.split("\n").filter { it.contains(":") }

    fun inMyList(v: VideoItem): Boolean = v.key in myKeys()

    fun toggleMyList(v: VideoItem) {
        val l = myKeys().toMutableList()
        if (!l.remove(v.key)) l.add(0, v.key)
        Prefs.videoList = l.joinToString("\n")
        mine = if (v.key in l) listOf(v) + mine.filter { it.key != v.key } else mine.filter { it.key != v.key }
    }

    fun isSource(chatId: Long): Boolean = chatId in Prefs.idSet(Prefs.videoSources)

    fun toggleSource(chatId: Long) {
        val s = Prefs.idSet(Prefs.videoSources).toMutableSet()
        if (!s.remove(chatId)) s.add(chatId)
        Prefs.videoSources = s.joinToString(",")
    }

    // ---- watch progress ----
    fun position(c: Context, key: String): Long = sp(c).getString(key, null)?.split(",")?.getOrNull(0)?.toLongOrNull() ?: 0L

    fun progress(c: Context, key: String): Float {
        val p = sp(c).getString(key, null)?.split(",") ?: return 0f
        val pos = p.getOrNull(0)?.toLongOrNull() ?: 0L
        val dur = p.getOrNull(1)?.toLongOrNull() ?: 0L
        return if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    }

    fun savePosition(c: Context, key: String, pos: Long, dur: Long) {
        sp(c).edit().putString(key, "$pos,$dur,${System.currentTimeMillis()}").apply()
        version++
    }

    fun continueWatching(c: Context): List<VideoItem> {
        val all = (mine + rows.flatMap { it.second }).distinctBy { it.key }
        return all.filter { val p = progress(c, it.key); p > 0.02f && p < 0.95f }
            .sortedByDescending { sp(c).getString(it.key, null)?.split(",")?.getOrNull(2)?.toLongOrNull() ?: 0L }
    }

    // ---- loading ----
    suspend fun load(a: TgAccount) {
        if (loading) return
        loading = true
        try {
            val my = ArrayList<VideoItem>()
            for (k in myKeys()) {
                val chat = k.substringBefore(":").toLongOrNull() ?: continue
                val msg = k.substringAfter(":").toLongOrNull() ?: continue
                val m = (a.client.getMessage(chatId = chat, messageId = msg) as? TdlResult.Success)?.result ?: continue
                VideoItem.from(a.index, m)?.let { my.add(it) }
            }
            mine = my
            val out = ArrayList<Pair<Long, List<VideoItem>>>()
            for (chatId in Prefs.idSet(Prefs.videoSources)) {
                val list = ArrayList<VideoItem>()
                for (filter in listOf(SearchMessagesFilterVideo(), SearchMessagesFilterDocument())) {
                    var from = 0L
                    for (round in 0 until 3) {
                        val r = a.client.searchChatMessages(chatId = chatId, query = "", fromMessageId = from, offset = 0, limit = 100, filter = filter)
                        val found = (r as? TdlResult.Success)?.result ?: break
                        if (found.messages.isEmpty()) break
                        for (m in found.messages) VideoItem.from(a.index, m)?.let { list.add(it) }
                        from = found.nextFromMessageId
                        if (from == 0L) break
                    }
                }
                if (list.isNotEmpty()) out.add(chatId to list.distinctBy { it.key }.sortedByDescending { it.date })
            }
            rows = out
        } finally {
            loading = false
        }
    }
}

/**
 * Streams a Telegram file to the video player: downloads only the part being watched,
 * so playback starts right away instead of waiting for the whole file.
 */
class TdlDataSource(private val acc: TgAccount, private val file: File) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var pos = 0L
    private var remaining = 0L
    private var readyEnd = 0L
    private var readyStart = 0L
    private val total: Long = if (file.size > 0) file.size else file.expectedSize

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        pos = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
        else if (total > 0) total - pos else C.LENGTH_UNSET.toLong()
        readyEnd = pos
        readyStart = pos
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        var want = length.toLong()
        if (remaining > 0) want = min(want, remaining)
        want = min(want, 512 * 1024L)
        val bytes: ByteArray = runBlocking {
            if (pos < readyStart || pos + want > readyEnd) {
                val chunk = 1024 * 1024L
                acc.client.downloadFile(fileId = file.id, priority = 32, offset = pos, limit = chunk, synchronous = true)
                readyStart = pos
                readyEnd = pos + chunk
            }
            (acc.client.readFilePart(fileId = file.id, offset = pos, count = want) as? TdlResult.Success)?.result?.data ?: ByteArray(0)
        }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        System.arraycopy(bytes, 0, buffer, offset, bytes.size)
        pos += bytes.size
        if (remaining > 0) remaining -= bytes.size
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (uri != null) {
            uri = null
            transferEnded()
        }
    }
}

fun accountOf(index: Int): TgAccount? = Tg.accounts.value[index]
