package com.rivers.rivpost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ChatInviteLinkInfo
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.InternalLinkTypeBotStart
import dev.g000sha256.tdl.dto.InternalLinkTypeBotStartInGroup
import dev.g000sha256.tdl.dto.InternalLinkTypeChatInvite
import dev.g000sha256.tdl.dto.InternalLinkTypeMainWebApp
import dev.g000sha256.tdl.dto.InternalLinkTypeMessage
import dev.g000sha256.tdl.dto.InternalLinkTypeProxy
import dev.g000sha256.tdl.dto.InternalLinkTypePublicChat
import dev.g000sha256.tdl.dto.InternalLinkTypeStickerSet
import dev.g000sha256.tdl.dto.InternalLinkTypeUserPhoneNumber
import dev.g000sha256.tdl.dto.InternalLinkTypeWebApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens Telegram links (t.me, tg://) inside Riv Post. */
object TgLinks {
    var act: MainActivity? = null
    var invite by mutableStateOf<Pair<String, ChatInviteLinkInfo>?>(null)
    var stickerSet by mutableStateOf(0L)

    private val hosts = setOf("t.me", "telegram.me", "telegram.dog", "www.t.me")

    fun isTelegram(url: String): Boolean {
        val u = url.trim().lowercase()
        if (u.startsWith("tg:") || u.startsWith("tg://")) return true
        val host = u.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?')
        return host in hosts || host.endsWith(".t.me")
    }

    /** Returns false if it's not something we handle (caller opens the browser). */
    fun open(url: String, fallback: (String) -> Unit) {
        val a = act ?: return fallback(url)
        val acc = Tg.get(Tg.activeIndex.value)
        acc.scope.launch {
            val type = (acc.client.getInternalLinkType(link = url) as? TdlResult.Success)?.result
            suspend fun openChatByName(name: String, then: suspend (Long) -> Unit = {}) {
                val c = (acc.client.searchPublicChat(username = name) as? TdlResult.Success)?.result
                withContext(Dispatchers.Main) {
                    if (c == null) toast(a, t("Couldn't find") + " @" + name)
                    else a.push(Screen.Chat(c.id))
                }
                if (c != null) then(c.id)
            }
            when (type) {
                is InternalLinkTypePublicChat -> openChatByName(type.chatUsername)
                is InternalLinkTypeMessage -> {
                    val info = (acc.client.getMessageLinkInfo(url = type.url) as? TdlResult.Success)?.result
                    withContext(Dispatchers.Main) {
                        if (info == null || info.chatId == 0L) toast(a, t("Couldn't open the message"))
                        else a.push(Screen.Chat(info.chatId, jump = info.message?.id ?: 0L))
                    }
                }
                is InternalLinkTypeChatInvite -> {
                    when (val r = acc.client.checkChatInviteLink(inviteLink = type.inviteLink)) {
                        is TdlResult.Success -> withContext(Dispatchers.Main) {
                            if (r.result.chatId != 0L && acc.chats.containsKey(r.result.chatId)) a.push(Screen.Chat(r.result.chatId))
                            else invite = type.inviteLink to r.result
                        }
                        is TdlResult.Failure -> withContext(Dispatchers.Main) { toast(a, r.message) }
                    }
                }
                is InternalLinkTypeBotStart -> openChatByName(type.botUsername) { chatId ->
                    val c = (acc.client.getChat(chatId = chatId) as? TdlResult.Success)?.result
                    val bot = (c?.type as? ChatTypePrivate)?.userId ?: 0L
                    if (bot != 0L) acc.client.sendBotStartMessage(botUserId = bot, chatId = chatId, parameter = type.startParameter)
                }
                is InternalLinkTypeBotStartInGroup -> openChatByName(type.botUsername)
                is InternalLinkTypeMainWebApp -> openChatByName(type.botUsername)
                is InternalLinkTypeWebApp -> openChatByName(type.botUsername)
                is InternalLinkTypeUserPhoneNumber -> {
                    val u = (acc.client.searchUserByPhoneNumber(phoneNumber = type.phoneNumber, onlyLocal = false) as? TdlResult.Success)?.result
                    val c = u?.let { acc.privateChat(it.id) }
                    withContext(Dispatchers.Main) { if (c != null) a.push(Screen.Chat(c)) else toast(a, t("Couldn't find this number on Telegram")) }
                }
                is InternalLinkTypeStickerSet -> {
                    val s = (acc.client.searchStickerSet(name = type.stickerSetName, ignoreCache = false) as? TdlResult.Success)?.result
                    withContext(Dispatchers.Main) { if (s != null) stickerSet = s.id else toast(a, t("Couldn't find the sticker pack")) }
                }
                is InternalLinkTypeProxy -> withContext(Dispatchers.Main) { LinkEdit.proxyLink = url }
                else -> withContext(Dispatchers.Main) { fallback(url) }
            }
        }
    }
}

/** "Join this group/channel?" and sticker pack dialogs for links. */
@Composable
fun TgLinkDialogs() {
    val ctx = LocalContext.current
    val acc = Tg.get(Tg.activeIndex.value)
    TgLinks.invite?.let { (link, info) ->
        AlertDialog(
            onDismissRequest = { TgLinks.invite = null },
            title = { Text(info.title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("${info.memberCount} " + t("members"))
                    if (info.description.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(info.description, fontSize = 14.sp) }
                    if (info.createsJoinRequest) { Spacer(Modifier.height(6.dp)); Text(t("Admins must approve your request."), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TgLinks.invite = null
                    acc.scope.launch {
                        when (val r = acc.client.joinChatByInviteLink(inviteLink = link)) {
                            is TdlResult.Success -> withContext(Dispatchers.Main) {
                                val id = (r.result as? dev.g000sha256.tdl.dto.ChatJoinResultSuccess)?.chatId ?: 0L
                                if (id != 0L) TgLinks.act?.push(Screen.Chat(id)) else toast(ctx, t("Request sent"))
                            }
                            is TdlResult.Failure -> withContext(Dispatchers.Main) { toast(ctx, r.message) }
                        }
                    }
                }) { Text(if (info.createsJoinRequest) t("Request to join") else t("Join")) }
            },
            dismissButton = { TextButton(onClick = { TgLinks.invite = null }) { Text(t("Cancel")) } },
        )
    }
    if (TgLinks.stickerSet != 0L) StickerPackDialog(acc, TgLinks.stickerSet) { TgLinks.stickerSet = 0L }
}

/** Search Telegram: public chats/bots, people by phone, and messages in all chats. */
@Composable
fun GlobalSearch(acc: com.rivers.rivpost.tg.TgAccount, act: MainActivity, query: String, shown: Set<Long>) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    var publicChats by androidx.compose.runtime.remember { mutableStateOf<List<Long>>(emptyList()) }
    var messages by androidx.compose.runtime.remember { mutableStateOf<List<dev.g000sha256.tdl.dto.Message>>(emptyList()) }
    var loading by androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(query) {
        kotlinx.coroutines.delay(400)
        loading = true
        val ids = LinkedHashSet<Long>()
        (acc.client.searchChatsOnServer(query = query, limit = 20) as? TdlResult.Success)?.result?.chatIds?.forEach { ids.add(it) }
        (acc.client.searchPublicChats(query = query.removePrefix("@")) as? TdlResult.Success)?.result?.chatIds?.forEach { ids.add(it) }
        val digits = query.filter { it.isDigit() }
        if (digits.length >= 7 && digits.length >= query.count { !it.isWhitespace() } - 1) {
            (acc.client.searchUserByPhoneNumber(phoneNumber = digits, onlyLocal = false) as? TdlResult.Success)?.result?.let { u -> acc.privateChat(u.id)?.let { ids.add(it) } }
        }
        publicChats = ids.filter { it !in shown }
        messages = (acc.client.searchMessages(query = query, offset = "", limit = 30, minDate = 0, maxDate = 0) as? TdlResult.Success)?.result?.messages?.filterNotNull().orEmpty()
        loading = false
    }
    Column {
        if (publicChats.isNotEmpty()) {
            Text(t("Global search"), color = cs.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            for (id in publicChats) {
                val c = acc.chats[id] ?: continue
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable { act.push(Screen.Chat(id)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    ChatAvatar(acc, c, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        val sub = when {
                            c.isChannel -> t("Channel"); c.isGroup -> t("Group"); else -> ""
                        }
                        val count = acc.memberCount(c)
                        Text(listOf(sub, count).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        if (messages.isNotEmpty()) {
            Text(t("Messages"), color = cs.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            for (m in messages) {
                val c = acc.chats[m.chatId] ?: continue
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable { act.push(Screen.Chat(m.chatId, jump = m.id)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    ChatAvatar(acc, c, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(com.rivers.rivpost.tg.Fmt.preview(acc, m), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 2)
                    }
                    Text(com.rivers.rivpost.tg.Fmt.listTime(m.date), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        if (loading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
    }
}

