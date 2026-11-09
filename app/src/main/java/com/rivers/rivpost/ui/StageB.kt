package com.rivers.rivpost.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatAvailableReactions
import dev.g000sha256.tdl.dto.ChatAvailableReactionsAll
import dev.g000sha256.tdl.dto.ChatAvailableReactionsSome
import dev.g000sha256.tdl.dto.ChatEvent
import dev.g000sha256.tdl.dto.ChatEventAction
import dev.g000sha256.tdl.dto.ChatEventAvailableReactionsChanged
import dev.g000sha256.tdl.dto.ChatEventDescriptionChanged
import dev.g000sha256.tdl.dto.ChatEventForumTopicCreated
import dev.g000sha256.tdl.dto.ChatEventForumTopicDeleted
import dev.g000sha256.tdl.dto.ChatEventHasProtectedContentToggled
import dev.g000sha256.tdl.dto.ChatEventInviteLinkDeleted
import dev.g000sha256.tdl.dto.ChatEventInviteLinkEdited
import dev.g000sha256.tdl.dto.ChatEventInviteLinkRevoked
import dev.g000sha256.tdl.dto.ChatEventIsAllHistoryAvailableToggled
import dev.g000sha256.tdl.dto.ChatEventLinkedChatChanged
import dev.g000sha256.tdl.dto.ChatEventMemberInvited
import dev.g000sha256.tdl.dto.ChatEventMemberJoined
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByInviteLink
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByRequest
import dev.g000sha256.tdl.dto.ChatEventMemberLeft
import dev.g000sha256.tdl.dto.ChatEventMemberPromoted
import dev.g000sha256.tdl.dto.ChatEventMemberRestricted
import dev.g000sha256.tdl.dto.ChatEventMessageDeleted
import dev.g000sha256.tdl.dto.ChatEventMessageEdited
import dev.g000sha256.tdl.dto.ChatEventMessagePinned
import dev.g000sha256.tdl.dto.ChatEventMessageUnpinned
import dev.g000sha256.tdl.dto.ChatEventPermissionsChanged
import dev.g000sha256.tdl.dto.ChatEventPhotoChanged
import dev.g000sha256.tdl.dto.ChatEventPollStopped
import dev.g000sha256.tdl.dto.ChatEventSignMessagesToggled
import dev.g000sha256.tdl.dto.ChatEventSlowModeDelayChanged
import dev.g000sha256.tdl.dto.ChatEventTitleChanged
import dev.g000sha256.tdl.dto.ChatEventUsernameChanged
import dev.g000sha256.tdl.dto.ChatEventVideoChatCreated
import dev.g000sha256.tdl.dto.ChatEventVideoChatEnded
import dev.g000sha256.tdl.dto.ChatInviteLink
import dev.g000sha256.tdl.dto.ChatInviteLinkMember
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageReadDateMyPrivacyRestricted
import dev.g000sha256.tdl.dto.MessageReadDateRead
import dev.g000sha256.tdl.dto.MessageReadDateTooOld
import dev.g000sha256.tdl.dto.MessageReadDateUnread
import dev.g000sha256.tdl.dto.MessageReadDateUserPrivacyRestricted
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.PublicForwardMessage
import dev.g000sha256.tdl.dto.ReactionType
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.Supergroup
import dev.g000sha256.tdl.dto.SupergroupFullInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hashtag taps from messages: the open chat searches for it. */
object HashtagNav { var tag by mutableStateOf("") }

private suspend fun <T> TdlResult<T>.say(ctx: android.content.Context, ok: String? = null): T? = when (this) {
    is TdlResult.Success -> { if (ok != null) withContext(Dispatchers.Main) { toast(ctx, ok) }; result }
    is TdlResult.Failure -> { withContext(Dispatchers.Main) { toast(ctx, message) }; null }
}

// ---------------------------------------------------------------- read time

@Composable
fun readDateText(acc: TgAccount, m: Message): String {
    var s by remember(m.id) { mutableStateOf("") }
    LaunchedEffect(m.id) {
        s = when (val r = (acc.client.getMessageReadDate(chatId = m.chatId, messageId = m.id) as? TdlResult.Success)?.result) {
            is MessageReadDateRead -> t("Read at") + " " + Fmt.listTime(r.readDate) + " " + Fmt.clock(r.readDate)
            is MessageReadDateUnread -> t("Not read yet")
            is MessageReadDateMyPrivacyRestricted -> t("Read time hidden (your privacy setting)")
            is MessageReadDateUserPrivacyRestricted -> t("They hide their read time")
            is MessageReadDateTooOld -> t("Read (too old to show the time)")
            else -> ""
        }
    }
    return s
}

// ---------------------------------------------------------------- poll voters

@Composable
fun PollVotersDialog(acc: TgAccount, m: Message, onDismiss: () -> Unit) {
    val poll = (m.content as? MessagePoll)?.poll ?: return
    var votes by remember { mutableStateOf<Map<Int, List<MessageSender>>?>(null) }
    LaunchedEffect(m.id) {
        val out = LinkedHashMap<Int, List<MessageSender>>()
        poll.options.indices.forEach { i ->
            out[i] = (acc.client.getPollVoters(chatId = m.chatId, messageId = m.id, optionId = i, offset = 0, limit = 50) as? TdlResult.Success)
                ?.result?.voters?.map { it.voterId }.orEmpty()
        }
        votes = out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(poll.question.text) },
        text = {
            val v = votes
            if (v == null) CircularProgressIndicator()
            else LazyColumn(Modifier.heightIn(max = 460.dp)) {
                poll.options.forEachIndexed { i, o ->
                    item { Text(o.text.text + " · ${o.voterCount}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                    items(v[i].orEmpty()) { s ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s is MessageSenderUser) UserAvatar(acc, s.userId, 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(acc.senderName(s), maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- public forwards

@Composable
fun PublicForwardsDialog(acc: TgAccount, act: MainActivity, m: Message, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Message>?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(m.id) {
        when (val r = acc.client.getMessagePublicForwards(chatId = m.chatId, messageId = m.id, offset = "", limit = 50)) {
            is TdlResult.Success -> { total = r.result.totalCount; list = r.result.forwards.mapNotNull { (it as? PublicForwardMessage)?.message } }
            is TdlResult.Failure -> { err = r.message; list = emptyList() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Public shares") + if (total > 0) " ($total)" else "") },
        text = {
            val l = list
            if (l == null) CircularProgressIndicator()
            else if (l.isEmpty()) Text(err.ifBlank { t("No public channels shared this yet") })
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(l) { f ->
                    val c = acc.chats[f.chatId]
                    Row(Modifier.fillMaxWidth().clickable { onDismiss(); act.push(Screen.Chat(f.chatId, jump = f.id)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (c != null) ChatAvatar(acc, c, 34.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(c?.title ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("👁 ${f.interactionInfo?.viewCount ?: 0}", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- groups in common / similar

@Composable
fun RelatedChats(acc: TgAccount, act: MainActivity, chat: com.rivers.rivpost.tg.ChatItem, isBot: Boolean) {
    var ids by remember { mutableStateOf<List<Long>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    LaunchedEffect(chat.id) {
        when {
            isBot -> {
                title = t("Similar bots")
                val users = (acc.client.getBotSimilarBots(botUserId = chat.privateUserId) as? TdlResult.Success)?.result?.userIds?.toList().orEmpty()
                ids = users.mapNotNull { acc.privateChat(it) }
            }
            chat.privateUserId != 0L -> {
                title = t("Groups in common")
                ids = (acc.client.getGroupsInCommon(userId = chat.privateUserId, offsetChatId = 0, limit = 50) as? TdlResult.Success)?.result?.chatIds?.toList().orEmpty()
            }
            chat.isChannel -> {
                title = t("Similar channels")
                ids = (acc.client.getChatSimilarChats(chatId = chat.id) as? TdlResult.Success)?.result?.chatIds?.toList().orEmpty()
            }
        }
    }
    if (ids.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title + " (${ids.size})", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            items(ids) { id ->
                val c = acc.chats[id] ?: return@items
                Column(Modifier.width(76.dp).clickable { act.push(Screen.Chat(id)) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChatAvatar(acc, c, 56.dp)
                    Text(c.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- admin log

@Composable
fun AdminLogScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    var events by remember { mutableStateOf<List<ChatEvent>?>(null) }
    var err by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        when (val r = acc.client.getChatEventLog(chatId = chatId, query = query, fromEventId = 0, limit = 100, filters = null, userIds = LongArray(0))) {
            is TdlResult.Success -> events = r.result.events.toList()
            is TdlResult.Failure -> { err = r.message; events = emptyList() }
        }
    }
    Page(t("Recent actions"), onBack) {
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(t("Search")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        val l = events
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(err.ifBlank { t("No recent actions") }, modifier = Modifier.padding(20.dp))
        else for (e in l) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row {
                    Text(acc.senderName(e.memberId), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(Fmt.listTime(e.date) + " " + Fmt.clock(e.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(describe(acc, e.action), fontSize = 14.sp)
            }
            HorizontalDivider()
        }
    }
}

private fun describe(acc: TgAccount, a: ChatEventAction): String = when (a) {
    is ChatEventMessageEdited -> t("edited a message") + ": " + Fmt.preview(acc, a.newMessage)
    is ChatEventMessageDeleted -> t("deleted a message") + ": " + Fmt.preview(acc, a.message)
    is ChatEventMessagePinned -> t("pinned") + ": " + Fmt.preview(acc, a.message)
    is ChatEventMessageUnpinned -> t("unpinned") + ": " + Fmt.preview(acc, a.message)
    is ChatEventPollStopped -> t("stopped a poll")
    is ChatEventMemberJoined -> t("joined")
    is ChatEventMemberJoinedByInviteLink -> t("joined by link") + " " + a.inviteLink.name.ifBlank { a.inviteLink.inviteLink }
    is ChatEventMemberJoinedByRequest -> t("joined after approval")
    is ChatEventMemberLeft -> t("left")
    is ChatEventMemberInvited -> t("added") + " " + acc.userName(a.userId)
    is ChatEventMemberPromoted -> t("changed admin rights of") + " " + acc.userName(a.userId)
    is ChatEventMemberRestricted -> t("restricted") + " " + acc.senderName(a.memberId)
    is ChatEventTitleChanged -> t("renamed the chat to") + " \"" + a.newTitle + "\""
    is ChatEventDescriptionChanged -> t("changed the description")
    is ChatEventPhotoChanged -> t("changed the photo")
    is ChatEventUsernameChanged -> t("changed the link to") + " @" + a.newUsername
    is ChatEventPermissionsChanged -> t("changed member permissions")
    is ChatEventSlowModeDelayChanged -> t("set slow mode to") + " " + a.newSlowModeDelay + "s"
    is ChatEventInviteLinkEdited -> t("edited an invite link")
    is ChatEventInviteLinkRevoked -> t("revoked an invite link")
    is ChatEventInviteLinkDeleted -> t("deleted an invite link")
    is ChatEventHasProtectedContentToggled -> if (a.hasProtectedContent) t("turned on content protection") else t("turned off content protection")
    is ChatEventSignMessagesToggled -> if (a.signMessages) t("turned on signatures") else t("turned off signatures")
    is ChatEventIsAllHistoryAvailableToggled -> t("changed history for new members")
    is ChatEventAvailableReactionsChanged -> t("changed allowed reactions")
    is ChatEventLinkedChatChanged -> t("changed the discussion group")
    is ChatEventVideoChatCreated -> t("started a video chat")
    is ChatEventVideoChatEnded -> t("ended a video chat")
    is ChatEventForumTopicCreated -> t("created a topic") + " " + a.topicInfo.name
    is ChatEventForumTopicDeleted -> t("deleted a topic") + " " + a.topicInfo.name
    else -> a::class.simpleName?.removePrefix("ChatEvent")?.replace(Regex("([a-z])([A-Z])"), "$1 $2")?.lowercase() ?: ""
}

// ---------------------------------------------------------------- group / channel settings

@Composable
fun GroupSettingsSection(acc: TgAccount, act: MainActivity, chatId: Long) {
    val ctx = LocalContext.current
    val c = acc.chats[chatId] ?: return
    val sgId = c.supergroupId
    var full by remember { mutableStateOf<SupergroupFullInfo?>(null) }
    var sg by remember { mutableStateOf<Supergroup?>(null) }
    var chat by remember { mutableStateOf<Chat?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    LaunchedEffect(tick) {
        chat = (acc.client.getChat(chatId = chatId) as? TdlResult.Success)?.result
        if (sgId != 0L) {
            sg = (acc.client.getSupergroup(supergroupId = sgId) as? TdlResult.Success)?.result
            full = (acc.client.getSupergroupFullInfo(supergroupId = sgId) as? TdlResult.Success)?.result
        }
    }
    fun run(block: suspend () -> TdlResult<*>) = acc.scope.launch { block().say(ctx, t("Saved")); tick++ }
    SectionTitle(t("Settings"))
    ClickRow(t("Recent actions"), "›") { act.push(Screen.Page("adminlog:$chatId")) }
    if (sgId == 0L && c.isGroup) {
        ClickRow(t("Upgrade to supergroup"), t("Needed for most settings below")) {
            acc.scope.launch { acc.client.upgradeBasicGroupChatToSupergroupChat(chatId = chatId).say(ctx, t("Upgraded")) }
        }
    }
    val s = sg; val f = full; val ch = chat
    if (s != null && f != null) {
        ClickRow(t("Public link"), s.usernames?.editableUsername?.let { "t.me/$it" } ?: t("Private")) { dialog = "username" }
        if (!s.isChannel) {
            ClickRow(t("Slow mode"), if (f.slowModeDelay == 0) t("Off") else "${f.slowModeDelay}s") { dialog = "slow" }
            SwitchRow(t("Approve new members"), t("People ask to join; admins accept"), s.joinByRequest) { v ->
                run { acc.client.toggleSupergroupJoinByRequest(supergroupId = sgId, joinByRequest = v, guardBotUserId = 0, applyToInviteLinks = false) }
            }
            if (f.canHideMembers) SwitchRow(t("Hide members list"), null, f.hasHiddenMembers) { v -> run { acc.client.toggleSupergroupHasHiddenMembers(supergroupId = sgId, hasHiddenMembers = v) } }
            if (f.canToggleAggressiveAntiSpam) SwitchRow(t("Aggressive anti-spam"), null, f.hasAggressiveAntiSpamEnabled) { v ->
                run { acc.client.toggleSupergroupHasAggressiveAntiSpamEnabled(supergroupId = sgId, hasAggressiveAntiSpamEnabled = v) }
            }
            SwitchRow(t("New members see old messages"), null, f.isAllHistoryAvailable) { v -> run { acc.client.toggleSupergroupIsAllHistoryAvailable(supergroupId = sgId, isAllHistoryAvailable = v) } }
        } else {
            SwitchRow(t("Sign messages"), t("Show the admin's name on posts"), s.signMessages) { v -> run { acc.client.toggleSupergroupSignMessages(supergroupId = sgId, signMessages = v, showMessageSender = false) } }
            ClickRow(t("Discussion group"), if (f.linkedChatId != 0L) acc.chats[f.linkedChatId]?.title ?: "" else t("None")) { dialog = "discussion" }
        }
    }
    if (ch != null) {
        SwitchRow(t("Protect content"), t("No saving, forwarding or screenshots"), ch.hasProtectedContent) { v -> run { acc.client.toggleChatHasProtectedContent(chatId = chatId, hasProtectedContent = v) } }
        ClickRow(t("Allowed reactions"), if (ch.availableReactions is ChatAvailableReactionsAll) t("All") else t("Some")) { dialog = "reactions" }
    }
    ClickRow(t("Transfer ownership"), null) { dialog = "transfer" }

    when (dialog) {
        "slow" -> ChoiceDialog(t("Slow mode"), listOf(t("Off") to 0, "10s" to 10, "30s" to 30, "1m" to 60, "5m" to 300, "15m" to 900, "1h" to 3600), onDismiss = { dialog = "" }) { v ->
            dialog = ""; run { acc.client.setChatSlowModeDelay(chatId = chatId, slowModeDelay = v) }
        }
        "username" -> {
            var u by remember { mutableStateOf(sg?.usernames?.editableUsername ?: "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Public link")) },
                text = { Column { Text(t("Leave empty to make it private."), fontSize = 13.sp); OutlinedTextField(u, { u = it.trim() }, prefix = { Text("t.me/") }, singleLine = true) } },
                confirmButton = { TextButton(onClick = { dialog = ""; run { acc.client.setSupergroupUsername(supergroupId = sgId, username = u) } }) { Text(t("Save")) } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
        "discussion" -> {
            var groups by remember { mutableStateOf<List<Long>?>(null) }
            LaunchedEffect(Unit) { groups = (acc.client.getSuitableDiscussionChats() as? TdlResult.Success)?.result?.chatIds?.toList().orEmpty() }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Discussion group")) },
                text = {
                    val g = groups
                    if (g == null) CircularProgressIndicator()
                    else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        item { Text(t("None"), modifier = Modifier.fillMaxWidth().clickable { dialog = ""; run { acc.client.setChatDiscussionGroup(chatId = chatId, discussionChatId = 0) } }.padding(10.dp)) }
                        items(g) { id ->
                            Text(acc.chats[id]?.title ?: id.toString(), modifier = Modifier.fillMaxWidth().clickable {
                                dialog = ""; run { acc.client.setChatDiscussionGroup(chatId = chatId, discussionChatId = id) }
                            }.padding(10.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
        "reactions" -> {
            val common = listOf("👍", "👎", "❤", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🙏", "👌", "🕊", "🤡", "🥱", "😍", "🐳", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "💋", "😈", "😴", "😭", "🤓", "👻", "👀", "🎃", "🙈", "😇", "😨", "🤝", "✍", "🤗", "🫡", "🎅", "🎄", "☃", "💅", "🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "🙊", "😎", "👾", "🤷", "😡")
            val cur = (chat?.availableReactions as? ChatAvailableReactionsSome)?.reactions?.mapNotNull { (it as? ReactionTypeEmoji)?.emoji }?.toSet()
            val picked = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(cur ?: common) } }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Allowed reactions")) },
                text = {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(44.dp), Modifier.heightIn(max = 380.dp)) {
                        items(common.size) { i ->
                            val e = common[i]
                            Text(
                                e, fontSize = 24.sp,
                                modifier = Modifier.padding(4.dp).clickable { if (!picked.remove(e)) picked.add(e) }
                                    .graphicsLayer { alpha = if (e in picked) 1f else 0.25f },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = ""
                        val r: ChatAvailableReactions = if (picked.size == common.size) ChatAvailableReactionsAll(maxReactionCount = 11)
                        else ChatAvailableReactionsSome(reactions = picked.map { ReactionTypeEmoji(emoji = it) as ReactionType }.toTypedArray(), maxReactionCount = 11)
                        run { acc.client.setChatAvailableReactions(chatId = chatId, availableReactions = r) }
                    }) { Text(t("Save")) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { picked.clear() }) { Text(t("None")) }
                        TextButton(onClick = { picked.clear(); picked.addAll(common) }) { Text(t("All")) }
                    }
                },
            )
        }
        "transfer" -> {
            var members by remember { mutableStateOf<List<Long>?>(null) }
            var who by remember { mutableStateOf(0L) }
            var pw by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                members = (acc.client.getChatAdministrators(chatId = chatId) as? TdlResult.Success)?.result?.administrators?.map { it.userId }?.filter { it != acc.myId }.orEmpty()
            }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Transfer ownership")) },
                text = {
                    Column {
                        Text(t("Pick an admin. You'll need your two-step verification password, and you can't undo this."), fontSize = 13.sp)
                        val m = members
                        if (m == null) CircularProgressIndicator()
                        else if (m.isEmpty()) Text(t("Make someone an admin first."))
                        else LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(m) { uid ->
                                Text((if (who == uid) "● " else "○ ") + acc.userName(uid), modifier = Modifier.fillMaxWidth().clickable { who = uid }.padding(8.dp))
                            }
                        }
                        OutlinedTextField(pw, { pw = it }, label = { Text(t("Two-step password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    }
                },
                confirmButton = {
                    TextButton(enabled = who != 0L && pw.isNotEmpty(), onClick = {
                        dialog = ""
                        acc.scope.launch { acc.client.transferChatOwnership(chatId = chatId, userId = who, password = pw).say(ctx, t("Ownership transferred")) }
                    }) { Text(t("Transfer"), color = Color(0xFFE53935)) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
    }
}

// ---------------------------------------------------------------- invite link details

@Composable
fun InviteLinkDialog(acc: TgAccount, chatId: Long, link: ChatInviteLink, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var members by remember { mutableStateOf<List<ChatInviteLinkMember>?>(null) }
    var name by remember { mutableStateOf(link.name) }
    var approve by remember { mutableStateOf(link.createsJoinRequest) }
    var limit by remember { mutableStateOf(if (link.memberLimit > 0) link.memberLimit.toString() else "") }
    LaunchedEffect(link.inviteLink) {
        members = (acc.client.getChatInviteLinkMembers(chatId = chatId, inviteLink = link.inviteLink, onlyWithExpiredSubscription = false, offsetMember = null, limit = 100) as? TdlResult.Success)
            ?.result?.members?.toList().orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(link.name.ifBlank { t("Invite link") }) },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                Text(link.inviteLink + "  ⧉", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("link", link.inviteLink))
                    toast(ctx, t("Copied"))
                })
                if (!link.isPrimary) {
                    OutlinedTextField(name, { name = it }, label = { Text(t("Name")) }, singleLine = true)
                    OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text(t("Max people (empty = no limit)")) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(approve, { approve = it }); Text(t("Admins must approve joins")) }
                }
                Text(t("Joined through this link"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                val m = members
                if (m == null) CircularProgressIndicator()
                else if (m.isEmpty()) Text(t("Nobody yet"))
                else LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(m) { x -> Text(acc.userName(x.userId) + " · " + Fmt.listTime(x.joinedChatDate), modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        },
        confirmButton = {
            Row {
                if (!link.isPrimary) TextButton(onClick = {
                    onDismiss()
                    acc.scope.launch {
                        acc.client.editChatInviteLink(chatId = chatId, inviteLink = link.inviteLink, name = name, expirationDate = link.expirationDate, memberLimit = limit.toIntOrNull() ?: 0, createsJoinRequest = approve).say(ctx, t("Saved"))
                        withContext(Dispatchers.Main) { onChanged() }
                    }
                }) { Text(t("Save")) }
                TextButton(onClick = {
                    onDismiss()
                    acc.scope.launch {
                        acc.client.revokeChatInviteLink(chatId = chatId, inviteLink = link.inviteLink).say(ctx, t("Link revoked"))
                        withContext(Dispatchers.Main) { onChanged() }
                    }
                }) { Text(t("Revoke"), color = Color(0xFFE53935)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}
