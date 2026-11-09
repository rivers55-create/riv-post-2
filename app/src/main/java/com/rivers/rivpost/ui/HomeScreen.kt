package com.rivers.rivpost.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.BuildConfig
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.music.Music
import com.rivers.rivpost.tg.ChatItem
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.ChatList
import dev.g000sha256.tdl.dto.ChatListArchive
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ConnectionStateConnecting
import dev.g000sha256.tdl.dto.ConnectionStateConnectingToProxy
import dev.g000sha256.tdl.dto.ConnectionStateUpdating
import dev.g000sha256.tdl.dto.ConnectionStateWaitingForNetwork
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.UserStatusOnline
import kotlinx.coroutines.launch

data class NavTab(val id: String, val label: String, val icon: ImageVector)

fun navTabs(): List<NavTab> = buildList {
    add(NavTab("chats", t("Chats"), Ic.Chat))
    if (Prefs.navContacts) add(NavTab("contacts", t("Contacts"), Ic.Account))
    if (Prefs.navCalls) add(NavTab("calls", t("Calls"), Ic.Phone))
    if (Prefs.navMusic) add(NavTab("music", t("Music"), Ic.Note))
    if (Prefs.navVideos) add(NavTab("videos", t("Videos"), Ic.Movie))
    if (Prefs.navProfile) add(NavTab("profile", t("Profile"), Ic.Person))
}

@Composable
fun HomeScreen(act: MainActivity, acc: TgAccount) {
    val style = LocalStyle.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabs = navTabs()
    var page by remember { mutableStateOf(act.homePage) }
    if (tabs.none { it.id == page }) page = "chats"
    act.homePage = page
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folderTab by remember(acc) { mutableIntStateOf(0) }
    val folders by acc.folders.collectAsState()
    val ver by acc.chatsVersion.collectAsState()
    val extra by acc.extraVersion.collectAsState()

    val lists: List<Pair<String, ChatList>> = buildList {
        add(t("All") to ChatListMain())
        if (Prefs.showFolders) for (f in folders) add(f.name.text.text to ChatListFolder(chatFolderId = f.id))
        if (Prefs.showArchive) add(t("Archive") to ChatListArchive())
    }
    if (folderTab >= lists.size) folderTab = 0
    val counts = remember(extra, lists.size) {
        lists.map { (_, l) ->
            val c = acc.unreadCounts[acc.listKey(l)]
            if (c == null) 0 else if (Prefs.countMuted) c.first else c.second
        }
    }
    val mainUnread = counts.firstOrNull() ?: 0
    val showTabs = page == "chats" && lists.size > 1 && Prefs.tabsStyle != "off"

    val menuNav = rememberMenu(act, acc)

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                DrawerContent(acc, act, close = { scope.launch { drawer.close() } })
            }
        },
    ) {
        Scaffold(
            containerColor = style.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (page != "videos" || searching) Column(Modifier.background(style.topBar).statusBarsPadding()) {
                    TopTitleBar(
                        acc, page, searching, query,
                        onQuery = { query = it },
                        onSearch = { searching = !searching; if (!searching) query = "" },
                        onLogo = { scope.launch { drawer.open() } },
                        menu = menuNav,
                    )
                    if (Prefs.navPos == "top" && Prefs.navBar != "off" && tabs.size > 1) NavBar(tabs, page, mainUnread) { page = it }
                    if (showTabs && Prefs.tabsPos == "top") FolderTabs(lists, folderTab, counts) { folderTab = it }
                } else Spacer(Modifier.height(0.dp))
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer()
                    if (showTabs && Prefs.tabsPos == "bottom") FolderTabs(lists, folderTab, counts) { folderTab = it }
                    if (Prefs.navPos != "top" && Prefs.navBar != "off" && tabs.size > 1) NavBar(tabs, page, mainUnread) { page = it }
                }
            },
            floatingActionButton = {
                if (page == "chats" && Prefs.fab) FloatingActionButton(
                    onClick = { page = if (Prefs.navContacts) "contacts" else page; if (!Prefs.navContacts) act.push(Screen.NewGroup) },
                    containerColor = style.accent, contentColor = onColor(style.accent),
                ) { Icon(Ic.Edit, null) }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (page) {
                    "contacts" -> ContactsPage(acc, act, query)
                    "calls" -> CallsPage(acc, act)
                    "music" -> MusicLibraryPage(acc) { act.push(Screen.Sources("music")) }
                    "videos" -> VideoLibraryPage(acc, onPlay = { act.push(Screen.Video(it)) }) { act.push(Screen.Sources("video")) }
                    "profile" -> ProfilePage(acc, act)
                    else -> ChatListPage(acc, act, lists[folderTab].second, query, ver)
                }
            }
        }
    }
}

/** ⋮ menu items for the top bar. */
@Composable
private fun rememberMenu(act: MainActivity, acc: TgAccount): List<Pair<Pair<String, ImageVector>, () -> Unit>> {
    val scope = rememberCoroutineScope()
    return listOf(
        (if (Prefs.theme == "light") t("Night Mode") to Ic.Moon else t("Day Mode") to Ic.Sun) to {
            Prefs.theme = if (Prefs.theme == "light") "dark" else "light"
        },
        (t("New Group") to Ic.Group) to { act.push(Screen.NewGroup) },
        (t("Saved Messages") to Ic.Bookmark) to {
            scope.launch { acc.privateChat(acc.myId)?.let { act.push(Screen.Chat(it)) } }
            Unit
        },
        (t("Customize") to Ic.Palette) to { act.push(Screen.Customize) },
        (t("Settings") to Ic.Settings) to { act.push(Screen.Settings) },
    )
}

@Composable
private fun TopTitleBar(
    acc: TgAccount, page: String, searching: Boolean, query: String,
    onQuery: (String) -> Unit, onSearch: () -> Unit, onLogo: () -> Unit,
    menu: List<Pair<Pair<String, ImageVector>, () -> Unit>>,
) {
    val style = LocalStyle.current
    val fg = style.topBarFg
    val conn by acc.conn.collectAsState()
    val title = when (conn) {
        is ConnectionStateWaitingForNetwork -> t("Waiting for network…")
        is ConnectionStateConnecting -> t("Connecting…")
        is ConnectionStateConnectingToProxy -> t("Connecting to proxy…")
        is ConnectionStateUpdating -> t("Updating…")
        else -> when (page) {
            "contacts" -> t("Contacts"); "calls" -> t("Calls"); "music" -> t("Music"); "videos" -> t("Videos"); "profile" -> t("Profile")
            else -> Prefs.appTitle.ifBlank { "Riv Post" }
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val large = Prefs.titleStyle == "large"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (large) 10.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            IconButton(onClick = onSearch) { Icon(Ic.Back, null, tint = fg) }
            TextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(t("Search")) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = fg, unfocusedTextColor = fg,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            if (Prefs.showLogo) {
                Box(
                    Modifier.size(if (large) 46.dp else 38.dp).clip(CircleShape)
                        .border(2.dp, style.accent, CircleShape).padding(3.dp).clip(CircleShape)
                        .background(Color(0xFF2A0D4D)).clickable(onClick = onLogo),
                    contentAlignment = Alignment.Center,
                ) { Text("R", color = Color(0xFFB388FF), fontWeight = FontWeight.Bold, fontSize = if (large) 22.sp else 18.sp) }
            } else {
                IconButton(onClick = onLogo) { Icon(Ic.Menu, null, tint = fg) }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title, color = fg, fontWeight = FontWeight.Bold, fontSize = if (large) 28.sp else 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (page == "chats" || page == "contacts") IconButton(onClick = onSearch) { Icon(Ic.Search, null, tint = fg) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Ic.More, null, tint = fg) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(18.dp)) {
                    for ((label, action) in menu) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(label.second, null) },
                            text = { Text(label.first, fontSize = 16.sp) },
                            onClick = { menuOpen = false; action() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderTabs(lists: List<Pair<String, ChatList>>, selected: Int, counts: List<Int>, onSelect: (Int) -> Unit) {
    val style = LocalStyle.current
    val shown = if (Prefs.tabCounts) counts else null
    when (Prefs.tabsStyle) {
        "underline" -> PrimaryScrollableTabRow(
            selectedTabIndex = selected, edgePadding = 8.dp,
            containerColor = style.topBar, contentColor = style.accent,
        ) {
            lists.forEachIndexed { i, (name, _) ->
                Tab(selected = i == selected, onClick = { onSelect(i) }, text = {
                    val c = shown?.getOrNull(i) ?: 0
                    Text(name + if (c > 0) "  $c" else "", maxLines = 1, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
                })
            }
        }
        "text" -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
            lists.forEachIndexed { i, (name, _) ->
                val c = shown?.getOrNull(i) ?: 0
                Text(
                    name + if (c > 0) " ($c)" else "", fontSize = 16.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == selected) style.accent else style.topBarFg.copy(alpha = 0.65f),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        else -> PillTabs(lists.map { it.first }, selected, shown, onSelect)
    }
}

@Composable
fun NavBar(tabs: List<NavTab>, page: String, chatsBadge: Int, onSelect: (String) -> Unit) {
    val style = LocalStyle.current
    val floating = Prefs.navBar == "floating"
    Row(
        (if (floating) Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(34.dp))
        else Modifier.fillMaxWidth()).background(style.navBar).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tb in tabs) {
            val sel = tb.id == page
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(if (sel) style.pill else Color.Transparent)
                    .clickable { onSelect(tb.id) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Icon(tb.icon, null, tint = if (sel) style.pillFg else style.navBarFg, modifier = Modifier.size(26.dp))
                    if (tb.id == "chats" && chatsBadge > 0) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).clip(CircleShape).background(style.badge)
                            .padding(horizontal = 5.dp, vertical = 0.dp),
                    ) { Text(if (chatsBadge > 99) "99+" else "$chatsBadge", color = onColor(style.badge), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                if (Prefs.navLabels) Text(
                    tb.label, fontSize = 12.sp, maxLines = 1, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) style.pillFg else style.navBarFg,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- chat list

@Composable
private fun ChatListPage(acc: TgAccount, act: MainActivity, list: ChatList, query: String, ver: Long) {
    val style = LocalStyle.current
    val extra by acc.extraVersion.collectAsState()
    LaunchedEffect(acc, list) { acc.load(list) }
    val rows = remember(ver, list, query, extra) {
        val all = acc.chatList(list)
        if (query.isBlank()) all else all.filter { it.first.title.contains(query, ignoreCase = true) }
    }
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 8 } }
    LaunchedEffect(nearEnd, rows.size) { if (nearEnd && rows.isNotEmpty()) acc.load(list) }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (query.isNotBlank()) t("No chats found") else t("Loading chats…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.first.id }) { (c, pinned) ->
            ChatRow(acc, c, pinned, list, onClick = { act.push(Screen.Chat(c.id)) })
            if (style.dividers) HorizontalDivider(
                Modifier.padding(start = style.avatar + 26.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ChatAvatar(acc: TgAccount, c: ChatItem, size: androidx.compose.ui.unit.Dp) {
    val style = LocalStyle.current
    if (acc.isSaved(c)) {
        Box(Modifier.size(size).clip(style.avatarShape).background(Color(0xFF5B7CF0)), contentAlignment = Alignment.Center) {
            Icon(Ic.Bookmark, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
    } else {
        Box {
            Avatar(acc, c.id, c.title, c.photo, c.mini, size)
            if (Prefs.onlineDot && c.privateUserId != 0L && acc.statuses[c.privateUserId] is UserStatusOnline) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(size * 0.26f).clip(CircleShape).background(style.bg).padding(2.dp)
                        .clip(CircleShape).background(Color(0xFF4CD964)),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(acc: TgAccount, c: ChatItem, pinned: Boolean, list: ChatList, onClick: () -> Unit) {
    val style = LocalStyle.current
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val muted = acc.isMuted(c)
    val m = c.lastMessage
    val typing = acc.typingText(c.id)
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = style.rowPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Prefs.showAvatars) {
                ChatAvatar(acc, c, style.avatar)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.isSecret) { Icon(Ic.Lock, null, Modifier.size(15.dp), tint = Color(0xFF4CD964)); Spacer(Modifier.width(3.dp)) }
                    Text(
                        if (acc.isSaved(c)) t("Saved Messages") else c.title.ifBlank { t("Deleted account") },
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = style.title,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (acc.isVerified(c)) { Spacer(Modifier.width(4.dp)); Icon(Ic.Verified, null, Modifier.size(17.dp), tint = Color(0xFF5B8DEF)) }
                    if (muted) { Spacer(Modifier.width(4.dp)); Icon(Ic.Muted, null, Modifier.size(15.dp), tint = cs.onSurfaceVariant) }
                    Spacer(Modifier.weight(1f))
                    if (m != null && m.isOutgoing && !acc.isSaved(c)) {
                        Icon(if (m.id <= c.lastReadOutbox) Ic.DoneAll else Ic.Check, null, Modifier.size(17.dp), tint = style.accent)
                        Spacer(Modifier.width(4.dp))
                    }
                    val time = if (m != null) Fmt.listTime(m.date) else ""
                    if (pinned) {
                        Row(
                            Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ic.Pin, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(3.dp))
                            Text(time, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    } else {
                        Text(time, fontSize = 13.sp, color = if (c.unread > 0 && !muted) style.badge else cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (typing != null) {
                        val parts = typing.split("|")
                        val who = if (c.isGroup) parts[0] + " " else ""
                        Text(who + t(parts.getOrElse(1) { "typing" }) + "…", fontSize = 15.sp, color = style.accent, maxLines = 1, modifier = Modifier.weight(1f))
                    } else {
                        val who = if (m != null && c.isGroup && !Fmt.isService(m)) {
                            if (m.isOutgoing) t("You") else acc.senderName(m.senderId).split(" ").first()
                        } else ""
                        if (who.isNotBlank()) Text("$who: ", fontSize = 15.sp, color = style.title.copy(alpha = 0.85f), maxLines = 1)
                        if (Prefs.mediaThumbs && m != null) MediaThumb(acc, m)
                        val content = m?.content
                        val colored = content != null && content !is MessageText
                        Text(
                            Fmt.preview(acc, m).replace('\n', ' '),
                            fontSize = 15.sp, maxLines = style.previewLines, overflow = TextOverflow.Ellipsis,
                            color = if (colored) style.accent else cs.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                    }
                    if (c.mentions > 0) { Spacer(Modifier.width(6.dp)); CountBadge("@", style.badge) }
                    if (c.unread > 0 || c.markedUnread) {
                        Spacer(Modifier.width(6.dp))
                        CountBadge(if (c.unread > 0) (if (c.unread > 9999) "9999+" else c.unread.toString()) else "", if (muted) style.badgeMuted else style.badge)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (pinned) t("Unpin") else t("Pin")) }, onClick = {
                menu = false
                acc.scope.launch { acc.client.toggleChatIsPinned(chatList = list, chatId = c.id, isPinned = !pinned) }
            })
            DropdownMenuItem(text = { Text(if (muted) t("Unmute") else t("Mute")) }, onClick = { menu = false; acc.setMuted(c, !muted) })
            if (c.unread > 0 && m != null) DropdownMenuItem(text = { Text(t("Mark as read")) }, onClick = {
                menu = false
                acc.markAllRead(c.id, longArrayOf(m.id))
            })
        }
    }
}

@Composable
private fun MediaThumb(acc: TgAccount, m: dev.g000sha256.tdl.dto.Message) {
    val mini = when (val c = m.content) {
        is MessagePhoto -> c.photo.minithumbnail?.data
        is MessageVideo -> c.video.minithumbnail?.data
        is MessageAnimation -> c.animation.minithumbnail?.data
        is MessageAudio -> c.audio.albumCoverMinithumbnail?.data
        else -> null
    } ?: return
    val bmp = remember(m.id) { miniBitmap(mini) } ?: return
    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(5.dp))
}

@Composable
fun CountBadge(text: String, color: Color) {
    Box(
        Modifier.height(24.dp).widthIn(min = 24.dp).clip(CircleShape).background(color).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = onColor(color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

// ---------------------------------------------------------------- drawer

@Composable
private fun DrawerContent(acc: TgAccount, act: MainActivity, close: () -> Unit) {
    val me by acc.me.collectAsState()
    val accounts by Tg.accounts.collectAsState()
    val active by Tg.activeIndex.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)).statusBarsPadding().padding(20.dp)) {
            val u = me
            Avatar(acc, u?.id ?: 0L, acc.userName(u?.id ?: 0L).ifBlank { "R" }, u?.profilePhoto?.small, u?.profilePhoto?.minithumbnail?.data, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(acc.userName(u?.id ?: 0L), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (u != null && u.phoneNumber.isNotBlank()) Text("+" + u.phoneNumber, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(t("Accounts"), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        for (i in Prefs.accountList()) {
            val a = accounts[i]
            if (a != null) AccountRow(a, i == active) { Tg.switchTo(i); close() }
        }
        DrawerItem(Ic.Add, t("Add account")) { close(); Tg.addAccount() }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DrawerItem(Ic.Group, t("New Group")) { close(); act.push(Screen.NewGroup) }
        DrawerItem(Ic.Bookmark, t("Saved Messages")) {
            close()
            acc.scope.launch { acc.privateChat(acc.myId)?.let { id -> act.runOnUiThread { act.push(Screen.Chat(id)) } } }
        }
        DrawerItem(Ic.Palette, t("Customize")) { close(); act.push(Screen.Customize) }
        DrawerItem(Ic.Settings, t("Settings")) { close(); act.push(Screen.Settings) }
        Spacer(Modifier.height(16.dp))
        Text("Riv Post " + BuildConfig.VERSION_NAME, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(text, fontSize = 16.sp)
    }
}

@Composable
private fun AccountRow(a: TgAccount, isActive: Boolean, onClick: () -> Unit) {
    val st by a.auth.collectAsState()
    val am by a.me.collectAsState()
    if (st is AuthorizationStateReady) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(a, am?.id ?: 0L, a.userName(am?.id ?: 0L).ifBlank { "?" }, am?.profilePhoto?.small, null, 36.dp)
            Spacer(Modifier.width(14.dp))
            Text(a.userName(am?.id ?: 0L), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isActive) Icon(Ic.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
