package com.rivers.rivpost.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.BuildConfig
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ChatFolder
import dev.g000sha256.tdl.dto.ChatFolderName
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.FileDownload
import dev.g000sha256.tdl.dto.FileType
import dev.g000sha256.tdl.dto.FileTypeAnimation
import dev.g000sha256.tdl.dto.FileTypeAudio
import dev.g000sha256.tdl.dto.FileTypeDocument
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypeSticker
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.FileTypeVideoNote
import dev.g000sha256.tdl.dto.FileTypeVoiceNote
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.Session
import dev.g000sha256.tdl.dto.StorageStatistics
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.UserPrivacySetting
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowCalls
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowChatInvites
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRules
import dev.g000sha256.tdl.dto.UserPrivacySettingShowPhoneNumber
import dev.g000sha256.tdl.dto.UserPrivacySettingShowProfilePhoto
import dev.g000sha256.tdl.dto.UserPrivacySettingShowStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Ic.Back, null) } },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun HubCard(content: @Composable () -> Unit) {
    val st = LocalStyle.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).clip(RoundedCornerShape(st.card)).background(st.navBar).padding(vertical = 4.dp),
    ) { content() }
}

@Composable
fun HubRow(icon: ImageVector, color: Color, title: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 17.sp)
            if (sub != null) Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The main Settings screen, laid out like the reference app. */
@Composable
fun SettingsHub(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val me by acc.me.collectAsState()
    var lang by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) acc.scope.launch {
            val f = copyToCache(ctx, uri) ?: return@launch
            val r = acc.client.setProfilePhoto(photo = InputChatPhotoStatic(photo = InputFileLocal(path = f.absolutePath)), isPublic = false)
            withContext(Dispatchers.Main) { toast(ctx, if (r is TdlResult.Success) t("Photo updated") else t("Couldn't update the photo")) }
        }
    }
    fun open(url: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Throwable) { } }
    Box(Modifier.fillMaxSize().background(LocalStyle.current.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val u = me
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Avatar(acc, u?.id ?: 0L, acc.userName(u?.id ?: 0L), u?.profilePhoto?.small, u?.profilePhoto?.minithumbnail?.data, 110.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(38.dp).clip(CircleShape)
                                .background(LocalStyle.current.accent).clickable { pickPhoto.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) { Text("📷", fontSize = 18.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(acc.userName(u?.id ?: 0L), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    val un = u?.usernames?.activeUsernames?.firstOrNull()
                    Text(
                        listOfNotNull(u?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "+$it" }, un?.let { "@$it" }).joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            item {
                HubCard {
                    HubRow(Ic.Account, Color(0xFF3A9BF0), t("Account"), t("Number, Username, Bio")) { act.push(Screen.Page("account")) }
                    HubRow(Ic.Chat, Color(0xFFF5A623), t("Chat Settings"), t("Wallpaper, Night Mode, Animations")) { act.push(Screen.Settings) }
                    HubRow(Ic.Lock, Color(0xFF4CD964), t("Privacy & Security"), t("Last Seen, Ghost mode, Devices, Passcode")) { act.push(Screen.Page("privacy")) }
                    HubRow(Ic.Muted, Color(0xFFE53935), t("Notifications"), t("Sounds, Groups, Channels")) { act.push(Screen.Settings) }
                    HubRow(Ic.Download, Color(0xFF5B7CF0), t("Data and Storage"), t("Downloads, uploads, cache, save folders")) { act.push(Screen.Page("data")) }
                    HubRow(Ic.Doc, Color(0xFF29B6F6), t("Chat Folders"), t("Sort chats into folders")) { act.push(Screen.Page("folders")) }
                    HubRow(Ic.PhoneAndroid, Color(0xFF26C6DA), t("Devices"), t("Manage connected devices")) { act.push(Screen.Page("devices")) }
                    HubRow(Ic.Speed, Color(0xFFFF8A3D), t("Power Saving"), t("Reduce power usage")) { act.push(Screen.Page("power")) }
                    HubRow(Ic.Info, Color(0xFF8E85EE), t("Language"), if (Lang.arabic) "العربية" else "English") { lang = true }
                }
            }
            item {
                HubCard {
                    HubRow(Ic.Palette, Color(0xFFE5508A), t("Customize"), t("Bars, colors, bubbles, everything")) { act.push(Screen.Customize) }
                    HubRow(Ic.Refresh, Color(0xFF00A884), t("Proxy"), if (Prefs.proxyOn) t("On") else t("Off")) { act.push(Screen.Proxy) }
                    HubRow(Ic.Lock, Color(0xFF7C4DFF), t("Hidden chats"), t("Vault with PIN or fingerprint")) { act.push(Screen.Vault) }
                }
            }
            item {
                HubCard {
                    Text(t("Help"), color = LocalStyle.current.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    HubRow(Ic.Chat, Color(0xFFF5A623), t("Ask a Question")) { open("https://telegram.org/support") }
                    HubRow(Ic.Info, Color(0xFF3A9BF0), t("Telegram FAQ")) { open("https://telegram.org/faq") }
                    HubRow(Ic.Lock, Color(0xFF4CD964), t("Privacy Policy")) { open("https://telegram.org/privacy") }
                }
                Text(
                    "Riv Post " + BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(60.dp))
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(6.dp)) { Icon(Ic.Back, null) }
    }
    if (lang) ChoiceDialog(
        t("Language"), listOf(t("Phone language") to "system", "English" to "en", "العربية" to "ar"), onDismiss = { lang = false },
    ) { Prefs.lang = it; Lang.update(); lang = false }
}

// ---------------------------------------------------------------- account

@Composable
fun AccountPage(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val me by acc.me.collectAsState()
    val u = me
    var first by remember(u?.id) { mutableStateOf(u?.firstName ?: "") }
    var last by remember(u?.id) { mutableStateOf(u?.lastName ?: "") }
    var user by remember(u?.id) { mutableStateOf(u?.usernames?.activeUsernames?.firstOrNull() ?: "") }
    var bio by remember { mutableStateOf("") }
    LaunchedEffect(u?.id) {
        if (u != null) bio = (acc.client.getUserFullInfo(userId = u.id) as? TdlResult.Success)?.result?.bio?.text ?: ""
    }
    fun done(r: TdlResult<*>) { toast(ctx, if (r is TdlResult.Failure) r.message else t("Saved")) }
    Page(t("Account"), onBack) {
        Column(Modifier.padding(16.dp)) {
            Text(t("Phone") + ": " + (u?.phoneNumber?.let { "+$it" } ?: ""), fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(first, { first = it }, label = { Text(t("First name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(last, { last = it }, label = { Text(t("Last name (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { acc.scope.launch { val r = acc.client.setName(firstName = first.trim(), lastName = last.trim()); withContext(Dispatchers.Main) { done(r) } } }) { Text(t("Save name")) }
            OutlinedTextField(user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(32) }, label = { Text(t("Username")) }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { acc.scope.launch { val r = acc.client.setUsername(username = user); withContext(Dispatchers.Main) { done(r) } } }) { Text(t("Save username")) }
            OutlinedTextField(bio, { bio = it.take(70) }, label = { Text(t("Bio")) }, modifier = Modifier.fillMaxWidth())
            Text("${bio.length}/70", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { acc.scope.launch { val r = acc.client.setBio(bio = bio); withContext(Dispatchers.Main) { done(r) } } }) { Text(t("Save bio")) }
        }
    }
}

// ---------------------------------------------------------------- privacy

@Composable
fun PrivacyPage(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val items: List<Pair<String, UserPrivacySetting>> = listOf(
        t("Last seen & online") to UserPrivacySettingShowStatus(),
        t("Phone number") to UserPrivacySettingShowPhoneNumber(),
        t("Profile photo") to UserPrivacySettingShowProfilePhoto(),
        t("Calls") to UserPrivacySettingAllowCalls(),
        t("Groups & invites") to UserPrivacySettingAllowChatInvites(),
    )
    val values = remember { mutableStateListOf(*Array(items.size) { "" }) }
    var editing by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        items.forEachIndexed { i, (_, s) ->
            val r = (acc.client.getUserPrivacySettingRules(setting = s) as? TdlResult.Success)?.result
            values[i] = when (r?.rules?.firstOrNull()) {
                is UserPrivacySettingRuleAllowAll -> "all"
                is UserPrivacySettingRuleAllowContacts -> "contacts"
                is UserPrivacySettingRuleRestrictAll -> "nobody"
                else -> ""
            }
        }
    }
    fun label(v: String) = when (v) { "all" -> t("Everybody"); "contacts" -> t("My Contacts"); "nobody" -> t("Nobody"); else -> "…" }
    Page(t("Privacy & Security"), onBack) {
        SectionTitle(t("Ghost mode"))
        SwitchRow(
            t("Ghost mode"), t("Read messages without read receipts, appear offline, never show typing"), Prefs.ghost,
        ) { Prefs.ghost = it; acc.setOnline(!it) }
        SwitchRow(t("Don't mark as read while scrolling"), t("A \"Mark read\" button appears instead"), Prefs.readFreeze) { Prefs.readFreeze = it }
        SectionTitle(t("Privacy"))
        items.forEachIndexed { i, (name, _) -> ClickRow(name, label(values[i])) { editing = i } }
        SectionTitle(t("Security"))
        ClickRow(t("App lock and passcode"), if (Prefs.lock) t("On") else t("Off")) { act.push(Screen.Settings) }
        SwitchRow(t("Lock the drawer and account switcher"), t("Asks for your PIN or fingerprint"), Prefs.drawerLock) { Prefs.drawerLock = it }
        ClickRow(t("Hidden chats"), null) { act.push(Screen.Vault) }
        ClickRow(t("Devices"), null) { act.push(Screen.Page("devices")) }
        SwitchRow(t("Auto-mute when leaving the app"), t("Stops music and voice playback"), Prefs.autoMute) { Prefs.autoMute = it }
        SectionTitle(t("Message history (testing)"))
        SwitchRow(
            t("Keep deleted messages and edits"),
            t("Off by default. Only keeps what already reached this phone, stored only here. Deleted ones show 🗑, edits show ⓘ."),
            Prefs.antiRevoke,
        ) { Prefs.antiRevoke = it }
    }
    if (editing >= 0) ChoiceDialog(
        items[editing].first, listOf(t("Everybody") to "all", t("My Contacts") to "contacts", t("Nobody") to "nobody"),
        onDismiss = { editing = -1 },
    ) { v ->
        val i = editing
        editing = -1
        values[i] = v
        val rule = when (v) { "all" -> UserPrivacySettingRuleAllowAll(); "contacts" -> UserPrivacySettingRuleAllowContacts(); else -> UserPrivacySettingRuleRestrictAll() }
        acc.scope.launch { acc.client.setUserPrivacySettingRules(setting = items[i].second, rules = UserPrivacySettingRules(rules = arrayOf(rule))) }
    }
}

// ---------------------------------------------------------------- devices

@Composable
fun DevicesPage(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sessions by remember { mutableStateOf<List<Session>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { sessions = (acc.client.getActiveSessions() as? TdlResult.Success)?.result?.sessions?.toList() ?: emptyList() }
    Page(t("Devices"), onBack) {
        val l = sessions
        if (l == null) { CircularProgressIndicator(Modifier.padding(20.dp)); return@Page }
        for (s in l.sortedByDescending { it.isCurrent }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.deviceModel.ifBlank { s.platform } + if (s.isCurrent) "  (" + t("this device") + ")" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.applicationName + " " + s.applicationVersion, fontSize = 14.sp)
                    Text(listOf(s.location, s.ipAddress, Fmt.listTime(s.lastActiveDate)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!s.isCurrent) TextButton(onClick = {
                    acc.scope.launch {
                        val r = acc.client.terminateSession(sessionId = s.id)
                        withContext(Dispatchers.Main) { if (r is TdlResult.Failure) toast(ctx, r.message); tick++ }
                    }
                }) { Text(t("End"), color = Color(0xFFE53935)) }
            }
            HorizontalDivider()
        }
    }
}

// ---------------------------------------------------------------- data and storage

private val fileTypes: List<Pair<String, FileType>> by lazy {
    listOf(
        "Photos" to FileTypePhoto(), "Videos" to FileTypeVideo(), "Voice messages" to FileTypeVoiceNote(), "Video messages" to FileTypeVideoNote(),
        "Music" to FileTypeAudio(), "Files" to FileTypeDocument(), "Stickers" to FileTypeSticker(), "GIFs" to FileTypeAnimation(),
    )
}

@Composable
fun DataPage(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<StorageStatistics?>(null) }
    var busy by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<Int>() }
    var age by remember { mutableIntStateOf(0) }
    LaunchedEffect(busy) { if (!busy) stats = (acc.client.getStorageStatistics(chatLimit = 0) as? TdlResult.Success)?.result }
    Page(t("Data and Storage"), onBack) {
        SectionTitle(t("Automatic download"))
        SwitchRow(t("Photos"), null, Prefs.autoPhotos) { Prefs.autoPhotos = it }
        SwitchRow(t("GIFs"), null, Prefs.autoGifs) { Prefs.autoGifs = it }
        SwitchRow(t("Voice messages"), null, Prefs.autoVoice) { Prefs.autoVoice = it }
        SectionTitle(t("Transfers"))
        ClickRow(t("Downloads"), "›") { act.push(Screen.Page("downloads")) }
        ClickRow(t("Uploads"), "›") { act.push(Screen.Page("uploads")) }
        SectionTitle(t("Save to phone"))
        OutlinedTextField(
            Prefs.saveFolder, { Prefs.saveFolder = it.filter { c -> c.isLetterOrDigit() || c == ' ' || c == '_' || c == '-' }.take(40) },
            label = { Text(t("Folder name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        SwitchRow(t("Separate folders by type"), t("Photos, Videos, Music, Voice, Files"), Prefs.saveByType) { Prefs.saveByType = it }
        SectionTitle(t("Cache"))
        val s = stats
        Text(
            if (s == null) t("Counting…") else t("Used by Riv Post") + ": " + Fmt.size(s.size) + " · ${s.count} " + t("files"),
            fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Text(t("Choose what to clear"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        fileTypes.forEachIndexed { i, (name, _) ->
            Row(Modifier.fillMaxWidth().clickable { if (!chosen.remove(i)) chosen.add(i) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i in chosen, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(t(name), fontSize = 16.sp)
            }
        }
        Choice(t("Older than"), listOf("0" to t("Any age"), "7" to t("7 days"), "30" to t("30 days"), "90" to t("90 days")), age.toString()) { age = it.toInt() }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                acc.scope.launch {
                    val types = (if (chosen.isEmpty()) fileTypes.indices.toList() else chosen.toList()).map { fileTypes[it].second }.toTypedArray()
                    acc.client.optimizeStorage(
                        size = 0, ttl = age * 86400, count = 0, immunityDelay = 0, fileTypes = types,
                        chatIds = LongArray(0), excludeChatIds = LongArray(0), returnDeletedFileStatistics = false, chatLimit = 0,
                    )
                    withContext(Dispatchers.Main) { busy = false; toast(ctx, t("Cache cleared")) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) { Text(if (busy) t("Clearing…") else t("Clear selected")) }
    }
}

@Composable
fun DownloadsPage(acc: TgAccount, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<FileDownload>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        while (true) {
            list = (acc.client.searchFileDownloads(query = "", onlyActive = false, onlyCompleted = false, offset = "", limit = 100) as? TdlResult.Success)
                ?.result?.files?.toList() ?: emptyList()
            delay(1500)
        }
    }
    Page(t("Downloads"), onBack) {
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { acc.scope.launch { acc.client.toggleAllDownloadsArePaused(arePaused = true) }; tick++ }) { Text(t("Pause all")) }
            TextButton(onClick = { acc.scope.launch { acc.client.toggleAllDownloadsArePaused(arePaused = false) }; tick++ }) { Text(t("Resume all")) }
        }
        if (list.isEmpty()) Text(t("Files you download from chats appear here."), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (d in list) TransferRow(acc, d.fileId, Fmt.preview(acc, d.message), d.isPaused,
            onPause = { acc.scope.launch { acc.client.toggleDownloadIsPaused(fileId = d.fileId, isPaused = !d.isPaused) }; tick++ },
            onRemove = { acc.scope.launch { acc.client.removeFileFromDownloads(fileId = d.fileId, deleteFromCache = false) }; tick++ })
    }
}

@Composable
private fun TransferRow(acc: TgAccount, fileId: Int, name: String, paused: Boolean, upload: Boolean = false, onPause: (() -> Unit)?, onRemove: (() -> Unit)?) {
    var file by remember(fileId) { mutableStateOf(acc.fileById(fileId)) }
    LaunchedEffect(fileId) { if (file == null) file = (acc.client.getFile(fileId = fileId) as? TdlResult.Success)?.result }
    val cur = if (file != null) rememberFile(acc, file, autoDownload = false) else null
    var last by remember { mutableStateOf(0L to System.currentTimeMillis()) }
    var speed by remember { mutableStateOf(0L) }
    val done = if (upload) cur?.remote?.isUploadingCompleted == true else cur?.local?.isDownloadingCompleted == true
    val got = if (upload) cur?.remote?.uploadedSize ?: 0L else cur?.local?.downloadedSize ?: 0L
    val total = cur?.let { if (it.size > 0) it.size else it.expectedSize } ?: 0L
    LaunchedEffect(got) {
        val now = System.currentTimeMillis()
        val dt = now - last.second
        if (dt > 400) { speed = (got - last.first) * 1000 / dt; last = got to now }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
        LinearProgressIndicator(progress = { if (total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.size(got) + " / " + Fmt.size(total) + when { done -> " · " + t("Done"); paused -> " · " + t("Paused"); speed > 0 -> " · " + Fmt.size(speed) + "/s"; else -> "" },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (onPause != null && !done) TextButton(onClick = onPause) { Text(if (paused) t("Resume") else t("Pause")) }
            if (onRemove != null) TextButton(onClick = onRemove) { Text(t("Remove")) }
        }
    }
}

@Composable
fun UploadsPage(acc: TgAccount, onBack: () -> Unit) {
    val v by acc.uploadsVersion.collectAsState()
    val list = remember(v) { acc.uploads.values.sortedByDescending { it.id } }
    Page(t("Uploads"), onBack) {
        if (list.isEmpty()) Text(t("Files you send appear here while they upload."), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (f in list) TransferRow(acc, f.id, java.io.File(f.local.path).name, false, upload = true, onPause = null, onRemove = {
            acc.scope.launch { acc.client.cancelPreliminaryUploadFile(fileId = f.id) }
            acc.uploads.remove(f.id)
        })
        if (list.isNotEmpty()) TextButton(onClick = {
            acc.uploads.values.filter { it.remote.isUploadingCompleted }.forEach { acc.uploads.remove(it.id) }
            acc.uploadsVersion.value = acc.uploadsVersion.value + 1
        }, modifier = Modifier.padding(12.dp)) { Text(t("Clear finished")) }
    }
}

// ---------------------------------------------------------------- folders

@Composable
fun FoldersPage(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val folders by acc.folders.collectAsState()
    var creating by remember { mutableStateOf(false) }
    Page(t("Chat Folders"), onBack) {
        for (f in folders) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Doc, null, tint = LocalStyle.current.accent)
                Spacer(Modifier.width(14.dp))
                Text(f.name.text.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { acc.scope.launch { acc.client.deleteChatFolder(chatFolderId = f.id, leaveChatIds = LongArray(0)) } }) {
                    Text(t("Delete"), color = Color(0xFFE53935))
                }
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(18.dp)) { Text(t("Create folder")) }
    }
    if (creating) NewFolderDialog(acc, { creating = false }) { r -> creating = false; if (r != null) toast(ctx, r) }
}

@Composable
private fun NewFolderDialog(acc: TgAccount, onDismiss: () -> Unit, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf(false) }
    var bots by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf(false) }
    var others by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Long>() }
    val chats = remember { acc.chatList(ChatListMain()).map { it.first }.take(80) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Create folder")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(12) }, label = { Text(t("Folder name")) }, singleLine = true)
                for ((label, get, set) in listOf<Triple<String, Boolean, (Boolean) -> Unit>>(
                    Triple(t("Contacts"), contacts) { contacts = it }, Triple(t("Non-contacts"), others) { others = it },
                    Triple(t("Groups"), groups) { groups = it }, Triple(t("Channels"), channels) { channels = it }, Triple(t("Bots"), bots) { bots = it },
                )) {
                    Row(Modifier.fillMaxWidth().clickable { set(!get) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = get, onCheckedChange = { set(it) }); Text(label)
                    }
                }
                Text(t("Add chats"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                for (c in chats) {
                    Row(Modifier.fillMaxWidth().clickable { if (!picked.remove(c.id)) picked.add(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.id in picked, onCheckedChange = null); Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (picked.isNotEmpty() || groups || channels || bots || contacts || others), onClick = {
                acc.scope.launch {
                    val r = acc.client.createChatFolder(
                        folder = ChatFolder(
                            name = ChatFolderName(text = FormattedText(text = name.trim(), entities = emptyArray<TextEntity>()), animateCustomEmoji = false),
                            icon = null, colorId = -1, isShareable = false, pinnedChatIds = LongArray(0), includedChatIds = picked.toLongArray(),
                            excludedChatIds = LongArray(0), excludeMuted = false, excludeRead = false, excludeArchived = false,
                            includeContacts = contacts, includeNonContacts = others, includeBots = bots, includeGroups = groups, includeChannels = channels,
                        )
                    )
                    withContext(Dispatchers.Main) { onDone(if (r is TdlResult.Failure) r.message else t("Folder created")) }
                }
            }) { Text(t("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- power saving

@Composable
fun PowerPage(onBack: () -> Unit) {
    Page(t("Power Saving"), onBack) {
        SwitchRow(t("Power saving mode"), t("Turns off autoplay, animations, blur and glow"), Prefs.powerSave) { on ->
            Prefs.powerSave = on
            if (on) { Prefs.autoGifs = false; Prefs.animStickers = false; Prefs.glass = false; Prefs.bubbleGlow = false; Prefs.playerGlow = false; Prefs.mWavy = false }
        }
        SwitchRow(t("Play GIFs automatically"), null, Prefs.autoGifs) { Prefs.autoGifs = it }
        SwitchRow(t("Animated stickers"), null, Prefs.animStickers) { Prefs.animStickers = it }
        SwitchRow(t("Glass blur"), null, Prefs.glass) { Prefs.glass = it }
        SwitchRow(t("Wavy seek bar"), null, Prefs.mWavy) { Prefs.mWavy = it }
        SwitchRow(t("Player glow"), null, Prefs.playerGlow) { Prefs.playerGlow = it }
        SwitchRow(t("Stay connected for notifications"), null, Prefs.background) { Prefs.background = it }
    }
}
