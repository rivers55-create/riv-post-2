package com.rivers.rivpost.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t

@Composable
fun LockScreen(act: MainActivity) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val canBio = remember {
        Prefs.fingerprint && BiometricManager.from(act)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun bio() {
        if (!canBio) return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                act.unlock()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Riv Post")
            .setSubtitle(t("Unlock with fingerprint"))
            .setNegativeButtonText(t("Use PIN"))
            .build()
        try { prompt.authenticate(info) } catch (_: Throwable) { }
    }

    LaunchedEffect(Unit) { bio() }

    fun press(d: String) {
        wrong = false
        if (pin.length >= 8) return
        pin += d
        if (pin.length >= 4 && Prefs.hash(pin) == Prefs.pinHash) {
            act.unlock()
        } else if (pin.length == 8) {
            wrong = true
            pin = ""
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Ic.Lock, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(t("Enter your PIN"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (wrong) t("Wrong PIN") else " ", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for (k in row) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (k.length == 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                            .clickable {
                                if (k == "del") {
                                    pin = pin.dropLast(1)
                                } else if (k == "bio") {
                                    bio()
                                } else {
                                    press(k)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Text("⌫", fontSize = 24.sp)
                            "bio" -> { if (canBio) Text("👆", fontSize = 24.sp) }
                            else -> Text(k, fontSize = 26.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (canBio) TextButton(onClick = { bio() }) { Text(t("Unlock with fingerprint")) }
    }
}

/** Asks for fingerprint or PIN before running [onOk] (vault, drawer lock). */
object Auth {
    var pending by mutableStateOf<(() -> Unit)?>(null)
}

fun requireAuth(act: MainActivity, onOk: () -> Unit) {
    if (Prefs.pinHash.isEmpty()) {
        android.widget.Toast.makeText(act, t("Set a PIN first: Settings → Chat Settings → App lock"), android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val bio = Prefs.fingerprint && BiometricManager.from(act)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    if (!bio) { Auth.pending = onOk; return }
    val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) Auth.pending = onOk
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder().setTitle("Riv Post").setSubtitle(t("Unlock with fingerprint"))
        .setNegativeButtonText(t("Use PIN")).build()
    try { prompt.authenticate(info) } catch (_: Throwable) { Auth.pending = onOk }
}

@Composable
fun AuthPinDialog() {
    val ok = Auth.pending ?: return
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { Auth.pending = null },
        title = { Text(t("Enter your PIN")) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = pin, onValueChange = { s -> pin = s.filter { it.isDigit() }.take(8); wrong = false },
                    singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                )
                if (wrong) Text(t("Wrong PIN"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Prefs.hash(pin) == Prefs.pinHash) { Auth.pending = null; ok() } else { wrong = true }
            }) { Text(t("Unlock")) }
        },
        dismissButton = { TextButton(onClick = { Auth.pending = null }) { Text(t("Cancel")) } },
    )
}
