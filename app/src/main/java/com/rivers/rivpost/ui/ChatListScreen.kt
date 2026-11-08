package com.rivers.rivpost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.BuildConfig
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
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
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(acc: TgAccount, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val style = LocalStyle.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val version by acc.chatsVersion.collectAsState()
    val folders by acc.folders.collectAsState()
    val conn by acc.conn.collectAsState()
    var tab by remember(acc) { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Tabs: All, folders…, Archive
    val tabs: List<Pair<String, ChatList>> = buildList {
        add(t("All chats") to ChatListMain())
        if (Prefs.showFolders) for (f in folders) add(f.name.text.text to ChatListFolder(chatFolderId = f.id))
        if (Prefs.showArchive) add(t("Archive") to ChatListArchive())
    }
    if (tab >= tabs.size) tab = 0
    val list = tabs[tab].second

    LaunchedEffect(acc, tab, tabs.size) { acc.load(list) }

    val rows = remember(version, tab, tabs.size, query) {
        val all = acc.chatList(list)
        if (query.isBlank()) all else all.filter { it.first.title.contains(query, ignoreCase = true) }
    }

    val title = when (conn) {
        is ConnectionStateWaitingForNetwork -> t("Waiting for network…")
        is ConnectionStateConnecting -> t("Connecting…")
        is ConnectionStateConnectingToProxy -> t("Connecting to proxy…")
        is ConnectionStateUpdating -> t("Updating…")
        else -> "Riv Post"
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                DrawerContent(acc, onSettings = {
                    scope.launch { drawer.close() }
                    onSettings()
                }, close = { scope.launch { drawer.close() } })
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    val barColors = if (style.filledBar) TopAppBarDefaults.topAppBarColors(
                        containerColor = style.accent, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                    ) else TopAppBarDefaults.topAppBarColors()
                    TopAppBar(
                        colors = barColors,
                        navigationIcon = {
                            IconButton(onClick = {
                                if (searching) { searching = false; query = "" } else scope.launch { drawer.open() }
                            }) { Icon(if (searching) Ic.Back else Ic.Menu, null) }
                        },
                        title = {
                            if (searching) {
                                TextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    placeholder = { Text(t("Search chats")) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(title, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        actions = {
                            if (!searching) IconButton(onClick = { searching = true }) { Icon(Ic.Search, null) }
                        },
                    )
                    if (tabs.size > 1) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = tab,
                            edgePadding = 8.dp,
                            containerColor = if (style.filledBar) style.accent else MaterialTheme.colorScheme.surface,
                            contentColor = if (style.filledBar) Color.White else MaterialTheme.colorScheme.primary,
                        ) {
                            tabs.forEachIndexed { i, (name, _) ->
                                Tab(selected = i == tab, onClick = { tab = i }, text = {
                                    Text(name, maxLines = 1, fontWeight = if (i == tab) FontWeight.SemiBold else FontWeight.Normal)
                                })
                            }
                        }
                    }
                }
            },
        ) { pad ->
            val state = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= state.layoutInfo.totalItemsCount - 8
                }
            }
            LaunchedEffect(nearEnd, rows.size) { if (nearEnd && rows.isNotEmpty()) acc.load(list) }

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) t("No chats found") else t("Loading chats…"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(pad)) {
                    items(rows, key = { it.first.id }) { (c, pinned) ->
                        ChatRow(acc, c, pinned, list, onClick = { onOpen(c.id) })
                        if (style.dividers) HorizontalDivider(
                            Modifier.padding(start = style.avatar + 26.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(acc: TgAccount, c: ChatItem, pinned: Boolean, list: ChatList, onClick: () -> Unit) {
    val style = LocalStyle.current
    var menu by remember { mutableStateOf(false) }
    val muted = acc.isMuted(c)
    val m = c.lastMessage
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Prefs.showAvatars) {
                Avatar(acc, c.id, c.title, c.photo, c.mini, style.avatar)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title.ifBlank { t("Deleted account") }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Ic.Muted, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    if (m != null && m.isOutgoing) {
                        val read = m.id <= c.lastReadOutbox
                        Icon(
                            if (read) Ic.DoneAll else Ic.Check, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        if (m != null) Fmt.listTime(m.date) else "", fontSize = 12.sp,
                        color = if (c.unread > 0 && !muted) style.unreadBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val who = if (m != null && c.isGroup && !Fmt.isService(m)) {
                        if (m.isOutgoing) t("You") else acc.senderName(m.senderId).split(" ").first()
                    } else if (m != null && m.isOutgoing && !c.isChannel && !Fmt.isService(m)) t("You") else ""
                    Text(
                        (if (who.isNotBlank()) "$who: " else "") + Fmt.preview(acc, m).replace('\n', ' '),
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    if (c.mentions > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge("@", style.unreadBadge)
                    }
                    if (c.unread > 0 || c.markedUnread) {
                        Spacer(Modifier.width(6.dp))
                        Badge(
                            if (c.unread > 0) (if (c.unread > 999) "999+" else c.unread.toString()) else "",
                            if (muted) MaterialTheme.colorScheme.outline else style.unreadBadge,
                        )
                    } else if (pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Ic.Pin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (pinned) t("Unpin") else t("Pin")) }, onClick = {
                menu = false
                acc.scope.launch { acc.client.toggleChatIsPinned(chatList = list, chatId = c.id, isPinned = !pinned) }
            })
            if (c.unread > 0 && m != null) {
                DropdownMenuItem(text = { Text(t("Mark as read")) }, onClick = {
                    menu = false
                    acc.markAllRead(c.id, longArrayOf(m.id))
                })
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier.height(20.dp).widthIn(min = 20.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DrawerContent(acc: TgAccount, onSettings: () -> Unit, close: () -> Unit) {
    val me by acc.me.collectAsState()
    val accounts by Tg.accounts.collectAsState()
    val active by Tg.activeIndex.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .statusBarsPadding().padding(20.dp),
        ) {
            val u = me
            Avatar(
                acc, u?.id ?: 0L, acc.userName(u?.id ?: 0L).ifBlank { "R" },
                u?.profilePhoto?.small, u?.profilePhoto?.minithumbnail?.data, 64.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(acc.userName(u?.id ?: 0L), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (u != null && u.phoneNumber.isNotBlank()) {
                Text("+" + u.phoneNumber, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            t("Accounts"), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        for (i in Prefs.accountList()) {
            val a = accounts[i]
            if (a != null) AccountRow(a, i == active) { Tg.switchTo(i); close() }
        }
        DrawerItem(Ic.Add, t("Add account")) { close(); Tg.addAccount() }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DrawerItem(Ic.Settings, t("Settings"), onSettings)
        Spacer(Modifier.height(16.dp))
        Text(
            "Riv Post " + BuildConfig.VERSION_NAME, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
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
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(a, am?.id ?: 0L, a.userName(am?.id ?: 0L).ifBlank { "?" }, am?.profilePhoto?.small, null, 36.dp)
            Spacer(Modifier.width(14.dp))
            Text(a.userName(am?.id ?: 0L), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isActive) Icon(Ic.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
