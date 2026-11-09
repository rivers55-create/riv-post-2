package com.rivers.rivpost.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.media.VideoLib
import com.rivers.rivpost.music.MusicLib
import com.rivers.rivpost.tg.ChatItem
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ImportedContact
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.ReportChatResultOptionRequired
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterVoiceNote
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.dto.UserFullInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- contacts

@Composable
fun ContactsPage(acc: TgAccount, act: MainActivity, query: String) {
    val cs = MaterialTheme.colorScheme
    val uv by acc.usersVersion.collectAsState()
    var ids by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(acc) {
        ids = (acc.client.getContacts() as? TdlResult.Success)?.result?.userIds?.toList() ?: emptyList()
    }
    val list = remember(ids, uv, query) {
        (ids ?: emptyList()).mapNotNull { acc.users[it] }
            .filter { query.isBlank() || (it.firstName + " " + it.lastName).contains(query, true) }
            .sortedWith(compareByDescending<User> { acc.status(it.id) is dev.g000sha256.tdl.dto.UserStatusOnline }.thenBy { it.firstName.lowercase() })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            RowItem(Ic.Group, t("New Group")) { act.push(Screen.NewGroup) }
        }
        if (ids == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        items(list, key = { it.id }) { u ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    acc.scope.launch { acc.privateChat(u.id)?.let { id -> act.runOnUiThread { act.push(Screen.Chat(id)) } } }
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(acc, u.id, acc.userName(u.id), u.profilePhoto?.small, u.profilePhoto?.minithumbnail?.data, 50.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(acc.userName(u.id), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val st = Fmt.lastSeenStatus(acc.status(u.id), u)
                    Text(st, fontSize = 14.sp, color = if (st == t("online")) LocalStyle.current.accent else cs.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun RowItem(icon: ImageVector, text: String, sub: String? = null, color: Color? = null, onClick: () -> Unit) {
    val style = LocalStyle.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color ?: style.accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- calls

@Composable
fun CallsPage(acc: TgAccount, act: MainActivity) {
    val cs = MaterialTheme.colorScheme
    var calls by remember { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(acc) {
        calls = (acc.client.searchCallMessages(offset = "", limit = 100, onlyMissed = false) as? TdlResult.Success)?.result?.messages?.toList() ?: emptyList()
    }
    val l = calls
    if (l == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                t("Calling from Riv Post is coming in a later version. Here is your call history."),
                fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
        }
        if (l.isEmpty()) item { Text(t("No calls yet"), color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        items(l, key = { "${it.chatId}_${it.id}" }) { m ->
            val c = acc.chats[m.chatId]
            val call = m.content as? MessageCall
            val missed = call?.discardReason is CallDiscardReasonMissed || (call?.discardReason is CallDiscardReasonDeclined && !m.isOutgoing)
            Row(
                Modifier.fillMaxWidth().clickable { act.push(Screen.Chat(m.chatId)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (c != null) Avatar(acc, c.id, c.title, c.photo, c.mini, 50.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c?.title ?: "", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (missed) Color(0xFFE53935) else cs.onSurface, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (m.isOutgoing) Ic.CallOut else Ic.CallIn, null, Modifier.size(15.dp), tint = if (missed) Color(0xFFE53935) else Color(0xFF4CD964))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Fmt.listTime(m.date) + (if ((call?.duration ?: 0) > 0) " · " + Fmt.duration(call?.duration ?: 0) else "") +
                                (if (call?.isVideo == true) " · " + t("Video") else ""),
                            fontSize = 14.sp, color = cs.onSurfaceVariant,
                        )
                    }
                }
                Icon(Ic.Phone, null, tint = LocalStyle.current.accent)
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ---------------------------------------------------------------- my profile

@Composable
fun ProfilePage(acc: TgAccount, act: MainActivity) {
    val me by acc.me.collectAsState()
    val cs = MaterialTheme.colorScheme
    val u = me ?: return
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Spacer(Modifier.height(20.dp))
            Avatar(acc, u.id, acc.userName(u.id), u.profilePhoto?.small, u.profilePhoto?.minithumbnail?.data, 110.dp)
            Spacer(Modifier.height(12.dp))
            Text(acc.userName(u.id), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val un = u.usernames?.activeUsernames?.firstOrNull()
            Text(listOfNotNull(if (u.phoneNumber.isNotBlank()) "+" + u.phoneNumber else null, un?.let { "@$it" }).joinToString(" • "), color = cs.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
        }
        item {
            Card {
                RowItem(Ic.Bookmark, t("Saved Messages"), color = Color(0xFF5B7CF0)) {
                    acc.scope.launch { acc.privateChat(u.id)?.let { id -> act.runOnUiThread { act.push(Screen.Chat(id)) } } }
                }
                RowItem(Ic.Palette, t("Customize"), t("Bars, colors, bubbles, everything"), Color(0xFFE5508A)) { act.push(Screen.Customize) }
                RowItem(Ic.Settings, t("Settings"), t("Notifications, privacy, proxy, accounts"), Color(0xFF8E85EE)) { act.push(Screen.Hub) }
                RowItem(Ic.Note, t("Music sources"), t("Choose which chats feed the music library"), Color(0xFFFF8A3D)) { act.push(Screen.Sources("music")) }
                RowItem(Ic.Movie, t("Video sources"), t("Choose which chats get a row in Videos"), Color(0xFF00A884)) { act.push(Screen.Sources("video")) }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(LocalStyle.current.navBar).padding(vertical = 4.dp),
    ) { content() }
}

// ---------------------------------------------------------------- picker (forward, sources, new group)

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, fab: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Ic.Back, null) } },
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
            )
        },
        floatingActionButton = { fab?.invoke() },
    ) { pad -> Box(Modifier.fillMaxSize().padding(pad)) { content() } }
}

/** Pick chats for the music or video library. */
@Composable
fun SourcesScreen(acc: TgAccount, kind: String, onBack: () -> Unit) {
    val ver by acc.chatsVersion.collectAsState()
    var query by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val chats = remember(ver, query) {
        acc.chatList(ChatListMain()).map { it.first }.filter { query.isBlank() || it.title.contains(query, true) }
    }
    PageScaffold(if (kind == "music") t("Music sources") else t("Video sources"), onBack) {
        Column {
            Text(
                if (kind == "music") t("Pick chats and channels for your music. If none are picked, every chat is used.")
                else t("Each chat you pick gets its own row in Videos."),
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { Text(t("Search")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { c ->
                    @Suppress("UNUSED_VARIABLE") val x = tick
                    val on = if (kind == "music") MusicLib.isSource(c.id) else VideoLib.isSource(c.id)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (kind == "music") MusicLib.toggleSource(c.id) else VideoLib.toggleSource(c.id)
                            tick++
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(acc, c.id, c.title, c.photo, c.mini, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(c.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Checkbox(checked = on, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

/** Pick a chat to forward messages to. */
@Composable
fun ForwardScreen(acc: TgAccount, fromChat: Long, ids: LongArray, onDone: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val ver by acc.chatsVersion.collectAsState()
    var query by remember { mutableStateOf("") }
    val chats = remember(ver, query) { acc.chatList(ChatListMain()).map { it.first }.filter { query.isBlank() || it.title.contains(query, true) } }
    var clean by remember { mutableStateOf(Prefs.cleanForward) }
    PageScaffold(t("Forward to…"), { onDone(null) }) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { clean = !clean }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Hide \"Forwarded from\""), modifier = Modifier.weight(1f))
                Switch(checked = clean, onCheckedChange = { clean = it })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { Text(t("Search")) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            acc.scope.launch {
                                val r = acc.client.forwardMessages(chatId = c.id, fromChatId = fromChat, messageIds = ids, sendCopy = clean, removeCaption = false)
                                withContext(Dispatchers.Main) {
                                    if (r is TdlResult.Failure) toast(ctx, r.message) else toast(ctx, t("Forwarded"))
                                    onDone(c.id)
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChatAvatar(acc, c, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(if (acc.isSaved(c)) t("Saved Messages") else c.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun NewGroupScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ids by remember { mutableStateOf<List<Long>>(emptyList()) }
    val picked = remember { mutableStateListOf<Long>() }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(acc) { ids = (acc.client.getContacts() as? TdlResult.Success)?.result?.userIds?.toList() ?: emptyList() }
    PageScaffold(t("New Group"), onBack, fab = {
        if (picked.isNotEmpty() && title.isNotBlank() && !busy) FloatingActionButton(onClick = {
            busy = true
            acc.scope.launch {
                val r = acc.client.createNewBasicGroupChat(userIds = picked.toLongArray(), title = title.trim(), messageAutoDeleteTime = 0)
                withContext(Dispatchers.Main) {
                    busy = false
                    if (r is TdlResult.Success) { onBack(); act.push(Screen.Chat(r.result.chatId)) } else if (r is TdlResult.Failure) toast(ctx, r.message)
                }
            }
        }) { Icon(Ic.Check, null) }
    }) {
        Column {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(128) }, singleLine = true, label = { Text(t("Group name")) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Text(t("Add members") + " (${picked.size})", modifier = Modifier.padding(horizontal = 16.dp), color = LocalStyle.current.accent, fontWeight = FontWeight.SemiBold)
            LazyColumn(Modifier.fillMaxSize()) {
                items(ids, key = { it }) { id ->
                    val u = acc.users[id]
                    Row(
                        Modifier.fillMaxWidth().clickable { if (!picked.remove(id)) picked.add(id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(acc, id, acc.userName(id), u?.profilePhoto?.small, u?.profilePhoto?.minithumbnail?.data, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(acc.userName(id), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Checkbox(checked = id in picked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- chat / user profile

@Composable
fun ChatInfoScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val style = LocalStyle.current
    val ver by acc.chatsVersion.collectAsState()
    val uv by acc.usersVersion.collectAsState()
    val c: ChatItem = remember(ver) { acc.chats[chatId] } ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val userId = c.privateUserId
    val user = remember(uv) { acc.users[userId] }
    var full by remember { mutableStateOf<UserFullInfo?>(null) }
    var desc by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(chatId) {
        if (userId != 0L) full = (acc.client.getUserFullInfo(userId = userId) as? TdlResult.Success)?.result
        if (c.supergroupId != 0L) desc = (acc.client.getSupergroupFullInfo(supergroupId = c.supergroupId) as? TdlResult.Success)?.result?.description ?: ""
    }
    val muted = acc.isMuted(c)
    val tabs = listOf(
        t("Media") to SearchMessagesFilterPhotoAndVideo(), t("Files") to SearchMessagesFilterDocument(),
        t("Links") to SearchMessagesFilterUrl(), t("Voice") to SearchMessagesFilterVoiceNote(), t("Music") to SearchMessagesFilterAudio(),
    )
    var media by remember(tab) { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(tab) {
        media = (acc.client.searchChatMessages(chatId = chatId, query = "", fromMessageId = 0, offset = 0, limit = 60, filter = tabs[tab].second) as? TdlResult.Success)
            ?.result?.messages?.toList() ?: emptyList()
    }

    Box(Modifier.fillMaxSize().background(style.bg)) {
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(56.dp))
                    ChatAvatar(acc, c, 110.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(if (acc.isSaved(c)) t("Saved Messages") else c.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    val sub = when {
                        userId != 0L -> Fmt.lastSeenStatus(acc.status(userId), user)
                        c.isChannel -> "${acc.memberCount(c)} " + t("subscribers")
                        c.isGroup -> "${acc.memberCount(c)} " + t("members")
                        else -> ""
                    }
                    Text(sub, color = cs.onSurfaceVariant, fontSize = 16.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionTile(Ic.Chat, t("Message"), Modifier.weight(1f)) { onBack(); act.push(Screen.Chat(chatId)) }
                        ActionTile(Ic.Muted, if (muted) t("Unmute") else t("Mute"), Modifier.weight(1f)) { acc.setMuted(c, !muted) }
                        if (c.isGroup) ActionTile(Ic.Group, t("Members"), Modifier.weight(1f)) { act.push(Screen.Page("members:$chatId")) }
                        if (c.isGroup || c.isChannel) ActionTile(Ic.Settings, t("Manage"), Modifier.weight(1f)) { act.push(Screen.Page("admin:$chatId")) }
                        if (userId != 0L) {
                            ActionTile(Ic.Phone, t("Call"), Modifier.weight(1f)) { toast(ctx, t("Calls are coming in a later version")) }
                            ActionTile(Ic.Movie, t("Video"), Modifier.weight(1f)) { toast(ctx, t("Calls are coming in a later version")) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp)).background(style.navBar).padding(vertical = 6.dp),
                    ) {
                        val un = user?.usernames?.activeUsernames?.firstOrNull()
                            ?: acc.supergroups[c.supergroupId]?.usernames?.activeUsernames?.firstOrNull()
                        if (un != null) InfoLine("@$un", t("Username"))
                        if (user != null && user.phoneNumber.isNotBlank()) InfoLine("+" + user.phoneNumber, t("Phone"))
                        full?.birthdate?.let { b ->
                            val txt = "%02d/%02d".format(b.day, b.month) + if (b.year > 0) "/${b.year}" else ""
                            InfoLine(txt, t("Birthday"))
                        }
                        full?.bio?.text?.takeIf { it.isNotBlank() }?.let { InfoLine(it, t("Bio")) }
                        if (desc.isNotBlank()) InfoLine(desc, t("Description"))
                        @Suppress("UNUSED_VARIABLE") val x = tick
                        LibSwitch(t("Add to Music library"), MusicLib.isSource(chatId)) { MusicLib.toggleSource(chatId); tick++ }
                        LibSwitch(t("Add to Videos (own row)"), VideoLib.isSource(chatId)) { VideoLib.toggleSource(chatId); tick++ }
                    }
                    Spacer(Modifier.height(12.dp))
                    PillTabs(tabs.map { it.first }, tab) { tab = it }
                }
            }
            val l = media
            if (l == null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (tab == 0) {
                items(l, key = { it.id }) { m -> MediaCell(acc, m) { onBack(); act.push(Screen.Chat(chatId, m.id)) } }
            } else {
                items(l, key = { it.id }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { m ->
                    val track = remember(m.id) { com.rivers.rivpost.music.Track.from(acc.index, m) }
                    val url = remember(m.id) {
                        val ct = m.content
                        val txt = (ct as? dev.g000sha256.tdl.dto.MessageText)?.text
                        txt?.entities?.firstNotNullOfOrNull { e -> (e.type as? dev.g000sha256.tdl.dto.TextEntityTypeTextUrl)?.url }
                            ?: Regex("https?://\\S+").find(txt?.text ?: "")?.value
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            when {
                                track != null -> com.rivers.rivpost.music.Music.play(l.mapNotNull { x -> com.rivers.rivpost.music.Track.from(acc.index, x) }, l.mapNotNull { x -> com.rivers.rivpost.music.Track.from(acc.index, x) }.indexOfFirst { it.key == track.key }.coerceAtLeast(0))
                                tab == 2 && url != null -> try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
                                else -> { onBack(); act.push(Screen.Chat(chatId, m.id)) }
                            }
                        }.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.preview(acc, m), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text(Fmt.listTime(m.date) + (if (url != null && tab == 2) "  ·  $url" else ""), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (tab != 0) IconButton(onClick = { onBack(); act.push(Screen.Chat(chatId, m.id)) }) { Icon(Ic.Forward, null, tint = cs.onSurfaceVariant) }
                    }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Spacer(Modifier.height(60.dp)) }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null) }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Ic.More, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                    MenuItem(Ic.Refresh, t("Auto-Delete")) { menu = false; dialog = "autodelete" }
                    if (userId != 0L && !acc.isSaved(c)) {
                        MenuItem(Ic.Close, t("Block user")) { menu = false; dialog = "block" }
                        MenuItem(Ic.Edit, t("Edit Contact")) { menu = false; dialog = "edit" }
                        MenuItem(Ic.Delete, t("Delete Contact")) {
                            menu = false
                            acc.scope.launch { acc.client.removeContacts(userIds = longArrayOf(userId)) }
                            toast(ctx, t("Deleted"))
                        }
                        if (!c.isSecret) MenuItem(Ic.Lock, t("Start Secret Chat")) {
                            menu = false
                            acc.scope.launch {
                                val r = acc.client.createNewSecretChat(userId = userId)
                                withContext(Dispatchers.Main) {
                                    if (r is TdlResult.Success) act.push(Screen.Chat(r.result.id)) else if (r is TdlResult.Failure) toast(ctx, r.message)
                                }
                            }
                        }
                    }
                    MenuItem(Ic.Add, t("Add to Home Screen")) { menu = false; addShortcut(ctx, acc, c) }
                    MenuItem(Ic.Info, t("Report"), Color(0xFFE53935)) { menu = false; dialog = "report" }
                }
            }
        }
    }

    when (dialog) {
        "autodelete" -> ChoiceDialog(
            t("Auto-Delete"), listOf(t("Off") to 0, t("1 day") to 86400, t("1 week") to 604800, t("1 month") to 2678400),
            onDismiss = { dialog = "" },
        ) { secs ->
            acc.scope.launch { acc.client.setChatMessageAutoDeleteTime(chatId = chatId, messageAutoDeleteTime = secs) }
            dialog = ""
        }
        "block" -> ConfirmDialog(t("Block user?"), t("They won't be able to message you."), { dialog = "" }) {
            acc.scope.launch { acc.client.setMessageSenderBlockList(senderId = MessageSenderUser(userId = userId), blockList = BlockListMain()) }
            dialog = ""
        }
        "edit" -> {
            var first by remember { mutableStateOf(user?.firstName ?: "") }
            var last by remember { mutableStateOf(user?.lastName ?: "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Edit Contact")) },
                text = {
                    Column {
                        OutlinedTextField(first, { first = it }, label = { Text(t("First name")) }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(last, { last = it }, label = { Text(t("Last name (optional)")) }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        acc.scope.launch {
                            acc.client.addContact(
                                userId = userId,
                                contact = ImportedContact(phoneNumber = user?.phoneNumber ?: "", firstName = first.trim(), lastName = last.trim(), note = null),
                                sharePhoneNumber = false,
                            )
                        }
                        dialog = ""
                    }) { Text(t("Save")) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
        "report" -> ReportDialog(acc, chatId) { dialog = "" }
    }
}

@Composable
private fun LibSwitch(text: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { onClick() })
    }
}

@Composable
private fun InfoLine(value: String, label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(value, fontSize = 18.sp)
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(LocalStyle.current.navBar).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MediaCell(acc: TgAccount, m: Message, onClick: () -> Unit) {
    val (file, mini, dur) = when (val c = m.content) {
        is MessagePhoto -> Triple(c.photo.sizes.firstOrNull { it.type == "m" }?.photo ?: c.photo.sizes.firstOrNull()?.photo, c.photo.minithumbnail?.data, 0)
        is MessageVideo -> Triple(c.video.thumbnail?.file, c.video.minithumbnail?.data, c.video.duration)
        else -> Triple(null, null, 0)
    }
    val f = rememberFile(acc, file, autoDownload = true, priority = 1)
    val mb = remember(m.id) { miniBitmap(mini) }
    Box(Modifier.aspectRatio(1f).padding(1.dp).background(Color(0xFF222222)).clickable(onClick = onClick)) {
        if (f != null && f.local.isDownloadingCompleted) {
            AsyncImage(model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (mb != null) Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (dur > 0) Text(
            "▶ " + Fmt.duration(dur), color = Color.White, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
        )
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String, color: Color? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null, tint = color ?: MaterialTheme.colorScheme.onSurface) },
        text = { Text(text, fontSize = 16.sp, color = color ?: MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<String, T>>, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for ((label, v) in options) {
                    Text(label, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onOk) { Text(t("Yes"), color = Color(0xFFE53935)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun ReportDialog(acc: TgAccount, chatId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<ReportChatResultOptionRequired?>(null) }
    LaunchedEffect(chatId) {
        val r = acc.client.reportChat(chatId = chatId, optionId = ByteArray(0), messageIds = LongArray(0), text = "")
        val res = (r as? TdlResult.Success)?.result
        if (res is ReportChatResultOptionRequired) options = res else { toast(ctx, t("Reported")); onDone() }
    }
    val o = options ?: return
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(o.title.ifBlank { t("Report") }) },
        text = {
            Column {
                for (op in o.options) {
                    Text(op.text, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            acc.client.reportChat(chatId = chatId, optionId = op.id, messageIds = LongArray(0), text = "")
                            toast(ctx, t("Reported"))
                            onDone()
                        }
                    }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(t("Cancel")) } },
    )
}

private fun addShortcut(ctx: android.content.Context, acc: TgAccount, c: ChatItem) {
    try {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .putExtra("acc", acc.index).putExtra("chat", c.id)
        val s = ShortcutInfoCompat.Builder(ctx, "chat_${acc.index}_${c.id}")
            .setShortLabel(c.title.ifBlank { "Chat" }.take(24))
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_purple))
            .setIntent(i).build()
        ShortcutManagerCompat.requestPinShortcut(ctx, s, null)
    } catch (_: Throwable) {
        toast(ctx, t("Couldn't add the shortcut"))
    }
}
