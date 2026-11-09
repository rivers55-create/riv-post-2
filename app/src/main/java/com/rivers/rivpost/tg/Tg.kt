package com.rivers.rivpost.tg

import android.content.Context
import android.content.Intent
import android.os.Build
import com.rivers.rivpost.data.Prefs
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps all accounts. Each account has its own TDLib client. */
object Tg {
    lateinit var app: Context
        private set

    val accounts = MutableStateFlow<Map<Int, TgAccount>>(emptyMap())
    val activeIndex = MutableStateFlow(0)

    /** Account index we came from when adding a new account (to go back on Cancel). */
    @Volatile var previousIndex = -1

    @Volatile var foreground = false

    fun init(c: Context) {
        DebugLog.init(c)
        app = c.applicationContext
        if (Prefs.hasKeys) startAll()
        Proxies.startWatcher()
    }

    fun startAll() {
        var list = Prefs.accountList()
        if (list.isEmpty()) {
            list = listOf(0)
            Prefs.setAccountList(list)
        }
        for (i in list) get(i)
        activeIndex.value = if (Prefs.active in list) Prefs.active else list.first()
    }

    @Synchronized
    fun get(i: Int): TgAccount {
        accounts.value[i]?.let { return it }
        val a = TgAccount(app, i)
        accounts.value = accounts.value + (i to a)
        a.start()
        return a
    }

    val active: TgAccount get() = get(activeIndex.value)

    fun readyAccounts(): List<TgAccount> =
        Prefs.accountList().mapNotNull { accounts.value[it] }.filter { it.auth.value is AuthorizationStateReady }

    fun switchTo(i: Int) {
        Prefs.active = i
        activeIndex.value = i
    }

    fun addAccount() {
        previousIndex = activeIndex.value
        val i = Prefs.nextIndex
        Prefs.nextIndex = i + 1
        Prefs.setAccountList(Prefs.accountList() + i)
        get(i)
        switchTo(i)
    }

    /** Cancel adding an account that has not finished logging in. */
    fun cancelAdd() {
        val a = active
        if (a.isReady) return
        val back = previousIndex
        if (back < 0 || back !in Prefs.accountList() || back == a.index) return
        a.loggingOut = true
        a.close()
        switchTo(back)
        previousIndex = -1
    }

    /** Called when an account's client has closed (after log out or cancel). */
    fun onClosed(a: TgAccount) {
        if (!a.loggingOut) return
        try { a.dir.deleteRecursively() } catch (_: Throwable) { }
        synchronized(this) { accounts.value = accounts.value - a.index }
        var list = Prefs.accountList() - a.index
        if (list.isEmpty()) {
            val i = Prefs.nextIndex
            Prefs.nextIndex = i + 1
            list = listOf(i)
        }
        Prefs.setAccountList(list)
        if (activeIndex.value == a.index || activeIndex.value !in list) switchTo(list.first())
        get(activeIndex.value)
    }

    /** Restart all clients (after API keys change). */
    fun restartAll() {
        for (a in accounts.value.values) a.close()
        accounts.value = emptyMap()
        startAll()
    }

    fun setOnlineAll(on: Boolean) {
        for (a in accounts.value.values) a.setOnline(on)
    }

    fun applyProxyAll() {
        for (a in accounts.value.values) a.applyProxy()
    }

    fun updateService(c: Context) {
        val i = Intent(c, TgService::class.java)
        if (Prefs.background && Prefs.hasKeys) {
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (_: Throwable) { }
        } else {
            c.stopService(i)
        }
    }
}
