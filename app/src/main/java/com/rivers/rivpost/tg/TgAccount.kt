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
) {
    fun withPosition(p: ChatPosition): ChatItem {
        val rest = positions.filter { it.list != p.list }
        return copy(positions = if (p.order == 0L) rest else rest + p)
    }

    val isChannel: Boolean get() = (type as? ChatTypeSupergroup)?.isChannel == true
    val isGroup: Boolean get() = type is ChatTypeBasicGroup || (type is ChatTypeSupergroup && !isChannel)
    val privateUserId: Long get() = (type as? ChatTypePrivate)?.userId ?: (type as? ChatTypeSecret)?.userId ?: 0L
}

/** Message events that open chat screens listen to. */
sealed class MsgEvent {
    class New(val m: Message) : MsgEvent()
    class Replaced(val oldId: Long, val m: Message) : MsgEvent()
    class Deleted(val chatId: Long, val ids: LongArray) : MsgEvent()
    class Content(val chatId: Long, val id: Long, val content: MessageContent) : MsgEvent()
    class Edited(val chatId: Long, val id: Long, val editDate: Int) : MsgEvent()
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

    @Volatile var openChatId = 0L
    @Volatile var loggingOut = false
    @Volatile private var paramsSent = false

    val dir = java.io.File(ctx.filesDir, "tdlib/acc_$index")
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
            is UpdateUser -> {
                users[u.user.id] = u.user
                u.user.profilePhoto?.small?.let { track(it) }
                usersVersion.value = usersVersion.value + 1
                if (me.value?.id == u.user.id) me.value = u.user
            }
            is UpdateFile -> {
                val f = files[u.file.id]
                if (f != null) f.value = u.file else files[u.file.id] = MutableStateFlow(u.file)
            }
            is UpdateNewMessage -> {
                events.tryEmit(MsgEvent.New(u.message))
                Notifier.onNewMessage(ctx, this, u.message)
            }
            is UpdateMessageSendSucceeded -> events.tryEmit(MsgEvent.Replaced(u.oldMessageId, u.message))
            is UpdateMessageSendFailed -> events.tryEmit(MsgEvent.Replaced(u.oldMessageId, u.message))
            is UpdateDeleteMessages -> if (u.isPermanent) events.tryEmit(MsgEvent.Deleted(u.chatId, u.messageIds))
            is UpdateMessageContent -> events.tryEmit(MsgEvent.Content(u.chatId, u.messageId, u.newContent))
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
}
