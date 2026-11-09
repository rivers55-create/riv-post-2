package com.rivers.rivpost.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AccountTtl
import dev.g000sha256.tdl.dto.ArchiveChatListSettings
import dev.g000sha256.tdl.dto.Birthdate
import dev.g000sha256.tdl.dto.ChatFolder
import dev.g000sha256.tdl.dto.ChatFolderName
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ConnectedWebsite
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.ImportedContact
import dev.g000sha256.tdl.dto.NewChatPrivacySettings
import dev.g000sha256.tdl.dto.PhoneNumberCodeTypeChange
import dev.g000sha256.tdl.dto.ReadDatePrivacySettings
import dev.g000sha256.tdl.dto.RecommendedChatFolder
import dev.g000sha256.tdl.dto.TextEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun <T> TdlResult<T>.tell(ctx: android.content.Context, ok: String? = null): T? = when (this) {
    is TdlResult.Success -> { if (ok != null) withContext(Dispatchers.Main) { toast(ctx, ok) }; result }
    is TdlResult.Failure -> { withContext(Dispatchers.Main) { toast(ctx, message) }; null }
}

private fun ft(s: String) = FormattedText(text = s, entities = emptyArray<TextEntity>())

// ---------------------------------------------------------------- contacts: add by phone, import

@Composable
fun AddContactScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) acc.scope.launch { importPhoneContacts(acc, ctx) } else toast(ctx, t("Contacts permission is needed"))
    }
    Page(t("Add Contact"), onBack) {
        OutlinedTextField(phone, { phone = it }, label = { Text(t("Phone number (with country code)")) }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        OutlinedTextField(first, { first = it }, label = { Text(t("First name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        OutlinedTextField(last, { last = it }, label = { Text(t("Last name (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        Button(enabled = !busy && phone.count { it.isDigit() } >= 6 && first.isNotBlank(), onClick = {
            busy = true
            acc.scope.launch {
                val r = acc.client.importContacts(contacts = arrayOf(ImportedContact(phoneNumber = phone.trim(), firstName = first.trim(), lastName = last.trim(), note = null)))
                val uid = (r as? TdlResult.Success)?.result?.userIds?.firstOrNull() ?: 0L
                val chat = if (uid != 0L) acc.privateChat(uid) else null
                withContext(Dispatchers.Main) {
                    busy = false
                    when {
                        r is TdlResult.Failure -> toast(ctx, r.message)
                        chat == null -> toast(ctx, t("This number isn't on Telegram"))
                        else -> { act.pop(); act.push(Screen.Chat(chat)) }
                    }
                }
            }
        }, modifier = Modifier.padding(20.dp)) { Text(t("Add")) }
        HorizontalDivider()
        ClickRow(t("Import phone contacts"), t("Find which of your phone contacts use Telegram")) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) acc.scope.launch { importPhoneContacts(acc, ctx) }
            else perm.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

private suspend fun importPhoneContacts(acc: TgAccount, ctx: android.content.Context) {
    val list = ArrayList<ImportedContact>()
    withContext(Dispatchers.IO) {
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                list.add(ImportedContact(phoneNumber = num, firstName = name.substringBefore(' '), lastName = name.substringAfter(' ', ""), note = null))
            }
        }
    }
    if (list.isEmpty()) { withContext(Dispatchers.Main) { toast(ctx, t("No phone contacts found")) }; return }
    var found = 0
    for (chunk in list.distinctBy { it.phoneNumber }.chunked(100)) {
        val r = (acc.client.importContacts(contacts = chunk.toTypedArray()) as? TdlResult.Success)?.result
        found += r?.userIds?.count { it != 0L } ?: 0
    }
    withContext(Dispatchers.Main) { toast(ctx, "$found " + t("contacts are on Telegram")) }
}

// ---------------------------------------------------------------- account: number, delete, sessions, birthday, usernames

@Composable
fun AccountExtrasScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ttl by remember { mutableIntStateOf(0) }
    var sessTtl by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var usernames by remember { mutableStateOf<List<String>>(emptyList()) }
    var birth by remember { mutableStateOf<Birthdate?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        ttl = (acc.client.getAccountTtl() as? TdlResult.Success)?.result?.days ?: 0
        sessTtl = (acc.client.getActiveSessions() as? TdlResult.Success)?.result?.inactiveSessionTtlDays ?: 0
        val me = acc.users[acc.myId]
        usernames = me?.usernames?.activeUsernames?.toList().orEmpty()
        birth = (acc.client.getUserFullInfo(userId = acc.myId) as? TdlResult.Success)?.result?.birthdate
    }
    Page(t("Account"), onBack) {
        SectionTitle(t("Phone number"))
        ClickRow(t("Change number"), acc.users[acc.myId]?.phoneNumber?.let { "+$it" }) { dialog = "phone" }
        SectionTitle(t("Profile"))
        ClickRow(t("Birthday"), birth?.let { b -> "${b.day}/${b.month}" + if (b.year > 0) "/${b.year}" else "" } ?: t("Not set")) { dialog = "birth" }
        if (usernames.size > 1) {
            Text(t("Usernames (order shown on your profile)"), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            usernames.forEachIndexed { i, u ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("@$u", modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = {
                        if (i > 0) { val l = usernames.toMutableList().apply { add(i - 1, removeAt(i)) }; usernames = l
                            acc.scope.launch { acc.client.reorderActiveUsernames(usernames = l.toTypedArray()).tell(ctx) } }
                    }) { Icon(Ic.Up, null) }
                    IconButton(onClick = {
                        if (i < usernames.lastIndex) { val l = usernames.toMutableList().apply { add(i + 1, removeAt(i)) }; usernames = l
                            acc.scope.launch { acc.client.reorderActiveUsernames(usernames = l.toTypedArray()).tell(ctx) } }
                    }) { Icon(Ic.Down, null) }
                }
            }
        }
        SectionTitle(t("Sessions"))
        ClickRow(t("End all other sessions"), t("Logs out every other device")) { dialog = "endall" }
        ClickRow(t("End inactive sessions after"), if (sessTtl > 0) "$sessTtl " + t("days") else "—") { dialog = "sessttl" }
        ClickRow(t("Websites logged in with Telegram"), "›") { act.push(Screen.Page("websites")) }
        SectionTitle(t("Delete account"))
        ClickRow(t("Delete my account if I'm away for"), if (ttl > 0) "$ttl " + t("days") else "—") { dialog = "ttl" }
        ClickRow(t("Delete account now"), t("This can't be undone")) { dialog = "delete" }
    }
    when (dialog) {
        "ttl" -> ChoiceDialog(t("Delete my account if I'm away for"), listOf(t("1 month") to 30, t("3 months") to 90, t("6 months") to 180, t("1 year") to 365, t("18 months") to 548, t("2 years") to 730), onDismiss = { dialog = "" }) { d ->
            dialog = ""; acc.scope.launch { acc.client.setAccountTtl(ttl = AccountTtl(days = d)).tell(ctx, t("Saved")); tick++ }
        }
        "sessttl" -> ChoiceDialog(t("End inactive sessions after"), listOf(t("1 week") to 7, t("1 month") to 30, t("3 months") to 90, t("6 months") to 180, t("1 year") to 365), onDismiss = { dialog = "" }) { d ->
            dialog = ""; acc.scope.launch { acc.client.setInactiveSessionTtl(inactiveSessionTtlDays = d).tell(ctx, t("Saved")); tick++ }
        }
        "endall" -> ConfirmDialog(t("End all other sessions?"), t("Every other device will be logged out."), { dialog = "" }) {
            dialog = ""; acc.scope.launch { acc.client.terminateAllOtherSessions().tell(ctx, t("Done")) }
        }
        "birth" -> {
            var d by remember { mutableStateOf(birth?.day?.toString() ?: "") }
            var m by remember { mutableStateOf(birth?.month?.toString() ?: "") }
            var y by remember { mutableStateOf(birth?.year?.takeIf { it > 0 }?.toString() ?: "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Birthday")) },
                text = {
                    Row {
                        OutlinedTextField(d, { d = it.filter(Char::isDigit).take(2) }, label = { Text(t("Day")) }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(m, { m = it.filter(Char::isDigit).take(2) }, label = { Text(t("Month")) }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(y, { y = it.filter(Char::isDigit).take(4) }, label = { Text(t("Year")) }, singleLine = true, modifier = Modifier.weight(1.3f))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = ""
                        val dd = d.toIntOrNull() ?: 0; val mm = m.toIntOrNull() ?: 0
                        acc.scope.launch {
                            acc.client.setBirthdate(birthdate = if (dd in 1..31 && mm in 1..12) Birthdate(day = dd, month = mm, year = y.toIntOrNull() ?: 0) else null).tell(ctx, t("Saved"))
                            tick++
                        }
                    }) { Text(t("Save")) }
                },
                dismissButton = { TextButton(onClick = { dialog = ""; acc.scope.launch { acc.client.setBirthdate(birthdate = null).tell(ctx, t("Removed")); tick++ } }) { Text(t("Remove")) } },
            )
        }
        "phone" -> {
            var num by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            var sent by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Change number")) },
                text = {
                    Column {
                        Text(t("Your chats and contacts move to the new number. Telegram sends a code to it."), fontSize = 13.sp)
                        OutlinedTextField(num, { num = it }, label = { Text(t("New number (with country code)")) }, singleLine = true, enabled = !sent)
                        if (sent) OutlinedTextField(code, { code = it.filter(Char::isDigit) }, label = { Text(t("Code")) }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        acc.scope.launch {
                            if (!sent) { if (acc.client.sendPhoneNumberCode(phoneNumber = num.trim(), settings = null, type = PhoneNumberCodeTypeChange()).tell(ctx) != null) sent = true }
                            else if (acc.client.checkPhoneNumberCode(code = code).tell(ctx, t("Number changed")) != null) dialog = ""
                        }
                    }) { Text(if (sent) t("Confirm") else t("Send code")) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
        "delete" -> {
            var reason by remember { mutableStateOf("") }
            var pw by remember { mutableStateOf("") }
            var sure by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(t("Delete account now"), color = Color(0xFFE53935)) },
                text = {
                    Column {
                        Text(t("All your messages, chats and contacts will be deleted forever. This can't be undone."))
                        OutlinedTextField(reason, { reason = it }, label = { Text(t("Reason (optional)")) }, singleLine = true)
                        OutlinedTextField(pw, { pw = it }, label = { Text(t("Two-step password (if you have one)")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sure, { sure = it }); Text(t("I understand")) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = sure, onClick = {
                        dialog = ""
                        acc.scope.launch { acc.client.deleteAccount(reason = reason, password = pw).tell(ctx, t("Account deleted")) }
                    }) { Text(t("Delete"), color = Color(0xFFE53935)) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Cancel")) } },
            )
        }
    }
}

@Composable
fun WebsitesScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<ConnectedWebsite>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = (acc.client.getConnectedWebsites() as? TdlResult.Success)?.result?.websites?.toList().orEmpty() }
    Page(t("Websites logged in with Telegram"), onBack) {
        val l = list
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (l.isEmpty()) Text(t("None"), modifier = Modifier.padding(20.dp))
        else {
            TextButton(onClick = { acc.scope.launch { acc.client.disconnectAllWebsites().tell(ctx, t("Done")); tick++ } }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(t("Log out of all websites"), color = Color(0xFFE53935))
            }
            for (w in l) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.domainName, fontWeight = FontWeight.SemiBold)
                        Text(listOf(w.browser, w.platform, w.location).filter { it.isNotBlank() }.joinToString(" · ") + "\n" + t("Last active") + " " + Fmt.listTime(w.lastActiveDate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { acc.scope.launch { acc.client.disconnectWebsite(websiteId = w.id).tell(ctx, t("Done")); tick++ } }) { Text(t("Log out")) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- privacy extras

@Composable
fun PrivacyExtras(acc: TgAccount) {
    val ctx = LocalContext.current
    var readDate by remember { mutableStateOf<Boolean?>(null) }
    var newChats by remember { mutableStateOf<Boolean?>(null) }
    var archive by remember { mutableStateOf<ArchiveChatListSettings?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        readDate = (acc.client.getReadDatePrivacySettings() as? TdlResult.Success)?.result?.showReadDate
        newChats = (acc.client.getNewChatPrivacySettings() as? TdlResult.Success)?.result?.allowNewChatsFromUnknownUsers
        archive = (acc.client.getArchiveChatListSettings() as? TdlResult.Success)?.result
    }
    SectionTitle(t("Messages"))
    readDate?.let { v ->
        SwitchRow(t("Show my read time"), t("If off, you also can't see others' read time"), v) { n ->
            acc.scope.launch { acc.client.setReadDatePrivacySettings(settings = ReadDatePrivacySettings(showReadDate = n)).tell(ctx); tick++ }
        }
    }
    newChats?.let { v ->
        SwitchRow(t("Strangers can message me"), t("Off: only contacts can start new chats (may need Premium)"), v) { n ->
            acc.scope.launch { acc.client.setNewChatPrivacySettings(settings = NewChatPrivacySettings(allowNewChatsFromUnknownUsers = n, incomingPaidMessageStarCount = 0)).tell(ctx); tick++ }
        }
    }
    archive?.let { a ->
        SectionTitle(t("Archive"))
        SwitchRow(t("Archive and mute new chats from strangers"), null, a.archiveAndMuteNewChatsFromUnknownUsers) { n ->
            acc.scope.launch { acc.client.setArchiveChatListSettings(settings = ArchiveChatListSettings(n, a.keepUnmutedChatsArchived, a.keepChatsFromFoldersArchived)).tell(ctx); tick++ }
        }
        SwitchRow(t("Keep unmuted chats archived"), t("Off: they leave the archive when a message arrives"), a.keepUnmutedChatsArchived) { n ->
            acc.scope.launch { acc.client.setArchiveChatListSettings(settings = ArchiveChatListSettings(a.archiveAndMuteNewChatsFromUnknownUsers, n, a.keepChatsFromFoldersArchived)).tell(ctx); tick++ }
        }
        SwitchRow(t("Keep folder chats archived"), null, a.keepChatsFromFoldersArchived) { n ->
            acc.scope.launch { acc.client.setArchiveChatListSettings(settings = ArchiveChatListSettings(a.archiveAndMuteNewChatsFromUnknownUsers, a.keepUnmutedChatsArchived, n)).tell(ctx); tick++ }
        }
    }
}

// ---------------------------------------------------------------- 2FA recovery

@Composable
fun PasswordRecoveryDialog(acc: TgAccount, atLogin: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (atLogin) { if (acc.client.requestAuthenticationPasswordRecovery().tell(ctx) != null) sent = true }
        else { val r = acc.client.requestPasswordRecovery().tell(ctx); if (r != null) { pattern = r.emailAddressPattern; sent = true } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Reset password")) },
        text = {
            Column {
                Text(if (sent) t("We sent a code to your recovery email") + (if (pattern.isNotBlank()) " ($pattern)" else "") + "." else t("Sending code…"))
                OutlinedTextField(code, { code = it.trim() }, label = { Text(t("Code from email")) }, singleLine = true)
                OutlinedTextField(pw, { pw = it }, label = { Text(t("New password (optional)")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(enabled = sent && code.isNotBlank(), onClick = {
                acc.scope.launch {
                    val ok = if (atLogin) acc.client.recoverAuthenticationPassword(recoveryCode = code, newPassword = pw, newHint = "").tell(ctx, t("Done")) != null
                    else acc.client.recoverPassword(recoveryCode = code, newPassword = pw, newHint = "").tell(ctx, t("Done")) != null
                    if (ok) withContext(Dispatchers.Main) { onDismiss() }
                }
            }) { Text(t("Reset")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- country picker (login)

@Composable
fun CountryPicker(acc: TgAccount, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        list = (acc.client.getCountries() as? TdlResult.Success)?.result?.countries?.filter { !it.isHidden && it.callingCodes.isNotEmpty() }
            ?.map { Triple(it.flagEmoji, it.name, it.callingCodes.first()) }?.sortedBy { it.second }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Country")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(t("Search")) })
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list.filter { q.isBlank() || it.second.contains(q, true) || it.third.startsWith(q.trimStart('+')) }) { (flag, name, code) ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 10.dp)) {
                            Text("$flag  $name", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("+$code", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

// ---------------------------------------------------------------- folders: edit, reorder, suggested, share

@Composable
fun FolderEditDialog(acc: TgAccount, folderId: Int, start: ChatFolder?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var folder by remember { mutableStateOf(start) }
    LaunchedEffect(folderId) { if (folderId != 0 && folder == null) folder = (acc.client.getChatFolder(chatFolderId = folderId) as? TdlResult.Success)?.result }
    val f = folder
    if (f == null) { AlertDialog(onDismissRequest = onDismiss, text = { CircularProgressIndicator() }, confirmButton = {}); return }
    var name by remember(f) { mutableStateOf(f.name.text.text) }
    var contacts by remember(f) { mutableStateOf(f.includeContacts) }
    var others by remember(f) { mutableStateOf(f.includeNonContacts) }
    var groups by remember(f) { mutableStateOf(f.includeGroups) }
    var channels by remember(f) { mutableStateOf(f.includeChannels) }
    var bots by remember(f) { mutableStateOf(f.includeBots) }
    var noMuted by remember(f) { mutableStateOf(f.excludeMuted) }
    var noRead by remember(f) { mutableStateOf(f.excludeRead) }
    val picked = remember(f) { mutableStateListOf<Long>().apply { addAll(f.includedChatIds.toList()) } }
    val chats = remember { acc.chatList(ChatListMain()).map { it.first }.take(150) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (folderId == 0) t("Create folder") else t("Edit folder")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                item { OutlinedTextField(name, { name = it.take(12) }, label = { Text(t("Folder name")) }, singleLine = true) }
                item {
                    for ((label, get, set) in listOf<Triple<String, Boolean, (Boolean) -> Unit>>(
                        Triple(t("Contacts"), contacts) { contacts = it }, Triple(t("Non-contacts"), others) { others = it },
                        Triple(t("Groups"), groups) { groups = it }, Triple(t("Channels"), channels) { channels = it }, Triple(t("Bots"), bots) { bots = it },
                        Triple(t("Hide muted"), noMuted) { noMuted = it }, Triple(t("Hide read"), noRead) { noRead = it },
                    )) {
                        Row(Modifier.fillMaxWidth().clickable { set(!get) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(get, { set(it) }); Text(label) }
                    }
                    Text(t("Chats"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(chats, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { if (!picked.remove(c.id)) picked.add(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id in picked, null); Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nf = ChatFolder(
                    name = ChatFolderName(text = ft(name.ifBlank { t("Folder") }), animateCustomEmoji = false), icon = f.icon, colorId = f.colorId, isShareable = f.isShareable,
                    pinnedChatIds = f.pinnedChatIds.filter { it in picked }.toLongArray(), includedChatIds = picked.toLongArray(), excludedChatIds = f.excludedChatIds,
                    excludeMuted = noMuted, excludeRead = noRead, excludeArchived = f.excludeArchived,
                    includeContacts = contacts, includeNonContacts = others, includeBots = bots, includeGroups = groups, includeChannels = channels,
                )
                onDismiss()
                acc.scope.launch {
                    if (folderId == 0) acc.client.createChatFolder(folder = nf).tell(ctx, t("Saved")) else acc.client.editChatFolder(chatFolderId = folderId, folder = nf).tell(ctx, t("Saved"))
                }
            }) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

fun emptyFolder() = ChatFolder(
    name = ChatFolderName(text = ft(""), animateCustomEmoji = false), icon = null, colorId = -1, isShareable = false,
    pinnedChatIds = LongArray(0), includedChatIds = LongArray(0), excludedChatIds = LongArray(0), excludeMuted = false, excludeRead = false, excludeArchived = false,
    includeContacts = false, includeNonContacts = false, includeBots = false, includeGroups = false, includeChannels = false,
)

@Composable
fun SuggestedFolders(acc: TgAccount) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<RecommendedChatFolder>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = (acc.client.getRecommendedChatFolders() as? TdlResult.Success)?.result?.chatFolders?.toList().orEmpty() }
    if (list.isEmpty()) return
    SectionTitle(t("Suggested folders"))
    for (r in list) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.folder.name.text.text, fontWeight = FontWeight.SemiBold)
                Text(r.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { acc.scope.launch { acc.client.createChatFolder(folder = r.folder).tell(ctx, t("Added")); tick++ } }) { Text(t("Add")) }
        }
    }
}

suspend fun shareFolder(acc: TgAccount, ctx: android.content.Context, folderId: Int) {
    val f = (acc.client.getChatFolder(chatFolderId = folderId) as? TdlResult.Success)?.result ?: return
    val ids = f.includedChatIds.filter { id -> acc.chats[id]?.let { it.isGroup || it.isChannel } == true }.toLongArray()
    if (ids.isEmpty()) { withContext(Dispatchers.Main) { toast(ctx, t("Only folders with groups or channels you picked by hand can be shared")) }; return }
    val link = acc.client.createChatFolderInviteLink(chatFolderId = folderId, name = "", chatIds = ids).tell(ctx) ?: return
    withContext(Dispatchers.Main) {
        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("link", link.inviteLink))
        toast(ctx, t("Folder link copied"))
    }
}
