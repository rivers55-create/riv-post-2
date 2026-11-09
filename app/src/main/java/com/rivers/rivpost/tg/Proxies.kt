package com.rivers.rivpost.tg

import com.rivers.rivpost.data.Prefs
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ConnectionStateReady
import dev.g000sha256.tdl.dto.Proxy
import dev.g000sha256.tdl.dto.ProxyTypeMtproto
import dev.g000sha256.tdl.dto.ProxyTypeSocks5
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A saved proxy. */
data class SavedProxy(val type: String, val host: String, val port: Int, val user: String, val pass: String, val secret: String) {
    fun line() = listOf(type, host, port.toString(), user, pass, secret).joinToString("|") { it.replace("|", "") }
    fun toTd(): Proxy = Proxy(
        server = host, port = port,
        type = if (type == "mtproto") ProxyTypeMtproto(secret = secret) else ProxyTypeSocks5(username = user, password = pass),
    )
    val label: String get() = (if (type == "mtproto") "MTProto" else "SOCKS5") + "  $host:$port"

    companion object {
        fun parse(l: String): SavedProxy? {
            val p = l.split("|")
            if (p.size < 6) return null
            return SavedProxy(p[0], p[1], p[2].toIntOrNull() ?: return null, p[3], p[4], p[5])
        }
    }
}

/** Saved proxies, ping, and automatic switch to the fastest one. */
object Proxies {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Reads a proxy share link. */
    fun parseLink(raw: String): SavedProxy? {
        val s = raw.trim().replace("&amp;", "&")
        val m = Regex("(?i)(?:tg://|https?://(?:t\\.me|telegram\\.me)/)(proxy|socks)\\?(\\S+)").find(s) ?: return null
        val kind = m.groupValues[1].lowercase()
        val q = m.groupValues[2].split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).lowercase() to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val host = q["server"]?.takeIf { it.isNotBlank() } ?: return null
        val port = q["port"]?.toIntOrNull() ?: return null
        return if (kind == "proxy") SavedProxy("mtproto", host, port, "", "", q["secret"] ?: return null)
        else SavedProxy("socks5", host, port, q["user"] ?: "", q["pass"] ?: "", "")
    }

    /** Adds every proxy link found in a block of text. Returns how many were added. */
    fun importText(text: String): Int {
        val links = Regex("(?i)(?:tg://|https?://(?:t\\.me|telegram\\.me)/)(?:proxy|socks)\\?\\S+").findAll(text).map { it.value }.toList()
        var n = 0
        for (l in links) parseLink(l)?.let { save(it); n++ }
        return n
    }

    fun list(): List<SavedProxy> = Prefs.proxies.split("\n").mapNotNull { SavedProxy.parse(it) }

    fun save(p: SavedProxy) {
        val l = list().filter { !(it.host == p.host && it.port == p.port && it.type == p.type) } + p
        Prefs.proxies = l.joinToString("\n") { it.line() }
    }

    fun remove(p: SavedProxy) {
        Prefs.proxies = list().filter { it != p }.joinToString("\n") { it.line() }
    }

    fun use(p: SavedProxy) {
        Prefs.proxyType = p.type; Prefs.proxyHost = p.host; Prefs.proxyPort = p.port
        Prefs.proxyUser = p.user; Prefs.proxyPass = p.pass; Prefs.proxySecret = p.secret
        Prefs.proxyOn = true
        Tg.applyProxyAll()
    }

    /** Round-trip time in ms, or -1 if it doesn't answer. */
    suspend fun ping(p: SavedProxy): Int {
        val a = Tg.accounts.value.values.firstOrNull() ?: return -1
        val r = a.client.pingProxy(proxy = p.toTd())
        return (r as? TdlResult.Success)?.result?.seconds?.let { (it * 1000).toInt() } ?: -1
    }

    suspend fun useFastest(): SavedProxy? {
        val best = list().map { it to ping(it) }.filter { it.second >= 0 }.minByOrNull { it.second }?.first ?: return null
        use(best)
        return best
    }

    /** Watches the connection; when it stays down, switches to the fastest saved proxy. */
    fun startWatcher() {
        if (started) return
        started = true
        scope.launch {
            var bad = 0
            while (true) {
                delay(15_000)
                if (!Prefs.proxyAuto || list().isEmpty()) { bad = 0; continue }
                val a = Tg.accounts.value[Tg.activeIndex.value] ?: continue
                if (a.conn.value is ConnectionStateReady) bad = 0 else bad++
                if (bad >= 2) { useFastest(); bad = 0 }
            }
        }
    }
}
