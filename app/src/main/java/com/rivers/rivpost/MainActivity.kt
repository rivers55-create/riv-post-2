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
import com.rivers.rivpost.music.Music
import com.rivers.rivpost.ui.AccountPage
import com.rivers.rivpost.ui.AuthPinDialog
import com.rivers.rivpost.ui.CameraScreen
import com.rivers.rivpost.ui.DataPage
import com.rivers.rivpost.ui.DevicesPage
import com.rivers.rivpost.ui.DownloadsPage
import com.rivers.rivpost.ui.DrawerUnlock
import com.rivers.rivpost.ui.FoldersPage
import com.rivers.rivpost.ui.PowerPage
import com.rivers.rivpost.ui.PrivacyPage
import com.rivers.rivpost.ui.SettingsHub
import com.rivers.rivpost.ui.UploadsPage
import com.rivers.rivpost.ui.VaultScreen
import com.rivers.rivpost.ui.VoicePlayer
import com.rivers.rivpost.ui.ChatInfoScreen
import com.rivers.rivpost.ui.CustomizeScreen
import com.rivers.rivpost.ui.ForwardScreen
import com.rivers.rivpost.ui.HomeScreen
import com.rivers.rivpost.ui.MusicNav
import com.rivers.rivpost.ui.NewGroupScreen
import com.rivers.rivpost.ui.NowPlayingScreen
import com.rivers.rivpost.ui.PlayRequest
import com.rivers.rivpost.ui.SourcesScreen
import com.rivers.rivpost.ui.VideoPlayerScreen
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
    class Chat(val id: Long, val jump: Long = 0, val thread: Long = 0, val topic: Int = 0, val flat: Boolean = false) : Screen()
    object Settings : Screen()
    object Proxy : Screen()
    object Customize : Screen()
    object NewGroup : Screen()
    class Info(val id: Long) : Screen()
    class Sources(val kind: String) : Screen()
    class Video(val req: PlayRequest) : Screen()
    class Forward(val fromChat: Long, val ids: LongArray) : Screen()
    object Hub : Screen()
    object Vault : Screen()
    class Page(val name: String) : Screen()
    class Camera(val chatId: Long) : Screen()
    class WebApp(val req: com.rivers.rivpost.ui.WebAppRequest) : Screen()
    class VideoNote(val chatId: Long) : Screen()
}

class MainActivity : FragmentActivity() {
    var locked by mutableStateOf(false)
        private set
    private var leftAt = 0L
    val stack = mutableStateListOf<Screen>(Screen.Chats)
    private var askedNotif = false
    var homePage = "chats"

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
        if (i.getBooleanExtra("open_player", false)) Music.playerOpen = true
        val callUser = i.getLongExtra("call_user", 0L)
        if (callUser != 0L) {
            i.removeExtra("call_user")
            val a = Tg.accounts.value[i.getIntExtra("acc", 0)] ?: Tg.active
            window.decorView.post { com.rivers.rivpost.calls.Calls.start(this, a, callUser) }
        }
        if (i.getBooleanExtra("call_open", false)) { i.removeExtra("call_open"); com.rivers.rivpost.calls.Calls.minimize(false) }
        if (i.getBooleanExtra("call_accept", false)) { i.removeExtra("call_accept"); com.rivers.rivpost.calls.Calls.minimize(false); window.decorView.post { com.rivers.rivpost.calls.Calls.accept(this) } }
        val data = i.dataString
        if (i.action == Intent.ACTION_VIEW && data != null && com.rivers.rivpost.ui.TgLinks.isTelegram(data)) {
            com.rivers.rivpost.ui.TgLinks.act = this
            window.decorView.postDelayed({ com.rivers.rivpost.ui.TgLinks.open(data) { } }, 1200)
        }
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
        Tg.setOnlineAll(true)
        if (Prefs.lock && Prefs.pinHash.isNotEmpty() && leftAt > 0 &&
            System.currentTimeMillis() - leftAt >= Prefs.lockDelay * 1000L
        ) locked = true
        Tg.updateService(this)
    }

    override fun onStop() {
        super.onStop()
        Tg.foreground = false
        Tg.setOnlineAll(false)
        DrawerUnlock.open = false
        if (Prefs.autoMute) {
            try { if (Music.isPlaying) Music.toggle() } catch (_: Throwable) { }
            VoicePlayer.stop()
        }
        leftAt = System.currentTimeMillis()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build(),
            )
        } catch (_: Throwable) { }
    }

    private var lastVolDown = 0L

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val call = com.rivers.rivpost.calls.Calls.ui.value
        if (call != null && !call.ended && com.rivers.rivpost.data.Prefs.callVolKeys &&
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0
        ) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (call.incomingRinging) { com.rivers.rivpost.calls.Calls.silence(); return true }
            if (now - lastVolDown < 450) { lastVolDown = 0; com.rivers.rivpost.calls.Calls.toggleMute(); return true }
            lastVolDown = now
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Prefs.autoPip && com.rivers.rivpost.media.VideoState.playing && stack.lastOrNull() is Screen.Video) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.rivers.rivpost.media.VideoState.inPip = isInPictureInPictureMode
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
        val dir = if (Lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
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
                                LaunchedEffect(Unit) { act.askNotificationPermission(); if (Prefs.fbOn) com.rivers.rivpost.push.Push.start(act) }
                                MainNav(act, acc)
                                com.rivers.rivpost.ui.MusicDrawer(act)
                                com.rivers.rivpost.ui.BotAlertDialog()
                                com.rivers.rivpost.ui.TgLinkDialogs()
                                LaunchedEffect(Unit) { com.rivers.rivpost.ui.TgLinks.act = act }
                                AuthPinDialog()
                                com.rivers.rivpost.ui.CallOverlay(act)
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
    MusicNav.openPage = { act.push(Screen.Page(it)) }
    MusicNav.openChat = { accIndex, chatId ->
        if (accIndex != Tg.activeIndex.value) Tg.switchTo(accIndex)
        act.push(Screen.Chat(chatId))
    }
    if (Music.playerOpen && Music.current != null) {
        NowPlayingScreen()
        return
    }
    BackHandler(enabled = act.stack.size > 1) { act.pop() }
    when (val s = act.stack.lastOrNull() ?: Screen.Chats) {
        is Screen.Chats -> HomeScreen(act, acc)
        is Screen.Chat -> {
            val sg = acc.chats[s.id]?.supergroupId ?: 0L
            val forum = sg != 0L && acc.supergroups[sg]?.isForum == true
            if (forum && s.thread == 0L && s.topic == 0 && !s.flat && Prefs.forumTopicsView) com.rivers.rivpost.ui.ForumTopicsScreen(acc, act, s.id, onBack = { act.pop() })
            else key(s.id, s.jump, s.thread, s.topic) { ChatScreen(acc, act, s.id, onBack = { act.pop() }, jump = s.jump, thread = s.thread, topic = s.topic) }
        }
        is Screen.Settings -> SettingsScreen(
            act, acc, onBack = { act.pop() }, onProxy = { act.push(Screen.Proxy) },
            onCustomize = { act.push(Screen.Customize) }, onSources = { act.push(Screen.Sources(it)) },
        )
        is Screen.Proxy -> ProxyScreen(acc, onBack = { act.pop() })
        is Screen.Hub -> SettingsHub(acc, act, onBack = { act.pop() })
        is Screen.Vault -> VaultScreen(acc, act, onBack = { act.pop() })
        is Screen.Camera -> CameraScreen(acc, s.chatId, onClose = { act.pop() })
        is Screen.WebApp -> com.rivers.rivpost.ui.WebAppScreen(s.req, onClose = { act.pop() })
        is Screen.VideoNote -> com.rivers.rivpost.ui.VideoNoteScreen(acc, s.chatId, onClose = { act.pop() })
        is Screen.Page -> if (com.rivers.rivpost.ui.isMorePage(s.name)) com.rivers.rivpost.ui.MorePage(acc, act, s.name, onBack = { act.pop() }) else when (s.name) {
            "account" -> AccountPage(acc, onBack = { act.pop() }, act = act)
            "privacy" -> PrivacyPage(acc, act, onBack = { act.pop() })
            "devices" -> DevicesPage(acc, onBack = { act.pop() })
            "data" -> DataPage(acc, act, onBack = { act.pop() })
            "downloads" -> DownloadsPage(acc, onBack = { act.pop() })
            "uploads" -> UploadsPage(acc, onBack = { act.pop() })
            "folders" -> FoldersPage(acc, onBack = { act.pop() })
            "iptv" -> com.rivers.rivpost.ui.IptvScreen(act, onBack = { act.pop() })
            "smb" -> com.rivers.rivpost.ui.SmbScreen(act, onBack = { act.pop() })
            "hidden" -> com.rivers.rivpost.ui.HiddenSongsScreen(acc, onBack = { act.pop() })
            else -> PowerPage(onBack = { act.pop() })
        }
        is Screen.Customize -> CustomizeScreen(onBack = { act.pop() })
        is Screen.NewGroup -> NewGroupScreen(acc, act, onBack = { act.pop() })
        is Screen.Info -> key(s.id) { ChatInfoScreen(acc, act, s.id, onBack = { act.pop() }) }
        is Screen.Sources -> SourcesScreen(acc, s.kind, onBack = { act.pop() })
        is Screen.Video -> key(s.req.key) { VideoPlayerScreen(s.req, onClose = { act.pop() }) }
        is Screen.Forward -> ForwardScreen(acc, s.fromChat, s.ids) { target ->
            act.pop()
            if (target != null && target != s.fromChat) act.push(Screen.Chat(target))
        }
    }
}
