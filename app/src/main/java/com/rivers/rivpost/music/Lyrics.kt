package com.rivers.rivpost.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val time: Long, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String)

/** Synced lyrics from lrclib.net (free), saved in the cache. Same method as RTP. */
object LyricsRepo {
    private val memory = HashMap<String, Lyrics?>()
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String, source: String): Lyrics? {
        if (text.isBlank()) return null
        val lines = ArrayList<LyricLine>()
        var synced = false
        text.lineSequence().forEach { line ->
            val stamps = stamp.findAll(line).toList()
            if (stamps.isNotEmpty()) {
                synced = true
                val content = line.substring(stamps.last().range.last + 1).trim()
                stamps.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    lines += LyricLine(min * 60_000 + sec * 1000 + ms, content)
                }
            } else if (!line.trim().matches(Regex("^\\[[a-zA-Z]+:.*]$"))) {
                lines += LyricLine(-1, line.trim())
            }
        }
        val result = if (synced) lines.filter { it.time >= 0 }.sortedBy { it.time } else lines
        if (result.isEmpty()) return null
        return Lyrics(synced, result, source)
    }

    private fun file(c: Context, key: String) =
        java.io.File(java.io.File(c.cacheDir, "lyrics").apply { mkdirs() }, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".lrc")

    suspend fun load(c: Context, t: Track, online: Boolean): Lyrics? = withContext(Dispatchers.IO) {
        if (memory.containsKey(t.key)) return@withContext memory[t.key]
        val f = file(c, t.key)
        val found = if (f.exists()) parse(f.readText(), "lrclib") else if (online) fetch(t)?.also { f.writeText(it.second) }?.first else null
        memory[t.key] = found
        found
    }

    private fun get(url: String): String? = try {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 8000
        con.readTimeout = 8000
        con.setRequestProperty("User-Agent", "RivPost/1.0 (Android)")
        try { if (con.responseCode == 200) con.inputStream.bufferedReader().readText() else null } finally { con.disconnect() }
    } catch (_: Throwable) { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(t: Track): Pair<Lyrics, String>? {
        if (t.artist == "Unknown artist") return null
        val base = "https://lrclib.net/api"
        val exact = if (t.duration > 0) get("$base/get?track_name=${enc(t.title)}&artist_name=${enc(t.artist)}&duration=${t.duration}") else null
        val obj = exact?.let { try { JSONObject(it) } catch (_: Throwable) { null } } ?: run {
            val arr = get("$base/search?track_name=${enc(t.title)}&artist_name=${enc(t.artist)}")?.let { try { JSONArray(it) } catch (_: Throwable) { null } }
            if (arr == null || arr.length() == 0) null else {
                var best: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (best == null || (!o.isNull("syncedLyrics") && best.isNull("syncedLyrics"))) best = o
                }
                best
            }
        } ?: return null
        val synced = if (!obj.isNull("syncedLyrics")) obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() } else null
        val plain = if (!obj.isNull("plainLyrics")) obj.optString("plainLyrics", "").takeIf { it.isNotBlank() } else null
        val text = synced ?: plain ?: return null
        val l = parse(text, "lrclib") ?: return null
        return l to text
    }
}
