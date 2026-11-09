package com.rivers.rivpost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.media.Channel
import com.rivers.rivpost.media.M3uParser
import com.rivers.rivpost.media.SmbClient
import com.rivers.rivpost.media.SmbEntry
import com.rivers.rivpost.media.SmbServer
import com.rivers.rivpost.music.Music
import com.rivers.rivpost.music.MusicLib
import com.rivers.rivpost.tg.TgAccount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun PageTop(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Ic.Back, null) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

// ---------------------------------------------------------------- IPTV

@Composable
fun IptvScreen(act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var lists by remember { mutableStateOf(M3uParser.lists()) }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }

    val cur = open
    BackHandler { if (cur != null) open = null else onBack() }
    LaunchedEffect(cur) {
        if (cur == null) return@LaunchedEffect
        loading = true
        channels = try { M3uParser.load(ctx, cur.second) } catch (e: Throwable) { toast(ctx, t("Couldn't load the list") + ": " + (e.message ?: "")); emptyList() }
        group = ""
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        if (cur == null) {
            PageTop(t("IPTV"), onBack) { IconButton(onClick = { add = true }) { Icon(Ic.Add, null) } }
            if (lists.isEmpty()) Text(
                t("Add an M3U playlist link to watch live TV channels."),
                color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
            LazyColumn {
                items(lists, key = { it.first + it.second }) { l ->
                    Row(Modifier.fillMaxWidth().clickable { open = l }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Movie, null, tint = cs.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.first, fontWeight = FontWeight.SemiBold)
                            Text(l.second, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { lists = lists - l; M3uParser.saveLists(lists) }) { Icon(Ic.Delete, null) }
                    }
                }
            }
        } else {
            PageTop(cur.first, { open = null })
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val groups = remember(channels) { channels.map { it.group }.distinct() }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(t("Search channels")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            if (groups.size > 1) LazyRow(Modifier.padding(vertical = 6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = group.isEmpty(), onClick = { group = "" }, label = { Text(t("All")) }) }
                items(groups) { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
            }
            val shown = channels.filter { (group.isEmpty() || it.group == group) && (query.isBlank() || it.name.contains(query.trim(), true)) }
            LazyColumn {
                items(shown, key = { it.url + it.name }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            act.push(Screen.Video(PlayRequest(-1, null, c.name, "iptv:" + c.url, c.url, c.userAgent, c.referer, live = true)))
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (c.logo.isNotBlank()) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                            else Icon(Ic.Movie, null)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.group, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text(t("Add playlist")) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("Name")) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(t("M3U link")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) {
                        lists = lists + (name.ifBlank { t("Playlist") } to url.trim())
                        M3uParser.saveLists(lists)
                        add = false
                    }
                }) { Text(t("Add")) }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text(t("Cancel")) } },
        )
    }
}

// ---------------------------------------------------------------- SMB

@Composable
fun SmbScreen(act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var servers by remember { mutableStateOf(SmbClient.servers()) }
    val path = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    val url = path.lastOrNull()
    BackHandler { if (path.isNotEmpty()) path.removeAt(path.lastIndex) else onBack() }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        loading = true
        entries = try { SmbClient.list(url) } catch (e: Throwable) { toast(ctx, t("Couldn't open the folder") + ": " + (e.message ?: "")); emptyList() }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        if (url == null) {
            PageTop(t("Shared folders (SMB)"), onBack) { IconButton(onClick = { add = true }) { Icon(Ic.Add, null) } }
            if (servers.isEmpty()) Text(
                t("Add a computer or NAS on your network to play its videos and music."),
                color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
            LazyColumn {
                items(servers, key = { it.name + it.host + it.share }) { s ->
                    Row(Modifier.fillMaxWidth().clickable { path.add(SmbClient.rootUrl(s)) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Folder, null, tint = cs.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name.ifBlank { s.host }, fontWeight = FontWeight.SemiBold)
                            Text("smb://" + s.host + "/" + s.share, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { servers = servers - s; SmbClient.saveServers(servers) }) { Icon(Ic.Delete, null) }
                    }
                }
            }
        } else {
            PageTop(url.trimEnd('/').substringAfterLast('/'), { path.removeAt(path.lastIndex) })
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn {
                items(entries, key = { it.url }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (e.isDir) path.add(e.url)
                            else act.push(Screen.Video(PlayRequest(-1, null, e.name, "smb:" + e.url, e.url)))
                        }.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (e.isDir) Ic.Folder else Ic.Movie, null, tint = if (e.isDir) cs.primary else cs.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!e.isDir && e.size > 0) Text(com.rivers.rivpost.tg.Fmt.size(e.size), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var share by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var domain by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text(t("Add shared folder")) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("Name")) }, singleLine = true)
                    OutlinedTextField(value = host, onValueChange = { host = it.trim() }, label = { Text(t("Computer address (e.g. 192.168.1.5)")) }, singleLine = true)
                    OutlinedTextField(value = share, onValueChange = { share = it.trim() }, label = { Text(t("Shared folder name")) }, singleLine = true)
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(t("User (empty for guest)")) }, singleLine = true)
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text(t("Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text(t("Domain (optional)")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (host.isNotBlank()) {
                        servers = servers + SmbServer(name.ifBlank { host }, host, share, user, pass, domain)
                        SmbClient.saveServers(servers)
                        add = false
                    }
                }) { Text(t("Add")) }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text(t("Cancel")) } },
        )
    }
}


// ---------------------------------------------------------------- hidden songs

@Composable
fun HiddenSongsScreen(acc: TgAccount, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler { onBack() }
    @Suppress("UNUSED_VARIABLE") val watch = Prefs.hiddenSongs + Prefs.hiddenChats
    val hidden = Music.hiddenSet()
    val songs = MusicLib.everything().filter { it.key in hidden }
    val chats = Prefs.idSet(Prefs.hiddenChats).toList()
    Column(Modifier.fillMaxSize()) {
        PageTop(t("Hidden songs"), onBack)
        if (songs.isEmpty() && chats.isEmpty() && hidden.isEmpty()) Text(t("Nothing hidden."), color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        LazyColumn {
            if (chats.isNotEmpty()) item { SectionTitle(t("Hidden chats")) }
            items(chats, key = { "c$it" }) { id ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(acc.chats[id]?.title ?: id.toString(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { Music.hideChat(id) }) { Text(t("Show")) }
                }
            }
            if (songs.isNotEmpty()) item { SectionTitle(t("Hidden songs")) }
            items(songs, key = { it.key }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackArt(s, Modifier.size(44.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    TextButton(onClick = { Music.unhide(s.key) }) { Text(t("Show")) }
                }
            }
            val unknown = hidden.size - songs.size
            if (unknown > 0) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$unknown " + t("more hidden songs not loaded yet"), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Prefs.hiddenSongs = songs.joinToString("\n") { it.key } }) { Text(t("Show all")) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- floating music drawer

/** A small tab on the screen edge that opens music controls, on screens without the mini player. */
@Composable
fun MusicDrawer(act: MainActivity) {
    val s = Music.current ?: return
    if (!Prefs.musicDrawer || Music.playerOpen) return
    val top = act.stack.lastOrNull()
    if (top == null || top is Screen.Chats || top is Screen.Video || top is Screen.VideoNote || top is Screen.WebApp || top is Screen.Camera) return
    val style = LocalStyle.current
    var open by remember { mutableStateOf(false) }
    var y by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterEnd).offset { IntOffset(0, y.roundToInt()) }
                .pointerInput(Unit) { detectVerticalDragGestures { _, d -> y = (y + d).coerceIn(-900f, 900f) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(22.dp).height(70.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)).background(style.accent)
                    .clickable { open = !open },
                contentAlignment = Alignment.Center,
            ) { Icon(if (Music.isPlaying) Ic.Note else Ic.Play, null, tint = onColor(style.accent), modifier = Modifier.size(16.dp)) }
            AnimatedVisibility(open) {
                Row(
                    Modifier.clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)).background(style.navBar).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(s, Modifier.size(46.dp).clickable { Music.playerOpen = true; open = false }, RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(120.dp).clickable { Music.playerOpen = true; open = false }) {
                        Text(s.title, color = style.navBarFg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, color = style.navBarFg.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { Music.previous() }) { Icon(Ic.SkipPrev, null, tint = style.navBarFg) }
                    IconButton(onClick = { Music.toggle() }) { Icon(if (Music.isPlaying) Ic.Pause else Ic.Play, null, tint = style.navBarFg) }
                    IconButton(onClick = { Music.next() }) { Icon(Ic.SkipNext, null, tint = style.navBarFg) }
                }
            }
        }
    }
}
