package com.rivers.rivpost.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.calls.CallPeople
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount

/** Every call option in one place. */
@Composable
fun CallSettingsScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val holdPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) { }
            Prefs.callHoldMusic = uri.toString(); tick++
        }
    }
    Page(t("Calls"), onBack) {
        if (tick < 0) Text("")
        SwitchRow(t("Voice calls"), t("Call people and answer calls in Riv Post"), Prefs.voiceCalls) { Prefs.voiceCalls = it; tick++ }
        if (!Prefs.voiceCalls) return@Page
        SwitchRow(t("Direct connection in calls"), t("Better quality. Off sends calls through Telegram's servers so the other person can't see your IP address."), Prefs.callP2p) { Prefs.callP2p = it; tick++ }

        SectionTitle(t("During a call"))
        SwitchRow(t("Record button"), t("Saves both sides to Downloads › Riv Post › Calls. In some places you must tell the other person."), Prefs.callRecord) { Prefs.callRecord = it; tick++ }
        SwitchRow(t("Voice changer button"), t("Deep, high or robot voice"), Prefs.callVoiceFx) { Prefs.callVoiceFx = it; tick++ }
        SwitchRow(t("Live captions on start"), t("Shows their words as text. Android 13 or newer."), Prefs.callCaptions) { Prefs.callCaptions = it; tick++ }
        SwitchRow(t("Translate captions"), t("Into the app language"), Prefs.callTranslate) { Prefs.callTranslate = it; tick++ }
        SwitchRow(t("Call notes"), t("Notes and captions go to Saved Messages after the call"), Prefs.callNotes) { Prefs.callNotes = it; tick++ }
        SwitchRow(t("Chat during calls"), null, Prefs.callChat) { Prefs.callChat = it; tick++ }
        SwitchRow(t("Call details button"), t("Network, data used, signal"), Prefs.callStats) { Prefs.callStats = it; tick++ }
        SwitchRow(t("Muted reminder"), t("Tells you when you talk while muted"), Prefs.callMuteReminder) { Prefs.callMuteReminder = it; tick++ }
        SwitchRow(t("Weak connection warning"), null, Prefs.callWeakWarn) { Prefs.callWeakWarn = it; tick++ }
        SwitchRow(t("Volume keys"), t("Press volume down twice to mute"), Prefs.callVolKeys) { Prefs.callVolKeys = it; tick++ }
        SwitchRow(t("Vibrate on connect and hang-up"), null, Prefs.callVibrate) { Prefs.callVibrate = it; tick++ }
        SwitchRow(t("Shake to hang up"), null, Prefs.callShake) { Prefs.callShake = it; tick++ }
        ChoiceRow(t("Call time alert"), listOf("0" to t("Off"), "5" to "5 " + t("min"), "10" to "10 " + t("min"), "30" to "30 " + t("min"), "60" to "60 " + t("min")), Prefs.callTimeAlert.toString()) { Prefs.callTimeAlert = it.toInt(); tick++ }
        ClickRow(t("Hold music"), if (Prefs.callHoldMusic.isBlank()) t("Built-in tune") else t("Your file")) { holdPick.launch(arrayOf("audio/*")) }
        if (Prefs.callHoldMusic.isNotBlank()) ClickRow(t("Use the built-in hold tune"), null) { Prefs.callHoldMusic = ""; tick++ }
        ChoiceRow(
            t("Call screen style"),
            listOf("violet" to t("Violet"), "blue" to t("Blue"), "green" to t("Green"), "sunset" to t("Sunset"), "dark" to t("Dark"), "photo" to t("Their photo")),
            Prefs.callStyle,
        ) { Prefs.callStyle = it; tick++ }

        SectionTitle(t("Sound"))
        Text(t("Your mic level (whisper mode above 1×)") + ": %.1f×".format(Prefs.callMicGain), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(Prefs.callMicGain, { Prefs.callMicGain = it; tick++ }, valueRange = 0.5f..4f, modifier = Modifier.padding(horizontal = 20.dp))
        Text(t("Their volume") + ": %.1f×".format(Prefs.callPlayGain), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(Prefs.callPlayGain, { Prefs.callPlayGain = it; tick++ }, valueRange = 0.5f..3f, modifier = Modifier.padding(horizontal = 20.dp))
        Text(t("Bass") + ": %+.1f".format(Prefs.callBass), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(Prefs.callBass, { Prefs.callBass = it; tick++ }, valueRange = -1f..1f, modifier = Modifier.padding(horizontal = 20.dp))
        Text(t("Treble (clarity)") + ": %+.1f".format(Prefs.callTreble), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(Prefs.callTreble, { Prefs.callTreble = it; tick++ }, valueRange = -1f..1f, modifier = Modifier.padding(horizontal = 20.dp))

        SectionTitle(t("Incoming calls"))
        SwitchRow(t("Flip to silence"), t("Turn the phone face down to stop the ringing"), Prefs.callFlip) { Prefs.callFlip = it; tick++ }
        SwitchRow(t("Auto-answer with headphones"), null, Prefs.callAutoHeadset) { Prefs.callAutoHeadset = it; tick++ }
        SwitchRow(t("Headset button"), t("Press to answer or mute, hold to hang up"), Prefs.callHeadsetButton) { Prefs.callHeadsetButton = it; tick++ }
        SwitchRow(t("Silent ring for unknown callers"), t("People not in your contacts"), Prefs.callUnknownSilent) { Prefs.callUnknownSilent = it; tick++ }
        SwitchRow(t("Show their last message while ringing"), null, Prefs.callLastMsg) { Prefs.callLastMsg = it; tick++ }
        SwitchRow(t("Call waiting"), t("See a second call instead of them getting busy"), Prefs.callWaiting) { Prefs.callWaiting = it; tick++ }
        SwitchRow(t("Missed call notification"), t("With call back and remind me buttons"), Prefs.callMissedNotif) { Prefs.callMissedNotif = it; tick++ }
        var editReplies by remember { mutableStateOf(false) }
        ClickRow(t("Quick decline messages"), "›") { editReplies = true }
        if (editReplies) {
            var txt by remember { mutableStateOf(Prefs.callReplies) }
            AlertDialog(
                onDismissRequest = { editReplies = false },
                title = { Text(t("Quick decline messages")) },
                text = { OutlinedTextField(txt, { txt = it }, label = { Text(t("One per line. Empty uses the defaults.")) }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { Prefs.callReplies = txt; editReplies = false }) { Text(t("Save")) } },
            )
        }

        SectionTitle(t("Quiet hours"))
        SwitchRow(t("Quiet hours for calls"), t("Favourites marked \"Ring at night\" still ring"), Prefs.callQuiet) { Prefs.callQuiet = it; tick++ }
        if (Prefs.callQuiet) {
            val hours = (0..23).map { it.toString() to "%02d:00".format(it) }
            ChoiceRow(t("From"), hours, Prefs.callQuietFrom.toString()) { Prefs.callQuietFrom = it.toInt(); tick++ }
            ChoiceRow(t("To"), hours, Prefs.callQuietTo.toString()) { Prefs.callQuietTo = it.toInt(); tick++ }
            ChoiceRow(t("During quiet hours"), listOf("silent" to t("Ring silently"), "decline" to t("Decline automatically")), Prefs.callQuietMode) { Prefs.callQuietMode = it; tick++ }
            if (Prefs.callQuietMode == "decline") OutlinedTextField(
                Prefs.callQuietMsg, { Prefs.callQuietMsg = it; tick++ }, label = { Text(t("Auto-reply (optional)")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }

        SectionTitle(t("Outgoing calls"))
        ChoiceRow(t("Auto-redial"), listOf("0" to t("Off"), "1" to "1×", "2" to "2×", "3" to "3×", "5" to "5×"), Prefs.callRedial.toString()) { Prefs.callRedial = it.toInt(); tick++ }
        SwitchRow(t("Wi-Fi only"), t("Blocks calls on mobile data"), Prefs.callWifiOnly) { Prefs.callWifiOnly = it; tick++ }
        SwitchRow(t("Battery and data saver"), t("Lower quality on mobile data or when the battery is low"), Prefs.callSaver) { Prefs.callSaver = it; tick++ }
        SwitchRow(t("Call button in message notifications"), null, Prefs.callNotifButton) { Prefs.callNotifButton = it; tick++ }

        SectionTitle(t("History"))
        SwitchRow(t("Hide Vault calls"), t("Calls with people in your Vault don't show in the list"), Prefs.callHideVault) { Prefs.callHideVault = it; tick++ }
        SwitchRow(t("Talk time stats"), t("Weekly and monthly totals on the Calls page"), Prefs.callStats) { Prefs.callStats = it; tick++ }
        if (Prefs.callHidden.isNotBlank()) ClickRow(t("Show hidden calls again"), null) { Prefs.callHidden = ""; tick++ }

        SectionTitle(t("Phone and car"))
        SwitchRow(
            t("Work like a normal phone call"),
            t("Answer from the car or Bluetooth, and normal calls put Riv Post calls on hold. Experimental."),
            Prefs.callTelecom,
        ) { Prefs.callTelecom = it; tick++ }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(t("Long-press someone in the Calls list for per-person options: auto-answer, ring at night, speaker, auto-record and their own ringtone."), fontSize = 13.sp, modifier = Modifier.padding(20.dp))
    }
}

/** Per-person call options. */
@Composable
fun CallPersonDialog(acc: TgAccount, userId: Long, onDismiss: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val tone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val u: Uri? = r.data?.let {
                if (android.os.Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else @Suppress("DEPRECATION") it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            CallPeople.setRingtone(userId, u?.toString() ?: ""); tick++
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(acc.userName(userId)) },
        text = {
            androidx.compose.runtime.key(tick) {
                Column {
                    SwitchRow(t("Auto-answer"), null, CallPeople.has(userId, CallPeople.AUTO)) { CallPeople.set(userId, CallPeople.AUTO, it); tick++ }
                    SwitchRow(t("Ring at night"), t("Rings during quiet hours"), CallPeople.has(userId, CallPeople.LOUD)) { CallPeople.set(userId, CallPeople.LOUD, it); tick++ }
                    SwitchRow(t("Speaker on"), null, CallPeople.has(userId, CallPeople.SPEAKER)) { CallPeople.set(userId, CallPeople.SPEAKER, it); tick++ }
                    if (Prefs.callRecord) SwitchRow(t("Auto-record"), null, CallPeople.has(userId, CallPeople.REC)) { CallPeople.set(userId, CallPeople.REC, it); tick++ }
                    ClickRow(t("Ringtone"), if (CallPeople.ringtone(userId).isBlank()) t("Default") else t("Custom")) {
                        tone.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done")) } },
    )
}
