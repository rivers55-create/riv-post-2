package com.rivers.rivpost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ChatMember
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessagePoll
import dev.g000sha256.tdl.dto.InputPollOption
import dev.g000sha256.tdl.dto.InputPollTypeQuiz
import dev.g000sha256.tdl.dto.InputPollTypeRegular
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.PollTypeQuiz
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.TextEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun ft(s: String) = FormattedText(text = s, entities = emptyArray<TextEntity>())

// ---------------------------------------------------------------- polls

@Composable
fun PollView(acc: TgAccount, m: Message, c: MessagePoll, fg: Color, meta: Color) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val p = c.poll
    val quiz = p.type as? PollTypeQuiz
    val voted = p.options.any { it.isChosen }
    val showResults = voted || p.isClosed || p.canSeeResults && voted
    val multi = remember(m.id) { mutableStateListOf<Int>() }
    Column(Modifier.widthIn(min = 240.dp, max = 300.dp).padding(8.dp)) {
        Text(p.question.text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            (if (p.isAnonymous) t("Anonymous") + " " else "") + (if (quiz != null) t("Quiz") else t("Poll")) + if (p.isClosed) " · " + t("Final results") else "",
            color = meta, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp),
        )
        p.options.forEachIndexed { i, o ->
            val correct = quiz != null && i in quiz.correctOptionIds
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !p.isClosed && (!voted || p.allowsRevoting)) {
                    if (p.allowsMultipleAnswers && !voted) { if (!multi.remove(i)) multi.add(i) }
                    else acc.scope.launch {
                        val r = acc.client.setPollAnswer(chatId = m.chatId, messageId = m.id, optionIds = intArrayOf(i))
                        if (r is TdlResult.Failure) withContext(Dispatchers.Main) { toast(ctx, r.message) }
                    }
                }.padding(vertical = 6.dp, horizontal = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showResults) Text("${o.votePercentage}%", color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(44.dp))
                    else Box(
                        Modifier.size(20.dp).clip(if (p.allowsMultipleAnswers) RoundedCornerShape(5.dp) else CircleShape)
                            .background(if (i in multi) style.accent else Color.Transparent)
                            .let { mod -> mod.then(Modifier.background(Color.Transparent)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(18.dp).clip(if (p.allowsMultipleAnswers) RoundedCornerShape(5.dp) else CircleShape).background(if (i in multi) style.accent else meta.copy(alpha = 0.35f)))
                    }
                    if (!showResults) Spacer(Modifier.width(10.dp))
                    Text(o.text.text, color = fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (o.isChosen) Text(if (quiz != null && !correct) " ✗" else " ✓", color = if (quiz != null && !correct) Color(0xFFE57373) else style.accent, fontWeight = FontWeight.Bold)
                }
                if (showResults) Box(Modifier.padding(start = 44.dp, top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(meta.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(o.votePercentage / 100f).fillMaxHeight().background(if (quiz != null && correct) Color(0xFF4CAF50) else style.accent))
                }
            }
        }
        if (p.allowsMultipleAnswers && !voted && multi.isNotEmpty()) TextButton(onClick = {
            acc.scope.launch { acc.client.setPollAnswer(chatId = m.chatId, messageId = m.id, optionIds = multi.toIntArray()) }
        }) { Text(t("Vote"), color = style.accent) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${p.totalVoterCount} " + t("votes"), color = meta, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (voted && p.allowsRevoting && !p.isClosed) TextButton(onClick = {
                acc.scope.launch { acc.client.setPollAnswer(chatId = m.chatId, messageId = m.id, optionIds = IntArray(0)) }
            }) { Text(t("Retract vote"), color = style.accent, fontSize = 12.sp) }
        }
        if (quiz != null && voted && quiz.explanation.text.isNotBlank()) Text("💡 " + quiz.explanation.text, color = meta, fontSize = 13.sp)
    }
}

@Composable
fun CreatePollDialog(onDismiss: () -> Unit, onSend: (InputMessageContent) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("New poll")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text(t("Question")) })
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { i, o ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (quiz) androidx.compose.material3.RadioButton(selected = correct == i, onClick = { correct = i })
                        OutlinedTextField(
                            value = o, onValueChange = { v -> options[i] = v; if (i == options.lastIndex && v.isNotBlank() && options.size < 12) options.add("") },
                            label = { Text(t("Option") + " ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(anonymous, { anonymous = it }); Text(t("Anonymous voting")) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(multiple, { multiple = it; if (it) quiz = false }); Text(t("Multiple answers")) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(quiz, { quiz = it; if (it) multiple = false }); Text(t("Quiz mode")) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val opts = options.map { it.trim() }.filter { it.isNotEmpty() }
                if (question.isBlank() || opts.size < 2) return@TextButton
                onSend(
                    InputMessagePoll(
                        question = ft(question.trim()), options = opts.map { InputPollOption(text = ft(it), media = null) }.toTypedArray(),
                        description = null, media = null, isAnonymous = anonymous, allowsMultipleAnswers = multiple, allowsRevoting = !quiz,
                        membersOnly = false, countryCodes = emptyArray(), shuffleOptions = false, hideResultsUntilCloses = false,
                        type = if (quiz) InputPollTypeQuiz(correctOptionIds = intArrayOf(correct.coerceAtMost(opts.lastIndex)), explanation = ft(""), explanationMedia = null)
                        else InputPollTypeRegular(allowAddingOptions = false),
                        openPeriod = 0, closeDate = 0, isClosed = false,
                    ),
                )
                onDismiss()
            }) { Text(t("Send")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- @mentions

/** Members whose name or username matches what's typed after "@". */
@Composable
fun MentionSuggestions(acc: TgAccount, chatId: Long, text: String, onPick: (String, Long, String) -> Unit) {
    val q = Regex("(?:^|\\s)@(\\w*)$").find(text)?.groupValues?.get(1) ?: return
    val style = LocalStyle.current
    var list by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(q) {
        delay(200)
        val r = (acc.client.searchChatMembers(chatId = chatId, query = q, limit = 20) as? TdlResult.Success)?.result
        list = r?.members?.mapNotNull { (it.memberId as? MessageSenderUser)?.userId }.orEmpty()
    }
    if (list.isEmpty()) return
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).background(style.navBar)) {
        items(list) { uid ->
            val u = acc.users[uid]
            val un = u?.usernames?.activeUsernames?.firstOrNull()
            Row(
                Modifier.fillMaxWidth().clickable {
                    val base = text.dropLast(q.length + 1)
                    if (un != null) onPick("$base@$un ", 0L, "")
                    else { val name = acc.userName(uid); onPick("$base$name ", uid, name) }
                }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(acc, uid, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(acc.userName(uid), color = style.navBarFg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (un != null) Text("  @$un", color = style.navBarFg.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- seen by / reactions

@Composable
fun SeenByDialog(acc: TgAccount, m: Message, onDismiss: () -> Unit) {
    var viewers by remember { mutableStateOf<List<Pair<Long, Int>>?>(null) }
    var reactions by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(m.id) {
        val r = acc.client.getMessageViewers(chatId = m.chatId, messageId = m.id)
        viewers = (r as? TdlResult.Success)?.result?.viewers?.map { it.userId to it.viewDate }.orEmpty()
        if (r is TdlResult.Failure) error = r.message
        val rr = (acc.client.getMessageAddedReactions(chatId = m.chatId, messageId = m.id, offset = "", limit = 100) as? TdlResult.Success)?.result
        reactions = rr?.reactions?.mapNotNull { a -> (a.senderId as? MessageSenderUser)?.userId?.let { it to ((a.type as? ReactionTypeEmoji)?.emoji ?: "❤") } }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Seen by")) },
        text = {
            val v = viewers
            if (v == null) CircularProgressIndicator()
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                val react = reactions.toMap()
                val all = (v.map { it.first } + reactions.map { it.first }).distinct()
                if (all.isEmpty()) item { Text(error.ifBlank { t("Nobody yet") }) }
                items(all) { uid ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(acc, uid, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(acc.userName(uid), fontWeight = FontWeight.SemiBold, maxLines = 1)
                            v.firstOrNull { it.first == uid }?.second?.takeIf { it > 0 }?.let { Text(Fmt.listTime(it) + " " + Fmt.clock(it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        react[uid]?.let { Text(it, fontSize = 22.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- translate

@Composable
fun TranslateDialog(acc: TgAccount, m: Message, original: String, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf(if (Lang.arabic) "ar" else "en") }
    LaunchedEffect(lang) {
        result = null
        val r = acc.client.translateMessageText(chatId = m.chatId, messageId = m.id, toLanguageCode = lang, tone = "")
        result = when (r) {
            is TdlResult.Success -> r.result.text
            is TdlResult.Failure -> {
                val r2 = acc.client.translateText(text = ft(original), toLanguageCode = lang, tone = "")
                (r2 as? TdlResult.Success)?.result?.text ?: (t("Couldn't translate") + ": " + r.message)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Translation")) },
        text = {
            Column {
                Row {
                    for ((code, name) in listOf("en" to "English", "ar" to "العربية", "fr" to "Français", "tr" to "Türkçe", "fa" to "فارسی")) {
                        TextButton(onClick = { lang = code }) { Text(name, fontWeight = if (lang == code) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                val r = result
                if (r == null) CircularProgressIndicator() else androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(r, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- members

@Composable
fun MembersScreen(acc: TgAccount, act: MainActivity, chatId: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<ChatMember>?>(null) }
    LaunchedEffect(query) {
        delay(250)
        list = (acc.client.searchChatMembers(chatId = chatId, query = query, limit = 200) as? TdlResult.Success)?.result?.members?.toList().orEmpty()
    }
    Column(Modifier.fillMaxSize().background(cs.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null) }
            Column {
                Text(t("Members") + (acc.chats[chatId]?.let { " · " + acc.memberCount(it) } ?: ""), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(t("Hold a member for admin options"), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(t("Search members")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        val l = list
        if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn {
            items(l, key = { it.memberId.toString() + it.joinedChatDate }) { mem ->
                val uid = (mem.memberId as? MessageSenderUser)?.userId ?: 0L
                var menu by remember { mutableStateOf(false) }
                val ctx = LocalContext.current
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(Ic.Account, t("Make admin")) { menu = false; acc.scope.launch { memberAction(acc, ctx, chatId, mem.memberId, "promote") } }
                    MenuItem(Ic.Close, t("Remove admin")) { menu = false; acc.scope.launch { memberAction(acc, ctx, chatId, mem.memberId, "demote") } }
                    MenuItem(Ic.Delete, t("Remove from group"), Color(0xFFE53935)) { menu = false; acc.scope.launch { memberAction(acc, ctx, chatId, mem.memberId, "ban") } }
                }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(enabled = uid != 0L, onLongClick = { menu = true }) {
                        acc.scope.launch {
                            val c = acc.privateChat(uid)
                            if (c != null) withContext(Dispatchers.Main) { act.push(Screen.Info(c)) }
                        }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(acc, uid, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(acc.userName(uid), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.lastSeenStatus(acc.status(uid), acc.users[uid]), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    val role = when (val st = mem.status) {
                        is ChatMemberStatusCreator -> t("Owner")
                        is ChatMemberStatusAdministrator -> t("Admin")
                        else -> ""
                    }
                    if (role.isNotBlank()) Text(role, color = cs.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun UserAvatar(acc: TgAccount, userId: Long, size: androidx.compose.ui.unit.Dp) {
    val u = acc.users[userId]
    Avatar(acc, userId, acc.userName(userId), u?.profilePhoto?.small, u?.profilePhoto?.minithumbnail?.data, size)
}
