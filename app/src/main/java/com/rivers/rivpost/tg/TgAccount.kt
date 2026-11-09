package com.rivers.rivpost.tg

import android.content.Context
import android.os.Build
import com.rivers.rivpost.BuildConfig
import com.rivers.rivpost.data.Prefs
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One chat as the chat list needs it. */
data class ChatItem(
    val id: Long,
    val title: String,
    val type: ChatType,
    val photo: File?,
    val mini: ByteArray?,
    val lastMessage: Message?,
    val positions: List<ChatPosition>,
    val unread: Int,
    val mentions: Int,
    val muteFor: Int,
    val useDefaultMute: Boolean,
    val lastReadInbox: Long,
    val lastReadOutbox: Long,
    val markedUnread: Boolean,
    val notif: ChatNotificationSettings,
) {
    fun withPosition(p: ChatPosition): ChatItem {
        val rest = positions.filter { it.list != p.list }
        return copy(positions = if (p.order == 0L) rest else rest + p)
    }

    val isChannel: Boolean get() = (type as? ChatTypeSupergroup)?.isChannel == true
    val isGroup: Boolean get() = type is ChatTypeBasicGroup || (type is ChatTypeSupergroup && !isChannel)
    val privateUserId: Long get() = (type as? ChatTypePrivate)?.userId ?: (type as? ChatTypeSecret)?.userId ?: 0L
    val isSecret: Boolean get() = type is ChatTypeSecret
    val secretId: Int get() = (type as? ChatTypeSecret)?.secretChatId ?: 0
    val supergroupId: Long get() = (type as? ChatTypeSupergroup)?.supergroupId ?: 0L
}

/** Message events that open chat screens listen to. */
sealed class MsgEvent {
    class New(val m: Message) : MsgEvent()
    class Replaced(val oldId: Long, val m: Message) : MsgEvent()
    class Deleted(val chatId: Long, val ids: LongArray) : MsgEvent()
    class Content(val chatId: Long, val id: Long, val content: MessageContent) : MsgEvent()
    class Edited(val chatId: Long, val id: Long, val editDate: Int) : MsgEvent()
    class Interaction(val chatId: Long, val id: Long, val info: MessageInteractionInfo?) : MsgEvent()
}

/** One Telegram account: its own TDLib client and database folder. */
class TgAccount(private val ctx: Context, val index: Int) {
    val client: TdlClient = TdlClient.create()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val auth = MutableStateFlow<AuthorizationState?>(null)
    val conn = MutableStateFlow<ConnectionState?>(null)
    val me = MutableStateFlow<User?>(null)
    val folders = MutableStateFlow<List<ChatFolderInfo>>(emptyList())
    val events = MutableSharedFlow<MsgEvent>(extraBufferCapacity = 512)

    val chats = ConcurrentHashMap<Long, ChatItem>()
    val chatsVersion = MutableStateFlow(0L)
    val users = ConcurrentHashMap<Long, User>()
    val usersVersion = MutableStateFlow(0L)
    private val files = ConcurrentHashMap<Int, MutableStateFlow<File>>()
    private val scopeMute = ConcurrentHashMap<String, Int>()
    val supergroups = ConcurrentHashMap<Long, Supergroup>()
    val basicGroups = ConcurrentHashMap<Long, BasicGroup>()
    val secretChats = ConcurrentHashMap<Int, SecretChat>()
    val statuses = ConcurrentHashMap<Long, UserStatus>()
    /** Unread counters per chat list key (see [listKey]): first = unread chats, second = unread unmuted chats. */
    val unreadCounts = ConcurrentHashMap<String, Pair<Int, Int>>()
    /** chatId -> (who is typing, until when in ms). */
    val typing = ConcurrentHashMap<Long, Pair<String, Long>>()
    val extraVersion = MutableStateFlow(0L)
    private fun bumpExtra() { extraVersion.value = extraVersion.value + 1 }

    @Volatile var openChatId = 0L
    @Volatile var loggingOut = false
    @Volatile private var paramsSent = false

    val dir = java.io.File(ctx.filesDir, "tdlib/acc_$index")
    val uploads = ConcurrentHashMap<Int, File>()

    // ---------- anti-revoke / edit history (opt-in, local only) ----------
    private val seen = object : LinkedHashMap<String, Message>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Message>?): Boolean = size > 5000
    }
    private val lastText = HashMap<String, String>()
    private val revokePrefs by lazy { ctx.getSharedPreferences("riv_revoke_$index", Context.MODE_PRIVATE) }

    fun remember(m: Message) {
        if (!Prefs.antiRevoke) return
        synchronized(seen) {
            val k = "${m.chatId}:${m.id}"
            seen[k] = m
            if (!lastText.containsKey(k)) lastText[k] = Fmt.preview(this, m)
        }
    }

    fun rememberedMessage(chatId: Long, id: Long): Message? = synchronized(seen) { seen["$chatId:$id"] }

    /** Deleted messages saved for one chat: lines of "id|date|sender|text". */
    fun revokedLog(chatId: Long): List<List<String>> =
        (revokePrefs.getString("d$chatId", "") ?: "").split("\n").filter { it.isNotBlank() }.map { it.split("|", limit = 4) }

    fun editHistory(chatId: Long, id: Long): List<String> =
        (revokePrefs.getString("e$chatId:$id", "") ?: "").split("\n").filter { it.isNotBlank() }.map { it.replace("\\n", "\n") }

    fun hasHistory(chatId: Long, id: Long): Boolean = revokePrefs.contains("e$chatId:$id")

    private fun logDeleted(chatId: Long, ids: LongArray) {
        val lines = StringBuilder(revokePrefs.getString("d$chatId", "") ?: "")
        synchronized(seen) {
            for (id in ids) {
                val m = seen["$chatId:$id"] ?: continue
                val text = Fmt.preview(this, m).replace("\n", " ").replace("|", "/")
                lines.append("$id|${m.date}|${senderName(m.senderId).replace("|", "/")}|$text\n")
            }
        }
        revokePrefs.edit().putString("d$chatId", lines.toString().takeLast(60_000)).apply()
    }

    private fun logEdit(chatId: Long, id: Long, newContent: MessageContent) {
        val k = "$chatId:$id"
        val old = synchronized(seen) { lastText[k] } ?: return
        val prev = revokePrefs.getString("e$k", "") ?: ""
        revokePrefs.edit().putString("e$k", (prev + old.replace("\n", "\\n") + "\n").takeLast(8_000)).apply()
        synchronized(seen) {
            seen[k]?.let { m -> lastText[k] = Fmt.previewContent(this, newContent, m) }
        }
    }
    val uploadsVersion = MutableStateFlow(0L)

    /** Tells Telegram whether we're online (off in ghost mode). */
    fun setOnline(on: Boolean) {
        if (!isReady) return
        scope.launch { client.setOption(name = "online", value = OptionValueBoolean(value = on && !Prefs.ghost)) }
    }
    val isReady: Boolean get() = auth.value is AuthorizationStateReady

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.allUpdates.collect { u ->
                try { handle(u) } catch (_: Throwable) { }
            }
        }
        scope.launch { client.getOption(name = "version") }
    }

    // ---------- updates ----------

    private fun bumpChats() { chatsVersion.value = chatsVersion.value + 1 }

    private inline fun edit(chatId: Long, f: (ChatItem) -> ChatItem) {
        val c = chats[chatId] ?: return
        chats[chatId] = f(c)
        bumpChats()
    }

    private fun handle(u: Update) {
        when (u) {
            is UpdateAuthorizationState -> onAuth(u.authorizationState)
            is UpdateConnectionState -> conn.value = u.state
            is UpdateNewChat -> {
                val c = u.chat
                c.photo?.small?.let { track(it) }
                chats[c.id] = ChatItem(
                    id = c.id, title = c.title, type = c.type,
                    photo = c.photo?.small, mini = c.photo?.minithumbnail?.data,
                    lastMessage = c.lastMessage, positions = c.positions.toList(),
                    unread = c.unreadCount, mentions = c.unreadMentionCount,
                    muteFor = c.notificationSettings.muteFor,
                    useDefaultMute = c.notificationSettings.useDefaultMuteFor,
                    lastReadInbox = c.lastReadInboxMessageId,
                    lastReadOutbox = c.lastReadOutboxMessageId,
                    markedUnread = c.isMarkedAsUnread,
                    notif = c.notificationSettings,
                )
                bumpChats()
            }
            is UpdateChatTitle -> edit(u.chatId) { it.copy(title = u.title) }
            is UpdateChatPhoto -> {
                u.photo?.small?.let { track(it) }
                edit(u.chatId) { it.copy(photo = u.photo?.small, mini = u.photo?.minithumbnail?.data) }
            }
            is UpdateChatLastMessage -> edit(u.chatId) {
                var x = it.copy(lastMessage = u.lastMessage)
                for (p in u.positions) x = x.withPosition(p)
                x
            }
            is UpdateChatPosition -> edit(u.chatId) { it.withPosition(u.position) }
            is UpdateChatReadInbox -> edit(u.chatId) {
                it.copy(unread = u.unreadCount, lastReadInbox = u.lastReadInboxMessageId)
            }
            is UpdateChatReadOutbox -> edit(u.chatId) { it.copy(lastReadOutbox = u.lastReadOutboxMessageId) }
            is UpdateChatUnreadMentionCount -> edit(u.chatId) { it.copy(mentions = u.unreadMentionCount) }
            is UpdateChatIsMarkedAsUnread -> edit(u.chatId) { it.copy(markedUnread = u.isMarkedAsUnread) }
            is UpdateChatNotificationSettings -> edit(u.chatId) {
                it.copy(
                    muteFor = u.notificationSettings.muteFor,
                    useDefaultMute = u.notificationSettings.useDefaultMuteFor,
                    notif = u.notificationSettings,
                )
            }
            is UpdateScopeNotificationSettings -> {
                val key = when (u.scope) {
                    is NotificationSettingsScopePrivateChats -> "private"
                    is NotificationSettingsScopeGroupChats -> "group"
                    else -> "channel"
                }
                scopeMute[key] = u.notificationSettings.muteFor
            }
            is UpdateChatFolders -> folders.value = u.chatFolders.toList()
            is UpdateSupergroup -> { supergroups[u.supergroup.id] = u.supergroup; bumpExtra() }
            is UpdateBasicGroup -> { basicGroups[u.basicGroup.id] = u.basicGroup; bumpExtra() }
            is UpdateSecretChat -> { secretChats[u.secretChat.id] = u.secretChat; bumpExtra() }
            is UpdateUserStatus -> { statuses[u.userId] = u.status; bumpExtra() }
            is UpdateUnreadChatCount -> {
                unreadCounts[listKey(u.chatList)] = u.unreadCount to u.unreadUnmutedCount
                bumpExtra()
            }
            is UpdateChatAction -> {
                if (u.action is ChatActionCancel) typing.remove(u.chatId)
                else {
                    val who = senderName(u.senderId).split(" ").first()
                    val what = when (u.action) {
                        is ChatActionRecordingVoiceNote -> "recording voice"
                        is ChatActionUploadingPhoto -> "sending a photo"
                        is ChatActionUploadingVideo, is ChatActionRecordingVideo -> "sending a video"
                        is ChatActionUploadingDocument -> "sending a file"
                        is ChatActionChoosingSticker -> "choosing a sticker"
                        else -> "typing"
                    }
                    typing[u.chatId] = (who + "|" + what) to (System.currentTimeMillis() + 6000)
                }
                bumpExtra()
            }
            is UpdateMessageInteractionInfo -> events.tryEmit(MsgEvent.Interaction(u.chatId, u.messageId, u.interactionInfo))
            is UpdateUser -> {
                users[u.user.id] = u.user
                statuses[u.user.id] = u.user.status
                u.user.profilePhoto?.small?.let { track(it) }
                usersVersion.value = usersVersion.value + 1
                if (me.value?.id == u.user.id) me.value = u.user
            }
            is UpdateFile -> {
                if (u.file.remote.isUploadingActive || uploads.containsKey(u.file.id)) {
                    uploads[u.file.id] = u.file
                    uploadsVersion.value = uploadsVersion.value + 1
                }
                val f = files[u.file.id]
                if (f != null) f.value = u.file else files[u.file.id] = MutableStateFlow(u.file)
            }
            is UpdateNewMessage -> {
                remember(u.message)
                events.tryEmit(MsgEvent.New(u.message))
                Notifier.onNewMessage(ctx, this, u.message)
            }
            is UpdateMessageSendSucceeded -> events.tryEmit(MsgEvent.Replaced(u.oldMessageId, u.message))
            is UpdateMessageSendFailed -> events.tryEmit(MsgEvent.Replaced(u.oldMessageId, u.message))
            is UpdateDeleteMessages -> if (u.isPermanent) {
                if (Prefs.antiRevoke) logDeleted(u.chatId, u.messageIds)
                events.tryEmit(MsgEvent.Deleted(u.chatId, u.messageIds))
            }
            is UpdateMessageContent -> {
                if (Prefs.antiRevoke) logEdit(u.chatId, u.messageId, u.newContent)
                events.tryEmit(MsgEvent.Content(u.chatId, u.messageId, u.newContent))
            }
            is UpdateMessageEdited -> events.tryEmit(MsgEvent.Edited(u.chatId, u.messageId, u.editDate))
            else -> {}
        }
    }

    private fun onAuth(s: AuthorizationState) {
        auth.value = s
        when (s) {
            is AuthorizationStateWaitTdlibParameters -> if (!paramsSent) {
                paramsSent = true
                scope.launch { sendParameters() }
            }
            is AuthorizationStateReady -> scope.launch {
                (client.getMe() as? TdlResult.Success)?.let { r ->
                    users[r.result.id] = r.result
                    me.value = r.result
                    r.result.profilePhoto?.small?.let { track(it) }
                }
                load(ChatListMain())
            }
            is AuthorizationStateClosed -> Tg.onClosed(this)
            else -> {}
        }
    }

    private suspend fun sendParameters() {
        dir.mkdirs()
        client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = dir.absolutePath,
            filesDirectory = java.io.File(dir, "files").absolutePath,
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = true,
            apiId = Prefs.apiId,
            apiHash = Prefs.apiHash,
            systemLanguageCode = Locale.getDefault().language.ifBlank { "en" },
            deviceModel = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
            systemVersion = "Android " + Build.VERSION.RELEASE,
            applicationVersion = BuildConfig.VERSION_NAME,
        )
        applyProxy()
    }

    /** Applies the proxy from Settings to this account. */
    fun applyProxy() {
        scope.launch {
            val old = client.getProxies()
            if (old is TdlResult.Success) {
                for (p in old.result.proxies) client.removeProxy(proxyId = p.id)
            }
            if (Prefs.proxyOn && Prefs.proxyHost.isNotBlank()) {
                val type: ProxyType = if (Prefs.proxyType == "mtproto") {
                    ProxyTypeMtproto(secret = Prefs.proxySecret.trim())
                } else {
                    ProxyTypeSocks5(username = Prefs.proxyUser, password = Prefs.proxyPass)
                }
                client.addProxy(
                    proxy = Proxy(server = Prefs.proxyHost.trim(), port = Prefs.proxyPort, type = type),
                    enable = true,
                    comment = "Riv Post",
                )
            } else {
                client.disableProxy()
            }
        }
    }

    // ---------- login ----------

    suspend fun sendPhone(phone: String): String? = err(client.setAuthenticationPhoneNumber(phoneNumber = phone))
    suspend fun sendCode(code: String): String? = err(client.checkAuthenticationCode(code = code))
    suspend fun sendPassword(pw: String): String? = err(client.checkAuthenticationPassword(password = pw))
    suspend fun register(first: String, last: String): String? =
        err(client.registerUser(firstName = first, lastName = last, disableNotification = false))

    suspend fun resendCode(): String? = err(client.resendAuthenticationCode())

    fun logOut() {
        loggingOut = true
        scope.launch { client.logOut() }
    }

    fun close() {
        scope.launch { client.close() }
    }

    // ---------- chats ----------

    fun load(list: ChatList, limit: Int = 100) {
        scope.launch { client.loadChats(chatList = list, limit = limit) }
    }

    /** Chats of one list, in Telegram's order. Second value: pinned. */
    fun chatList(list: ChatList): List<Pair<ChatItem, Boolean>> {
        val out = ArrayList<Triple<ChatItem, Long, Boolean>>()
        for (c in chats.values) {
            val p = c.positions.firstOrNull { it.list == list } ?: continue
            if (p.order != 0L) out.add(Triple(c, p.order, p.isPinned))
        }
        out.sortByDescending { it.second }
        return out.map { it.first to it.third }
    }

    fun userName(id: Long): String {
        val u = users[id] ?: return ""
        return (u.firstName + " " + u.lastName).trim().ifBlank { u.usernames?.activeUsernames?.firstOrNull() ?: "" }
    }

    fun senderName(s: MessageSender): String = when (s) {
        is MessageSenderUser -> userName(s.userId)
        is MessageSenderChat -> chats[s.chatId]?.title ?: ""
        else -> ""
    }

    fun isMuted(c: ChatItem): Boolean {
        if (!c.useDefaultMute) return c.muteFor > 0
        return when {
            c.isChannel -> !Prefs.notifChannels || (scopeMute["channel"] ?: 0) > 0
            c.isGroup -> !Prefs.notifGroups || (scopeMute["group"] ?: 0) > 0
            else -> (scopeMute["private"] ?: 0) > 0
        }
    }

    fun markAllRead(chatId: Long, ids: LongArray) {
        if (ids.isEmpty()) return
        scope.launch { client.viewMessages(chatId = chatId, messageIds = ids, source = null, forceRead = true) }
    }

    // ---------- files ----------

    fun track(f: File) {
        val cur = files[f.id]
        if (cur == null) files[f.id] = MutableStateFlow(f)
        else if (f.local.isDownloadingCompleted && !cur.value.local.isDownloadingCompleted) cur.value = f
    }

    fun fileById(id: Int): File? = files[id]?.value

    fun fileFlow(f: File): StateFlow<File> {
        track(f)
        return files[f.id]!!
    }

    fun download(f: File, priority: Int = 1) {
        if (f.local.isDownloadingCompleted || f.local.isDownloadingActive || !f.local.canBeDownloaded) return
        scope.launch { client.downloadFile(fileId = f.id, priority = priority, offset = 0, limit = 0, synchronous = false) }
    }

    fun cancelDownload(f: File) {
        scope.launch { client.cancelDownloadFile(fileId = f.id, onlyIfPending = false) }
    }

    private fun err(r: TdlResult<*>): String? = (r as? TdlResult.Failure)?.message

    fun listKey(l: ChatList): String = when (l) {
        is ChatListFolder -> "folder" + l.chatFolderId
        is ChatListArchive -> "archive"
        else -> "main"
    }

    fun isVerified(c: ChatItem): Boolean {
        if (c.privateUserId != 0L) return users[c.privateUserId]?.verificationStatus?.isVerified == true
        if (c.supergroupId != 0L) return supergroups[c.supergroupId]?.verificationStatus?.isVerified == true
        return false
    }

    fun status(userId: Long): UserStatus? = statuses[userId] ?: users[userId]?.status

    fun isSaved(c: ChatItem): Boolean = !c.isSecret && c.privateUserId != 0L && c.privateUserId == me.value?.id

    fun isOnline(userId: Long): Boolean = statuses[userId] is UserStatusOnline

    fun memberCount(c: ChatItem): Int = when (val t = c.type) {
        is ChatTypeSupergroup -> supergroups[t.supergroupId]?.memberCount ?: 0
        is ChatTypeBasicGroup -> basicGroups[t.basicGroupId]?.memberCount ?: 0
        else -> 0
    }

    fun typingText(chatId: Long): String? {
        val t = typing[chatId] ?: return null
        if (t.second < System.currentTimeMillis()) { typing.remove(chatId); return null }
        return t.first
    }

    val myId: Long get() = me.value?.id ?: 0L

    suspend fun <T> ok(r: TdlResult<T>): T? = (r as? TdlResult.Success)?.result

    /** Opens (or creates) the private chat with a user and returns its id. */
    suspend fun privateChat(userId: Long): Long? = ok(client.createPrivateChat(userId = userId, force = false))?.id

    fun setMuted(c: ChatItem, mute: Boolean, secs: Int = 366 * 24 * 3600) {
        val n = c.notif
        val ns = ChatNotificationSettings(
            useDefaultMuteFor = false, muteFor = if (mute) secs else 0,
            useDefaultSound = n.useDefaultSound, soundId = n.soundId,
            useDefaultShowPreview = n.useDefaultShowPreview, showPreview = n.showPreview,
            useDefaultMuteStories = n.useDefaultMuteStories, muteStories = n.muteStories,
            useDefaultStorySound = n.useDefaultStorySound, storySoundId = n.storySoundId,
            useDefaultShowStoryPoster = n.useDefaultShowStoryPoster, showStoryPoster = n.showStoryPoster,
            useDefaultDisablePinnedMessageNotifications = n.useDefaultDisablePinnedMessageNotifications,
            disablePinnedMessageNotifications = n.disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications = n.useDefaultDisableMentionNotifications,
            disableMentionNotifications = n.disableMentionNotifications,
        )
        scope.launch { client.setChatNotificationSettings(chatId = c.id, notificationSettings = ns) }
    }

    /** Searches all chats for messages of one kind (music, videos…). */
    suspend fun searchAll(filter: SearchMessagesFilter, query: String, offset: String, limit: Int = 100): FoundMessages? =
        ok(client.searchMessages(chatList = null, query = query, offset = offset, limit = limit, filter = filter, chatTypeFilter = null, minDate = 0, maxDate = 0))
}
