package com.rivers.rivpost.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.rivers.rivpost.calls.CallCaptions
import com.rivers.rivpost.calls.CallReminders
import com.rivers.rivpost.data.Prefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.calls.CallUi
import com.rivers.rivpost.calls.Calls
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.Tg
import kotlinx.coroutines.delay

/** Full-screen call view, or a thin green bar when minimised. Shown above everything else. */
@Composable
fun CallOverlay(act: MainActivity) {
    val u by Calls.ui.collectAsState()
    val call = u ?: return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt > 0) { now = SystemClock.elapsedRealtime(); delay(1000) }
    }
    val status = when {
        call.ended -> call.status
        call.connectedAt > 0 && call.status.isEmpty() -> Fmt.duration(((now - call.connectedAt) / 1000).toInt())
        call.connectedAt > 0 -> Fmt.duration(((now - call.connectedAt) / 1000).toInt()) + " · " + call.status
        else -> call.status
    }
    if (call.minimized) {
        CallBar(call, status)
        return
    }
    BackHandler { Calls.minimize(true) }
    CallFull(act, call, status)
}

@Composable
private fun CallBar(call: CallUi, status: String) {
    val acc = Tg.get(call.acc)
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF2E9E5B)).clickable { Calls.minimize(false) }
            .statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            acc.userName(call.userId) + " · " + status + " · " + t("Tap to return"),
            color = Color.White, fontSize = 14.sp, maxLines = 1,
        )
    }
}

@Composable
private fun CallFull(act: MainActivity, call: CallUi, status: String) {
    val acc = Tg.get(call.acc)
    val ctx = LocalContext.current
    val colors = when (Prefs.callStyle) {
        "blue" -> listOf(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF051B3A))
        "green" -> listOf(Color(0xFF2E7D5B), Color(0xFF1B5E45), Color(0xFF0B2A1F))
        "dark" -> listOf(Color(0xFF1C1C1E), Color(0xFF111113), Color(0xFF000000))
        "sunset" -> listOf(Color(0xFFE96443), Color(0xFF904E95), Color(0xFF2B1D4F))
        else -> listOf(Color(0xFF3A4DB0), Color(0xFF6A3BA8), Color(0xFF2B1D4F))
    }
    var replies by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(false) }
    var chat by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) Calls.playIntoCall(uri) }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(colors))
            .clickable(enabled = false) { },
    ) {
        if (Prefs.callStyle == "photo") {
            Box(Modifier.fillMaxSize().blur(40.dp), contentAlignment = Alignment.Center) { UserAvatar(acc, call.userId, 700.dp) }
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Calls.minimize(true) }) { Icon(Ic.Down, null, tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (call.emojis.isNotEmpty()) Text(call.emojis.joinToString(" "), fontSize = 26.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            if (call.emojis.isNotEmpty()) Text(
                t("If these emoji match on their screen, the call is encrypted end to end"),
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(40.dp))
            UserAvatar(acc, call.userId, 130.dp)
            Spacer(Modifier.height(18.dp))
            Text(acc.userName(call.userId), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, textAlign = TextAlign.Center)
            if (call.connectedAt > 0 && !call.ended) {
                Spacer(Modifier.height(8.dp))
                SignalBars(call.bars)
            }
            if (call.incomingRinging && call.lastMsg.isNotBlank()) Text(
                "“" + call.lastMsg + "”", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp, start = 20.dp, end = 20.dp),
            )
            if (call.talkingMuted) Banner(t("You're muted. Tap Mute to talk."))
            if (call.weak) Banner(t("Weak connection. Try Wi-Fi or move to a better spot."))
            if (call.hold) Banner(t("On hold. They hear hold music."))
            if (call.waitingId != 0) {
                Banner(acc.userName(call.waitingUser) + " " + t("is calling you"))
                Row {
                    TextButton(onClick = { Calls.declineWaiting() }) { Text(t("Decline"), color = Color.White) }
                    TextButton(onClick = { Calls.switchToWaiting(act) }) { Text(t("End and answer"), color = Color(0xFF7CF29C)) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (call.captions && CallCaptions.lines.isNotEmpty()) Column(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(12.dp),
            ) { for (l in CallCaptions.lines) Text(l, color = Color.White, fontSize = 16.sp) }
            if (call.incomingRinging) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SmallButton(Ic.Chat, t("Message")) { replies = true }
                    SmallButton(Ic.Clock, t("Remind me")) { remind = true }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Ic.CallEnd, t("Decline"), Color(0xFFE53935)) { Calls.decline() }
                    RoundButton(Ic.Phone, t("Accept"), Color(0xFF34C759)) { Calls.accept(act) }
                }
            } else if (!call.ended) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (Prefs.callRecord) SmallButton(Ic.Record, if (call.recording) t("Stop rec") else t("Record"), call.recording) { Calls.toggleRecord(ctx) }
                    SmallButton(Ic.Pause, t("Hold"), call.hold) { Calls.setHold(!call.hold) }
                    SmallButton(Ic.Note, if (call.playing && !call.hold) t("Stop audio") else t("Play audio"), call.playing && !call.hold) {
                        if (call.playing && !call.hold) Calls.playIntoCall(null) else pick.launch("audio/*")
                    }
                    SmallButton(Ic.Hide, t("Mute both"), call.deafen) { Calls.toggleDeafen() }
                    if (Prefs.callVoiceFx) SmallButton(Ic.Smile, listOf(t("Normal voice"), t("Deep"), t("High"), t("Robot"))[voice], voice != 0) {
                        voice = (voice + 1) % 4; Calls.setVoice(voice)
                    }
                    SmallButton(Ic.Translate, t("Captions"), call.captions) { Calls.toggleCaptions(ctx) }
                    if (Prefs.callNotes) SmallButton(Ic.Edit, t("Notes"), call.notes.isNotBlank()) { notes = true }
                    if (Prefs.callChat) SmallButton(Ic.Chat, t("Chat")) { chat = true }
                    if (Prefs.callStats) SmallButton(Ic.Info, t("Details")) { details = true }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Ic.Speaker, t("Speaker"), if (call.speaker) Color.White else Color.White.copy(alpha = 0.2f), if (call.speaker) Color.Black else Color.White) { Calls.toggleSpeaker() }
                    RoundButton(if (call.muted) Ic.MicOff else Ic.Mic, t("Mute"), if (call.muted) Color.White else Color.White.copy(alpha = 0.2f), if (call.muted) Color.Black else Color.White) { Calls.toggleMute() }
                    RoundButton(Ic.CallEnd, t("End"), Color(0xFFE53935)) { Calls.hangUp() }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (replies) {
        val list = (Prefs.callReplies.ifBlank { null }?.lines()?.filter { it.isNotBlank() }
            ?: listOf(t("Can't talk now, I'll call you back."), t("I'm in a meeting."), t("Text me please."), t("On my way.")))
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replies = false },
            title = { Text(t("Decline with a message")) },
            text = {
                Column {
                    for (r in list) Text(r, Modifier.fillMaxWidth().clickable { replies = false; Calls.declineWithMessage(r) }.padding(vertical = 10.dp))
                    OutlinedTextField(custom, { custom = it }, label = { Text(t("Your own message")) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { replies = false; if (custom.isNotBlank()) Calls.declineWithMessage(custom) }) { Text(t("Send")) } },
            dismissButton = { TextButton(onClick = { replies = false }) { Text(t("Cancel")) } },
        )
    }
    if (remind) AlertDialog(
        onDismissRequest = { remind = false },
        title = { Text(t("Decline and remind me")) },
        text = {
            Column {
                for (m in listOf(10, 30, 60, 180)) Text(
                    if (m < 60) "$m " + t("min") else "${m / 60} " + t("hours"),
                    Modifier.fillMaxWidth().clickable { remind = false; val u = call; Calls.decline(); CallReminders.schedule(ctx, u.acc, u.userId, m) }.padding(vertical = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { remind = false }) { Text(t("Cancel")) } },
    )
    if (notes) {
        var n by remember { mutableStateOf(call.notes) }
        AlertDialog(
            onDismissRequest = { Calls.setNotes(n); notes = false },
            title = { Text(t("Call notes")) },
            text = {
                Column {
                    Text(t("Saved to Saved Messages when the call ends."), fontSize = 13.sp)
                    OutlinedTextField(n, { n = it }, modifier = Modifier.fillMaxWidth().height(180.dp))
                }
            },
            confirmButton = { TextButton(onClick = { Calls.setNotes(n); notes = false }) { Text(t("Done")) } },
        )
    }
    if (chat) {
        var m by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chat = false },
            title = { Text(acc.userName(call.userId)) },
            text = { OutlinedTextField(m, { m = it }, label = { Text(t("Message")) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { Calls.sendChat(m); chat = false }) { Text(t("Send")) } },
            dismissButton = { TextButton(onClick = { chat = false }) { Text(t("Cancel")) } },
        )
    }
    if (details) {
        var rows by remember { mutableStateOf(Calls.details(ctx)) }
        LaunchedEffect(Unit) { while (true) { delay(1000); rows = Calls.details(ctx) } }
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text(t("Call details")) },
            text = { Column { for ((k, v) in rows) Row(Modifier.padding(vertical = 3.dp)) { Text(k, Modifier.weight(1f)); Text(v, fontWeight = FontWeight.SemiBold) } } },
            confirmButton = { TextButton(onClick = { details = false }) { Text(t("Close")) } },
        )
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SmallButton(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.18f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SignalBars(n: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier.padding(horizontal = 1.5.dp).width(4.dp).height((4 + i * 3).dp)
                    .background(if (i <= n) Color.White else Color.White.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, fg: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = fg, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
