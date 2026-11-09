package com.rivers.rivpost.media

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.rivers.rivpost.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Tidy title pieces pulled out of a messy file name or caption. */
data class CleanTitle(val title: String, val year: Int, val season: Int, val episode: Int) {
    val label: String get() = buildString {
        append(title)
        if (season > 0 && episode > 0) append(" · S%02dE%02d".format(season, episode))
        else if (episode > 0) append(" · E$episode")
        if (year > 0 && season == 0) append(" ($year)")
    }
}

/** Info from TMDB (optional, with the user's own key). */
data class OnlineInfo(val title: String, val year: String, val overview: String, val rating: Double, val poster: String, val backdrop: String)

/**
 * Cleans up video names and descriptions, only for showing them in Riv Post.
 * Nothing is changed in Telegram.
 */
object Meta {
    private val junkWords = Regex(
        "(?i)\\b(2160p|1440p|1080p|720p|576p|480p|360p|4k|uhd|fhd|hd|sd|x264|x265|h\\.?264|h\\.?265|hevc|avc|10bit|8bit|hdr10?\\+?|dv|" +
            "web-?dl|web-?rip|webrip|bluray|blu-ray|brrip|bdrip|dvdrip|hdrip|hdtv|hdcam|cam|ts|remux|proper|repack|extended|unrated|" +
            "aac\\d?(\\.\\d)?|ac3|eac3|ddp?\\d?(\\.\\d)?|dts(-hd)?|atmos|truehd|mp4|mkv|avi|nf|amzn|dsnp|hmax|atvp|yts|rarbg|psa|pahe|" +
            "subbed|dubbed|multi|dual|eng|arabic|arab|mkvcage|esub|hc)\\b",
    )
    private val arJunk = Regex("(مترجم(ة)?|مدبلج(ة)?|حصري(ا|اً)?|جودة\\s*عالية|بجودة\\s*\\S+|اونلاين|للتحميل|تحميل|مشاهدة|كامل(ة)?|نسخة\\s*\\S+|HD)")
    private val links = Regex("(https?://\\S+|t\\.me/\\S+|www\\.\\S+|@\\w+|#\\S+)")
    private val emoji = Regex("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")
    private val sxe = Regex("(?i)\\bS(\\d{1,2})\\s*[ .-]?E(\\d{1,3})\\b")
    private val xFormat = Regex("\\b(\\d{1,2})x(\\d{2,3})\\b")
    private val seasonWord = Regex("(?i)(season|الموسم)\\s*(\\d{1,2})")
    private val episodeWord = Regex("(?i)(episode|ep|الحلقة|حلقة)\\s*(\\d{1,3})")
    private val year = Regex("\\b(19[3-9]\\d|20[0-4]\\d)\\b")

    fun clean(raw: String): CleanTitle {
        var s = raw.substringBeforeLast('.').let { if (it.length < raw.length - 6) raw else it }
        s = s.replace(links, " ").replace(emoji, " ")
        var season = 0; var episode = 0
        sxe.find(s)?.let { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt() }
        if (season == 0) xFormat.find(s)?.let { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt() }
        if (season == 0) seasonWord.find(s)?.let { season = it.groupValues[2].toInt() }
        if (episode == 0) episodeWord.find(s)?.let { episode = it.groupValues[2].toInt() }
        val y = year.findAll(s).map { it.value.toInt() }.lastOrNull() ?: 0
        // the title is usually what comes before the year / episode tag
        val cutAt = listOfNotNull(sxe.find(s)?.range?.first, xFormat.find(s)?.range?.first, year.find(s)?.range?.first?.takeIf { it > 3 })
            .minOrNull()
        if (cutAt != null && cutAt > 2) s = s.substring(0, cutAt)
        s = s.replace(Regex("[\\[(\\{【].*?[\\])\\}】]"), " ")
            .replace(Regex("[._]+"), " ")
            .replace(junkWords, " ").replace(arJunk, " ")
            .replace(seasonWord, " ").replace(episodeWord, " ")
            .replace(Regex("[|•»«~*=+_]+"), " ")
            .replace(Regex("\\s+-\\s*$"), " ")
            .replace(Regex("\\s{2,}"), " ").trim(' ', '-', ':', ',', '.')
        if (s.isNotEmpty() && s == s.lowercase() && s.any { it in 'a'..'z' }) s = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return CleanTitle(s, y, season, episode)
    }

    /** Best title for a video: tidy caption first line if it looks like a name, else the file name. */
    fun titleOf(v: VideoItem): CleanTitle {
        val fromFile = if (v.fileName.isNotBlank()) clean(v.fileName) else null
        val firstLine = v.caption.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && links.replace(it, "").isNotBlank() } ?: ""
        val fromCap = if (firstLine.isNotBlank()) clean(firstLine) else null
        val good = listOfNotNull(fromCap, fromFile).filter { it.title.count { c -> c.isLetter() } >= 2 && it.title.length <= 70 }
        val best = good.firstOrNull() ?: fromFile ?: fromCap ?: CleanTitle(v.title, 0, 0, 0)
        val other = good.getOrNull(1)
        return best.copy(
            year = if (best.year > 0) best.year else other?.year ?: 0,
            season = if (best.season > 0) best.season else other?.season ?: 0,
            episode = if (best.episode > 0) best.episode else other?.episode ?: 0,
        )
    }

    fun title(v: VideoItem): String = if (Prefs.cleanTitles) titleOf(v).label.ifBlank { v.title } else v.title

    /** Caption without links, mentions, hashtags, "join us" lines and emoji spam. */
    fun description(v: VideoItem): String {
        if (!Prefs.cleanTitles) return v.caption
        return v.caption.lines().drop(1).map { it.replace(links, "").replace(emoji, "").trim() }
            .filter { l ->
                l.count { it.isLetter() } >= 3 &&
                    !Regex("(?i)(join|subscribe|follow|channel|قناتنا|انضم|اشترك|تابعونا|للمزيد)").containsMatchIn(l)
            }
            .distinct().joinToString("\n").trim()
    }

    // ---------------- online (TMDB, optional) ----------------

    val online = mutableStateMapOf<String, OnlineInfo?>()

    suspend fun lookup(ctx: Context, v: VideoItem): OnlineInfo? {
        if (Prefs.tmdbKey.isBlank()) return null
        val t = titleOf(v)
        if (t.title.length < 2) return null
        val cacheKey = t.title.lowercase() + "|" + t.year + "|" + (t.season > 0 || t.episode > 0)
        if (online.containsKey(cacheKey)) return online[cacheKey]
        val sp = ctx.getSharedPreferences("riv_meta", Context.MODE_PRIVATE)
        sp.getString(cacheKey, null)?.let { j -> val i = parseCached(j); online[cacheKey] = i; return i }
        val info = withContext(Dispatchers.IO) {
            try {
                val tv = t.season > 0 || t.episode > 0
                val type = if (tv) "tv" else "multi"
                var url = "https://api.themoviedb.org/3/search/$type?api_key=" + URLEncoder.encode(Prefs.tmdbKey, "UTF-8") +
                    "&query=" + URLEncoder.encode(t.title, "UTF-8") + "&language=" + (if (com.rivers.rivpost.data.Lang.arabic) "ar" else "en-US")
                if (t.year > 0 && !tv) url += "&year=${t.year}"
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000
                val body = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
                val r = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
                OnlineInfo(
                    title = r.optString("title").ifBlank { r.optString("name") },
                    year = r.optString("release_date").ifBlank { r.optString("first_air_date") }.take(4),
                    overview = r.optString("overview"),
                    rating = r.optDouble("vote_average", 0.0),
                    poster = r.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w342$it" } ?: "",
                    backdrop = r.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "",
                )
            } catch (_: Throwable) { null }
        }
        online[cacheKey] = info
        if (info != null) sp.edit().putString(cacheKey, JSONObject().apply {
            put("t", info.title); put("y", info.year); put("o", info.overview); put("r", info.rating); put("p", info.poster); put("b", info.backdrop)
        }.toString()).apply()
        return info
    }

    private fun parseCached(j: String): OnlineInfo? = try {
        val o = JSONObject(j)
        OnlineInfo(o.optString("t"), o.optString("y"), o.optString("o"), o.optDouble("r", 0.0), o.optString("p"), o.optString("b"))
    } catch (_: Throwable) { null }

    fun clearCache(ctx: Context) {
        online.clear()
        ctx.getSharedPreferences("riv_meta", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
