package com.rivers.rivpost.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.ChatItem
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.MsgEvent
import com.rivers.rivpost.tg.Notifier
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.InputMessagePhoto
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputPhoto
import dev.g000sha256.tdl.dto.InputVoiceNote
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.Minithumbnail
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads and keeps the messages of one open chat (newest first). */
private class ChatData(val acc: TgAccount, val chatId: Long) {
    val messages = mutableStateListOf<Message>()
    val content = mutableStateMapOf<Long, MessageContent>()
    var loading by mutableStateOf(false)
    var end by mutableStateOf(false)

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
            withContext(Dispatchers.Main) { messages.addAll(fresh) }
            got += fresh.size
            from = fresh.last().id
        }
        loading = false
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
            is MsgEvent.Edited -> {}
        }
    }
}

@Composable
fun ChatScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val scope = rememberCoroutineScope()
    val data = remember(acc, chatId) { ChatData(acc, chatId) }
    val version by acc.chatsVersion.collectAsState()
    val uv by acc.usersVersion.collectAsState()
    val chat: ChatItem? = remember(version) { acc.chats[chatId] }
    val listState = rememberLazyListState()
    var text by remember(chatId) { mutableStateOf("") }
    var viewer by remember { mutableStateOf<String?>(null) }
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableStateOf(0) }
    var attachMenu by remember { mutableStateOf(false) }

    // open / close
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
        data.loadMore()
    }
    // mark as read when new messages show up
    val newest = data.messages.firstOrNull()?.id ?: 0L
    LaunchedEffect(newest) {
        if (newest != 0L && (chat?.unread ?: 0) > 0) {
            acc.markAllRead(chatId, data.messages.take(60).map { it.id }.toLongArray())
        }
    }
    // load older when scrolled up
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearTop, data.messages.size) {
        if (nearTop && data.messages.isNotEmpty()) scope.launch { data.loadMore() }
    }
    LaunchedEffect(recording) {
        while (recording) {
            recSecs = ((System.currentTimeMillis() - recorder.startedAt) / 1000).toInt()
            kotlinx.coroutines.delay(300)
        }
    }

    fun send(content: InputMessageContent) {
        acc.scope.launch {
            val r = acc.client.sendMessage(chatId = chatId, inputMessageContent = content)
            if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, t("Couldn't send") + ": " + r.message) }
        }
        scope.launch { listState.scrollToItem(0) }
    }

    fun sendText() {
        val s = text.trim()
        if (s.isEmpty()) return
        text = ""
        send(InputMessageText(text = FormattedText(text = s, entities = emptyArray<TextEntity>()), linkPreviewOptions = null, clearDraft = true))
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) acc.scope.launch {
            val f = copyToCache(ctx, uri) ?: return@launch
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o)
            send(
                InputMessagePhoto(
                    photo = InputPhoto(
                        photo = InputFileLocal(path = f.absolutePath), thumbnail = null, video = null,
                        addedStickerFileIds = IntArray(0), width = o.outWidth.coerceAtLeast(0), height = o.outHeight.coerceAtLeast(0),
                    ),
                    caption = null, showCaptionAboveMedia = false, selfDestructType = null, hasSpoiler = false,
                )
            )
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) acc.scope.launch {
            val f = copyToCache(ctx, uri) ?: return@launch
            send(
                InputMessageDocument(
                    document = InputDocument(document = InputFileLocal(path = f.absolutePath), thumbnail = null, disableContentTypeDetection = false),
                    caption = null,
                )
            )
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { recording = recorder.start() } else { toast(ctx, t("Microphone permission is needed")) }
    }

    val peerUserId = chat?.privateUserId ?: 0L
    val subtitle = remember(uv, version) {
        when {
            chat == null -> ""
            chat.isChannel -> t("channel")
            chat.isGroup -> t("group")
            peerUserId != 0L -> Fmt.lastSeen(acc.users[peerUserId])
            else -> ""
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = style.chatBg,
        topBar = {
            val colors = if (style.filledBar) TopAppBarDefaults.topAppBarColors(
                containerColor = style.accent, titleContentColor = Color.White,
                navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
            ) else TopAppBarDefaults.topAppBarColors()
            TopAppBar(
                colors = colors,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Ic.Back, null) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat != null) Avatar(acc, chat.id, chat.title, chat.photo, chat.mini, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(chat?.title ?: "", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, maxLines = 1, color = LocalContentColorOr(style))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recording) {
                        IconButton(onClick = { recorder.cancel(); recording = false }) {
                            Icon(Ic.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        Spacer(Modifier.width(10.dp))
                        Text(t("Recording") + "  " + Fmt.duration(recSecs), modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            recording = false
                            val r = recorder.finish()
                            if (r != null) send(
                                InputMessageVoiceNote(
                                    voiceNote = InputVoiceNote(voiceNote = InputFileLocal(path = r.first.absolutePath), duration = r.second, waveform = ByteArray(0)),
                                    caption = null, selfDestructType = null,
                                )
                            )
                        }) { Icon(Ic.Send, null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        Box {
                            IconButton(onClick = { attachMenu = true }) { Icon(Ic.Attach, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Ic.Image, null) }, text = { Text(t("Photo")) },
                                    onClick = { attachMenu = false; pickPhoto.launch("image/*") },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Ic.Doc, null) }, text = { Text(t("File")) },
                                    onClick = { attachMenu = false; pickFile.launch("*/*") },
                                )
                            }
                        }
                        TextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text(t("Message")) },
                            maxLines = 6,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).onPreviewKeyEvent { ev ->
                                if (Prefs.enterSends && ev.key == Key.Enter) { sendText(); true } else false
                            },
                        )
                        if (text.isNotBlank()) {
                            IconButton(onClick = { sendText() }) { Icon(Ic.Send, null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    recording = recorder.start()
                                    if (!recording) toast(ctx, t("Couldn't start recording"))
                                } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Icon(Ic.Mic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                state = listState, reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(data.messages, key = { _, m -> m.id }) { i, m ->
                    val older = data.messages.getOrNull(i + 1)
                    Column(Modifier.fillMaxWidth()) {
                        if (older == null || Fmt.dayKey(older.date) != Fmt.dayKey(m.date)) DayChip(Fmt.dayLabel(m.date))
                        val sameSender = older != null && older.senderId == m.senderId && !Fmt.isService(older) &&
                            Fmt.dayKey(older.date) == Fmt.dayKey(m.date)
                        MessageItem(acc, chat, m, data.content[m.id] ?: m.content, showName = !sameSender, onPhoto = { viewer = it })
                    }
                }
                if (data.loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            }
            val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            if (showDown) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                ) { Icon(Ic.Down, null) }
            }
        }
    }

    viewer?.let { path -> PhotoViewer(path) { viewer = null } }
}

@Composable
private fun LocalContentColorOr(style: StyleSpec): Color =
    if (style.filledBar) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 12.sp, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MessageItem(acc: TgAccount, chat: ChatItem?, m: Message, content: MessageContent, showName: Boolean, onPhoto: (String) -> Unit) {
    val style = LocalStyle.current
    val ctx = LocalContext.current
    if (Fmt.isService(m)) {
        val who = acc.senderName(m.senderId)
        DayChip((if (who.isNotBlank()) "$who: " else "") + Fmt.preview(acc, m))
        return
    }
    val out = m.isOutgoing && !(chat?.isChannel ?: false)
    var menu by remember { mutableStateOf(false) }
    val bubble = if (out) style.outBubble else style.inBubble
    val fg = if (out) style.outText else style.inText
    val meta = if (out) style.outMeta else style.inMeta
    val r = style.radius
    val small = 4.dp
    val shape = when {
        !style.tail || !showName -> RoundedCornerShape(r)
        out -> RoundedCornerShape(topStart = r, topEnd = small, bottomEnd = r, bottomStart = r)
        else -> RoundedCornerShape(topStart = small, topEnd = r, bottomEnd = r, bottomStart = r)
    }
    val isSticker = content is MessageSticker
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (showName) 3.dp else 0.dp),
        horizontalArrangement = if (out) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .clip(shape)
                    .background(if (isSticker) Color.Transparent else bubble)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(if (isSticker) 0.dp else 3.dp),
            ) {
                if (showName && !out && chat != null && chat.isGroup) {
                    Text(
                        acc.senderName(m.senderId), color = style.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 7.dp, end = 7.dp, top = 3.dp), maxLines = 1,
                    )
                }
                MessageBody(acc, content, fg, meta, onPhoto)
                Row(
                    Modifier.align(Alignment.End).padding(start = 8.dp, end = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.editDate > 0) Text(t("edited") + " ", fontSize = 11.sp, color = meta)
                    Text(Fmt.clock(m.date), fontSize = 11.sp, color = meta)
                    if (m.isOutgoing) {
                        Spacer(Modifier.width(3.dp))
                        when (m.sendingState) {
                            is MessageSendingStatePending -> Text("🕓", fontSize = 10.sp)
                            is MessageSendingStateFailed -> Text("⚠", fontSize = 12.sp, color = Color(0xFFE53935))
                            else -> Icon(
                                if (m.id <= (chat?.lastReadOutbox ?: 0L)) Ic.DoneAll else Ic.Check, null,
                                Modifier.size(15.dp), tint = meta,
                            )
                        }
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val txt = textOf(content)
                if (txt.isNotEmpty()) DropdownMenuItem(text = { Text(t("Copy text")) }, onClick = {
                    menu = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("message", txt))
                    toast(ctx, t("Copied"))
                })
                DropdownMenuItem(text = { Text(t("Delete for me")) }, onClick = {
                    menu = false
                    acc.scope.launch { acc.client.deleteMessages(chatId = m.chatId, messageIds = longArrayOf(m.id), revoke = false) }
                })
                if (m.isOutgoing || chat?.isGroup == true) DropdownMenuItem(text = { Text(t("Delete for everyone")) }, onClick = {
                    menu = false
                    acc.scope.launch {
                        val res = acc.client.deleteMessages(chatId = m.chatId, messageIds = longArrayOf(m.id), revoke = true)
                        if (res is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, res.message) }
                    }
                })
            }
        }
    }
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
private fun MessageBody(acc: TgAccount, c: MessageContent, fg: Color, meta: Color, onPhoto: (String) -> Unit) {
    val ctx = LocalContext.current
    val size = Prefs.textSize.sp
    when (c) {
        is MessageText -> RichText(c.text, fg)
        is MessagePhoto -> {
            val best = c.photo.sizes.filter { maxOf(it.width, it.height) <= 1280 }.maxByOrNull { it.width * it.height }
                ?: c.photo.sizes.maxByOrNull { it.width * it.height }
            if (best != null) {
                val ratio = if (best.width > 0 && best.height > 0) best.width.toFloat() / best.height else 1f
                MediaBox(acc, best.photo, c.photo.minithumbnail, null, ratio.coerceIn(0.5f, 2f), auto = Prefs.autoPhotos, isPhoto = true,
                    onOpen = { onPhoto(it) })
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVideo -> {
            val v = c.video
            val ratio = if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 16f / 9f
            MediaBox(acc, v.video, v.minithumbnail, v.thumbnail, ratio.coerceIn(0.5f, 2f), auto = false, isPhoto = false,
                label = "▶ " + Fmt.duration(v.duration), onOpen = { openFile(ctx, it, v.mimeType) })
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageAnimation -> {
            val v = c.animation
            val ratio = if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 1f
            MediaBox(acc, v.animation, v.minithumbnail, v.thumbnail, ratio.coerceIn(0.5f, 2f), auto = false, isPhoto = false,
                label = "GIF", onOpen = { openFile(ctx, it, v.mimeType) })
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVideoNote -> {
            val v = c.videoNote
            Box(Modifier.size(200.dp).clip(CircleShape)) {
                MediaBox(acc, v.video, v.minithumbnail, v.thumbnail, 1f, auto = false, isPhoto = false,
                    label = "▶ " + Fmt.duration(v.duration), onOpen = { openFile(ctx, it, "video/mp4") })
            }
        }
        is MessageDocument -> {
            FileRow(acc, c.document.document, c.document.fileName.ifBlank { t("File") }, fg, meta) { openFile(ctx, it, c.document.mimeType) }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageAudio -> {
            val a = c.audio
            val name = listOf(a.performer, a.title).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { a.fileName.ifBlank { t("Music") } }
            FileRow(acc, a.audio, "🎵 $name", fg, meta, extra = Fmt.duration(a.duration)) { openFile(ctx, it, a.mimeType) }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVoiceNote -> VoiceRow(acc, c.voiceNote.voice, c.voiceNote.duration, fg, meta)
        is MessageSticker -> {
            val s = c.sticker
            val f = if (s.format is StickerFormatWebp) s.sticker else s.thumbnail?.file
            val cur = rememberFile(acc, f, autoDownload = true)
            if (cur != null && cur.local.isDownloadingCompleted) {
                AsyncImage(model = java.io.File(cur.local.path), contentDescription = s.emoji, modifier = Modifier.size(150.dp))
            } else {
                Text(s.emoji.ifBlank { "🙂" }, fontSize = 64.sp, modifier = Modifier.padding(8.dp))
            }
        }
        else -> Text(
            Fmt.preview(acc, null).ifBlank { previewFor(acc, c) },
            color = fg, fontSize = size, fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

private fun previewFor(acc: TgAccount, c: MessageContent): String = when (c) {
    is dev.g000sha256.tdl.dto.MessageLocation -> "📍 " + t("Location")
    is dev.g000sha256.tdl.dto.MessageContact -> "👤 " + (c.contact.firstName + " " + c.contact.lastName).trim() + "\n" + c.contact.phoneNumber
    is dev.g000sha256.tdl.dto.MessagePoll -> "📊 " + t("Poll") + ": " + c.poll.question.text
    is dev.g000sha256.tdl.dto.MessageCall -> "📞 " + t("Call") + " " + Fmt.duration(c.duration)
    else -> t("This message type isn't supported yet")
}

@Composable
private fun RichText(ft: FormattedText, fg: Color) {
    val link = Color(0xFF64B5F6)
    val s = remember(ft) { rich(ft, link) }
    Text(
        s, color = fg, fontSize = Prefs.textSize.sp, lineHeight = (Prefs.textSize + 5).sp,
        modifier = Modifier.padding(start = 7.dp, end = 7.dp, top = 4.dp),
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

/** Photo or video preview with download progress. */
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
    LaunchedEffect(done, want) {
        if (done && want && !isPhoto) { want = false; onOpen(cur.local.path) }
    }
    Box(
        Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp))
            .background(Color(0x33000000))
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
        if (label != null) {
            Text(
                label + if (!done) "  " + Fmt.size(if (cur.size > 0) cur.size else cur.expectedSize) else "",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0x88000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FileRow(acc: TgAccount, file: File, name: String, fg: Color, meta: Color, extra: String? = null, onOpen: (String) -> Unit) {
    var want by remember(file.id) { mutableStateOf(false) }
    val cur = rememberFile(acc, file, autoDownload = want) ?: file
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    LaunchedEffect(done, want) { if (done && want) { want = false; onOpen(cur.local.path) } }
    Row(
        Modifier.widthIn(min = 200.dp).clickable {
            if (done) onOpen(cur.local.path)
            else if (cur.local.isDownloadingActive) { acc.cancelDownload(cur); want = false }
            else { want = true; acc.download(cur, 16) }
        }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(LocalStyle.current.accent), contentAlignment = Alignment.Center) {
            if (cur.local.isDownloadingActive) {
                val total = if (cur.size > 0) cur.size else cur.expectedSize
                val p = if (total > 0) cur.local.downloadedSize.toFloat() / total else 0f
                CircularProgressIndicator(progress = { p.coerceIn(0f, 1f) }, color = Color.White, modifier = Modifier.size(38.dp))
            }
            Icon(if (done) Ic.Doc else if (cur.local.isDownloadingActive) Ic.Close else Ic.Download, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, color = fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val total = if (cur.size > 0) cur.size else cur.expectedSize
            val sizeText = if (cur.local.isDownloadingActive) Fmt.size(cur.local.downloadedSize) + " / " + Fmt.size(total) else Fmt.size(total)
            Text(listOfNotNull(sizeText, extra).joinToString(" · "), color = meta, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VoiceRow(acc: TgAccount, file: File, duration: Int, fg: Color, meta: Color) {
    val cur = rememberFile(acc, file, autoDownload = true, priority = 8) ?: file
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    val playing = VoicePlayer.playingFile == cur.id
    Row(Modifier.widthIn(min = 220.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(LocalStyle.current.accent)
                .clickable(enabled = done) { VoicePlayer.toggle(cur.id, cur.local.path) },
            contentAlignment = Alignment.Center,
        ) {
            if (!done) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
            else Icon(if (playing && !VoicePlayer.paused) Ic.Pause else Ic.Play, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (playing) VoicePlayer.position else 0f },
                color = fg, trackColor = meta.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(Fmt.duration(duration), color = meta, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PhotoViewer(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offX by remember { mutableFloatStateOf(0f) }
        var offY by remember { mutableFloatStateOf(0f) }
        val st = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offX += pan.x
            offY += pan.y
            if (scale == 1f) { offX = 0f; offY = 0f }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).transformable(st)) {
            AsyncImage(
                model = java.io.File(path), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offX; translationY = offY
                },
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Ic.Close, null, tint = Color.White)
            }
        }
    }
}

private suspend fun copyToCache(ctx: Context, uri: Uri): java.io.File? = withContext(Dispatchers.IO) {
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
