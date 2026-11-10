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

data class CastMember(val name: String, val role: String, val photo: String)

/** Info from TMDb or TheTVDB (optional, with the user's own keys). */
data class OnlineInfo(
    val title: String, val year: String, val overview: String, val rating: Double, val poster: String, val backdrop: String,
    val genres: List<String> = emptyList(), val runtime: Int = 0, val cast: List<CastMember> = emptyList(),
    val season: Int = 0, val episode: Int = 0, val epTitle: String = "", val epOverview: String = "", val epStill: String = "",
    val seasonPoster: String = "", val source: String = "",
)

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

    // ---------------- online (TMDb / TheTVDB, optional) ----------------

    val online = mutableStateMapOf<String, OnlineInfo?>()

    fun hasOnline(): Boolean = (Prefs.tmdbOn && Prefs.tmdbKey.isNotBlank()) || (Prefs.tvdbOn && Prefs.tvdbKey.isNotBlank())

    suspend fun lookup(ctx: Context, v: VideoItem): OnlineInfo? {
        if (!hasOnline()) return null
        val t = titleOf(v)
        if (t.title.length < 2) return null
        val cacheKey = t.title.lowercase() + "|" + t.year + "|" + t.season + "|" + t.episode + "|" + Prefs.showSource + "|" + com.rivers.rivpost.data.Lang.arabic
        if (online.containsKey(cacheKey)) return online[cacheKey]
        val sp = ctx.getSharedPreferences("riv_meta2", Context.MODE_PRIVATE)
        sp.getString(cacheKey, null)?.let { j -> val i = fromJson(j); online[cacheKey] = i; return i }
        val info = withContext(Dispatchers.IO) {
            val tv = t.season > 0 || t.episode > 0
            val order = if (tv && Prefs.showSource == "tvdb") listOf("tvdb", "tmdb") else listOf("tmdb", "tvdb")
            var r: OnlineInfo? = null
            for (src in order) {
                r = try {
                    when (src) {
                        "tmdb" -> if (Prefs.tmdbOn && Prefs.tmdbKey.isNotBlank()) tmdb(t) else null
                        else -> if (Prefs.tvdbOn && Prefs.tvdbKey.isNotBlank()) tvdb(ctx, t) else null
                    }
                } catch (_: Throwable) { null }
                if (r != null) break
            }
            r
        }
        online[cacheKey] = info
        if (info != null) sp.edit().putString(cacheKey, toJson(info)).apply()
        return info
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try { return JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun img(path: String?, size: String) = path?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/$size$it" } ?: ""

    private fun tmdb(t: CleanTitle): OnlineInfo? {
        val key = enc(Prefs.tmdbKey)
        val lang = if (com.rivers.rivpost.data.Lang.arabic) "ar" else "en-US"
        val tv = t.season > 0 || t.episode > 0
        var url = "https://api.themoviedb.org/3/search/${if (tv) "tv" else "multi"}?api_key=$key&query=${enc(t.title)}&language=$lang"
        if (t.year > 0 && !tv) url += "&year=${t.year}"
        val res = get(url).optJSONArray("results") ?: return null
        var r: JSONObject? = null
        for (i in 0 until res.length()) {
            val o = res.optJSONObject(i) ?: continue
            if (o.optString("media_type") == "person") continue
            r = o; break
        }
        r ?: return null
        val type = if (tv || r.optString("media_type") == "tv" || r.has("first_air_date")) "tv" else "movie"
        val id = r.optLong("id")
        val d = try { get("https://api.themoviedb.org/3/$type/$id?api_key=$key&language=$lang&append_to_response=credits") } catch (_: Throwable) { JSONObject() }
        val genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") } } ?: emptyList()
        val runtime = if (type == "movie") d.optInt("runtime") else d.optJSONArray("episode_run_time")?.optInt(0) ?: 0
        val cast = d.optJSONObject("credits")?.optJSONArray("cast")?.let { a ->
            (0 until minOf(12, a.length())).mapNotNull { i -> a.optJSONObject(i)?.let { CastMember(it.optString("name"), it.optString("character"), img(it.optString("profile_path"), "w185")) } }
        } ?: emptyList()
        var info = OnlineInfo(
            title = r.optString("title").ifBlank { r.optString("name") },
            year = r.optString("release_date").ifBlank { r.optString("first_air_date") }.take(4),
            overview = d.optString("overview").ifBlank { r.optString("overview") },
            rating = r.optDouble("vote_average", 0.0),
            poster = img(r.optString("poster_path"), "w342"),
            backdrop = img(r.optString("backdrop_path"), "w780"),
            genres = genres, runtime = runtime, cast = cast, source = "TMDb",
        )
        if (type == "tv" && t.season > 0) try {
            val s = get("https://api.themoviedb.org/3/tv/$id/season/${t.season}?api_key=$key&language=$lang")
            info = info.copy(season = t.season, seasonPoster = img(s.optString("poster_path"), "w342"))
            val eps = s.optJSONArray("episodes")
            if (eps != null && t.episode > 0) for (i in 0 until eps.length()) {
                val e = eps.optJSONObject(i) ?: continue
                if (e.optInt("episode_number") == t.episode) {
                    info = info.copy(episode = t.episode, epTitle = e.optString("name"), epOverview = e.optString("overview"), epStill = img(e.optString("still_path"), "w300"))
                    break
                }
            }
        } catch (_: Throwable) { }
        return info
    }

    // TheTVDB v4. The token lasts a month; it is kept in app storage.
    private fun tvdbToken(ctx: Context): String? {
        val sp = ctx.getSharedPreferences("riv_meta_tok", Context.MODE_PRIVATE)
        val tok = sp.getString("tok", "") ?: ""
        val age = System.currentTimeMillis() - sp.getLong("at", 0)
        if (tok.isNotBlank() && age < 25L * 86400_000L && sp.getString("for", "") == Prefs.tvdbKey.hashCode().toString()) return tok
        val c = URL("https://api4.thetvdb.com/v4/login").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.connectTimeout = 10000; c.readTimeout = 15000
        val body = JSONObject().put("apikey", Prefs.tvdbKey).apply { if (Prefs.tvdbPin.isNotBlank()) put("pin", Prefs.tvdbPin) }.toString()
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val t = j.optJSONObject("data")?.optString("token") ?: return null
            sp.edit().putString("tok", t).putLong("at", System.currentTimeMillis()).putString("for", Prefs.tvdbKey.hashCode().toString()).apply()
            return t
        } finally { c.disconnect() }
    }

    private fun tvdb(ctx: Context, t: CleanTitle): OnlineInfo? {
        val tok = tvdbToken(ctx) ?: return null
        val h = mapOf("Authorization" to "Bearer $tok", "Accept" to "application/json")
        val tv = t.season > 0 || t.episode > 0
        var url = "https://api4.thetvdb.com/v4/search?query=${enc(t.title)}&type=${if (tv) "series" else "movie"}&limit=1"
        if (t.year > 0 && !tv) url += "&year=${t.year}"
        val r = get(url, h).optJSONArray("data")?.optJSONObject(0) ?: return null
        val ar = com.rivers.rivpost.data.Lang.arabic
        val id = r.optString("tvdb_id").ifBlank { r.optString("id").filter { it.isDigit() } }
        val over = r.optJSONObject("overviews")?.let { o -> if (ar) o.optString("ara").ifBlank { o.optString("eng") } else o.optString("eng") }?.ifBlank { null } ?: r.optString("overview")
        val name = r.optJSONObject("translations")?.let { o -> if (ar) o.optString("ara") else o.optString("eng") }?.ifBlank { null } ?: r.optString("name")
        var info = OnlineInfo(
            title = name, year = r.optString("year"), overview = over, rating = 0.0,
            poster = r.optString("image_url").takeIf { it.startsWith("http") } ?: "", backdrop = "", source = "TheTVDB",
        )
        val kind = if (tv) "series" else "movies"
        try {
            val d = get("https://api4.thetvdb.com/v4/$kind/$id/extended", h).optJSONObject("data")
            if (d != null) {
                val genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") } } ?: emptyList()
                val chars = d.optJSONArray("characters")
                val cast = if (chars == null) emptyList() else (0 until chars.length()).mapNotNull { chars.optJSONObject(it) }
                    .filter { it.optInt("type") == 3 || it.optString("peopleType") == "Actor" }.take(12)
                    .map { CastMember(it.optString("personName"), it.optString("name"), it.optString("personImgURL").ifBlank { it.optString("image") }) }
                val arts = d.optJSONArray("artworks")
                val back = if (arts == null) "" else (0 until arts.length()).mapNotNull { arts.optJSONObject(it) }
                    .firstOrNull { it.optInt("type") == 3 || it.optInt("type") == 15 }?.optString("image") ?: ""
                var seasonPoster = ""
                val seasons = d.optJSONArray("seasons")
                if (seasons != null && t.season > 0) for (i in 0 until seasons.length()) {
                    val s = seasons.optJSONObject(i) ?: continue
                    if (s.optInt("number") == t.season && s.optJSONObject("type")?.optString("type") == "official") { seasonPoster = s.optString("image"); break }
                }
                info = info.copy(
                    genres = genres, cast = cast, backdrop = back, seasonPoster = seasonPoster,
                    runtime = d.optInt("averageRuntime").takeIf { it > 0 } ?: d.optInt("runtime"),
                    poster = info.poster.ifBlank { d.optString("image") },
                )
            }
        } catch (_: Throwable) { }
        if (tv && t.season > 0 && t.episode > 0) try {
            val e = get("https://api4.thetvdb.com/v4/series/$id/episodes/default?season=${t.season}&episodeNumber=${t.episode}", h)
                .optJSONObject("data")?.optJSONArray("episodes")?.optJSONObject(0)
            if (e != null) info = info.copy(season = t.season, episode = t.episode, epTitle = e.optString("name"), epOverview = e.optString("overview"), epStill = e.optString("image"))
        } catch (_: Throwable) { }
        return info
    }

    private fun toJson(i: OnlineInfo): String = JSONObject().apply {
        put("t", i.title); put("y", i.year); put("o", i.overview); put("r", i.rating); put("p", i.poster); put("b", i.backdrop)
        put("g", org.json.JSONArray(i.genres)); put("rt", i.runtime)
        put("c", org.json.JSONArray(i.cast.map { org.json.JSONArray(listOf(it.name, it.role, it.photo)) }))
        put("s", i.season); put("e", i.episode); put("et", i.epTitle); put("eo", i.epOverview); put("es", i.epStill); put("sp", i.seasonPoster); put("src", i.source)
    }.toString()

    private fun fromJson(j: String): OnlineInfo? = try {
        val o = JSONObject(j)
        val g = o.optJSONArray("g"); val c = o.optJSONArray("c")
        OnlineInfo(
            o.optString("t"), o.optString("y"), o.optString("o"), o.optDouble("r", 0.0), o.optString("p"), o.optString("b"),
            genres = if (g == null) emptyList() else (0 until g.length()).map { g.optString(it) },
            runtime = o.optInt("rt"),
            cast = if (c == null) emptyList() else (0 until c.length()).mapNotNull { c.optJSONArray(it) }.map { CastMember(it.optString(0), it.optString(1), it.optString(2)) },
            season = o.optInt("s"), episode = o.optInt("e"), epTitle = o.optString("et"), epOverview = o.optString("eo"),
            epStill = o.optString("es"), seasonPoster = o.optString("sp"), source = o.optString("src"),
        )
    } catch (_: Throwable) { null }

    fun clearCache(ctx: Context) {
        online.clear()
        ctx.getSharedPreferences("riv_meta2", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
