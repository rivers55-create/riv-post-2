package com.rivers.rivpost.ui

import android.Manifest
import android.content.ClipData
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import dev.g000sha256.tdl.dto.TextParseModeMarkdown
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate
import dev.g000sha256.tdl.dto.DraftMessage
import dev.g000sha256.tdl.dto.DraftMessageContentText
import androidx.compose.runtime.rememberUpdatedState
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
class ChatData(val acc: TgAccount, val chatId: Long, val thread: Long = 0, val topic: Int = 0) {
    val topicId: dev.g000sha256.tdl.dto.MessageTopic? =
        if (thread != 0L) dev.g000sha256.tdl.dto.MessageTopicThread(messageThreadId = thread)
        else if (topic != 0) dev.g000sha256.tdl.dto.MessageTopicForum(forumTopicId = topic) else null

    private fun belongs(m: Message): Boolean = when {
        thread != 0L -> (m.topicId as? dev.g000sha256.tdl.dto.MessageTopicThread)?.messageThreadId == thread || m.id == thread
        topic != 0 -> (m.topicId as? dev.g000sha256.tdl.dto.MessageTopicForum)?.forumTopicId == topic
        else -> true
    }

    private suspend fun history(from: Long, offset: Int, limit: Int) = when {
        thread != 0L -> acc.client.getMessageThreadHistory(chatId = chatId, messageId = thread, fromMessageId = from, offset = offset, limit = limit)
        topic != 0 -> acc.client.getForumTopicHistory(chatId = chatId, forumTopicId = topic, fromMessageId = from, offset = offset, limit = limit)
        else -> acc.client.getChatHistory(chatId = chatId, fromMessageId = from, offset = offset, limit = limit, onlyLocal = false)
    }

    /** An album is selected as a whole. */
    fun albumOf(id: Long): List<Long> {
        val m = messages.firstOrNull { it.id == id } ?: return listOf(id)
        if (m.mediaAlbumId == 0L) return listOf(id)
        return messages.filter { it.mediaAlbumId == m.mediaAlbumId }.map { it.id }
    }

    fun select(id: Long) { for (x in albumOf(id)) if (x !in selected) selected.add(x); anchor = id }
    val messages = mutableStateListOf<Message>()
    val content = mutableStateMapOf<Long, MessageContent>()
    val interaction = mutableStateMapOf<Long, MessageInteractionInfo?>()
    val replied = mutableStateMapOf<Long, Message?>()
    var loading by mutableStateOf(false)
    var end by mutableStateOf(false)
    var pinned by mutableStateOf<Message?>(null)
    val deleted = mutableStateListOf<Long>()
    val selected = mutableStateListOf<Long>()
    var anchor by mutableStateOf(0L)

    /** Long-press a second message to select everything between. */
    fun selectRange(id: Long) {
        val a = messages.indexOfFirst { it.id == anchor }
        val b = messages.indexOfFirst { it.id == id }
        if (a < 0 || b < 0) { if (id !in selected) selected.add(id); anchor = id; return }
        for (i in minOf(a, b)..maxOf(a, b)) { val mid = messages[i].id; if (mid !in selected) selected.add(mid) }
        anchor = id
    }

    /** Select every message between the first and last selected one. */
    fun fillRange() {
        val idx = selected.map { id -> messages.indexOfFirst { it.id == id } }.filter { it >= 0 }
        if (idx.size < 2) return
        for (i in idx.min()..idx.max()) { val mid = messages[i].id; if (mid !in selected) selected.add(mid) }
    }

    fun toggle(id: Long) { if (id in selected) selected.removeAll(albumOf(id)) else select(id) }

    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        var from = messages.lastOrNull()?.id ?: 0L
        var got = 0
        var tries = 0
        while (got < 30 && tries < 5) {
            tries++
            val r = history(from, 0, 50)
            val list = (r as? TdlResult.Success)?.result?.messages?.filterNotNull() ?: break
            val fresh = list.filter { m -> messages.none { it.id == m.id } }
            if (fresh.isEmpty()) { end = true; break }
            fresh.forEach { acc.remember(it) }
            messages.addAll(fresh)
            got += fresh.size
            from = fresh.last().id
        }
        loading = false
    }

    /** Loads the messages around one message (for jumping to replies, pins, search results). */
    suspend fun loadAround(id: Long): Int {
        messages.indexOfFirst { it.id == id }.let { if (it >= 0) return it }
        val r = history(id, -25, 60)
        val list = (r as? TdlResult.Success)?.result?.messages?.filterNotNull() ?: return -1
        list.forEach { acc.remember(it) }
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
            is MsgEvent.New -> if (e.m.chatId == chatId && belongs(e.m) && messages.none { it.id == e.m.id }) messages.add(0, e.m)
            is MsgEvent.Replaced -> if (e.m.chatId == chatId && belongs(e.m)) {
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
                if (Prefs.antiRevoke) {
                    for (id in e.ids) if (id !in deleted) deleted.add(id)
                } else {
                    val ids = e.ids.toSet()
                    messages.removeAll { it.id in ids }
                }
            }
            is MsgEvent.Content -> if (e.chatId == chatId) content[e.id] = e.content
            is MsgEvent.Interaction -> if (e.chatId == chatId) interaction[e.id] = e.info
            is MsgEvent.Edited -> {}
        }
    }
}

@Composable
fun ChatScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit, jump: Long = 0, thread: Long = 0, topic: Int = 0) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val data = remember(acc, chatId, thread, topic) { ChatData(acc, chatId, thread, topic) }
    var topicName by remember { mutableStateOf("") }
    LaunchedEffect(topic) {
        if (topic != 0) topicName = (acc.client.getForumTopic(chatId = chatId, forumTopicId = topic) as? TdlResult.Success)?.result?.info?.name ?: ""
    }
    val mentionIds = remember { mutableStateMapOf<String, Long>() }
    var sendAs by remember { mutableStateOf(false) }
    val version by acc.chatsVersion.collectAsState()
    val uv by acc.usersVersion.collectAsState()
    val extra by acc.extraVersion.collectAsState()
    val chat: ChatItem? = remember(version) { acc.chats[chatId] }
    val listState = rememberLazyListState()
    var text by remember(chatId) { mutableStateOf(acc.chats[chatId]?.draft ?: "") }
    var stickers by remember { mutableStateOf(false) }
    var sendMenu by remember { mutableStateOf(false) }
    var pollDialog by remember { mutableStateOf(false) }
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
    var selDelete by remember { mutableStateOf(false) }
    var showBotKb by remember(chatId) { mutableStateOf(true) }
    var cmdMenu by remember { mutableStateOf(false) }
    var botCmds by remember(chatId) { mutableStateOf<Pair<List<dev.g000sha256.tdl.dto.BotCommand>, dev.g000sha256.tdl.dto.BotMenuButton?>?>(null) }
    BackHandler(enabled = data.selected.isNotEmpty()) { data.selected.clear() }

    DisposableEffect(acc, chatId) {
        acc.openChatId = chatId
        Notifier.clear(ctx, acc, chatId)
        if (!Prefs.ghost) acc.scope.launch { acc.client.openChat(chatId = chatId) }
        onDispose {
            if (acc.openChatId == chatId) acc.openChatId = 0
            acc.scope.launch { acc.client.closeChat(chatId = chatId) }
            recorder.cancel()
        }
    }
    val latestText by rememberUpdatedState(text)
    DisposableEffect(acc, chatId) {
        onDispose {
            val d = latestText.trim()
            if (d != (acc.chats[chatId]?.draft ?: "").trim()) acc.scope.launch {
                acc.client.setChatDraftMessage(
                    chatId = chatId,
                    draftMessage = if (d.isEmpty()) null else DraftMessage(
                        replyTo = null, date = (System.currentTimeMillis() / 1000).toInt(),
                        content = DraftMessageContentText(text = FormattedText(text = d, entities = emptyArray<TextEntity>()), linkPreviewOptions = null),
                        effectId = 0, suggestedPostInfo = null,
                    ),
                )
            }
        }
    }
    val secured = chatId in Prefs.idSet(Prefs.secureChats)
    DisposableEffect(chatId, secured) {
        if (secured) act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { act.applySecure() }
    }
    var revokedLog by remember { mutableStateOf(false) }
    LaunchedEffect(data) {
        launch { acc.events.collect { e -> withContext(Dispatchers.Main) { data.onEvent(e) } } }
        launch { data.pinned = (acc.client.getChatPinnedMessage(chatId = chatId) as? TdlResult.Success)?.result }
        data.loadMore()
    }
    val newest = data.messages.firstOrNull()?.id ?: 0L
    val newestOut = data.messages.firstOrNull()?.isOutgoing == true
    LaunchedEffect(newest) {
        if (newest != 0L && (newestOut || listState.firstVisibleItemIndex <= 1)) listState.animateScrollToItem(0)
    }
    LaunchedEffect(newest) {
        if (newest != 0L && (chat?.unread ?: 0) > 0 && !Prefs.ghost && !Prefs.readFreeze) acc.markAllRead(chatId, data.messages.take(60).map { it.id }.toLongArray())
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

    LaunchedEffect(jump) {
        if (jump != 0L) {
            delay(300)
            val i = data.loadAround(jump)
            if (i >= 0) listState.scrollToItem(i)
        }
    }

    fun send(content: InputMessageContent, opts: MessageSendOptions? = null) {
        val r = replyTo
        replyTo = null
        acc.scope.launch {
            val res = acc.client.sendMessage(
                chatId = chatId,
                topicId = data.topicId,
                replyTo = r?.let { InputMessageReplyToMessage(messageId = it.id, quote = null, checklistTaskId = 0, pollOptionId = "") },
                options = opts,
                inputMessageContent = content,
            )
            if (res is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, t("Couldn't send") + ": " + res.message) }
            else if (opts?.schedulingState != null) withContext(Dispatchers.Main) { toast(ctx, t("Scheduled")) }
        }
        scope.launch { listState.scrollToItem(0) }
    }

    fun withMentions(f: FormattedText): FormattedText {
        if (mentionIds.isEmpty()) return f
        val ents = f.entities.toMutableList()
        for ((name, uid) in mentionIds) {
            var i = f.text.indexOf(name)
            while (i >= 0) {
                if (ents.none { e -> i < e.offset + e.length && e.offset < i + name.length })
                    ents.add(TextEntity(offset = i, length = name.length, type = dev.g000sha256.tdl.dto.TextEntityTypeMentionName(userId = uid)))
                i = f.text.indexOf(name, i + name.length)
            }
        }
        mentionIds.clear()
        return FormattedText(text = f.text, entities = ents.sortedBy { it.offset }.toTypedArray())
    }

    fun sendText(opts: MessageSendOptions? = null) {
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
        if (Prefs.markdown && s.any { it == '*' || it == '_' || it == '`' || it == '[' }) {
            acc.scope.launch {
                val parsed = (acc.client.parseTextEntities(text = s, parseMode = TextParseModeMarkdown(version = 1)) as? TdlResult.Success)?.result
                withContext(Dispatchers.Main) {
                    send(InputMessageText(text = withMentions(parsed ?: FormattedText(text = s, entities = emptyArray<TextEntity>())), linkPreviewOptions = null, clearDraft = true), opts)
                }
            }
            return
        }
        send(InputMessageText(text = withMentions(FormattedText(text = s, entities = emptyArray<TextEntity>())), linkPreviewOptions = null, clearDraft = true), opts)
    }

    fun options(silent: Boolean, at: Int = 0) = MessageSendOptions(
        suggestedPostInfo = null, disableNotification = silent, fromBackground = false, protectContent = false,
        allowPaidBroadcast = false, paidMessageStarCount = 0, updateOrderOfInstalledStickerSets = false,
        schedulingState = if (at > 0) MessageSchedulingStateSendAtDate(sendDate = at, repeatPeriod = 0) else null,
        effectId = 0, sendingId = 0, onlyPreview = false,
    )

    fun pickSchedule() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(ctx, { _, y, mo, d ->
            android.app.TimePickerDialog(ctx, { _, h, mi ->
                val c = java.util.Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }
                val at = (c.timeInMillis / 1000).toInt()
                if (at <= System.currentTimeMillis() / 1000 + 30) toast(ctx, t("Pick a time in the future"))
                else sendText(options(false, at))
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    fun quickForward(m: Message) {
        acc.scope.launch {
            val target = Prefs.quickForwardChat.toLongOrNull() ?: acc.privateChat(acc.myId) ?: return@launch
            val r = acc.client.forwardMessages(chatId = target, fromChatId = m.chatId, messageIds = longArrayOf(m.id), sendCopy = Prefs.cleanForward, removeCaption = false)
            withContext(Dispatchers.Main) {
                toast(ctx, if (r is TdlResult.Failure) r.message else t("Forwarded to") + " " + (acc.chats[target]?.let { c -> if (acc.isSaved(c)) t("Saved Messages") else c.title } ?: ""))
            }
        }
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
                Prefs.wallAccent = withContext(Dispatchers.IO) { wallAccentFrom(f.absolutePath) }
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
    val botChat = peerUserId != 0L && isBot(acc, peerUserId)
    val botMarkup = remember(extra) { acc.replyMarkups[chatId] }
    LaunchedEffect(chatId) {
        val c = (acc.client.getChat(chatId = chatId) as? TdlResult.Success)?.result
        if (c != null && c.replyMarkupMessageId != 0L && !acc.replyMarkups.containsKey(chatId)) {
            (acc.client.getMessage(chatId = chatId, messageId = c.replyMarkupMessageId) as? TdlResult.Success)?.result?.let {
                acc.replyMarkups[chatId] = it
                acc.bumpExtra()
            }
        }
    }
    LaunchedEffect(botChat) { if (botChat) botCmds = botCommands(acc, peerUserId) }

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
                } else if (data.selected.isNotEmpty()) SelectionBar(
                    count = data.selected.size,
                    onClose = { data.selected.clear() },
                    onDownload = {
                        val ids = data.selected.toSet()
                        for (m in data.messages.filter { it.id in ids }) {
                            val c = data.content[m.id] ?: m.content
                            val tr = Track.from(acc.index, m)
                            if (tr != null) saveToPhone(ctx, acc, tr.file, "Music", chat)
                            else mediaFileOf(c)?.let { (f, k) -> saveToPhone(ctx, acc, f, k, chat) }
                        }
                        data.selected.clear()
                    },
                    onForward = {
                        act.push(Screen.Forward(chatId, data.selected.sorted().toLongArray()))
                        data.selected.clear()
                    },
                    onCopy = {
                        val ids = data.selected.toSet()
                        val txt = data.messages.filter { it.id in ids }.reversed()
                            .map { textOf(data.content[it.id] ?: it.content) }.filter { it.isNotBlank() }.joinToString("\n\n")
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("messages", txt))
                        toast(ctx, t("Copied"))
                        data.selected.clear()
                    },
                    onHide = {
                        val ids = data.selected.toSet()
                        val keys = data.messages.filter { it.id in ids }.mapNotNull { Track.from(acc.index, it)?.key }
                        if (keys.isEmpty()) toast(ctx, t("No songs selected")) else { Music.hide(keys); toast(ctx, t("Hidden from Music") + " (${keys.size})") }
                        data.selected.clear()
                    },
                    onDelete = { selDelete = true },
                    onRange = { data.fillRange() },
                ) else ChatHeader(
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
                        MenuItem(Ic.Clock, t("Scheduled messages")) { menu = false; act.push(Screen.Page("scheduled:$chatId")) }
                        if (chat?.isGroup == true || chat?.isChannel == true) MenuItem(Ic.Settings, t("Manage")) { menu = false; act.push(Screen.Page("admin:$chatId")) }
                        if (chat?.isGroup == true) MenuItem(Ic.Account, t("Send as")) { menu = false; sendAs = true }
                        MenuItem(Ic.Lock, if (secured) t("Allow screenshots") else t("Block screenshots")) {
                            menu = false
                            Prefs.secureChats = Prefs.toggleId(Prefs.secureChats, chatId)
                        }
                        if (Prefs.antiRevoke) MenuItem(Ic.Delete, t("Deleted messages")) { menu = false; revokedLog = true }
                        MenuItem(Ic.Edit, t("Clear History")) { menu = false; confirm = "clear" }
                        MenuItem(Ic.Delete, t("Delete Chat"), Color(0xFFE53935)) { menu = false; confirm = "delete" }
                    }
                }
            }
            // ---------- pinned ----------
            val pin = data.pinned
            if (pin != null && Prefs.pinnedBar && !hidePinned && !searching) PinnedBar(acc, pin, onClick = { jumpTo(pin.id) }, onHide = { hidePinned = true })
            if (thread != 0L) InfoBanner("💬 " + t("Comments"))
            if (topic != 0) InfoBanner("# " + topicName.ifBlank { t("Topic") })
            if (data.selected.isEmpty() && !searching) MiniPlayer(compact = true)
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
                        state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(), flingBehavior = rivFling(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(data.messages, key = { _, m -> m.id }) { i, m ->
                            val older = data.messages.getOrNull(i + 1)
                            val albumId = m.mediaAlbumId
                            if (albumId != 0L && older?.mediaAlbumId == albumId) return@itemsIndexed
                            val album = if (albumId == 0L) null else buildList<Message> {
                                var j = i
                                while (j >= 0 && data.messages[j].mediaAlbumId == albumId) { add(data.messages[j]); j-- }
                            }
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
                                    deleted = m.id in data.deleted,
                                    onQuickForward = { quickForward(it) },
                                    onSwitchInline = { q -> text = q },
                                    album = album,
                                )
                            }
                        }
                        if (data.loading) item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                    if ((Prefs.ghost || Prefs.readFreeze) && (chat?.unread ?: 0) > 0) {
                        Text(
                            (if (Prefs.ghost) "👻 " else "") + t("Mark read") + " (${chat?.unread})",
                            color = onColor(style.accent), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(CircleShape).background(style.accent)
                                .clickable { acc.markAllRead(chatId, data.messages.take(100).map { it.id }.toLongArray()) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
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

            // ---------- bots ----------
            if (text.startsWith("@") && !(chat?.isGroup == true && !text.contains(' '))) InlineResults(acc, chatId, text) { text = "" }
            if (chat?.isGroup == true && Prefs.mentionHints) MentionSuggestions(acc, chatId, text) { newText, uid, name -> text = newText; if (uid != 0L) mentionIds[name] = uid }
            if (botMarkup != null && showBotKb && botMarkup.replyMarkup is dev.g000sha256.tdl.dto.ReplyMarkupShowKeyboard) {
                BotKeyboard(acc, act, chatId, botMarkup) { k ->
                    send(InputMessageText(text = FormattedText(text = k, entities = emptyArray<TextEntity>()), linkPreviewOptions = null, clearDraft = true))
                    if ((botMarkup.replyMarkup as dev.g000sha256.tdl.dto.ReplyMarkupShowKeyboard).oneTime) showBotKb = false
                }
            }
            val cmds = botCmds
            Box {
                DropdownMenu(expanded = cmdMenu, onDismissRequest = { cmdMenu = false }, shape = RoundedCornerShape(18.dp)) {
                    val mb = cmds?.second
                    if (mb != null && mb.url.isNotBlank()) MenuItem(Ic.Movie, mb.text.ifBlank { t("Open app") }) {
                        cmdMenu = false
                        acc.scope.launch { openWebApp(acc, act, chatId, peerUserId, mb.url, mb.text.ifBlank { chat?.title ?: "" }) }
                    }
                    for (c in cmds?.first.orEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("/" + c.command, color = style.accent, fontWeight = FontWeight.SemiBold)
                                    if (c.description.isNotBlank()) Text(c.description, fontSize = 13.sp, maxLines = 2)
                                }
                            },
                            onClick = {
                                cmdMenu = false
                                send(InputMessageText(text = FormattedText(text = "/" + c.command, entities = emptyArray<TextEntity>()), linkPreviewOptions = null, clearDraft = true))
                            },
                        )
                    }
                }
            }

            // ---------- input bar ----------
            InputBar(
                showMenu = botChat && cmds != null && (cmds.first.isNotEmpty() || cmds.second != null),
                onBotMenu = { cmdMenu = true },
                hasKeyboard = botMarkup?.replyMarkup is dev.g000sha256.tdl.dto.ReplyMarkupShowKeyboard,
                onKeyboard = { showBotKb = !showBotKb },
                onMicLong = { if (Prefs.videoNotes) act.push(Screen.VideoNote(chatId)) },
                onSendLong = { sendMenu = true },
                stickersOpen = stickers,
                text = text, onText = { text = it }, recording = recording, recSecs = recSecs,
                onSend = { sendText() },
                onAttach = { attach = true },
                onGif = { stickers = !stickers; if (stickers) keyboard?.hide() },
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
            Box(Modifier.align(Alignment.End).padding(end = 12.dp)) {
                DropdownMenu(expanded = sendMenu, onDismissRequest = { sendMenu = false }, shape = RoundedCornerShape(18.dp)) {
                    MenuItem(Ic.Muted, t("Send without sound")) { sendMenu = false; sendText(options(true)) }
                    MenuItem(Ic.Clock, t("Schedule message")) { sendMenu = false; pickSchedule() }
                }
            }
            if (stickers) {
                BackHandler { stickers = false }
                StickerPanel(acc, onEmoji = { e -> text += e }, onSend = { c -> send(c) })
            }
        }
    }

    if (sendAs) SendAsDialog(acc, chatId) { sendAs = false }
    if (pollDialog) CreatePollDialog(onDismiss = { pollDialog = false }) { send(it) }
    if (selDelete) {
        val ids = data.selected.toLongArray()
        val mine = data.messages.filter { it.id in data.selected }.all { it.isOutgoing }
        ChoiceDialog(
            t("Delete") + " (${ids.size})",
            listOfNotNull(t("Delete for me") to false, if (mine || chat?.isGroup == true || chat?.privateUserId != 0L) t("Delete for everyone") to true else null),
            onDismiss = { selDelete = false },
        ) { revoke ->
            selDelete = false
            data.selected.clear()
            acc.scope.launch {
                val r = acc.client.deleteMessages(chatId = chatId, messageIds = ids, revoke = revoke)
                if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, r.message) }
            }
        }
    }
    if (attach) AttachSheet(
        onDismiss = { attach = false }, onSend = { send(it) }, onCamera = { act.push(Screen.Camera(chatId)) },
        onPoll = if (chat?.isGroup == true || chat?.isChannel == true) ({ attach = false; pollDialog = true }) else null,
        onLiveLocation = { secs, l -> com.rivers.rivpost.tg.LiveLoc.start(ctx, acc, chatId, secs, l) { toast(ctx, it) } },
        onSendAlbum = { list ->
            val r = replyTo
            replyTo = null
            acc.scope.launch {
                val res = acc.client.sendMessageAlbum(
                    chatId = chatId,
                    topicId = data.topicId,
                    replyTo = r?.let { InputMessageReplyToMessage(messageId = it.id, quote = null, checklistTaskId = 0, pollOptionId = "") },
                    inputMessageContents = list.toTypedArray(),
                )
                if (res is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, t("Couldn't send") + ": " + res.message) }
            }
        },
    )
    if (revokedLog) {
        val log = remember { acc.revokedLog(chatId).reversed() }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { revokedLog = false },
            title = { Text(t("Deleted messages")) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (log.isEmpty()) item { Text(t("Nothing deleted yet.")) }
                    items(log) { l ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text((l.getOrNull(2) ?: "") + " · " + Fmt.listTime(l.getOrNull(1)?.toIntOrNull() ?: 0), fontSize = 13.sp, color = style.accent)
                            Text(l.getOrNull(3) ?: "")
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { revokedLog = false }) { Text(t("Close")) } },
        )
    }
    LinkEditDialog()
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
private fun LocationCard(lat: Double, lon: Double, title: String, sub: String, fg: Color) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    Row(
        Modifier.padding(6.dp).widthIn(min = 220.dp).clip(RoundedCornerShape(12.dp)).background(style.accent.copy(alpha = 0.12f)).clickable {
            try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Throwable) { toast(ctx, t("No maps app found")) }
        }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📍", fontSize = 30.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = fg, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, color = fg.copy(alpha = 0.75f), fontSize = 13.sp)
            Text("%.5f, %.5f".format(lat, lon) + " · " + t("Open map"), color = style.accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, fg: Color, onClick: () -> Unit) {
    val style = LocalStyle.current
    Column(
        Modifier.padding(6.dp).widthIn(min = 220.dp, max = 300.dp).clip(RoundedCornerShape(12.dp)).background(style.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Text(title, color = fg, fontWeight = FontWeight.SemiBold)
        if (body.isNotBlank()) Text(body, color = fg.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

/** Several photos/videos sent together, shown as one grid. */
@Composable
private fun AlbumGrid(acc: TgAccount, act: MainActivity, items: List<Message>, onPhoto: (String) -> Unit) {
    val n = items.size
    val rows: List<List<Message>> = when {
        n == 3 -> listOf(items.take(1), items.drop(1))
        n % 2 == 1 -> items.dropLast(3).chunked(2) + listOf(items.takeLast(3))
        else -> items.chunked(2)
    }
    val r = LocalStyle.current.radius - 3.dp
    Column(Modifier.width(260.dp).clip(RoundedCornerShape(r)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth().height(if (row.size == 1) 170.dp else if (row.size == 2) 130.dp else 90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (m in row) {
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    when (val c = m.content) {
                        is MessagePhoto -> {
                            val best = c.photo.sizes.filter { maxOf(it.width, it.height) <= 1280 }.maxByOrNull { it.width * it.height } ?: c.photo.sizes.lastOrNull()
                            if (best != null) MediaBox(acc, best.photo, c.photo.minithumbnail, null, 1f, auto = Prefs.autoPhotos, isPhoto = true, box = cell, onOpen = { onPhoto(it) })
                            else Box(cell)
                        }
                        is MessageVideo -> {
                            val v = c.video
                            VideoPreview(acc, v.thumbnail, v.minithumbnail, 1f, Fmt.duration(v.duration), box = cell) {
                                act.push(Screen.Video(PlayRequest(acc.index, v.video, c.caption.text.take(60).ifBlank { v.fileName.ifBlank { t("Video") } }, "${m.chatId}:${m.id}")))
                            }
                        }
                        else -> Box(cell)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(acc: TgAccount, lp: dev.g000sha256.tdl.dto.LinkPreview, fg: Color) {
    if (!Prefs.linkPreviews) return
    val style = LocalStyle.current
    val ctx = LocalContext.current
    val photo = when (val ty = lp.type) {
        is dev.g000sha256.tdl.dto.LinkPreviewTypePhoto -> ty.photo
        is dev.g000sha256.tdl.dto.LinkPreviewTypeArticle -> ty.photo
        is dev.g000sha256.tdl.dto.LinkPreviewTypeVideo -> ty.cover ?: ty.video.thumbnail?.let { null }
        is dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedVideoPlayer -> ty.thumbnail
        is dev.g000sha256.tdl.dto.LinkPreviewTypeApp -> ty.photo
        else -> null
    }
    val size = photo?.sizes?.filter { maxOf(it.width, it.height) <= 800 }?.maxByOrNull { it.width } ?: photo?.sizes?.firstOrNull()
    val img = rememberFile(acc, size?.photo, autoDownload = true)
    val large = lp.showLargeMedia && size != null
    Row(
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(10.dp))
            .background(style.accent.copy(alpha = 0.12f)).clickable {
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(lp.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
            }.height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(style.accent))
        Column(Modifier.weight(1f, fill = false).padding(8.dp)) {
            if (lp.siteName.isNotBlank()) Text(lp.siteName, color = style.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (lp.title.isNotBlank()) Text(lp.title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (lp.description.text.isNotBlank()) Text(lp.description.text, color = fg.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (large && img != null && img.local.isDownloadingCompleted) {
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = java.io.File(img.local.path), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio((size!!.width.toFloat() / size.height.coerceAtLeast(1)).coerceIn(0.6f, 2f)).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        if (!large && img != null && img.local.isDownloadingCompleted) {
            AsyncImage(
                model = java.io.File(img.local.path), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(8.dp).size(56.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun playOrToggle(acc: TgAccount, m: Message, track: Track) {
    if (Music.current?.key == track.key) Music.toggle() else Music.playFromChat(acc, m)
}

@Composable
private fun SelectionBar(
    count: Int, onClose: () -> Unit, onDownload: () -> Unit, onForward: () -> Unit,
    onCopy: () -> Unit, onHide: () -> Unit, onDelete: () -> Unit, onRange: () -> Unit,
) {
    val style = LocalStyle.current
    val fg = style.navBarFg
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(style.navBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Ic.Close, null, tint = fg) }
        Text("$count", color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (count >= 2) IconButton(onClick = onRange) { Icon(Ic.SelectRange, null, tint = style.accent) }
        IconButton(onClick = onDownload) { Icon(Ic.Download, null, tint = fg) }
        IconButton(onClick = onForward) { Icon(Ic.Forward, null, tint = fg) }
        IconButton(onClick = onDelete) { Icon(Ic.Delete, null, tint = Color(0xFFE57373)) }
        Box {
            IconButton(onClick = { more = true }) { Icon(Ic.More, null, tint = fg) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }, shape = RoundedCornerShape(18.dp)) {
                if (count >= 2) MenuItem(Ic.SelectRange, t("Select everything between")) { more = false; onRange() }
                MenuItem(Ic.Copy, t("Copy")) { more = false; onCopy() }
                MenuItem(Ic.Hide, t("Hide songs from Music")) { more = false; onHide() }
            }
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
    showMenu: Boolean, onBotMenu: () -> Unit, hasKeyboard: Boolean, onKeyboard: () -> Unit, onMicLong: () -> Unit,
    onSendLong: () -> Unit, stickersOpen: Boolean,
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
                if (showMenu) Box(
                    Modifier.padding(start = 4.dp).clip(RoundedCornerShape(16.dp)).background(style.accent).clickable(onClick = onBotMenu)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(t("Menu"), color = onColor(style.accent), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                else if (Prefs.gifButton) IconButton(onClick = onGif) {
                    Icon(if (stickersOpen) Ic.Keyboard else Ic.Smile, null, tint = fg.copy(alpha = 0.75f))
                }
                TextField(
                    value = text, onValueChange = onText, placeholder = { Text(t("Message")) }, maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = fg, unfocusedTextColor = fg, cursorColor = style.accent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = Prefs.textSize.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                        if (Prefs.enterSends && ev.key == Key.Enter) { onSend(); true } else false
                    },
                )
                if (hasKeyboard) IconButton(onClick = onKeyboard) { Icon(Ic.Keyboard, null, tint = fg.copy(alpha = 0.75f)) }
                IconButton(onClick = onAttach) { Icon(Ic.Attach, null, tint = fg.copy(alpha = 0.75f)) }
            }
        }
        Spacer(Modifier.width(6.dp))
        val showSend = recording || text.isNotBlank()
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(style.accent)
                .pointerInput(recording, text.isNotBlank()) {
                    detectTapGestures(
                        onTap = { if (recording) onSendRec() else if (text.isNotBlank()) onSend() else onMic() },
                        onLongPress = { if (!recording && text.isBlank()) onMicLong() else if (!recording) onSendLong() },
                    )
                },
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
    deleted: Boolean = false, onQuickForward: (Message) -> Unit = {}, onSwitchInline: (String) -> Unit = {},
    album: List<Message>? = null,
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
    val r = when (Prefs.bubbleShape) { "pill" -> 26.dp; "square" -> 3.dp; "borderless" -> 0.dp; else -> style.radius }
    val small = if (Prefs.bubbleShape == "square") 0.dp else 4.dp
    val shape = when {
        Prefs.bubbleShape == "pill" -> RoundedCornerShape(r)
        !style.tail || !showName -> RoundedCornerShape(r)
        out -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = small, bottomStart = r)
        else -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r, bottomStart = small)
    }
    val borderless = Prefs.bubbleShape == "borderless"
    val compact = Prefs.chatDensity == "compact"
    val isSticker = content is MessageSticker
    var dragX by remember(m.id) { mutableFloatStateOf(0f) }
    var history by remember { mutableStateOf<List<String>?>(null) }
    var selectDialog by remember { mutableStateOf(false) }
    var translate by remember { mutableStateOf(false) }
    var seenBy by remember { mutableStateOf(false) }
    var replied by remember(m.id) { mutableStateOf<Message?>(null) }
    LaunchedEffect(m.id) { if (m.replyTo is MessageReplyToMessage) replied = data.replyOf(m) }
    val video = remember(m.id) { VideoItem.from(acc.index, m) }
    val track = remember(m.id) { Track.from(acc.index, m) }

    Row(
        Modifier.fillMaxWidth()
            .background(if (m.id in data.selected) style.accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = if (compact) 0.dp else if (showName) 3.dp else 0.dp)
            .graphicsLayer { translationX = dragX; alpha = if (deleted) 0.55f else 1f }
            .pointerInput(m.id, data.selected.isNotEmpty()) {
                val selecting = data.selected.isNotEmpty()
                detectTapGestures(
                    onTap = { if (selecting) data.toggle(m.id) },
                    onLongPress = {
                        if (selecting) data.selectRange(m.id)
                        else if (Prefs.longPressSelect) data.select(m.id)
                    },
                )
            }
            .pointerInput(m.id, Prefs.swipeForward, Prefs.swipeReply) {
                if (Prefs.swipeForward || Prefs.swipeReply) detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -140f) { if (Prefs.swipeReply) onReply(m) else onQuickForward(m) }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { _, d -> dragX = (dragX + d).coerceIn(-220f, 0f) }
            },
        horizontalArrangement = if (out) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data.selected.isNotEmpty()) {
            val on = m.id in data.selected
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (on) Color(0xFF4CD964) else Color.Transparent)
                    .border(2.dp, if (on) Color.White else style.inMeta, CircleShape)
                    .clickable { data.toggle(m.id) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            if (out) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f, fill = false)) {
            Column(horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
            Column(
                Modifier.widthIn(max = if (borderless) 400.dp else 310.dp)
                    .let { mod ->
                        if (!isSticker && !borderless && (Prefs.bubbleShadow > 0 || Prefs.bubbleGlow)) mod.shadow(
                            elevation = if (Prefs.bubbleGlow) 10.dp else Prefs.bubbleShadow.dp, shape = shape,
                            ambientColor = if (Prefs.bubbleGlow) style.accent else Color.Black,
                            spotColor = if (Prefs.bubbleGlow) style.accent else Color.Black,
                        ) else mod
                    }
                    .clip(shape)
                    .background(if (isSticker || borderless) Color.Transparent else bubble)
                    .let { mod -> if (Prefs.bubbleBorder > 0 && !isSticker && !borderless) mod.border(Prefs.bubbleBorder.dp, style.border, shape) else mod }
                    .pointerInput(m.id, data.selected.isNotEmpty()) {
                        val selecting = data.selected.isNotEmpty()
                        detectTapGestures(
                            onLongPress = {
                                when {
                                    selecting -> data.selectRange(m.id)
                                    Prefs.longPressSelect -> data.select(m.id)
                                    else -> menu = true
                                }
                            },
                            onTap = {
                                if (selecting) data.toggle(m.id)
                                else if (Prefs.longPressSelect) menu = true
                            },
                            onDoubleTap = { if (!selecting && Prefs.doubleTapReact) onReact(m, Prefs.quickReaction) },
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
                MessageBody(acc, act, m, content, fg, meta, onPhoto, onMenu = { menu = true }, album = album)
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
                    if (deleted) Text("🗑 " + t("deleted") + "  ", fontSize = 12.sp, color = Color(0xFFE57373))
                    if (m.editDate > 0) {
                        val canSee = Prefs.antiRevoke && acc.hasHistory(m.chatId, m.id)
                        Text(
                            t("edited") + (if (canSee) " ⓘ" else "") + " ", fontSize = 12.sp, color = meta,
                            modifier = if (canSee) Modifier.clickable { history = acc.editHistory(m.chatId, m.id) } else Modifier,
                        )
                    }
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
                if (m.replyMarkup is dev.g000sha256.tdl.dto.ReplyMarkupInlineKeyboard) InlineButtons(acc, act, m, onSwitchInline)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    for (e in QuickReactions) {
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { menu = false; onReact(m, e) }.padding(6.dp))
                    }
                }
                HorizontalDivider()
                MenuItem(Ic.Reply, t("Reply")) { menu = false; onReply(m) }
                if (track != null) MenuItem(Ic.Download, t("Save to Music")) {
                    menu = false
                    saveToPhone(ctx, acc, track.file, "Music", chat)
                }
                MenuItem(Ic.Pin, if (m.isPinned) t("Unpin") else t("Pin")) {
                    menu = false
                    acc.scope.launch {
                        val r = if (m.isPinned) acc.client.unpinChatMessage(chatId = m.chatId, messageId = m.id)
                        else acc.client.pinChatMessage(chatId = m.chatId, messageId = m.id, disableNotification = false, onlyForSelf = false)
                        if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, r.message) }
                    }
                }
                MenuItem(Ic.Info, t("Report")) {
                    menu = false
                    acc.scope.launch {
                        acc.client.reportChat(chatId = m.chatId, optionId = ByteArray(0), messageIds = longArrayOf(m.id), text = "")
                        withContext(Dispatchers.Main) { toast(ctx, t("Reported")) }
                    }
                }
                val txt = textOf(content)
                if (txt.isNotEmpty()) MenuItem(Ic.Copy, t("Copy")) {
                    menu = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("message", txt))
                    toast(ctx, t("Copied"))
                }
                if (m.isOutgoing && content is MessageText) MenuItem(Ic.Edit, t("Edit")) { menu = false; onEdit(m) }
                MenuItem(Ic.Forward, t("Forward")) { menu = false; act.push(Screen.Forward(m.chatId, longArrayOf(m.id))) }
                if (chat?.isGroup == true || chat?.isChannel == true) MenuItem(Ic.Link, t("Copy link")) {
                    menu = false
                    acc.scope.launch {
                        val r = acc.client.getMessageLink(chatId = m.chatId, messageId = m.id, mediaTimestamp = 0, checklistTaskId = 0, pollOptionId = "", forAlbum = false, inMessageThread = false)
                        withContext(Dispatchers.Main) {
                            val link = (r as? TdlResult.Success)?.result?.link
                            if (link == null) toast(ctx, (r as? TdlResult.Failure)?.message ?: "")
                            else {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("link", link))
                                toast(ctx, t("Link copied"))
                            }
                        }
                    }
                }
                if (txt.isNotEmpty()) MenuItem(Ic.Edit, t("Select text")) { menu = false; selectDialog = true }
                if (txt.isNotEmpty() && Prefs.translateButton) MenuItem(Ic.Translate, t("Translate")) { menu = false; translate = true }
                if (m.isOutgoing && chat?.isGroup == true) MenuItem(Ic.DoneAll, t("Seen by")) { menu = false; seenBy = true }
                val savable = mediaFileOf(content)
                if (savable != null) MenuItem(Ic.Download, t("Save to phone")) {
                    menu = false
                    saveToPhone(ctx, acc, savable.first, savable.second, chat)
                }
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
        HistoryAndSelect(history, { history = null }, selectDialog, textOf(content), { selectDialog = false })
        if (translate) TranslateDialog(acc, m, textOf(content)) { translate = false }
        if (seenBy) SeenByDialog(acc, m) { seenBy = false }
        if (isPost && Prefs.commentsButton && info?.replyInfo != null) {
            Spacer(Modifier.width(6.dp))
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(style.navBar.copy(alpha = 0.85f)).clickable {
                    acc.scope.launch {
                        val r = acc.client.getMessageThread(chatId = m.chatId, messageId = m.id)
                        withContext(Dispatchers.Main) {
                            when (r) {
                                is TdlResult.Success -> act.push(Screen.Chat(r.result.chatId, thread = r.result.messageThreadId))
                                is TdlResult.Failure -> toast(ctx, r.message)
                            }
                        }
                    }
                }.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("💬", fontSize = 16.sp)
                Text(compact(info.replyInfo!!.replyCount), color = style.navBarFg, fontSize = 11.sp)
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

@Composable
private fun HistoryAndSelect(history: List<String>?, onCloseHistory: () -> Unit, select: Boolean, text: String, onCloseSelect: () -> Unit) {
    if (history != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = onCloseHistory,
        title = { Text(t("Earlier versions")) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScrollCompat()) {
                for ((i, h) in history.withIndex()) {
                    Text("${i + 1}.", fontSize = 12.sp, color = LocalStyle.current.accent)
                    Text(h, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onCloseHistory) { Text(t("Close")) } },
    )
    if (select) androidx.compose.material3.AlertDialog(
        onDismissRequest = onCloseSelect,
        title = { Text(t("Select text")) },
        text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, fontSize = 17.sp) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onCloseSelect) { Text(t("Close")) } },
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

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
private fun MessageBody(acc: TgAccount, act: MainActivity, m: Message, c: MessageContent, fg: Color, meta: Color, onPhoto: (String) -> Unit, onMenu: () -> Unit = {}, album: List<Message>? = null) {
    if (album != null && album.size > 1 && (c is MessagePhoto || c is MessageVideo)) {
        AlbumGrid(acc, act, album, onPhoto)
        val cap = album.firstNotNullOfOrNull { a -> when (val x = a.content) { is MessagePhoto -> x.caption; is MessageVideo -> x.caption; else -> null }?.takeIf { it.text.isNotBlank() } }
        if (cap != null) RichText(cap, fg)
        return
    }
    val ctx = LocalContext.current
    when (c) {
        is dev.g000sha256.tdl.dto.MessagePoll -> PollView(acc, m, c, fg, meta)
        is MessageText -> {
            val lp = c.linkPreview
            if (lp != null && lp.showAboveText) LinkPreviewCard(acc, lp, fg)
            RichText(c.text, fg)
            if (lp != null && !lp.showAboveText) LinkPreviewCard(acc, lp, fg)
        }
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
                VideoNoteBubble(cur.local.path, v.duration, meta)
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
                track != null -> SongRow(track, fg, meta, onMenu) { playOrToggle(acc, m, track) }
                video != null && Prefs.builtInVideo -> FileRow(acc, c.document.document, "🎬 " + c.document.fileName, fg, meta, play = true) {
                    act.push(Screen.Video(video.request()))
                }
                else -> FileRow(acc, c.document.document, c.document.fileName.ifBlank { t("File") }, fg, meta) { openFile(ctx, it, c.document.mimeType) }
            }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageAudio -> {
            val track = remember(m.id) { Track.from(acc.index, m) }
            if (track != null) SongRow(track, fg, meta, onMenu) { playOrToggle(acc, m, track) }
            if (c.caption.text.isNotBlank()) RichText(c.caption, fg)
        }
        is MessageVoiceNote -> VoiceRow(acc, c.voiceNote.voice, c.voiceNote.duration, c.voiceNote.waveform, fg, meta)
        is dev.g000sha256.tdl.dto.MessageDice -> Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.emoji, fontSize = 64.sp)
            if (c.value > 0) Text(t("Result") + ": " + c.value, color = fg, fontWeight = FontWeight.Bold)
        }
        is dev.g000sha256.tdl.dto.MessageLocation -> LocationCard(c.location.latitude, c.location.longitude, t("Location"), "", fg)
        is dev.g000sha256.tdl.dto.MessageLiveLocation -> LocationCard(
            c.location.location.latitude, c.location.location.longitude, "📡 " + t("Live location"),
            if (c.expiresIn > 0) t("Updating for") + " " + Fmt.duration(c.expiresIn) else t("Ended"), fg,
        )
        is dev.g000sha256.tdl.dto.MessageVenue -> LocationCard(c.venue.location.latitude, c.venue.location.longitude, c.venue.title, c.venue.address, fg)
        is dev.g000sha256.tdl.dto.MessageGame -> InfoCard("🎮 " + c.game.title, c.game.description.ifBlank { c.game.text.text }, fg) {
            acc.scope.launch {
                val r = acc.client.getCallbackQueryAnswer(chatId = m.chatId, messageId = m.id, payload = dev.g000sha256.tdl.dto.CallbackQueryPayloadGame(gameShortName = c.game.shortName))
                val url = (r as? TdlResult.Success)?.result?.url
                withContext(Dispatchers.Main) { if (!url.isNullOrBlank()) act.push(Screen.WebApp(WebAppRequest(acc.index, c.game.title, url))) else toast(ctx, (r as? TdlResult.Failure)?.message ?: "") }
            }
        }
        is dev.g000sha256.tdl.dto.MessageInvoice -> InfoCard(
            "🧾 " + c.productInfo.title, c.productInfo.description.text + "\n" + "%.2f".format(c.totalAmount / 100.0) + " " + c.currency, fg,
        ) { toast(ctx, t("Payments aren't supported in Riv Post")) }
        is dev.g000sha256.tdl.dto.MessageGiveaway -> InfoCard(
            "🎁 " + t("Giveaway"), "${c.winnerCount} " + t("winners") + " · " + t("ends") + " " + Fmt.listTime(c.parameters.winnersSelectionDate), fg,
        ) { }
        is dev.g000sha256.tdl.dto.MessageStory -> InfoCard("📖 " + t("Story"), acc.chats[c.storyPosterChatId]?.title ?: "", fg) {
            act.push(Screen.Page("story:" + c.storyPosterChatId))
        }
        is MessageSticker -> {
            val s = c.sticker
            val animated = Prefs.animStickers && (s.format is StickerFormatTgs || s.format is StickerFormatWebm)
            val f = if (s.format is StickerFormatWebp || animated) s.sticker else s.thumbnail?.file
            val cur = rememberFile(acc, f, autoDownload = true)
            val ok = cur != null && cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
            var pack by remember { mutableStateOf(false) }
            if (pack && s.setId != 0L) StickerPackDialog(acc, s.setId) { pack = false }
            Box(Modifier.clickable { pack = true }) { when {
                ok && s.format is StickerFormatTgs && animated -> TgsSticker(cur!!.local.path, Modifier.size(Prefs.stickerSize.dp))
                ok && s.format is StickerFormatWebm && animated -> LoopVideo(cur!!.local.path, Modifier.size(Prefs.stickerSize.dp))
                ok -> AsyncImage(model = java.io.File(cur!!.local.path), contentDescription = s.emoji, modifier = Modifier.size(Prefs.stickerSize.dp))
                else -> Text(s.emoji.ifBlank { "🙂" }, fontSize = 64.sp, modifier = Modifier.padding(8.dp))
            } }
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
private fun SongRow(t0: Track, fg: Color, meta: Color, onMenu: () -> Unit, onPlay: () -> Unit) {
    val style = LocalStyle.current
    val playing = Music.current?.key == t0.key
    val acc = com.rivers.rivpost.tg.Tg.accounts.value[t0.acc]
    val cur = if (acc != null) rememberFile(acc, t0.file, autoDownload = false) else null
    val downloaded = cur?.local?.isDownloadingCompleted == true
    val pos = if (playing) rememberMusicPosition() else 0L
    Row(Modifier.widthIn(min = 250.dp).padding(start = 6.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clickable(onClick = onPlay)) {
            TrackArt(t0, Modifier.fillMaxSize(), CircleShape)
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0x44000000)), contentAlignment = Alignment.Center) {
                if (playing && Music.loading) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (playing && Music.isPlaying) Ic.Pause else Ic.Play, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            if (!downloaded) Box(
                Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(style.accent),
                contentAlignment = Alignment.Center,
            ) {
                val p = cur?.let { f -> val tot = if (f.size > 0) f.size else f.expectedSize; if (f.local.isDownloadingActive && tot > 0) f.local.downloadedSize.toFloat() / tot else -1f } ?: -1f
                if (p >= 0f) CircularProgressIndicator(progress = { p }, color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(Ic.Download, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false).clickable(onClick = onPlay)) {
            Text(t0.title, color = style.link, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t0.artist, color = meta, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val total = if (t0.duration > 0) t0.duration else ((if (playing) Music.duration else 0L) / 1000).toInt()
            Text(Fmt.duration((pos / 1000).toInt()) + " / " + Fmt.duration(total), color = meta, fontSize = 13.sp)
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) { Icon(Ic.More, null, tint = meta) }
    }
}

@Composable
private fun RichText(ft: FormattedText, fg: Color) {
    val link = LocalStyle.current.link
    val s = remember(ft, link) { rich(ft, link) }
    Text(
        s, color = fg, fontSize = Prefs.textSize.sp, lineHeight = (Prefs.textSize + 6).sp,
        style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content, textAlign = androidx.compose.ui.text.style.TextAlign.Start),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
    )
}

private fun rich(ft: FormattedText, link: Color): AnnotatedString = buildAnnotatedString {
    val text = ft.text
    append(text)
    val ls = TextLinkStyles(style = SpanStyle(color = link, textDecoration = TextDecoration.Underline))
    fun url(u: String) = LinkAnnotation.Url(u, ls) { a -> LinkEdit.open((a as LinkAnnotation.Url).url) }
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
                addLink(url(if (u.contains("://")) u else "https://$u"), a, b)
            }
            is TextEntityTypeTextUrl -> addLink(url(ty.url), a, b)
            is TextEntityTypeEmailAddress -> addLink(url("mailto:" + text.substring(a, b)), a, b)
            is TextEntityTypeMention, is TextEntityTypeHashtag -> addStyle(SpanStyle(color = link), a, b)
            else -> {}
        }
    }
}

@Composable
private fun VideoPreview(acc: TgAccount, thumb: Thumbnail?, mini: Minithumbnail?, ratio: Float, label: String, box: Modifier? = null, onPlay: () -> Unit) {
    val thumbFile = if (thumb != null && (thumb.format is ThumbnailFormatJpeg || thumb.format is ThumbnailFormatWebp)) thumb.file else null
    val th = rememberFile(acc, thumbFile, autoDownload = true)
    val mb = remember(mini) { miniBitmap(mini?.data) }
    Box(
        (box ?: Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp))).background(Color(0x33000000)).clickable(onClick = onPlay),
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
    auto: Boolean, isPhoto: Boolean, label: String? = null, box: Modifier? = null, onOpen: (String) -> Unit,
) {
    var want by remember(file.id) { mutableStateOf(false) }
    val cur = rememberFile(acc, file, autoDownload = auto || want) ?: file
    val thumbFile = if (thumb != null && (thumb.format is ThumbnailFormatJpeg || thumb.format is ThumbnailFormatWebp)) thumb.file else null
    val th = rememberFile(acc, thumbFile, autoDownload = true)
    val mb = remember(mini) { miniBitmap(mini?.data) }
    val done = cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()
    LaunchedEffect(done, want) { if (done && want && !isPhoto) { want = false; onOpen(cur.local.path) } }
    Box(
        (box ?: Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(LocalStyle.current.radius - 3.dp))).background(Color(0x33000000))
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
            WaveBars(bars, prog, fg)
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

@Composable
private fun WaveBars(bars: FloatArray, prog: Float, fg: Color) {
    val accent = LocalStyle.current.accent
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width / bars.size
        when (Prefs.waveStyle) {
            "line" -> {
                val path = androidx.compose.ui.graphics.Path()
                for (i in bars.indices) {
                    val x = i * w + w / 2
                    val y = size.height / 2 - (bars[i] - 0.5f) * size.height * 0.9f * (if (i % 2 == 0) 1 else -1)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, fg.copy(alpha = 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                clipRect(right = size.width * prog) { drawPath(path, fg, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx())) }
            }
            "particles" -> {
                for (i in bars.indices) {
                    val played = (i + 0.5f) / bars.size <= prog
                    val n = (bars[i] * 4).toInt().coerceAtLeast(1)
                    for (k in 0 until n) {
                        val y = size.height / 2 + (k - n / 2f) * 5.dp.toPx()
                        drawCircle(if (played) accent else fg.copy(alpha = 0.35f), radius = w * 0.28f, center = Offset(i * w + w / 2, y))
                    }
                }
            }
            "neon" -> {
                for (i in bars.indices) {
                    val h = bars[i] * size.height
                    val played = (i + 0.5f) / bars.size <= prog
                    val c = if (played) accent else fg.copy(alpha = 0.3f)
                    if (played) drawRoundRect(c.copy(alpha = 0.3f), Offset(i * w, (size.height - h) / 2 - 2f), Size(w, h + 4f), CornerRadius(w / 2))
                    drawRoundRect(c, Offset(i * w + w * 0.3f, (size.height - h) / 2), Size(w * 0.4f, h), CornerRadius(w * 0.2f))
                }
            }
            else -> for (i in bars.indices) {
                val h = bars[i] * size.height
                val played = (i + 0.5f) / bars.size <= prog
                drawRoundRect(
                    if (played) fg else fg.copy(alpha = 0.4f),
                    Offset(i * w + w * 0.2f, (size.height - h) / 2), Size(w * 0.6f, h), CornerRadius(w * 0.3f),
                )
            }
        }
    }
}

/** Link taps go through here so the link can be edited first (optional). */
object LinkEdit {
    var pending by mutableStateOf<String?>(null)
    var direct: ((String) -> Unit)? = null
    var proxyLink by mutableStateOf<String?>(null)
    fun open(url: String) {
        if (com.rivers.rivpost.tg.Proxies.parseLink(url) != null) { proxyLink = url; return }
        if (Prefs.editLinks) pending = url else direct?.invoke(url)
    }
}

@Composable
fun LinkEditDialog() {
    val ctx = LocalContext.current
    fun go(u: String) {
        try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Throwable) { toast(ctx, t("Couldn't open the link")) }
    }
    LinkEdit.direct = { go(it) }
    LinkEdit.proxyLink?.let { link ->
        val px = com.rivers.rivpost.tg.Proxies.parseLink(link)
        if (px != null) androidx.compose.material3.AlertDialog(
            onDismissRequest = { LinkEdit.proxyLink = null },
            title = { Text(t("Add this proxy?")) },
            text = { Text(px.label) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.rivers.rivpost.tg.Proxies.save(px)
                    com.rivers.rivpost.tg.Proxies.use(px)
                    LinkEdit.proxyLink = null
                    toast(ctx, t("Proxy added and turned on"))
                }) { Text(t("Add and use")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.rivers.rivpost.tg.Proxies.save(px)
                    LinkEdit.proxyLink = null
                    toast(ctx, t("Saved"))
                }) { Text(t("Just save")) }
            },
        )
    }
    val p = LinkEdit.pending ?: return
    var text by remember(p) { mutableStateOf(p) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { LinkEdit.pending = null },
        title = { Text(t("Open link")) },
        text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { LinkEdit.pending = null; go(text.trim()) }) { Text(t("Open")) } },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", text))
                LinkEdit.pending = null
                toast(ctx, t("Copied"))
            }) { Text(t("Copy")) }
        },
    )
}

/** The downloadable file of a message and its kind (for "Save to phone"). */
private fun mediaFileOf(c: MessageContent): Pair<File, String>? = when (c) {
    is MessagePhoto -> c.photo.sizes.maxByOrNull { it.width * it.height }?.photo?.let { it to "Photos" }
    is MessageVideo -> c.video.video to "Videos"
    is MessageAnimation -> c.animation.animation to "Videos"
    is MessageAudio -> c.audio.audio to "Music"
    is MessageVoiceNote -> c.voiceNote.voice to "Voice"
    is MessageVideoNote -> c.videoNote.video to "Videos"
    is MessageDocument -> c.document.document to "Files"
    else -> null
}

private fun saveToPhone(ctx: Context, acc: TgAccount, f: File, kind: String, chat: ChatItem?) {
    toast(ctx, t("Saving…"))
    acc.scope.launch {
        val r = acc.client.downloadFile(fileId = f.id, priority = 32, offset = 0, limit = 0, synchronous = true)
        val local = (r as? TdlResult.Success)?.result?.local?.path?.takeIf { it.isNotEmpty() }
        val ok = local != null && withContext(Dispatchers.IO) { MediaSaver.save(ctx, java.io.File(local), kind) }
        withContext(Dispatchers.Main) { toast(ctx, if (ok) t("Saved to") + " " + Prefs.saveFolder else t("Couldn't save")) }
    }
}

object MediaSaver {
    fun save(ctx: Context, src: java.io.File, kind: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return try {
            val ext = src.extension.lowercase()
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val (collection, base) = when (kind) {
                "Photos" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI to android.os.Environment.DIRECTORY_PICTURES
                "Videos" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI to android.os.Environment.DIRECTORY_MOVIES
                "Music", "Voice" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to android.os.Environment.DIRECTORY_MUSIC
                else -> android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI to android.os.Environment.DIRECTORY_DOWNLOADS
            }
            val folder = Prefs.saveFolder.ifBlank { "Riv Post" }
            val rel = base + "/" + folder + if (Prefs.saveByType) "/$kind" else ""
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            true
        } catch (_: Throwable) { false }
    }
}
