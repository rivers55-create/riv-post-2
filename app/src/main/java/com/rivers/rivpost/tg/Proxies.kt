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
