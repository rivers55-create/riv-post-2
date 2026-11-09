package com.rivers.rivpost.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.media.VideoItem
import com.rivers.rivpost.media.VideoLib
import com.rivers.rivpost.music.Music
import com.rivers.rivpost.music.Track
import com.rivers.rivpost.tg.ChatItem
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.MsgEvent
import com.rivers.rivpost.tg.Notifier
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputVoiceNote
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageInteractionInfo
import dev.g000sha256.tdl.dto.MessageOriginChannel
import dev.g000sha256.tdl.dto.MessageOriginChat
import dev.g000sha256.tdl.dto.MessageOriginHiddenUser
import dev.g000sha256.tdl.dto.MessageOriginUser
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageReplyToMessage
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.Minithumbnail
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.SecretChatStateClosed
import dev.g000sha256.tdl.dto.SecretChatStatePending
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebm
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypeEmailAddress
import dev.g000sha256.tdl.dto.TextEntityTypeHashtag
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeMention
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypePreCode
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import dev.g000sha256.tdl.dto.TextEntityTypeUrl
import dev.g000sha256.tdl.dto.Thumbnail
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatWebp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val QuickReactions = listOf("❤️", "👍", "🔥", "😂", "😮", "😢", "🙏", "👎")

/** Loads and keeps the messages of one open chat (newest first). */
class ChatData(val acc: TgAccount, val chatId: Long) {
    val messages = mutableStateListOf<Message>()
    val content = mutableStateMapOf<Long, MessageContent>()
    val interaction = mutableStateMapOf<Long, MessageInteractionInfo?>()
    val replied = mutableStateMapOf<Long, Message?>()
    var loading by mutableStateOf(false)
    var end by mutableStateOf(false)
    var pinned by mutableStateOf<Message?>(null)

    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        var from = messages.lastOrNull()?.id ?: 0L
        var got = 0
        var tries = 0
        while (got < 30 && tries < 5) {
            tries++
            val r = acc.client.getChatHistory(chatId = chatId, fromMessageId = from, offset = 0, limit = 50, onlyLocal = false)
            val list = (r as? TdlResult.Success)?.result?.messages?.filterNotNull() ?: break
            val fresh = list.filter { m -> messages.none { it.id == m.id } }
            if (fresh.isEmpty()) { end = true; break }
            messages.addAll(fresh)
            got += fresh.size
            from = fresh.last().id
        }
        loading = false
    }

    /** Loads the messages around one message (for jumping to replies, pins, search results). */
    suspend fun loadAround(id: Long): Int {
        messages.indexOfFirst { it.id == id }.let { if (it >= 0) return it }
        val r = acc.client.getChatHistory(chatId = chatId, fromMessageId = id, offset = -25, limit = 60, onlyLocal = false)
        val list = (r as? TdlResult.Success)?.result?.messages?.filterNotNull() ?: return -1
        messages.clear()
        messages.addAll(list.distinctBy { it.id }.sortedByDescending { it.id })
        end = false
        return messages.indexOfFirst { it.id == id }
    }

    suspend fun replyOf(m: Message): Message? {
        if (replied.containsKey(m.id)) return replied[m.id]
        val r = (m.replyTo as? MessageReplyToMessage) ?: return null
        val local = messages.firstOrNull { it.id == r.messageId && r.chatId == chatId }
        val found = local ?: (acc.client.getRepliedMessage(chatId = m.chatId, messageId = m.id) as? TdlResult.Success)?.result
        replied[m.id] = found
        return found
    }

    fun onEvent(e: MsgEvent) {
        when (e) {
            is MsgEvent.New -> if (e.m.chatId == chatId && messages.none { it.id == e.m.id }) messages.add(0, e.m)
            is MsgEvent.Replaced -> if (e.m.chatId == chatId) {
                val i = messages.indexOfFirst { it.id == e.oldId }
                val exists = messages.any { it.id == e.m.id }
                if (i >= 0 && !exists) {
                    messages[i] = e.m
                } else if (i >= 0) {
                    messages.removeAt(i)
                } else if (!exists) {
                    messages.add(0, e.m)
                }
            }
            is MsgEvent.Deleted -> if (e.chatId == chatId) {
                val ids = e.ids.toSet()
                messages.removeAll { it.id in ids }
            }
            is MsgEvent.Content -> if (e.chatId == chatId) content[e.id] = e.content
            is MsgEvent.Interaction -> if (e.chatId == chatId) interaction[e.id] = e.info
            is MsgEvent.Edited -> {}
        }
    }
}

@Composable
fun ChatScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val data = remember(acc, chatId) { ChatData(acc, chatId) }
    val version by acc.chatsVersion.collectAsState()
    val uv by acc.usersVersion.collectAsState()
    val extra by acc.extraVersion.collectAsState()
    val chat: ChatItem? = remember(version) { acc.chats[chatId] }
    val listState = rememberLazyListState()
    var text by remember(chatId) { mutableStateOf("") }
    var viewer by remember { mutableStateOf<String?>(null) }
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableStateOf(0) }
    var attach by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var menu by remember { mutableStateOf(false) }
    var muteDialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var hidePinned by remember { mutableStateOf(false) }

    DisposableEffect(acc, chatId) {
        acc.openChatId = chatId
        Notifier.clear(ctx, acc, chatId)
        acc.scope.launch { acc.client.openChat(chatId = chatId) }
        onDispose {
            if (acc.openChatId == chatId) acc.openChatId = 0
            acc.scope.launch { acc.client.closeChat(chatId = chatId) }
            recorder.cancel()
        }
    }
    LaunchedEffect(data) {
        launch { acc.events.collect { e -> withContext(Dispatchers.Main) { data.onEvent(e) } } }
        launch { data.pinned = (acc.client.getChatPinnedMessage(chatId = chatId) as? TdlResult.Success)?.result }
        data.loadMore()
    }
    val newest = data.messages.firstOrNull()?.id ?: 0L
    LaunchedEffect(newest) {
        if (newest != 0L && (chat?.unread ?: 0) > 0) acc.markAllRead(chatId, data.messages.take(60).map { it.id }.toLongArray())
    }
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearTop, data.messages.size) { if (nearTop && data.messages.isNotEmpty()) data.loadMore() }
    LaunchedEffect(recording) {
        while (recording) {
            recSecs = ((System.currentTimeMillis() - recorder.startedAt) / 1000).toInt()
            delay(300)
        }
    }
    LaunchedEffect(searchQuery) {
        delay(350)
        results = if (searchQuery.isBlank()) emptyList()
        else (acc.client.searchChatMessages(chatId = chatId, query = searchQuery, fromMessageId = 0, offset = 0, limit = 50) as? TdlResult.Success)
            ?.result?.messages?.toList() ?: emptyList()
    }

    fun jumpTo(id: Long) {
        scope.launch {
            val i = data.loadAround(id)
            if (i >= 0) listState.scrollToItem(i)
        }
    }

    fun send(content: InputMessageContent) {
        val r = replyTo
        replyTo = null
        acc.scope.launch {
            val res = acc.client.sendMessage(
                chatId = chatId,
                replyTo = r?.let { InputMessageReplyToMessage(messageId = it.id, quote = null, checklistTaskId = 0, pollOptionId = "") },
                inputMessageContent = content,
            )
            if (res is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, t("Couldn't send") + ": " + res.message) }
        }
        scope.launch { listState.scrollToItem(0) }
    }

    fun sendText() {
        val s = text.trim()
        if (s.isEmpty()) return
        text = ""
        val ed = editing
        if (ed != null) {
            editing = null
            acc.scope.launch {
                val r = acc.client.editMessageText(
                    chatId = chatId, messageId = ed.id,
                    inputMessageContent = InputMessageText(text = FormattedText(text = s, entities = emptyArray<TextEntity>()), linkPreviewOptions = null, clearDraft = true),
                )
                if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, r.message) }
            }
            return
        }
        send(InputMessageText(text = FormattedText(text = s, entities = emptyArray<TextEntity>()), linkPreviewOptions = null, clearDraft = true))
    }

    fun react(m: Message, emoji: String) {
        val info = if (data.interaction.containsKey(m.id)) data.interaction[m.id] else m.interactionInfo
        val chosen = info?.reactions?.reactions?.any { it.isChosen && (it.type as? ReactionTypeEmoji)?.emoji == emoji } == true
        acc.scope.launch {
            val r = if (chosen) acc.client.removeMessageReaction(chatId = chatId, messageId = m.id, reactionType = ReactionTypeEmoji(emoji = emoji))
            else acc.client.addMessageReaction(chatId = chatId, messageId = m.id, reactionType = ReactionTypeEmoji(emoji = emoji), isBig = false, updateRecentReactions = true)
            if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, r.message) }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { recording = recorder.start() } else { toast(ctx, t("Microphone permission is needed")) }
    }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) {
                try {
                    val out = java.io.File(ctx.filesDir, "wallpaper_" + System.currentTimeMillis() + ".jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                } catch (_: Throwable) { null }
            }
            if (f != null) {
                Prefs.wallpaper.takeIf { it.isNotEmpty() }?.let { old -> try { java.io.File(old).delete() } catch (_: Throwable) { } }
                Prefs.wallpaper = f.absolutePath
            }
        }
    }

    val peerUserId = chat?.privateUserId ?: 0L
    val typing = remember(extra) { acc.typingText(chatId) }
    val subtitle = remember(uv, version, extra) {
        when {
            chat == null -> ""
            typing != null -> {
                val p = typing.split("|")
                (if (chat.isGroup) p[0] + " " else "") + t(p.getOrElse(1) { "typing" }) + "…"
            }
            acc.isSaved(chat) -> ""
            chat.isChannel -> "${acc.memberCount(chat)} " + t("subscribers")
            chat.isGroup -> "${acc.memberCount(chat)} " + t("members")
            peerUserId != 0L -> Fmt.lastSeenStatus(acc.status(peerUserId), acc.users[peerUserId])
            else -> ""
        }
    }
    val secret = if (chat?.isSecret == true) acc.secretChats[chat.secretId] else null

    Box(Modifier.fillMaxSize().background(style.chatBg)) {
        // wallpaper
        if (Prefs.wallpaper.isNotEmpty() && java.io.File(Prefs.wallpaper).exists()) {
            AsyncImage(
                model = java.io.File(Prefs.wallpaper), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().let { if (Prefs.wallBlur) it.blur(18.dp) else it },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Prefs.wallDim / 100f)))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            // ---------- header ----------
            Box(Modifier.statusBarsPadding()) {
                if (searching) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(28.dp)).background(style.navBar),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { searching = false; searchQuery = "" }) { Icon(Ic.Back, null, tint = style.navBarFg) }
                        TextField(
                            value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                            placeholder = { Text(t("Search")) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else ChatHeader(
                    acc, chat, subtitle, typing != null,
                    onBack = onBack,
                    onInfo = { act.push(Screen.Info(chatId)) },
                    onCall = { toast(ctx, t("Calls are coming in a later version")) },
                    onMenu = { menu = true },
                )
                Box(Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 12.dp)) {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                        if (chat != null) MenuItem(Ic.Muted, if (acc.isMuted(chat)) t("Unmute") else t("Mute")) {
                            menu = false
                            if (acc.isMuted(chat)) acc.setMuted(chat, false) else muteDialog = true
                        }
                        if (peerUserId != 0L) MenuItem(Ic.Movie, t("Video Call")) { menu = false; toast(ctx, t("Calls are coming in a later version")) }
                        MenuItem(Ic.Search, t("Search")) { menu = false; searching = true }
                        MenuItem(Ic.Image, t("Change Wallpaper")) { menu = false; confirm = "wallpaper" }
                        MenuItem(Ic.Edit, t("Clear History")) { menu = false; confirm = "clear" }
                        MenuItem(Ic.Delete, t("Delete Chat"), Color(0xFFE53935)) { menu = false; confirm = "delete" }
                    }
                }
            }
            // ---------- pinned ----------
            val pin = data.pinned
            if (pin != null && Prefs.pinnedBar && !hidePinned && !searching) PinnedBar(acc, pin, onClick = { jumpTo(pin.id) }, onHide = { hidePinned = true })
            if (secret != null && secret.state is SecretChatStatePending) InfoBanner("🔒 " + t("Waiting for the other person to come online…"))
            if (secret != null && secret.state is SecretChatStateClosed) InfoBanner("🔒 " + t("This secret chat was closed"))

            // ---------- messages / search results ----------
            Box(Modifier.weight(1f)) {
                if (searching && searchQuery.isNotBlank()) {
                    LazyColumn(Modifier.fillMaxSize().background(cs.background.copy(alpha = 0.92f))) {
                        if (results.isEmpty()) item { Text(t("No results"), modifier = Modifier.padding(20.dp), color = cs.onSurfaceVariant) }
                        items(results, key = { it.id }) { m ->
                            Column(Modifier.fillMaxWidth().clickable { searching = false; searchQuery = ""; jumpTo(m.id) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                Text(acc.senderName(m.senderId) + "  ·  " + Fmt.listTime(m.date), fontSize = 13.sp, color = style.accent)
                                Text(Fmt.preview(acc, m), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(data.messages, key = { _, m -> m.id }) { i, m ->
                            val older = data.messages.getOrNull(i + 1)
                            Column(Modifier.fillMaxWidth()) {
                                if (older == null || Fmt.dayKey(older.date) != Fmt.dayKey(m.date)) DayChip(Fmt.dayLabel(m.date))
                                val sameSender = older != null && older.senderId == m.senderId && !Fmt.isService(older) &&
                                    Fmt.dayKey(older.date) == Fmt.dayKey(m.date)
                                MessageItem(
                                    acc, act, data, chat, m, data.content[m.id] ?: m.content,
                                    if (data.interaction.containsKey(m.id)) data.interaction[m.id] else m.interactionInfo,
                                    showName = !sameSender,
                                    onPhoto = { viewer = it },
                                    onReply = { replyTo = it; editing = null },
                                    onEdit = { msg -> editing = msg; replyTo = null; text = (msg.content as? MessageText)?.text?.text ?: "" },
                                    onReact = { msg, e -> react(msg, e) },
                                    onJump = { jumpTo(it) },
                                )
                            }
                        }
                        if (data.loading) item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
                    if (showDown) Box(
                        Modifier.align(Alignment.BottomEnd).padding(14.dp).size(56.dp).clip(CircleShape).background(style.navBar)
                            .clickable { scope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.Down, null, tint = style.navBarFg, modifier = Modifier.size(30.dp)) }
                }
            }

            // ---------- reply / edit banner ----------
            val banner = editing ?: replyTo
            if (banner != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp)).background(style.navBar).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (editing != null) Ic.Edit else Ic.Reply, null, tint = style.accent)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(3.dp).height(34.dp).background(style.accent))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (editing != null) t("Edit message") else acc.senderName(banner.senderId), color = style.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(Fmt.preview(acc, banner), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = style.navBarFg)
                    }
                    IconButton(onClick = { if (editing != null) text = ""; editing = null; replyTo = null }) { Icon(Ic.Close, null, tint = style.navBarFg) }
                }
            }

            // ---------- input bar ----------
            InputBar(
                text = text, onText = { text = it }, recording = recording, recSecs = recSecs,
                onSend = { sendText() },
                onAttach = { attach = true },
                onGif = { attach = true },
                onMic = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        recording = recorder.start()
                        if (!recording) toast(ctx, t("Couldn't start recording"))
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCancelRec = { recorder.cancel(); recording = false },
                onSendRec = {
                    recording = false
                    val r = recorder.finish()
                    if (r != null) send(
                        InputMessageVoiceNote(
                            voiceNote = InputVoiceNote(voiceNote = InputFileLocal(path = r.first.absolutePath), duration = r.second, waveform = ByteArray(0)),
                            caption = null, selfDestructType = null,
                        )
                    )
                },
            )
        }
    }

    if (attach) AttachSheet(onDismiss = { attach = false }, onSend = { send(it) })
    viewer?.let { path -> PhotoViewer(path) { viewer = null } }
    if (muteDialog && chat != null) ChoiceDialog(
        t("Mute"), listOf(t("1 hour") to 3600, t("8 hours") to 8 * 3600, t("2 days") to 2 * 86400, t("Forever") to 366 * 86400),
        onDismiss = { muteDialog = false },
    ) { secs -> acc.setMuted(chat, true, secs); muteDialog = false }
    when (confirm) {
        "wallpaper" -> ChoiceDialog(
            t("Change Wallpaper"),
            listOf(t("Pick a photo") to "pick", t("Blur on/off") to "blur", t("Darker") to "dim+", t("Lighter") to "dim-", t("Remove wallpaper") to "none"),
            onDismiss = { confirm = "" },
        ) { k ->
            confirm = ""
            when (k) {
                "pick" -> { pickWallpaper.launch("image/*") }
                "blur" -> { Prefs.wallBlur = !Prefs.wallBlur }
                "dim+" -> { Prefs.wallDim = (Prefs.wallDim + 15).coerceAtMost(80) }
                "dim-" -> { Prefs.wallDim = (Prefs.wallDim - 15).coerceAtLeast(0) }
                else -> { Prefs.wallpaper = "" }
            }
        }
        "clear" -> ConfirmDialog(t("Clear History"), t("Delete all messages in this chat for you?"), { confirm = "" }) {
            confirm = ""
            acc.scope.launch { acc.client.deleteChatHistory(chatId = chatId, removeFromChatList = false, revoke = false) }
            data.messages.clear()
        }
        "delete" -> ConfirmDialog(t("Delete Chat"), t("Delete this chat and leave it?"), { confirm = "" }) {
            confirm = ""
            val c = chat
            acc.scope.launch {
                when {
                    c == null -> {}
                    c.isSecret -> { acc.client.closeSecretChat(secretChatId = c.secretId); acc.client.deleteChatHistory(chatId = chatId, removeFromChatList = true, revoke = false) }
                    c.isGroup || c.isChannel -> acc.client.leaveChat(chatId = chatId)
                    else -> acc.client.deleteChatHistory(chatId = chatId, removeFromChatList = true, revoke = false)
                }
            }
            onBack()
        }
    }
}

@Composable
private fun ChatHeader(
    acc: TgAccount, chat: ChatItem?, subtitle: String, typing: Boolean,
    onBack: () -> Unit, onInfo: () -> Unit, onCall: () -> Unit, onMenu: () -> Unit,
) {
    val style = LocalStyle.current
    val floating = Prefs.headerStyle == "floating"
    val bg = if (floating) style.navBar else style.topBar
    val fg = if (floating) style.navBarFg else style.topBarFg
    val title = if (chat != null && acc.isSaved(chat)) t("Saved Messages") else chat?.title ?: ""
    if (floating) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(bg).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Ic.Back, null, tint = fg)
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(bg).clickable(onClick = onInfo).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chat != null) ChatAvatar(acc, chat, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat?.isSecret == true) { Icon(Ic.Lock, null, Modifier.size(14.dp), tint = Color(0xFF4CD964)); Spacer(Modifier.width(3.dp)) }
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                    }
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 14.sp, maxLines = 1, color = if (typing) style.accent else fg.copy(alpha = 0.65f))
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(Modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(bg), verticalAlignment = Alignment.CenterVertically) {
                if (chat != null && chat.privateUserId != 0L && !acc.isSaved(chat)) IconButton(onClick = onCall) { Icon(Ic.Phone, null, tint = fg) }
                IconButton(onClick = onMenu) { Icon(Ic.More, null, tint = fg) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().background(bg).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null, tint = fg) }
            Row(Modifier.weight(1f).clickable(onClick = onInfo), verticalAlignment = Alignment.CenterVertically) {
                if (chat != null) ChatAvatar(acc, chat, 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, maxLines = 1, color = if (typing) style.accent else fg.copy(alpha = 0.7f))
                }
            }
            if (chat != null && chat.privateUserId != 0L && !acc.isSaved(chat)) IconButton(onClick = onCall) { Icon(Ic.Phone, null, tint = fg) }
            IconButton(onClick = onMenu) { Icon(Ic.More, null, tint = fg) }
        }
    }
}

@Composable
private fun PinnedBar(acc: TgAccount, m: Message, onClick: () -> Unit, onHide: () -> Unit) {
    val style = LocalStyle.current
    val mini = when (val c = m.content) {
        is MessagePhoto -> c.photo.minithumbnail?.data
        is MessageVideo -> c.video.minithumbnail?.data
        else -> null
    }
    val bmp = remember(m.id) { miniBitmap(mini) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(26.dp)).background(style.navBar)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).clip(CircleShape).background(style.accent))
        Spacer(Modifier.width(10.dp))
        if (bmp != null) {
            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(t("Pinned Message"), color = style.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(Fmt.preview(acc, m), color = style.navBarFg.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onHide) { Icon(Ic.Close, null, tint = style.navBarFg.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Text(
        text, fontSize = 14.sp, color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).padding(10.dp),
    )
}

@Composable
private fun InputBar(
    text: String, onText: (String) -> Unit, recording: Boolean, recSecs: Int,
    onSend: () -> Unit, onAttach: () -> Unit, onGif: () -> Unit, onMic: () -> Unit, onCancelRec: () -> Unit, onSendRec: () -> Unit,
) {
    val style = LocalStyle.current
    val floating = Prefs.inputStyle == "floating"
    val fg = style.navBarFg
    Row(
        (if (floating) Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp) else Modifier.fillMaxWidth().background(style.navBar).padding(4.dp)),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp)
                .let { if (floating) it.clip(RoundedCornerShape(28.dp)).background(style.navBar) else it }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                IconButton(onClick = onCancelRec) { Icon(Ic.Close, null, tint = MaterialTheme.colorScheme.error) }
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                Spacer(Modifier.width(10.dp))
                Text(t("Recording") + "  " + Fmt.duration(recSecs), color = fg, modifier = Modifier.weight(1f))
            } else {
                if (Prefs.gifButton) IconButton(onClick = onGif) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(fg.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("GIF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg.copy(alpha = 0.8f))
                    }
                }
                TextField(
                    value = text, onValueChange = onText, placeholder = { Text(t("Message")) }, maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = fg, unfocusedTextColor = fg, cursorColor = style.accent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = Prefs.textSize.sp),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                        if (Prefs.enterSends && ev.key == Key.Enter) { onSend(); true } else false
                    },
                )
                IconButton(onClick = onAttach) { Icon(Ic.Attach, null, tint = fg.copy(alpha = 0.75f)) }
            }
        }
        Spacer(Modifier.width(6.dp))
        val showSend = recording || text.isNotBlank()
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(style.accent)
                .clickable { if (recording) onSendRec() else if (text.isNotBlank()) onSend() else onMic() },
            contentAlignment = Alignment.Center,
        ) { Icon(if (showSend) Ic.Send else Ic.Mic, null, tint = onColor(style.accent)) }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 13.sp, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private fun originName(acc: TgAccount, m: Message): String? {
    val o = m.forwardInfo?.origin ?: return null
    return when (o) {
        is MessageOriginUser -> acc.userName(o.senderUserId)
        is MessageOriginChat -> acc.chats[o.senderChatId]?.title
        is MessageOriginChannel -> acc.chats[o.chatId]?.title
        is MessageOriginHiddenUser -> o.senderName
        else -> null
    }?.ifBlank { null } ?: t("Unknown")
}

@Composable
private fun MessageItem(
    acc: TgAccount, act: MainActivity, data: ChatData, chat: ChatItem?, m: Message, content: MessageContent,
    info: MessageInteractionInfo?, showName: Boolean,
    onPhoto: (String) -> Unit, onReply: (Message) -> Unit, onEdit: (Message) -> Unit,
    onReact: (Message, String) -> Unit, onJump: (Long) -> Unit,
) {
    val style = LocalStyle.current
    val ctx = LocalContext.current
    if (Fmt.isService(m)) {
        val who = acc.senderName(m.senderId)
        DayChip((if (who.isNotBlank()) "$who: " else "") + Fmt.preview(acc, m))
        return
    }
    val isPost = chat?.isChannel == true
    val out = m.isOutgoing && !isPost
    var menu by remember { mutableStateOf(false) }
    val bubble = if (out) style.outBubble else style.inBubble
    val fg = if (out) style.outText else style.inText
    val meta = if (out) style.outMeta else style.inMeta
    val r = style.radius
    val small = 4.dp
    val shape = when {
        !style.tail || !showName -> RoundedCornerShape(r)
        out -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = small, bottomStart = r)
        else -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r, bottomStart = small)
    }
    val isSticker = content is MessageSticker
    var replied by remember(m.id) { mutableStateOf<Message?>(null) }
    LaunchedEffect(m.id) { if (m.replyTo is MessageReplyToMessage) replied = data.replyOf(m) }
    val video = remember(m.id) { VideoItem.from(acc.index, m) }
    val track = remember(m.id) { Track.from(acc.index, m) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (showName) 3.dp else 0.dp),
        horizontalArrangement = if (out) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            Column(
                Modifier.widthIn(max = 310.dp).clip(shape)
                    .background(if (isSticker) Color.Transparent else bubble)
                    .pointerInput(m.id) {
                        detectTapGestures(
                            onLongPress = { menu = true },
                            onDoubleTap = { if (Prefs.doubleTapReact) onReact(m, Prefs.quickReaction) },
                        )
                    }
                    .padding(if (isSticker) 0.dp else 3.dp),
            ) {
                if (showName && !out && chat != null && chat.isGroup) {
                    Text(
                        acc.senderName(m.senderId), color = style.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 3.dp), maxLines = 1,
                    )
                }
                originName(acc, m)?.let { from ->
                    Text(
                        t("Forwarded from") + " " + from, color = if (out) fg.copy(alpha = 0.85f) else style.accent, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 3.dp), maxLines = 1,
                    )
                }
                val rep = replied
                if (rep != null) {
                    Row(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = 0.10f))
                            .clickable { onJump(rep.id) }.height(IntrinsicSize.Min),
                    ) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(if (out) fg else style.accent))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(acc.senderName(rep.senderId), color = if (out) fg else style.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(Fmt.preview(acc, rep), color = fg.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                MessageBody(acc, act, m, content, fg, meta, onPhoto)
                // reactions
                val reactions = info?.reactions?.reactions?.filter { it.type is ReactionTypeEmoji }.orEmpty()
                if (reactions.isNotEmpty()) {
                    FlowRow(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (re in reactions) {
                            val e = (re.type as ReactionTypeEmoji).emoji
                            Text(
                                "$e ${re.totalCount}", fontSize = 13.sp, color = if (re.isChosen) onColor(style.accent) else fg,
                                modifier = Modifier.clip(CircleShape).background(if (re.isChosen) style.accent else fg.copy(alpha = 0.12f))
                                    .clickable { onReact(m, e) }.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.End).padding(start = 8.dp, end = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val views = info?.viewCount ?: 0
                    if (isPost && Prefs.showViews && views > 0) Text("👁 " + compact(views) + "  ", fontSize = 12.sp, color = meta)
                    if (m.editDate > 0) Text(t("edited") + " ", fontSize = 12.sp, color = meta)
                    Text(Fmt.clock(m.date), fontSize = 12.sp, color = meta)
                    if (m.isOutgoing) {
                        Spacer(Modifier.width(3.dp))
                        when (m.sendingState) {
                            is MessageSendingStatePending -> Text("🕓", fontSize = 10.sp)
                            is MessageSendingStateFailed -> Text("⚠", fontSize = 12.sp, color = Color(0xFFE53935))
                            else -> Icon(if (m.id <= (chat?.lastReadOutbox ?: 0L)) Ic.DoneAll else Ic.Check, null, Modifier.size(16.dp), tint = meta)
                        }
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    for (e in QuickReactions) {
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { menu = false; onReact(m, e) }.padding(6.dp))
                    }
                }
                HorizontalDivider()
                MenuItem(Ic.Reply, t("Reply")) { menu = false; onReply(m) }
                val txt = textOf(content)
                if (txt.isNotEmpty()) MenuItem(Ic.Copy, t("Copy")) {
                    menu = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("message", txt))
                    toast(ctx, t("Copied"))
                }
                if (m.isOutgoing && content is MessageText) MenuItem(Ic.Edit, t("Edit")) { menu = false; onEdit(m) }
                MenuItem(Ic.Forward, t("Forward")) { menu = false; act.push(Screen.Forward(m.chatId, longArrayOf(m.id))) }
                if (video != null) MenuItem(Ic.Movie, if (VideoLib.inMyList(video)) t("Remove from Videos") else t("Add to Videos")) {
                    menu = false
                    VideoLib.toggleMyList(video)
                    toast(ctx, t("Done"))
                }
                if (track != null) MenuItem(Ic.Heart, if (Music.isFavorite(track)) t("Remove from favorites") else t("Add to favorites")) {
                    menu = false
                    Music.toggleFavorite(track)
                }
                MenuItem(Ic.Delete, t("Delete for me")) {
                    menu = false
                    acc.scope.launch { acc.client.deleteMessages(chatId = m.chatId, messageIds = longArrayOf(m.id), revoke = false) }
                }
                if (m.isOutgoing || chat?.isGroup == true) MenuItem(Ic.Delete, t("Delete for everyone"), Color(0xFFE53935)) {
                    menu = false
                    acc.scope.launch {
                        val res = acc.client.deleteMessages(chatId = m.chatId, messageIds = longArrayOf(m.id), revoke = true)
                        if (res is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, res.message) }
                    }
                }
            }
        }
        if (isPost && Prefs.shareButton) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(style.navBar.copy(alpha = 0.85f))
                    .clickable { act.push(Screen.Forward(m.chatId, longArrayOf(m.id))) },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Forward, null, tint = style.navBarFg, modifier = Modifier.size(22.dp)) }
        }
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else -> n.toString()
}

private fun textOf(c: MessageContent): String = when (c) {
    is MessageText -> c.text.text
    is MessagePhoto -> c.caption.text
    is MessageVideo -> c.caption.text
    is MessageDocument -> c.caption.text
    is MessageAudio -> c.caption.text
    is MessageAnimation -> c.caption.text
    else -> ""
}

@Composable
private fun MessageBody(acc: TgAccount, act: MainActivity, m: Message, c: MessageContent, fg: Color, meta: Color, onPhoto: (String) -> Unit) {
    val ctx = LocalContext.current
    when (c) {
        is MessageText -> RichText(c.text, fg)
        is MessagePhoto -> {
            val best = c.photo.sizes.filter { maxOf(it.width, it.height) <= 1280 }.maxByOrNull { it.width * it.height }
                ?: c.photo.sizes.maxByOrNull { it.width * it.height }
            if (best != null) {
                val ratio = if (best.width > 0 && best.height > 0) best.width.toFloat() / best.height else 1f
                MediaBox(acc, best.photo, c.photo.minithumbnail, null, ratio.coerceIn(0.5f, 2f), auto = Prefs.autoPhotos, isPhoto = true, onOpen = { onPhoto(it) })
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVideo -> {
            val v = c.video
            val ratio = if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 16f / 9f
            if (Prefs.builtInVideo) {
                VideoPreview(acc, v.thumbnail, v.minithumbnail, ratio.coerceIn(0.5f, 2f), "▶ " + Fmt.duration(v.duration)) {
                    act.push(Screen.Video(PlayRequest(acc.index, v.video, c.caption.text.take(60).ifBlank { v.fileName.ifBlank { t("Video") } }, "${m.chatId}:${m.id}")))
                }
            } else {
                MediaBox(acc, v.video, v.minithumbnail, v.thumbnail, ratio.coerceIn(0.5f, 2f), auto = false, isPhoto = false,
                    label = "▶ " + Fmt.duration(v.duration), onOpen = { openFile(ctx, it, v.mimeType) })
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageAnimation -> {
            val v = c.animation
            val ratio = (if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 1f).coerceIn(0.5f, 2f)
            val cur = rememberFile(acc, v.animation, autoDownload = Prefs.autoGifs) ?: v.animation
            if (cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()) {
                LoopVideo(cur.local.path, Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp)))
            } else {
                MediaBox(acc, v.animation, v.minithumbnail, v.thumbnail, ratio, auto = Prefs.autoGifs, isPhoto = false, label = "GIF", onOpen = { })
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVideoNote -> {
            val v = c.videoNote
            val cur = rememberFile(acc, v.video, autoDownload = Prefs.autoGifs) ?: v.video
            if (cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()) {
                Box(Modifier.size(220.dp).clip(CircleShape).clickable { openFile(ctx, cur.local.path, "video/mp4") }) {
                    LoopVideo(cur.local.path, Modifier.fillMaxSize())
                }
            } else {
                Box(Modifier.size(220.dp).clip(CircleShape)) {
                    MediaBox(acc, v.video, v.minithumbnail, v.thumbnail, 1f, auto = true, isPhoto = false, label = Fmt.duration(v.duration), onOpen = { })
                }
            }
        }
        is MessageDocument -> {
            val track = remember(m.id) { Track.from(acc.index, m) }
            val video = remember(m.id) { VideoItem.from(acc.index, m) }
            when {
                track != null -> SongRow(track, fg, meta) { Music.playFromChat(acc, m) }
                video != null && Prefs.builtInVideo -> FileRow(acc, c.document.document, "🎬 " + c.document.fileName, fg, meta, play = true) {
                    act.push(Screen.Video(video.request()))
                }
                else -> FileRow(acc, c.document.document, c.document.fileName.ifBlank { t("File") }, fg, meta) { openFile(ctx, it, c.document.mimeType) }
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageAudio -> {
            val track = remember(m.id) { Track.from(acc.index, m) }
            if (track != null) SongRow(track, fg, meta) { Music.playFromChat(acc, m) }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVoiceNote -> VoiceRow(acc, c.voiceNote.voice, c.voiceNote.duration, c.voiceNote.waveform, fg, meta)
        is MessageSticker -> {
            val s = c.sticker
            val animated = Prefs.animStickers && (s.format is StickerFormatTgs || s.format is StickerFormatWebm)
            val f = if (s.format is StickerFormatWebp || animated) s.sticker else s.thumbnail?.file
            val cur = rememberFile(acc, f, autoDownload = true)
            val ok = cur != null && cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
            when {
                ok && s.format is StickerFormatTgs && animated -> TgsSticker(cur!!.local.path, Modifier.size(170.dp))
                ok && s.format is StickerFormatWebm && animated -> LoopVideo(cur!!.local.path, Modifier.size(170.dp))
                ok -> AsyncImage(model = java.io.File(cur!!.local.path), contentDescription = s.emoji, modifier = Modifier.size(170.dp))
                else -> Text(s.emoji.ifBlank { "🙂" }, fontSize = 64.sp, modifier = Modifier.padding(8.dp))
            }
        }
        else -> Text(
            previewFor(c), color = fg, fontSize = Prefs.textSize.sp, fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun previewFor(c: MessageContent): String = when (c) {
    is dev.g000sha256.tdl.dto.MessageLocation -> "📍 " + t("Location") + "\n" + "%.5f, %.5f".format(c.location.latitude, c.location.longitude)
    is dev.g000sha256.tdl.dto.MessageContact -> "👤 " + (c.contact.firstName + " " + c.contact.lastName).trim() + "\n" + c.contact.phoneNumber
    is dev.g000sha256.tdl.dto.MessagePoll -> "📊 " + t("Poll") + ": " + c.poll.question.text
    is dev.g000sha256.tdl.dto.MessageCall -> "📞 " + t("Call") + " " + Fmt.duration(c.duration)
    else -> t("This message type isn't supported yet")
}

@Composable
private fun SongRow(t0: Track, fg: Color, meta: Color, onPlay: () -> Unit) {
    val playing = Music.current?.key == t0.key
    Row(Modifier.widthIn(min = 230.dp).clickable(onClick = onPlay).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp)) {
            TrackArt(t0, Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                Icon(if (playing && Music.isPlaying) Ic.Pause else Ic.Play, null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(t0.title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t0.artist + (if (t0.duration > 0) " · " + Fmt.duration(t0.duration) else "") + " · " + t0.ext, color = meta, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun RichText(ft: FormattedText, fg: Color) {
    val link = LocalStyle.current.link
    val s = remember(ft, link) { rich(ft, link) }
    Text(
        s, color = fg, fontSize = Prefs.textSize.sp, lineHeight = (Prefs.textSize + 6).sp,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
    )
}

private fun rich(ft: FormattedText, link: Color): AnnotatedString = buildAnnotatedString {
    val text = ft.text
    append(text)
    val ls = TextLinkStyles(style = SpanStyle(color = link, textDecoration = TextDecoration.Underline))
    for (e in ft.entities) {
        val a = e.offset
        val b = (e.offset + e.length).coerceAtMost(text.length)
        if (a < 0 || a >= b) continue
        when (val ty = e.type) {
            is TextEntityTypeBold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), a, b)
            is TextEntityTypeItalic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), a, b)
            is TextEntityTypeUnderline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b)
            is TextEntityTypeStrikethrough -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), a, b)
            is TextEntityTypeCode, is TextEntityTypePre, is TextEntityTypePreCode ->
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22000000)), a, b)
            is TextEntityTypeUrl -> {
                val u = text.substring(a, b)
                addLink(LinkAnnotation.Url(if (u.contains("://")) u else "https://$u", ls), a, b)
            }
            is TextEntityTypeTextUrl -> addLink(LinkAnnotation.Url(ty.url, ls), a, b)
            is TextEntityTypeEmailAddress -> addLink(LinkAnnotation.Url("mailto:" + text.substring(a, b), ls), a, b)
            is TextEntityTypeMention, is TextEntityTypeHashtag -> addStyle(SpanStyle(color = link), a, b)
            else -> {}
        }
    }
}

@Composable
private fun VideoPreview(acc: TgAccount, thumb: Thumbnail?, mini: Minithumbnail?, ratio: Float, label: String, onPlay: () -> Unit) {
    val thumbFile = if (thumb != null && (thumb.format is ThumbnailFormatJpeg || thumb.format is ThumbnailFormatWebp)) thumb.file else null
    val th = rememberFile(acc, thumbFile, autoDownload = true)
    val mb = remember(mini) { miniBitmap(mini?.data) }
    Box(
        Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp)).background(Color(0x33000000)).clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        when {
            th != null && th.local.isDownloadingCompleted -> AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mb != null -> Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Ic.Play, null, tint = Color.White) }
        Text(
            label, color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x88000000)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Photo or media preview with download progress. */
@Composable
private fun MediaBox(
    acc: TgAccount, file: File, mini: Minithumbnail?, thumb: Thumbnail?, ratio: Float,
    auto: Boolean, isPhoto: Boolean, label: String? = null, onOpen: (String) -> Unit,
) {
    var want by remember(file.id) { mutableStateOf(false) }
    val cur = rememberFile(acc, file, autoDownload = auto || want) ?: file
    val thumbFile = if (thumb != null && (thumb.format is ThumbnailFormatJpeg || thumb.format is ThumbnailFormatWebp)) thumb.file else null
    val th = rememberFile(acc, thumbFile, autoDownload = true)
    val mb = remember(mini) { miniBitmap(mini?.data) }
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    LaunchedEffect(done, want) { if (done && want && !isPhoto) { want = false; onOpen(cur.local.path) } }
    Box(
        Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp)).background(Color(0x33000000))
            .clickable {
                if (done) onOpen(cur.local.path)
                else if (cur.local.isDownloadingActive) { acc.cancelDownload(cur); want = false }
                else { want = true; acc.download(cur, 16) }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isPhoto && done -> AsyncImage(model = java.io.File(cur.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            th != null && th.local.isDownloadingCompleted -> AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mb != null -> Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (!(isPhoto && done)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                when {
                    cur.local.isDownloadingActive -> {
                        val total = if (cur.size > 0) cur.size else cur.expectedSize
                        val p = if (total > 0) cur.local.downloadedSize.toFloat() / total else 0f
                        CircularProgressIndicator(progress = { p.coerceIn(0f, 1f) }, color = Color.White, modifier = Modifier.size(40.dp))
                        Icon(Ic.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    done -> Icon(Ic.Play, null, tint = Color.White)
                    else -> Icon(Ic.Download, null, tint = Color.White)
                }
            }
        }
        if (label != null) Text(
            label, color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x88000000)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FileRow(acc: TgAccount, file: File, name: String, fg: Color, meta: Color, play: Boolean = false, onOpen: (String) -> Unit) {
    var want by remember(file.id) { mutableStateOf(false) }
    val cur = rememberFile(acc, file, autoDownload = want) ?: file
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    LaunchedEffect(done, want) { if (done && want) { want = false; onOpen(cur.local.path) } }
    Row(
        Modifier.widthIn(min = 210.dp).clickable {
            if (play || done) onOpen(cur.local.path)
            else if (cur.local.isDownloadingActive) { acc.cancelDownload(cur); want = false }
            else { want = true; acc.download(cur, 16) }
        }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(LocalStyle.current.accent), contentAlignment = Alignment.Center) {
            if (cur.local.isDownloadingActive && !play) {
                val total = if (cur.size > 0) cur.size else cur.expectedSize
                val p = if (total > 0) cur.local.downloadedSize.toFloat() / total else 0f
                CircularProgressIndicator(progress = { p.coerceIn(0f, 1f) }, color = Color.White, modifier = Modifier.size(40.dp))
            }
            Icon(
                when { play -> Ic.Play; done -> Ic.Doc; cur.local.isDownloadingActive -> Ic.Close; else -> Ic.Download },
                null, tint = Color.White, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, color = fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val total = if (cur.size > 0) cur.size else cur.expectedSize
            Text(
                if (cur.local.isDownloadingActive) Fmt.size(cur.local.downloadedSize) + " / " + Fmt.size(total) else Fmt.size(total),
                color = meta, fontSize = 12.sp,
            )
        }
    }
}

/** Telegram packs the waveform as 5-bit values. */
private fun decodeWaveform(data: ByteArray, bars: Int): FloatArray {
    if (data.isEmpty()) return FloatArray(bars) { 0.15f + 0.1f * ((it * 7) % 5) }
    val count = data.size * 8 / 5
    val values = IntArray(count) { i ->
        val bit = i * 5
        val byte = bit / 8
        val shift = bit % 8
        val v = ((data[byte].toInt() and 0xFF) or (if (byte + 1 < data.size) (data[byte + 1].toInt() and 0xFF) shl 8 else 0)) shr shift
        v and 31
    }
    return FloatArray(bars) { b ->
        val i = (b.toLong() * count / bars).toInt().coerceIn(0, count - 1)
        (values[i] / 31f).coerceIn(0.08f, 1f)
    }
}

@Composable
private fun VoiceRow(acc: TgAccount, file: File, duration: Int, wave: ByteArray, fg: Color, meta: Color) {
    val cur = rememberFile(acc, file, autoDownload = Prefs.autoVoice, priority = 8) ?: file
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    val playing = VoicePlayer.playingFile == cur.id
    val bars = remember(wave) { decodeWaveform(wave, 40) }
    Row(Modifier.widthIn(min = 240.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(fg)
                .clickable { if (done) VoicePlayer.toggle(cur.id, cur.local.path) else acc.download(cur, 16) },
            contentAlignment = Alignment.Center,
        ) {
            val bubbleColor = if (fg.red + fg.green + fg.blue > 1.5f) Color(0xFF222222) else Color.White
            if (!done && cur.local.isDownloadingActive) CircularProgressIndicator(color = bubbleColor, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
            else Icon(if (!done) Ic.Download else if (playing && !VoicePlayer.paused) Ic.Pause else Ic.Play, null, tint = bubbleColor, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val prog = if (playing) VoicePlayer.position else 0f
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val w = size.width / bars.size
                for (i in bars.indices) {
                    val h = bars[i] * size.height
                    val played = (i + 0.5f) / bars.size <= prog
                    drawRoundRect(
                        if (played) fg else fg.copy(alpha = 0.4f),
                        Offset(i * w + w * 0.2f, (size.height - h) / 2), Size(w * 0.6f, h), CornerRadius(w * 0.3f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(Fmt.duration(duration), color = meta, fontSize = 13.sp)
        }
    }
}

@Composable
fun PhotoViewer(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offX by remember { mutableFloatStateOf(0f) }
        var offY by remember { mutableFloatStateOf(0f) }
        @Suppress("DEPRECATION")
        val st = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offX += pan.x
            offY += pan.y
            if (scale == 1f) { offX = 0f; offY = 0f }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).transformable(st)) {
            AsyncImage(
                model = java.io.File(path), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offX; translationY = offY },
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Ic.Close, null, tint = Color.White) }
        }
    }
}

suspend fun copyToCache(ctx: Context, uri: Uri): java.io.File? = withContext(Dispatchers.IO) {
    try {
        var name = "file_" + System.currentTimeMillis()
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { if (it.isNotBlank()) name = it }
        }
        name = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val dir = java.io.File(ctx.cacheDir, "send/" + System.currentTimeMillis()).apply { mkdirs() }
        val out = java.io.File(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        out
    } catch (_: Throwable) {
        null
    }
}
