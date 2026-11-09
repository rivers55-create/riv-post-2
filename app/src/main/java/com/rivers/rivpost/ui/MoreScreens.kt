package com.rivers.rivpost.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.push.Push
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.ChatItem
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.dto.ChatAdministratorRights
import dev.g000sha256.tdl.dto.ChatInviteLink
import dev.g000sha256.tdl.dto.ChatJoinRequest
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusMember
import dev.g000sha256.tdl.dto.ChatPermissions
import dev.g000sha256.tdl.dto.ChatStatisticsChannel
import dev.g000sha256.tdl.dto.ChatStatisticsSupergroup
import dev.g000sha256.tdl.dto.ForumTopic
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import dev.g000sha256.tdl.dto.NotificationSettingsScopeChannelChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopeGroupChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopePrivateChats
import dev.g000sha256.tdl.dto.PasswordState
import dev.g000sha256.tdl.dto.StatisticalValue
import dev.g000sha256.tdl.dto.StickerSetInfo
import dev.g000sha256.tdl.dto.StickerTypeRegular
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copy(ctx: Context, s: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", s))
    toast(ctx, t("Copied"))
}

private fun <T> TdlResult<T>.ok(): T? = (this as? TdlResult.Success)?.result

private suspend fun <T> TdlResult<T>.report(ctx: Context, done: String? = null): T? = when (this) {
    is TdlResult.Success -> { if (done != null) withContext(Dispatchers.Main) { toast(ctx, done) }; result }
    is TdlResult.Failure -> { withContext(Dispatchers.Main) { toast(ctx, message) }; null }
}

/** Pages opened by name (Screen.Page). Returns false if the name isn't one of these. */
fun isMorePage(name: String): Boolean = name.substringBefore(':') in setOf(
    "members", "topics", "admin", "links", "requests", "stats", "scheduled", "newchannel", "twostep",
    "stickers", "exceptions", "datausage", "blocked", "story", "firebase", "sendas", "adminlog",
    "account2", "websites", "addcontact", "notifdefaults", "language", "wallpapers", "emojistatus", "iv", "storage", "qrscan", "mappick",
)

@Composable
fun MorePage(acc: TgAccount, act: MainActivity, name: String, onBack: () -> Unit) {
    val arg = name.substringAfter(':', "").toLongOrNull() ?: 0L
    when (name.substringBefore(':')) {
        "members" -> MembersScreen(acc, act, arg, onBack)
        "topics" -> ForumTopicsScreen(acc, act, arg, onBack)
        "admin" -> AdminScreen(acc, act, arg, onBack)
        "links" -> InviteLinksScreen(acc, arg, onBack)
        "requests" -> JoinRequestsScreen(acc, arg, onBack)
        "stats" -> StatsScreen(acc, arg, onBack)
        "scheduled" -> ScheduledScreen(acc, arg, onBack)
        "newchannel" -> CreateChannelScreen(acc, act, onBack)
        "twostep" -> TwoStepScreen(acc, onBack)
        "stickers" -> StickerPacksScreen(acc, onBack)
        "exceptions" -> ExceptionsScreen(acc, act, onBack)
        "datausage" -> DataUsageScreen(acc, onBack)
        "blocked" -> BlockedScreen(acc, onBack)
        "story" -> StoryViewer(acc, arg, onBack)
        "firebase" -> FirebaseScreen(acc, onBack)
        "adminlog" -> AdminLogScreen(acc, arg, onBack)
        "account2" -> AccountExtrasScreen(acc, act, onBack)
        "websites" -> WebsitesScreen(acc, onBack)
        "addcontact" -> AddContactScreen(acc, act, onBack)
        "notifdefaults" -> NotifDefaultsScreen(acc, onBack)
        "language" -> LanguageScreen(acc, onBack)
        "wallpapers" -> WallpapersScreen(acc, arg, onBack)
        "emojistatus" -> EmojiStatusScreen(acc, onBack)
        "iv" -> InstantViewScreen(acc, act, onBack)
        "storage" -> StorageScreen(acc, onBack)
        "qrscan" -> QrScanScreen(acc, onBack)
        "mappick" -> MapPickScreen(acc, arg, onBack)
    }
}

// ---------------------------------------------------------------- forum topics

@Composable
fun ForumTopicsScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler { onBack() }
    var topics by remember { mutableStateOf<List<ForumTopic>?>(null) }
    LaunchedEffect(chatId) {
        topics = acc.client.getForumTopics(chatId = chatId, query = "", offsetDate = 0, offsetMessageId = 0, offsetForumTopicId = 0, limit = 100).ok()?.topics?.toList().orEmpty()
    }
    val c = acc.chats[chatId]
    Column(Modifier.fillMaxSize().background(cs.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null) }
            if (c != null) ChatAvatar(acc, c, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c?.title ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("Topics"), fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { act.pop(); act.push(Screen.Chat(chatId, flat = true)) }) { Icon(Ic.Chat, null) }
            IconButton(onClick = { act.push(Screen.Info(chatId)) }) { Icon(Ic.Info, null) }
        }
        val l = topics
        if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn {
            items(l, key = { it.info.forumTopicId }) { tp ->
                Row(
                    Modifier.fillMaxWidth().clickable { act.push(Screen.Chat(chatId, topic = tp.info.forumTopicId)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val col = Color(0xFF000000.toInt() or tp.info.icon.color)
                    Box(Modifier.size(42.dp).clip(CircleShape).background(col.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Text(if (tp.info.isGeneral) "#" else tp.info.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tp.info.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (tp.info.isClosed) Text("  🔒", fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            tp.lastMessage?.let { Text(Fmt.listTime(it.date), fontSize = 12.sp, color = cs.onSurfaceVariant) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tp.lastMessage?.let { acc.senderName(it.senderId).split(" ").first() + ": " + Fmt.preview(acc, it) } ?: "", fontSize = 14.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (tp.unreadCount > 0) CountBadge(tp.unreadCount.toString(), LocalStyle.current.badge)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- admin tools

@Composable
fun AdminScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = acc.chats[chatId] ?: run { LaunchedEffect(Unit) { onBack() }; return }
    BackHandler { onBack() }
    var title by remember { mutableStateOf(c.title) }
    var desc by remember { mutableStateOf("") }
    var perms by remember { mutableStateOf<ChatPermissions?>(null) }
    LaunchedEffect(chatId) {
        val chat = acc.client.getChat(chatId = chatId).ok()
        perms = chat?.permissions
        if (c.supergroupId != 0L) desc = acc.client.getSupergroupFullInfo(supergroupId = c.supergroupId).ok()?.description ?: ""
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) acc.scope.launch {
            val f = copyToCache(ctx, uri) ?: return@launch
            acc.client.setChatPhoto(chatId = chatId, photo = InputChatPhotoStatic(photo = InputFileLocal(path = f.absolutePath))).report(ctx, t("Saved"))
        }
    }
    Page(t("Manage"), onBack) {
        SectionTitle(t("Info"))
        OutlinedTextField(title, { title = it }, label = { Text(t("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(desc, { desc = it }, label = { Text(t("Description")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        Row(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = {
                acc.scope.launch {
                    if (title.isNotBlank() && title != c.title) acc.client.setChatTitle(chatId = chatId, title = title.trim()).report(ctx)
                    acc.client.setChatDescription(chatId = chatId, description = desc.trim()).report(ctx, t("Saved"))
                }
            }) { Text(t("Save")) }
            TextButton(onClick = { pickPhoto.launch("image/*") }) { Text(t("Change photo")) }
        }
        ClickRow(t("Members"), "›") { act.push(Screen.Page("members:$chatId")) }
        ClickRow(t("Invite links"), "›") { act.push(Screen.Page("links:$chatId")) }
        ClickRow(t("Join requests"), "›") { act.push(Screen.Page("requests:$chatId")) }
        ClickRow(t("Statistics"), "›") { act.push(Screen.Page("stats:$chatId")) }
        val p = perms
        if (p != null && c.isGroup) {
            SectionTitle(t("What members can do"))
            fun upd(np: ChatPermissions) { perms = np; acc.scope.launch { acc.client.setChatPermissions(chatId = chatId, permissions = np).report(ctx) } }
            SwitchRow(t("Send messages"), null, p.canSendBasicMessages) { upd(perms(p, basic = it)) }
            SwitchRow(t("Send photos and videos"), null, p.canSendPhotos) { upd(perms(p, media = it)) }
            SwitchRow(t("Send stickers and GIFs"), null, p.canSendOtherMessages) { upd(perms(p, other = it)) }
            SwitchRow(t("Send polls"), null, p.canSendPolls) { upd(perms(p, polls = it)) }
            SwitchRow(t("Add members"), null, p.canInviteUsers) { upd(perms(p, invite = it)) }
            SwitchRow(t("Pin messages"), null, p.canPinMessages) { upd(perms(p, pin = it)) }
            SwitchRow(t("Change group info"), null, p.canChangeInfo) { upd(perms(p, info = it)) }
        }
        GroupSettingsSection(acc, act, chatId)
    }
}

private fun perms(
    p: ChatPermissions, basic: Boolean = p.canSendBasicMessages, media: Boolean = p.canSendPhotos, other: Boolean = p.canSendOtherMessages,
    polls: Boolean = p.canSendPolls, invite: Boolean = p.canInviteUsers, pin: Boolean = p.canPinMessages, info: Boolean = p.canChangeInfo,
) = ChatPermissions(
    canSendBasicMessages = basic, canSendAudios = media, canSendDocuments = media, canSendPhotos = media, canSendVideos = media,
    canSendVideoNotes = media, canSendVoiceNotes = media, canSendPolls = polls, canSendOtherMessages = other,
    canAddLinkPreviews = p.canAddLinkPreviews, canReactToMessages = p.canReactToMessages, canEditTag = p.canEditTag,
    canChangeInfo = info, canInviteUsers = invite, canPinMessages = pin, canCreateTopics = p.canCreateTopics,
)

fun adminRights(full: Boolean) = ChatAdministratorRights(
    canManageChat = true, canChangeInfo = full, canPostMessages = full, canEditMessages = full, canDeleteMessages = true,
    canInviteUsers = true, canRestrictMembers = true, canPinMessages = true, canManageTopics = full, canPromoteMembers = false,
    canManageVideoChats = full, canPostStories = false, canEditStories = false, canDeleteStories = false,
    canManageDirectMessages = false, canManageTags = false, canSendWelcomeMessages = false, isAnonymous = false,
)

/** Promote / demote / remove a member (from the members list). */
suspend fun memberAction(acc: TgAccount, ctx: Context, chatId: Long, member: MessageSender, action: String) {
    val r = when (action) {
        "promote" -> acc.client.setChatMemberStatus(chatId = chatId, memberId = member, status = ChatMemberStatusAdministrator(canBeEdited = true, rights = adminRights(false)))
        "demote" -> acc.client.setChatMemberStatus(chatId = chatId, memberId = member, status = ChatMemberStatusMember(memberUntilDate = 0))
        else -> acc.client.banChatMember(chatId = chatId, memberId = member, bannedUntilDate = 0, revokeMessages = false)
    }
    r.report(ctx, t("Done"))
}

// ---------------------------------------------------------------- invite links / join requests

@Composable
fun InviteLinksScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var links by remember { mutableStateOf<List<ChatInviteLink>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        links = acc.client.getChatInviteLinks(chatId = chatId, creatorUserId = acc.myId, isRevoked = false, offsetDate = 0, offsetInviteLink = "", limit = 50).ok()?.inviteLinks?.toList().orEmpty()
    }
    var newLink by remember { mutableStateOf(false) }
    var openLink by remember { mutableStateOf<ChatInviteLink?>(null) }
    openLink?.let { k -> InviteLinkDialog(acc, chatId, k, onChanged = { tick++ }) { openLink = null } }
    Page(t("Invite links"), onBack) {
        Row(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { newLink = true }) { Text("+ " + t("New link")) }
            TextButton(onClick = { acc.scope.launch { acc.client.replacePrimaryChatInviteLink(chatId = chatId).report(ctx, t("Done")); tick++ } }) { Text(t("Reset main link")) }
        }
        val l = links
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else for (k in l) {
            Column(Modifier.fillMaxWidth().clickable { openLink = k }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text((if (k.isPrimary) "⭐ " else "") + k.name.ifBlank { k.inviteLink.removePrefix("https://") }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${k.memberCount} " + t("joined") + (if (k.pendingJoinRequestCount > 0) " · ${k.pendingJoinRequestCount} " + t("requests") else "") +
                        (if (k.createsJoinRequest) " · " + t("needs approval") else "") + " · " + t("Tap for details"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (newLink) {
        var name by remember { mutableStateOf("") }
        var approve by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { newLink = false },
            title = { Text(t("New link")) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text(t("Name (optional)")) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(approve, { approve = it }); Text(t("Admins must approve joins"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    newLink = false
                    acc.scope.launch {
                        val k = acc.client.createChatInviteLink(chatId = chatId, name = name, expirationDate = 0, memberLimit = 0, createsJoinRequest = approve).report(ctx)
                        if (k != null) withContext(Dispatchers.Main) { copy(ctx, k.inviteLink) }
                        tick++
                    }
                }) { Text(t("Create")) }
            },
            dismissButton = { TextButton(onClick = { newLink = false }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
fun JoinRequestsScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<ChatJoinRequest>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        list = acc.client.getChatJoinRequests(chatId = chatId, inviteLink = "", query = "", limit = 100).ok()?.requests?.toList().orEmpty()
    }
    Page(t("Join requests"), onBack) {
        val l = list
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(t("No requests"), modifier = Modifier.padding(20.dp))
        else for (r in l) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(acc, r.userId, 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc.userName(r.userId), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (r.bio.isNotBlank()) Text(r.bio, fontSize = 12.sp, maxLines = 2)
                }
                TextButton(onClick = { acc.scope.launch { acc.client.processChatJoinRequest(chatId = chatId, userId = r.userId, approve = true).report(ctx); tick++ } }) { Text(t("Accept")) }
                TextButton(onClick = { acc.scope.launch { acc.client.processChatJoinRequest(chatId = chatId, userId = r.userId, approve = false).report(ctx); tick++ } }) { Text(t("Decline"), color = Color(0xFFE57373)) }
            }
        }
    }
}

// ---------------------------------------------------------------- create channel

@Composable
fun CreateChannelScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Page(t("New Channel"), onBack) {
        OutlinedTextField(title, { title = it }, label = { Text(t("Channel name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        OutlinedTextField(desc, { desc = it }, label = { Text(t("Description (optional)")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        Button(enabled = title.isNotBlank() && !busy, onClick = {
            busy = true
            acc.scope.launch {
                val c = acc.client.createNewSupergroupChat(title = title.trim(), isForum = false, isChannel = true, description = desc.trim(), messageAutoDeleteTime = 0, forImport = false).report(ctx)
                withContext(Dispatchers.Main) {
                    busy = false
                    if (c != null) { act.pop(); act.push(Screen.Chat(c.id)) }
                }
            }
        }, modifier = Modifier.padding(20.dp)) { Text(t("Create")) }
    }
}

// ---------------------------------------------------------------- statistics

@Composable
fun StatsScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(chatId) {
        when (val r = acc.client.getChatStatistics(chatId = chatId, isDark = true)) {
            is TdlResult.Success -> rows = when (val s = r.result) {
                is ChatStatisticsChannel -> listOf(
                    t("Followers") to sv(s.memberCount), t("Views per post") to sv(s.meanMessageViewCount),
                    t("Shares per post") to sv(s.meanMessageShareCount), t("Reactions per post") to sv(s.meanMessageReactionCount),
                    t("Notifications on") to "%.1f%%".format(s.enabledNotificationsPercentage),
                )
                is ChatStatisticsSupergroup -> listOf(
                    t("Members") to sv(s.memberCount), t("Messages") to sv(s.messageCount),
                    t("Viewing members") to sv(s.viewerCount), t("Posting members") to sv(s.senderCount),
                ) + s.topSenders.take(10).map { acc.userName(it.userId) to "${it.sentMessageCount} " + t("messages") }
                else -> emptyList()
            }
            is TdlResult.Failure -> { err = r.message; rows = emptyList() }
        }
    }
    Page(t("Statistics"), onBack) {
        val l = rows
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(err.ifBlank { t("No statistics for this chat") } + "\n" + t("Telegram only gives statistics for bigger channels and groups."), modifier = Modifier.padding(20.dp))
        else for ((k, v) in l) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(k, modifier = Modifier.weight(1f))
                Text(v, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun sv(v: StatisticalValue): String {
    val n = if (v.value >= 100) "%,.0f".format(v.value) else "%.1f".format(v.value)
    val g = v.growthRatePercentage
    return n + if (g != 0.0) "  (" + (if (g > 0) "+" else "") + "%.1f%%".format(g) + ")" else ""
}

// ---------------------------------------------------------------- scheduled messages

@Composable
fun ScheduledScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Message>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = acc.client.getChatScheduledMessages(chatId = chatId).ok()?.messages?.filterNotNull().orEmpty() }
    Page(t("Scheduled messages"), onBack) {
        val l = list
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(t("Nothing scheduled"), modifier = Modifier.padding(20.dp))
        else for (m in l) {
            val at = (m.schedulingState as? dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate)?.sendDate ?: 0
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("🕓 " + Fmt.listTime(at) + " " + Fmt.clock(at), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(Fmt.preview(acc, m), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = { acc.scope.launch { acc.client.editMessageSchedulingState(chatId = chatId, messageId = m.id, schedulingState = null).report(ctx, t("Sent")); tick++ } }) { Text(t("Send now")) }
                    TextButton(onClick = { acc.scope.launch { acc.client.deleteMessages(chatId = chatId, messageIds = longArrayOf(m.id), revoke = true).report(ctx); tick++ } }) { Text(t("Cancel"), color = Color(0xFFE57373)) }
                }
            }
            HorizontalDivider()
        }
    }
}

// ---------------------------------------------------------------- two-step verification

@Composable
fun TwoStepScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<PasswordState?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { state = acc.client.getPasswordState().ok() }
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Page(t("Two-Step Verification"), onBack) {
        val s = state
        if (s == null) { CircularProgressIndicator(Modifier.padding(20.dp)); return@Page }
        Text(
            if (s.hasPassword) t("A cloud password is on. You'll need it when signing in on a new device.")
            else t("Add a cloud password. You'll need it, plus the code, when signing in on a new device."),
            modifier = Modifier.padding(20.dp),
        )
        val pw = PasswordVisualTransformation()
        if (s.hasPassword) OutlinedTextField(old, { old = it }, label = { Text(t("Current password") + if (s.passwordHint.isNotBlank()) " (${s.passwordHint})" else "") }, singleLine = true, visualTransformation = pw, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(new1, { new1 = it }, label = { Text(t("New password")) }, singleLine = true, visualTransformation = pw, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(new2, { new2 = it }, label = { Text(t("Repeat new password")) }, singleLine = true, visualTransformation = pw, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(hint, { hint = it }, label = { Text(t("Hint (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        if (!s.hasRecoveryEmailAddress) OutlinedTextField(email, { email = it }, label = { Text(t("Recovery email (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Row(Modifier.padding(16.dp)) {
            Button(onClick = {
                if (new1 != new2) { toast(ctx, t("Passwords don't match")); return@Button }
                acc.scope.launch {
                    acc.client.setPassword(oldPassword = old, newPassword = new1, newHint = hint, setRecoveryEmailAddress = email.isNotBlank(), newRecoveryEmailAddress = email.trim())
                        .report(ctx, if (new1.isEmpty()) t("Password removed") else t("Password saved"))
                    old = ""; new1 = ""; new2 = ""; tick++
                }
            }) { Text(t("Save")) }
            if (s.hasPassword) {
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = {
                    acc.scope.launch {
                        acc.client.setPassword(oldPassword = old, newPassword = "", newHint = "", setRecoveryEmailAddress = false, newRecoveryEmailAddress = "").report(ctx, t("Password removed"))
                        old = ""; tick++
                    }
                }) { Text(t("Turn off"), color = Color(0xFFE57373)) }
            }
        }
        var recover by remember { mutableStateOf(false) }
        if (recover) PasswordRecoveryDialog(acc, atLogin = false) { recover = false; tick++ }
        if (s.hasPassword && s.hasRecoveryEmailAddress) TextButton(onClick = { recover = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(t("Forgot password?")) }
        if (s.recoveryEmailAddressCodeInfo != null) Text(t("Check your email for a confirmation code, then open Telegram's email link."), modifier = Modifier.padding(20.dp))
    }
}

// ---------------------------------------------------------------- sticker packs

@Composable
fun StickerPacksScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sets by remember { mutableStateOf<List<StickerSetInfo>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { sets = acc.client.getInstalledStickerSets(stickerType = StickerTypeRegular()).ok()?.sets?.toList().orEmpty() }
    fun reorder(l: List<StickerSetInfo>) {
        sets = l
        acc.scope.launch { acc.client.reorderInstalledStickerSets(stickerType = StickerTypeRegular(), stickerSetIds = l.map { it.id }.toLongArray()) }
    }
    Page(t("Sticker packs"), onBack) {
        Text(t("To add a pack, tap a sticker in any chat and choose Add."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        var q by remember { mutableStateOf("") }
        var found by remember { mutableStateOf<List<StickerSetInfo>>(emptyList()) }
        var showTrending by remember { mutableStateOf(false) }
        LaunchedEffect(q, showTrending) {
            delay(300)
            found = when {
                q.isNotBlank() -> acc.client.searchStickerSets(stickerType = StickerTypeRegular(), query = q).ok()?.sets?.toList().orEmpty()
                showTrending -> acc.client.getTrendingStickerSets(stickerType = StickerTypeRegular(), offset = 0, limit = 40).ok()?.sets?.toList().orEmpty()
                else -> emptyList()
            }
        }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(t("Search sticker packs")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { showTrending = !showTrending }) { Text(if (showTrending) t("Hide trending") else "🔥 " + t("Trending")) }
            TextButton(onClick = { acc.scope.launch { acc.client.clearRecentStickers(isAttached = false).report(ctx, t("Done")) } }) { Text(t("Clear recent")) }
            TextButton(onClick = { acc.scope.launch { acc.client.clearRecentReactions().report(ctx, t("Done")); acc.client.clearRecentEmojiStatuses() } }) { Text(t("Clear recent reactions")) }
        }
        var preview by remember { mutableStateOf(0L) }
        if (preview != 0L) StickerPackDialog(acc, preview) { preview = 0L; tick++ }
        for (s in found) {
            Row(Modifier.fillMaxWidth().clickable { preview = s.id }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val cover = s.covers.firstOrNull()
                Box(Modifier.size(44.dp)) { if (cover != null) StickerImage(acc, cover, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.size} " + t("stickers"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.isInstalled) Text("✓", color = MaterialTheme.colorScheme.primary)
                else TextButton(onClick = { acc.scope.launch { acc.client.changeStickerSet(setId = s.id, isInstalled = true, isArchived = false).report(ctx, t("Added")); tick++ } }) { Text(t("Add")) }
            }
        }
        if (found.isNotEmpty()) HorizontalDivider()
        val l = sets
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else l.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val cover = s.covers.firstOrNull()
                Box(Modifier.size(44.dp)) { if (cover != null) StickerImage(acc, cover, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.size} " + t("stickers"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (i > 0) reorder(l.toMutableList().apply { add(i - 1, removeAt(i)) }) }) { Icon(Ic.Up, null) }
                IconButton(onClick = { if (i < l.lastIndex) reorder(l.toMutableList().apply { add(i + 1, removeAt(i)) }) }) { Icon(Ic.Down, null) }
                IconButton(onClick = { acc.scope.launch { acc.client.changeStickerSet(setId = s.id, isInstalled = false, isArchived = false).report(ctx, t("Removed")); tick++ } }) { Icon(Ic.Delete, null) }
            }
        }
    }
}

@Composable
fun StickerImage(acc: TgAccount, s: dev.g000sha256.tdl.dto.Sticker, modifier: Modifier) {
    val f = if (s.format is dev.g000sha256.tdl.dto.StickerFormatWebp) s.sticker else s.thumbnail?.file
    val cur = rememberFile(acc, f, autoDownload = true)
    if (cur != null && cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()) AsyncImage(model = java.io.File(cur.local.path), contentDescription = s.emoji, modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { Text(s.emoji, fontSize = 22.sp) }
}

/** Dialog to see a sticker's pack and add it. */
@Composable
fun StickerPackDialog(acc: TgAccount, setId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var set by remember { mutableStateOf<dev.g000sha256.tdl.dto.StickerSet?>(null) }
    LaunchedEffect(setId) { set = acc.client.getStickerSet(setId = setId).ok() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(set?.title ?: "…") },
        text = {
            val s = set
            if (s == null) CircularProgressIndicator()
            else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(64.dp), Modifier.height(320.dp)) {
                items(s.stickers.size) { i -> StickerImage(acc, s.stickers[i], Modifier.size(64.dp).padding(4.dp)) }
            }
        },
        confirmButton = {
            val s = set
            if (s != null) TextButton(onClick = {
                onDismiss()
                acc.scope.launch { acc.client.changeStickerSet(setId = s.id, isInstalled = !s.isInstalled, isArchived = false).report(ctx, if (s.isInstalled) t("Removed") else t("Added")) }
            }) { Text(if (s.isInstalled) t("Remove pack") else t("Add pack")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- notification exceptions

@Composable
fun ExceptionsScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Long>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        val out = ArrayList<Long>()
        for (scope in listOf(NotificationSettingsScopePrivateChats(), NotificationSettingsScopeGroupChats(), NotificationSettingsScopeChannelChats())) {
            acc.client.getChatNotificationSettingsExceptions(scope = scope, compareSound = false).ok()?.chatIds?.let { out.addAll(it.toList()) }
        }
        list = out.distinct()
    }
    Page(t("Notification exceptions"), onBack) {
        Text(t("Chats with their own notification setting."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val l = list
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(t("No exceptions"), modifier = Modifier.padding(20.dp))
        else for (id in l) {
            val c = acc.chats[id] ?: continue
            val muted = acc.isMuted(c)
            Row(Modifier.fillMaxWidth().clickable { act.push(Screen.Info(id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(acc, c, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (muted) t("Muted") else t("Always on"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { acc.setMuted(c, !muted); tick++ }) { Text(if (muted) t("Unmute") else t("Mute")) }
            }
        }
    }
}

// ---------------------------------------------------------------- data usage

@Composable
fun DataUsageScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<Triple<String, Long, Long>>?>(null) }
    var since by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        val s = acc.client.getNetworkStatistics(onlyCurrent = false).ok()
        since = s?.sinceDate ?: 0
        val sums = linkedMapOf(t("Mobile data") to longArrayOf(0, 0), t("Wi-Fi") to longArrayOf(0, 0), t("Roaming") to longArrayOf(0, 0), t("Other") to longArrayOf(0, 0))
        for (e in s?.entries.orEmpty()) {
            val (nt, sent, recv) = when (e) {
                is NetworkStatisticsEntryFile -> Triple(e.networkType, e.sentBytes, e.receivedBytes)
                is NetworkStatisticsEntryCall -> Triple(e.networkType, e.sentBytes, e.receivedBytes)
                else -> continue
            }
            val k = when (nt) { is NetworkTypeMobile -> t("Mobile data"); is NetworkTypeWiFi -> t("Wi-Fi"); is NetworkTypeMobileRoaming -> t("Roaming"); else -> t("Other") }
            sums[k]!![0] += sent; sums[k]!![1] += recv
        }
        rows = sums.map { (k, v) -> Triple(k, v[0], v[1]) }
    }
    Page(t("Data usage"), onBack) {
        if (since > 0) Text(t("Since") + " " + Fmt.listTime(since), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val l = rows
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else for ((k, sent, recv) in l) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(k, fontWeight = FontWeight.SemiBold)
                Text("↓ " + Fmt.size(recv) + "    ↑ " + Fmt.size(sent) + "    = " + Fmt.size(sent + recv), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = { acc.scope.launch { acc.client.resetNetworkStatistics().report(ctx, t("Done")); tick++ } }, modifier = Modifier.padding(12.dp)) { Text(t("Reset")) }
    }
}

// ---------------------------------------------------------------- blocked users

@Composable
fun BlockedScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<MessageSender>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = acc.client.getBlockedMessageSenders(blockList = BlockListMain(), offset = 0, limit = 100).ok()?.senders?.toList().orEmpty() }
    Page(t("Blocked users"), onBack) {
        val l = list
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(t("Nobody is blocked"), modifier = Modifier.padding(20.dp))
        else for (s in l) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val uid = (s as? MessageSenderUser)?.userId
                if (uid != null) UserAvatar(acc, uid, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(acc.senderName(s), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { acc.scope.launch { acc.client.setMessageSenderBlockList(senderId = s, blockList = null).report(ctx, t("Unblocked")); tick++ } }) { Text(t("Unblock")) }
            }
        }
    }
}

// ---------------------------------------------------------------- stories

/** Row of people with stories, at the top of the chat list. */
@Composable
fun StoriesRow(acc: TgAccount, act: MainActivity) {
    if (!Prefs.storiesRow) return
    val extra by acc.extraVersion.collectAsState()
    val ids = remember(extra) {
        acc.stories.values.filter { it.list !is dev.g000sha256.tdl.dto.StoryListArchive }
            .sortedWith(compareByDescending<dev.g000sha256.tdl.dto.ChatActiveStories> { acc.hasUnseenStories(it.chatId) }.thenByDescending { it.order })
            .map { it.chatId }
    }
    if (ids.isEmpty()) return
    val style = LocalStyle.current
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        items(ids) { id ->
            val c = acc.chats[id]
            Column(Modifier.width(70.dp).clickable { act.push(Screen.Page("story:$id")) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(62.dp).clip(CircleShape)
                        .border(2.5.dp, if (acc.hasUnseenStories(id)) Brush.linearGradient(listOf(style.accent, Color(0xFF4FC3F7))) else Brush.linearGradient(listOf(Color.Gray, Color.Gray)), CircleShape)
                        .padding(4.dp),
                ) { if (c != null) ChatAvatar(acc, c, 54.dp) else UserAvatar(acc, id, 54.dp) }
                Text(if (c != null && acc.isSaved(c)) t("My story") else c?.title ?: "", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun StoryViewer(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    val active = acc.stories[chatId]
    val ids = active?.stories?.map { it.storyId }.orEmpty()
    if (ids.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }
    val firstUnseen = ids.indexOfFirst { it > (active?.maxReadStoryId ?: 0) }.coerceAtLeast(0)
    var index by remember { mutableIntStateOf(firstUnseen) }
    var story by remember { mutableStateOf<Story?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        story = null; progress = 0f
        story = acc.client.getStory(storyPosterChatId = chatId, storyId = ids[index], onlyLocal = false).ok()
        acc.client.openStory(storyPosterChatId = chatId, storyId = ids[index])
    }
    val s = story
    val isVideo = s?.content is StoryContentVideo
    val durMs = when (val c = s?.content) { is StoryContentVideo -> (c.video.duration * 1000).toLong().coerceAtLeast(1000); else -> 5000L }
    LaunchedEffect(index, s, paused) {
        if (s == null || paused) return@LaunchedEffect
        val step = 50L
        while (progress < 1f) { delay(step); progress += step.toFloat() / durMs }
        if (index < ids.lastIndex) index++ else onBack()
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(ids.size) {
            detectTapGestures(
                onPress = { paused = true; tryAwaitRelease(); paused = false },
                onTap = { o -> if (o.x < size.width / 3f) { if (index > 0) index-- } else { if (index < ids.lastIndex) index++ else onBack() } },
            )
        },
    ) {
        when (val c = s?.content) {
            is StoryContentPhoto -> {
                val best = c.photo.sizes.maxByOrNull { it.width }
                val f = rememberFile(acc, best?.photo, autoDownload = true, priority = 32)
                if (f != null && f.local.isDownloadingCompleted) AsyncImage(model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            is StoryContentVideo -> {
                val f = rememberFile(acc, c.video.video, autoDownload = true, priority = 32)
                if (f != null && f.local.isDownloadingCompleted) StoryVideoPlayer(f.local.path, paused)
                else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            else -> if (s == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else Text(t("This story type isn't supported yet"), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ids.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(when { i < index -> 1f; i == index -> progress.coerceIn(0f, 1f); else -> 0f }).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                acc.chats[chatId]?.let { ChatAvatar(acc, it, 34.dp) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc.chats[chatId]?.title ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (s != null) Text(Fmt.listTime(s.date) + " " + Fmt.clock(s.date), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                IconButton(onClick = onBack) { Icon(Ic.Close, null, tint = Color.White) }
            }
        }
        if (s != null && s.caption.text.isNotBlank()) Text(
            s.caption.text, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x88000000)).padding(10.dp),
        )
        if (isVideo && s == null) Unit
    }
}

@Composable
private fun StoryVideoPlayer(path: String, paused: Boolean) {
    val ctx = LocalContext.current
    val player = remember(path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            prepare(); playWhenReady = true
        }
    }
    LaunchedEffect(paused) { player.playWhenReady = !paused }
    DisposableEffect(path) { onDispose { player.release() } }
    AndroidView(factory = { c -> androidx.media3.ui.PlayerView(c).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())
}

// ---------------------------------------------------------------- send as channel

@Composable
fun SendAsDialog(acc: TgAccount, chatId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<MessageSender>?>(null) }
    LaunchedEffect(chatId) { list = acc.client.getChatAvailableMessageSenders(chatId = chatId).ok()?.senders?.filter { !it.needsPremium }?.map { it.sender }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Send as")) },
        text = {
            val l = list
            if (l == null) CircularProgressIndicator()
            else if (l.size <= 1) Text(t("This group only lets you send as yourself."))
            else Column {
                for (s in l) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onDismiss()
                            acc.scope.launch { acc.client.setChatMessageSender(chatId = chatId, messageSenderId = s).report(ctx, t("Done")) }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (s) {
                            is MessageSenderUser -> UserAvatar(acc, s.userId, 36.dp)
                            is MessageSenderChat -> acc.chats[s.chatId]?.let { ChatAvatar(acc, it, 36.dp) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(acc.senderName(s))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- Firebase push (optional)

@Composable
fun FirebaseScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apiKey by remember { mutableStateOf(Prefs.fbApiKey) }
    var appId by remember { mutableStateOf(Prefs.fbAppId) }
    var project by remember { mutableStateOf(Prefs.fbProject) }
    var sender by remember { mutableStateOf(Prefs.fbSender) }
    Page(t("Push notifications (Firebase)"), onBack) {
        Text(
            t("Optional. Fill these from your own Firebase project (Project settings → Your apps). Telegram may only send pushes to apps it has set up, so this might not deliver anything — keep \"Stay connected\" on if it doesn't."),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
        )
        SwitchRow(t("Use Firebase push"), Push.status, Prefs.fbOn) { on ->
            Prefs.fbOn = on
            if (on) Push.start(ctx)
        }
        OutlinedTextField(apiKey, { apiKey = it.trim() }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(appId, { appId = it.trim() }, label = { Text("App ID (1:…:android:…)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(project, { project = it.trim() }, label = { Text("Project ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        OutlinedTextField(sender, { sender = it.trim() }, label = { Text("Sender ID (project number)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Button(onClick = {
            Prefs.fbApiKey = apiKey; Prefs.fbAppId = appId; Prefs.fbProject = project; Prefs.fbSender = sender
            toast(ctx, t("Saved"))
            if (Prefs.fbOn) Push.start(ctx)
        }, modifier = Modifier.padding(20.dp)) { Text(t("Save")) }
    }
}
