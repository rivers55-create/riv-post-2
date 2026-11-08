package com.rivers.rivpost.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** All settings. Each value is Compose state, so the UI updates when it changes. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("riv_settings", Context.MODE_PRIVATE)
    }

    // Telegram API keys (entered by the user, kept only on this phone)
    var apiId by IntPref("api_id", 0)
    var apiHash by StrPref("api_hash", "")
    val hasKeys: Boolean get() = apiId > 0 && apiHash.length >= 16

    // Look
    var lang by StrPref("lang", "system")          // system / en / ar
    var theme by StrPref("theme", "dark")          // dark / light / system
    var accent by IntPref("accent", 0)
    var style by StrPref("style", "riv")           // riv / wave / leaf
    var icon by StrPref("icon", "purple")          // purple / black / blue
    var textSize by IntPref("text_size", 16)
    var showFolders by BoolPref("folders", true)
    var showAvatars by BoolPref("avatars", true)

    // Chats
    var autoPhotos by BoolPref("auto_photos", true)
    var enterSends by BoolPref("enter_sends", false)
    var showArchive by BoolPref("show_archive", true)

    // Notifications
    var background by BoolPref("background", true)
    var notifPreview by BoolPref("notif_preview", true)
    var notifGroups by BoolPref("notif_groups", true)
    var notifChannels by BoolPref("notif_channels", false)

    // Privacy
    var lock by BoolPref("lock", false)
    var pinHash by StrPref("pin_hash", "")
    var fingerprint by BoolPref("fingerprint", true)
    var lockDelay by IntPref("lock_delay", 0)       // seconds away before locking
    var secureScreen by BoolPref("secure_screen", false)

    // Proxy
    var proxyOn by BoolPref("proxy_on", false)
    var proxyType by StrPref("proxy_type", "socks5") // socks5 / mtproto
    var proxyHost by StrPref("proxy_host", "")
    var proxyPort by IntPref("proxy_port", 1080)
    var proxyUser by StrPref("proxy_user", "")
    var proxyPass by StrPref("proxy_pass", "")
    var proxySecret by StrPref("proxy_secret", "")

    // Accounts
    var accounts by StrPref("accounts", "0")
    var active by IntPref("active", 0)
    var nextIndex by IntPref("next_index", 1)

    fun accountList(): List<Int> =
        accounts.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()

    fun setAccountList(list: List<Int>) {
        accounts = list.distinct().joinToString(",")
    }

    fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("riv-post:" + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

class BoolPref(private val key: String, private val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
    private var state: MutableState<Boolean>? = null
    private fun s(): MutableState<Boolean> =
        state ?: mutableStateOf(Prefs.sp.getBoolean(key, def)).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        s().value = value
        Prefs.sp.edit().putBoolean(key, value).apply()
    }
}

class IntPref(private val key: String, private val def: Int) : ReadWriteProperty<Any?, Int> {
    private var state: MutableState<Int>? = null
    private fun s(): MutableState<Int> =
        state ?: mutableStateOf(Prefs.sp.getInt(key, def)).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        s().value = value
        Prefs.sp.edit().putInt(key, value).apply()
    }
}

class StrPref(private val key: String, private val def: String) : ReadWriteProperty<Any?, String> {
    private var state: MutableState<String>? = null
    private fun s(): MutableState<String> =
        state ?: mutableStateOf(Prefs.sp.getString(key, def) ?: def).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        s().value = value
        Prefs.sp.edit().putString(key, value).apply()
    }
}
