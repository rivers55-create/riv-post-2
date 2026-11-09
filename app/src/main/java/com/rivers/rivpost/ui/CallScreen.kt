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
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4DB0), Color(0xFF6A3BA8), Color(0xFF2B1D4F))))
            .clickable(enabled = false) { },
    ) {
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
            Spacer(Modifier.weight(1f))
            if (call.incomingRinging) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Ic.CallEnd, t("Decline"), Color(0xFFE53935)) { Calls.decline() }
                    RoundButton(Ic.Phone, t("Accept"), Color(0xFF34C759)) { Calls.accept(act) }
                }
            } else if (!call.ended) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Ic.Speaker, t("Speaker"), if (call.speaker) Color.White else Color.White.copy(alpha = 0.2f), if (call.speaker) Color.Black else Color.White) { Calls.toggleSpeaker() }
                    RoundButton(if (call.muted) Ic.MicOff else Ic.Mic, t("Mute"), if (call.muted) Color.White else Color.White.copy(alpha = 0.2f), if (call.muted) Color.Black else Color.White) { Calls.toggleMute() }
                    RoundButton(Ic.CallEnd, t("End"), Color(0xFFE53935)) { Calls.hangUp() }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
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
