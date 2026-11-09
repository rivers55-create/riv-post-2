package com.rivers.rivpost.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rivers.rivpost.data.Prefs
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Properties

/** Shared state the activity needs (picture-in-picture etc.). */
object VideoState {
    var playing by mutableStateOf(false)
    var inPip by mutableStateOf(false)
}

// ---------------------------------------------------------------- player builder

object Engine {
    private val softwareFirst = MediaCodecSelector { mime, secure, tunneling ->
        val all: List<MediaCodecInfo> = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
        if (MimeTypes.isVideo(mime)) all.filter { it.softwareOnly } + all.filterNot { it.softwareOnly } else all
    }

    /** Decoder choice: hw = phone chip only, hwplus = chip with fallback, sw = software first. FFmpeg for audio the phone can't play. */
    fun build(ctx: Context, userAgent: String = "", referer: String = ""): ExoPlayer {
        val renderers = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(Prefs.decoder != "hw")
            .setExtensionRendererMode(
                if (Prefs.ffmpeg) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            )
        if (Prefs.decoder == "sw") renderers.setMediaCodecSelector(softwareFirst)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent.ifBlank { "RivPost/1.3" })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(30000)
        if (referer.isNotBlank()) http.setDefaultRequestProperties(mapOf("Referer" to referer))
        val ds = SmbAwareDataSource.Factory(DefaultDataSource.Factory(ctx, http))
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .build()
    }
}

// ---------------------------------------------------------------- subtitles

data class Cue(val start: Long, val end: Long, val text: String)

/** Reads SRT, WebVTT and ASS/SSA files so they can be shifted in time. */
object SubtitleParser {
    fun load(context: Context, uri: Uri): List<Cue> {
        val bytes = if (uri.scheme == "file") java.io.File(uri.path!!).readBytes()
        else context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val text = decode(bytes)
        val name = uri.toString().lowercase()
        return when {
            name.endsWith(".ass") || name.endsWith(".ssa") || text.contains("[Events]") -> parseAss(text)
            else -> parseSrtOrVtt(text)
        }.sortedBy { it.start }
    }

    /** UTF-8 first, then Windows-1256 (common for Arabic .srt files). */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE)
        val utf = String(bytes, Charsets.UTF_8)
        if (!utf.contains('�')) return utf.removePrefix("﻿")
        return runCatching { String(bytes, Charset.forName("windows-1256")) }.getOrDefault(utf)
    }

    private val timeLine = Regex("(\\d{1,2}:)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}:)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})")
    private val tags = Regex("<[^>]+>|\\{\\\\[^}]*\\}")

    private fun ms(h: String, m: String, s: String, f: String): Long {
        val hours = h.trimEnd(':').toLongOrNull() ?: 0L
        val frac = f.padEnd(3, '0').take(3).toLong()
        return ((hours * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + frac
    }

    fun parseSrtOrVtt(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val match = timeLine.find(lines[i])
            if (match == null) { i++; continue }
            val g = match.groupValues
            val start = ms(g[1], g[2], g[3], g[4])
            val end = ms(g[5], g[6], g[7], g[8])
            i++
            val body = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank() && timeLine.find(lines[i]) == null) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i].replace(tags, ""))
                i++
            }
            if (body.isNotBlank()) cues += Cue(start, end, body.toString().trim())
        }
        return cues
    }

    private fun assTime(s: String): Long {
        val p = s.trim().split(':')
        if (p.size != 3) return 0
        val sec = p[2].split('.')
        val cs = sec.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return ((p[0].toLong() * 60 + p[1].toLong()) * 60 + sec[0].toLong()) * 1000 + cs * 10
    }

    fun parseAss(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        var startIdx = 1; var endIdx = 2; var textIdx = 9
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("Format:", true) && line.contains("Text")) {
                val f = line.substringAfter(":").split(',').map { it.trim().lowercase() }
                startIdx = f.indexOf("start").takeIf { it >= 0 } ?: 1
                endIdx = f.indexOf("end").takeIf { it >= 0 } ?: 2
                textIdx = f.indexOf("text").takeIf { it >= 0 } ?: 9
            } else if (line.startsWith("Dialogue:", true)) {
                val parts = line.substringAfter(":").split(',', limit = textIdx + 1)
                if (parts.size > textIdx) {
                    val body = parts[textIdx].replace(tags, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").trim()
                    if (body.isNotEmpty()) cues += Cue(assTime(parts[startIdx]), assTime(parts[endIdx]), body)
                }
            }
        }
        return cues
    }

    fun textAt(cues: List<Cue>, position: Long): String {
        if (cues.isEmpty()) return ""
        var lo = 0; var hi = cues.lastIndex; var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (cues[mid].start <= position) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (idx < 0) return ""
        val sb = StringBuilder()
        for (j in maxOf(0, idx - 5)..idx) {
            val c = cues[j]
            if (position in c.start..c.end) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(c.text) }
        }
        return sb.toString()
    }
}

// ---------------------------------------------------------------- IPTV

data class Channel(val name: String, val url: String, val logo: String, val group: String, val userAgent: String = "", val referer: String = "")

object M3uParser {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun lists(): List<Pair<String, String>> = Prefs.iptvLists.split("\n").filter { it.contains('|') }
        .map { it.substringBefore('|') to it.substringAfter('|') }

    fun saveLists(l: List<Pair<String, String>>) {
        Prefs.iptvLists = l.joinToString("\n") { (n, u) -> n.replace("|", " ") + "|" + u }
    }

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = ""; var logo = ""; var group = ""; var ua = ""; var ref = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"] ?: ""
                    group = attrs["group-title"] ?: ""
                    var inQuotes = false
                    var lastComma = -1
                    line.forEachIndexed { idx, ch ->
                        if (ch == '"') inQuotes = !inQuotes
                        else if (ch == ',' && !inQuotes) lastComma = idx
                    }
                    name = (if (lastComma >= 0) line.substring(lastComma + 1) else "").trim().ifBlank { attrs["tvg-name"] ?: "" }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(":").trim()
                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> ua = line.substringAfter("=")
                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) -> ref = line.substringAfter("=")
                line.startsWith("#") -> Unit
                else -> {
                    out += Channel(name.ifBlank { line.substringAfterLast('/') }, line, logo, group.ifBlank { "Other" }, ua, ref)
                    name = ""; logo = ""; group = ""; ua = ""; ref = ""
                }
            }
        }
        return out
    }

    suspend fun load(context: Context, source: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = if (source.startsWith("http://") || source.startsWith("https://")) {
            val conn = URL(source).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "RivPost/1.3")
            try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
        } else {
            context.contentResolver.openInputStream(Uri.parse(source))!!.bufferedReader().use { it.readText() }
        }
        parse(text)
    }
}

// ---------------------------------------------------------------- SMB (shared folders)

data class SmbServer(val name: String, val host: String, val share: String, val user: String, val pass: String, val domain: String)
data class SmbEntry(val name: String, val url: String, val isDir: Boolean, val size: Long)

object SmbClient {
    private val base: BaseContext by lazy {
        val p = Properties()
        p.setProperty("jcifs.smb.client.minVersion", "SMB202")
        p.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        p.setProperty("jcifs.smb.client.responseTimeout", "15000")
        p.setProperty("jcifs.resolveOrder", "DNS,BCAST")
        BaseContext(PropertyConfiguration(p))
    }

    fun servers(): List<SmbServer> = Prefs.smbServers.split("\n").map { it.split('|') }.filter { it.size >= 3 }.map {
        SmbServer(it[0], it[1], it[2], it.getOrElse(3) { "" }, it.getOrElse(4) { "" }, it.getOrElse(5) { "" })
    }

    fun saveServers(l: List<SmbServer>) {
        Prefs.smbServers = l.joinToString("\n") { s ->
            listOf(s.name, s.host, s.share, s.user, s.pass, s.domain).joinToString("|") { it.replace("|", "").replace("\n", "") }
        }
    }

    private fun serverFor(host: String?): SmbServer? = servers().firstOrNull { it.host.equals(host, true) }

    fun contextFor(host: String?): CIFSContext {
        val s = serverFor(host)
        return if (s == null || s.user.isBlank()) base.withAnonymousCredentials()
        else base.withCredentials(NtlmPasswordAuthenticator(s.domain.ifBlank { null }, s.user, s.pass))
    }

    fun rootUrl(s: SmbServer): String {
        val share = s.share.trim('/')
        return if (share.isEmpty()) "smb://${s.host}/" else "smb://${s.host}/$share/"
    }

    private val mediaExt = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts", "mts", "3gp", "mpg", "mpeg", "vob", "ogv",
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav",
    )

    suspend fun list(url: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(url, contextFor(Uri.parse(url).host))
        dir.listFiles().mapNotNull { f ->
            val name = f.name.trimEnd('/')
            if (name.startsWith(".") || name.endsWith("$")) return@mapNotNull null
            val isDir = f.isDirectory
            if (!isDir && name.substringAfterLast('.', "").lowercase() !in mediaExt) return@mapNotNull null
            SmbEntry(name, f.url.toString(), isDir, if (isDir) 0L else runCatching { f.length() }.getOrDefault(0L))
        }.sortedWith(compareBy<SmbEntry>({ !it.isDir }, { it.name.lowercase() }))
    }
}

/** Reads bytes straight from an SMB share, with seeking. */
class SmbDataSource : BaseDataSource(true) {
    private var raf: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val file = SmbFile(dataSpec.uri.toString(), SmbClient.contextFor(dataSpec.uri.host))
            val total = file.length()
            val r = SmbRandomAccessFile(file, "r")
            r.seek(dataSpec.position)
            raf = r
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else total - dataSpec.position
        } catch (e: Exception) {
            throw IOException(e)
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (remaining < length) remaining.toInt() else length
        val n = try { raf!!.read(buffer, offset, toRead) } catch (e: Exception) { throw IOException(e) }
        if (n < 0) return C.RESULT_END_OF_INPUT
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try { raf?.close() } catch (_: Exception) { }
        raf = null
        if (opened) { opened = false; transferEnded() }
    }
}

class SmbAwareDataSource(private val normal: DataSource) : DataSource {
    private val smb = SmbDataSource()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        normal.addTransferListener(transferListener)
        smb.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val src = if (dataSpec.uri.scheme.equals("smb", true)) smb else normal
        active = src
        return src.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
    override fun close() { active?.close(); active = null }

    class Factory(private val normal: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbAwareDataSource(normal.createDataSource())
    }
}
