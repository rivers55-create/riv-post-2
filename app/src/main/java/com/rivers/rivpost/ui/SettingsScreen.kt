package com.rivers.rivpost.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.BuildConfig
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.ConnectionStateConnectingToProxy
import dev.g000sha256.tdl.dto.ConnectionStateReady
import dev.g000sha256.tdl.dto.ConnectionStateWaitingForNetwork

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Ic.Back, null) } },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A row that opens a list of choices. */
@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickRow(title, options.firstOrNull { it.first == current }?.second ?: "") { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    for ((key, label) in options) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(key); open = false }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = key == current, onClick = { onPick(key); open = false })
                            Spacer(Modifier.size(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(t("Close")) } },
        )
    }
}

@Composable
fun SettingsScreen(act: MainActivity, acc: TgAccount, onBack: () -> Unit, onProxy: () -> Unit, onCustomize: () -> Unit = {}, onSources: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var keysDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }

    SettingsPage(t("Settings"), onBack) {
        SectionTitle(t("Look"))
        ChoiceRow(
            t("Language"),
            listOf("system" to t("Phone language"), "en" to "English", "ar" to "العربية"),
            Prefs.lang,
        ) { Prefs.lang = it; Lang.update() }
        ChoiceRow(
            t("Theme"),
            listOf("dark" to t("Dark"), "light" to t("Light"), "system" to t("Follow phone")),
            Prefs.theme,
        ) { Prefs.theme = it }
        ChoiceRow(
            t("Chat style"),
            listOf(
                "riv" to t("Riv (default)"),
                "wave" to t("Wave: blue bubbles, classic messenger look"),
                "leaf" to t("Leaf: green bubbles and chat wallpaper"),
            ),
            Prefs.style,
        ) { Prefs.style = it }

        Text(
            t("Accent color"), fontSize = 16.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Accents.forEachIndexed { i, (_, c) ->
                val sel = Prefs.accent == i
                Row(
                    Modifier.size(40.dp).clip(CircleShape).background(c)
                        .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable { Prefs.accent = i },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { if (sel) Icon(Ic.Check, null, tint = Color.White) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            t("Text size") + ": " + Prefs.textSize, fontSize = 16.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        Slider(
            value = Prefs.textSize.toFloat(), onValueChange = { Prefs.textSize = it.toInt() },
            valueRange = 13f..22f, steps = 8, modifier = Modifier.padding(horizontal = 20.dp),
        )
        ChoiceRow(
            t("App icon"),
            listOf("purple" to t("Purple"), "black" to t("Black"), "blue" to t("Blue")),
            Prefs.icon,
        ) { setIcon(act, it) }
        SwitchRow(t("Folder tabs"), t("Show your chat folders as tabs"), Prefs.showFolders) { Prefs.showFolders = it }
        SwitchRow(t("Archive tab"), null, Prefs.showArchive) { Prefs.showArchive = it }
        SwitchRow(t("Profile pictures in chat list"), null, Prefs.showAvatars) { Prefs.showAvatars = it }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Chats"))
        SwitchRow(t("Download photos automatically"), null, Prefs.autoPhotos) { Prefs.autoPhotos = it }
        SwitchRow(t("Enter key sends"), t("For physical keyboards"), Prefs.enterSends) { Prefs.enterSends = it }

        ClickRow(t("Customize everything"), "›", onCustomize)

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Media"))
        SwitchRow(t("Play GIFs automatically"), null, Prefs.autoGifs) { Prefs.autoGifs = it }
        SwitchRow(t("Animated stickers"), null, Prefs.animStickers) { Prefs.animStickers = it }
        SwitchRow(t("Built-in video player"), t("Off opens videos in another app"), Prefs.builtInVideo) { Prefs.builtInVideo = it }
        SwitchRow(t("Download voice messages automatically"), null, Prefs.autoVoice) { Prefs.autoVoice = it }
        ClickRow(t("Video sources"), "›") { onSources("video") }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Music player"))
        SwitchRow(t("Wavy seek bar"), null, Prefs.mWavy) { Prefs.mWavy = it }
        SwitchRow(t("Skip buttons (±seconds)"), t("Off shows shuffle and repeat there instead"), Prefs.mSkipButtons) { Prefs.mSkipButtons = it }
        ChoiceRow(t("Skip length"), listOf("5" to "5 s", "10" to "10 s", "15" to "15 s", "30" to "30 s"), Prefs.mSkipSecs.toString()) { Prefs.mSkipSecs = it.toInt() }
        ChoiceRow(t("Player background"), listOf("gradient" to t("Cover color"), "blur" to t("Blurred cover"), "solid" to t("Plain")), Prefs.mBg) { Prefs.mBg = it }
        SwitchRow(t("Show output device"), null, Prefs.mOutput) { Prefs.mOutput = it }
        SwitchRow(t("Show audio quality"), null, Prefs.mFormat) { Prefs.mFormat = it }
        SwitchRow(t("Lyrics"), null, Prefs.mLyrics) { Prefs.mLyrics = it }
        SwitchRow(t("Find lyrics online"), "lrclib.net", Prefs.mOnlineLyrics) { Prefs.mOnlineLyrics = it }
        SwitchRow(t("Mini player"), null, Prefs.mMini) { Prefs.mMini = it }
        ClickRow(t("Music sources"), "›") { onSources("music") }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Notifications"))
        SwitchRow(
            t("Stay connected for notifications"),
            t("Keeps a small connection running so messages arrive when the app is closed. Uses a little battery."),
            Prefs.background,
        ) { Prefs.background = it; Tg.updateService(ctx) }
        SwitchRow(t("Show message text"), null, Prefs.notifPreview) { Prefs.notifPreview = it }
        SwitchRow(t("Group notifications"), null, Prefs.notifGroups) { Prefs.notifGroups = it }
        SwitchRow(t("Channel notifications"), null, Prefs.notifChannels) { Prefs.notifChannels = it }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Privacy"))
        SwitchRow(t("App lock"), t("Ask for a PIN when opening the app"), Prefs.lock) { on ->
            if (on) { pinDialog = true } else { Prefs.lock = false }
        }
        if (Prefs.lock) {
            ClickRow(t("Change PIN"), null) { pinDialog = true }
            SwitchRow(t("Unlock with fingerprint"), null, Prefs.fingerprint) { Prefs.fingerprint = it }
            ChoiceRow(
                t("Lock after"),
                listOf("0" to t("Immediately"), "60" to t("1 minute"), "300" to t("5 minutes"), "1800" to t("30 minutes")),
                Prefs.lockDelay.toString(),
            ) { Prefs.lockDelay = it.toInt() }
        }
        SwitchRow(t("Hide app content in recent apps"), t("Also blocks screenshots"), Prefs.secureScreen) {
            Prefs.secureScreen = it
            act.applySecure()
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Connection"))
        ClickRow(t("Proxy"), if (Prefs.proxyOn) t("On") else t("Off"), onProxy)
        ClickRow(t("Telegram API keys"), "api_id " + Prefs.apiId) { keysDialog = true }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("Account"))
        ClickRow(t("Log out of this account"), null) { logoutDialog = true }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(t("About"))
        Text(
            "Riv Post " + BuildConfig.VERSION_NAME + "\n" + t("A Telegram client by Rivers. Built on TDLib."),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }

    if (pinDialog) PinSetupDialog(onDone = { pin ->
        pinDialog = false
        if (pin != null) { Prefs.pinHash = Prefs.hash(pin); Prefs.lock = true }
    })
    if (keysDialog) AlertDialog(
        onDismissRequest = { keysDialog = false },
        title = { Text(t("Telegram API keys")) },
        text = { Text(t("Changing the keys restarts the app. Your logged-in accounts stay logged in.")) },
        confirmButton = { TextButton(onClick = { keysDialog = false; Prefs.apiId = 0 }) { Text(t("Change")) } },
        dismissButton = { TextButton(onClick = { keysDialog = false }) { Text(t("Cancel")) } },
    )
    if (logoutDialog) AlertDialog(
        onDismissRequest = { logoutDialog = false },
        title = { Text(t("Log out?")) },
        text = { Text(t("You can log in again any time with your phone number.")) },
        confirmButton = { TextButton(onClick = { logoutDialog = false; onBack(); acc.logOut() }) { Text(t("Log out"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text(t("Cancel")) } },
    )
}

@Composable
private fun PinSetupDialog(onDone: (String?) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(t("Set a PIN")) },
        text = {
            Column {
                OutlinedTextField(
                    value = a, onValueChange = { s -> a = s.filter { it.isDigit() }.take(8) }, label = { Text(t("PIN (4–8 digits)")) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = b, onValueChange = { s -> b = s.filter { it.isDigit() }.take(8) }, label = { Text(t("Repeat PIN")) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (a.length < 4) {
                    err = t("Use at least 4 digits")
                } else if (a != b) {
                    err = t("The PINs don't match")
                } else {
                    onDone(a)
                }
            }) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(t("Cancel")) } },
    )
}

private fun setIcon(act: MainActivity, name: String) {
    val pm = act.packageManager
    val all = mapOf("purple" to ".IconPurple", "black" to ".IconBlack", "blue" to ".IconBlue")
    for ((key, cls) in all) {
        val state = if (key == name) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            pm.setComponentEnabledSetting(ComponentName(act, act.packageName + cls), state, PackageManager.DONT_KILL_APP)
        } catch (_: Throwable) { }
    }
    Prefs.icon = name
    toast(act, t("The new icon may take a few seconds to show"))
}

@Composable
fun ProxyScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val conn by acc.conn.collectAsState()
    var on by remember { mutableStateOf(Prefs.proxyOn) }
    var type by remember { mutableStateOf(Prefs.proxyType) }
    var host by remember { mutableStateOf(Prefs.proxyHost) }
    var port by remember { mutableStateOf(Prefs.proxyPort.toString()) }
    var user by remember { mutableStateOf(Prefs.proxyUser) }
    var pass by remember { mutableStateOf(Prefs.proxyPass) }
    var secret by remember { mutableStateOf(Prefs.proxySecret) }

    SettingsPage(t("Proxy"), onBack) {
        SwitchRow(t("Use proxy"), t("For when Telegram is blocked or slow"), on) { on = it }
        ChoiceRow(t("Type"), listOf("socks5" to "SOCKS5", "mtproto" to "MTProto"), type) { type = it }
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(value = host, onValueChange = { host = it.trim() }, label = { Text(t("Server")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = port, onValueChange = { s -> port = s.filter { it.isDigit() }.take(5) }, label = { Text(t("Port")) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (type == "mtproto") {
                OutlinedTextField(value = secret, onValueChange = { secret = it.trim() }, label = { Text(t("Secret")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(t("Username (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text(t("Password (optional)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = {
                Prefs.proxyOn = on && host.isNotBlank()
                Prefs.proxyType = type
                Prefs.proxyHost = host
                Prefs.proxyPort = port.toIntOrNull() ?: 1080
                Prefs.proxyUser = user
                Prefs.proxyPass = pass
                Prefs.proxySecret = secret
                Tg.applyProxyAll()
                toast(ctx, t("Saved"))
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(t("Save")) }
            Spacer(Modifier.height(14.dp))
            Text(
                t("Status") + ": " + when (conn) {
                    is ConnectionStateReady -> t("Connected")
                    is ConnectionStateConnectingToProxy -> t("Connecting to proxy…")
                    is ConnectionStateWaitingForNetwork -> t("Waiting for network…")
                    else -> t("Connecting…")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
