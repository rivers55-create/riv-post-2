package com.rivers.rivpost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import com.rivers.rivpost.ui.ChatListScreen
import com.rivers.rivpost.ui.ChatScreen
import com.rivers.rivpost.ui.LockScreen
import com.rivers.rivpost.ui.LoginScreen
import com.rivers.rivpost.ui.ProxyScreen
import com.rivers.rivpost.ui.RivTheme
import com.rivers.rivpost.ui.SettingsScreen
import com.rivers.rivpost.ui.SetupScreen
import dev.g000sha256.tdl.dto.AuthorizationStateReady

sealed class Screen {
    object Chats : Screen()
    class Chat(val id: Long) : Screen()
    object Settings : Screen()
    object Proxy : Screen()
}

class MainActivity : FragmentActivity() {
    var locked by mutableStateOf(false)
        private set
    private var leftAt = 0L
    val stack = mutableStateListOf<Screen>(Screen.Chats)
    private var askedNotif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = Prefs.lock && Prefs.pinHash.isNotEmpty()
        applySecure()
        handleIntent(intent)
        setContent { Root(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val acc = i.getIntExtra("acc", -1)
        val chat = i.getLongExtra("chat", 0L)
        if (acc >= 0 && chat != 0L) {
            if (acc != Tg.activeIndex.value && acc in Prefs.accountList()) Tg.switchTo(acc)
            stack.clear()
            stack.add(Screen.Chats)
            stack.add(Screen.Chat(chat))
        }
    }

    override fun onStart() {
        super.onStart()
        Tg.foreground = true
        if (Prefs.lock && Prefs.pinHash.isNotEmpty() && leftAt > 0 &&
            System.currentTimeMillis() - leftAt >= Prefs.lockDelay * 1000L
        ) locked = true
        Tg.updateService(this)
    }

    override fun onStop() {
        super.onStop()
        Tg.foreground = false
        leftAt = System.currentTimeMillis()
    }

    fun unlock() {
        locked = false
    }

    fun applySecure() {
        if (Prefs.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun askNotificationPermission() {
        if (askedNotif || Build.VERSION.SDK_INT < 33) return
        askedNotif = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

@Composable
private fun Root(act: MainActivity) {
    RivTheme {
        val dir = if (Lang.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !Prefs.hasKeys -> SetupScreen()
                    act.locked -> LockScreen(act)
                    else -> {
                        val idx by Tg.activeIndex.collectAsState()
                        key(idx) {
                            val acc = Tg.get(idx)
                            val st by acc.auth.collectAsState()
                            if (st is AuthorizationStateReady) {
                                LaunchedEffect(Unit) { act.askNotificationPermission() }
                                MainNav(act, acc)
                            } else {
                                LoginScreen(acc)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNav(act: MainActivity, acc: TgAccount) {
    BackHandler(enabled = act.stack.size > 1) { act.pop() }
    when (val s = act.stack.lastOrNull() ?: Screen.Chats) {
        is Screen.Chats -> ChatListScreen(
            acc,
            onOpen = { act.push(Screen.Chat(it)) },
            onSettings = { act.push(Screen.Settings) },
        )
        is Screen.Chat -> key(s.id) { ChatScreen(acc, s.id, onBack = { act.pop() }) }
        is Screen.Settings -> SettingsScreen(act, acc, onBack = { act.pop() }, onProxy = { act.push(Screen.Proxy) })
        is Screen.Proxy -> ProxyScreen(acc, onBack = { act.pop() })
    }
}
