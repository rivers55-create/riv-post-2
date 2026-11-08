package com.rivers.rivpost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailAddress
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthenticationCodeTypeSms
import dev.g000sha256.tdl.dto.AuthenticationCodeTypeTelegramMessage
import kotlinx.coroutines.launch

@Composable
private fun Logo() {
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF2A0D4D)),
        contentAlignment = Alignment.Center,
    ) {
        Text("R", color = Color(0xFFB388FF), fontSize = 44.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
    Text("Riv Post", fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AuthPage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Logo()
        Spacer(Modifier.height(22.dp))
        content()
    }
}

/** First launch: the user pastes their own api_id and api_hash. */
@Composable
fun SetupScreen() {
    var id by remember { mutableStateOf(if (Prefs.apiId > 0) Prefs.apiId.toString() else "") }
    var hash by remember { mutableStateOf(Prefs.apiHash) }
    var error by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    AuthPage {
        Text(t("Welcome! One-time setup"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            t("Paste your Telegram api_id and api_hash from my.telegram.org → API development tools. They stay only on this phone."),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = id, onValueChange = { s -> id = s.filter { it.isDigit() }.take(12) },
            label = { Text("api_id") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = hash, onValueChange = { hash = it.trim().take(64) },
            label = { Text("api_hash") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val n = id.toIntOrNull() ?: 0
                if (n <= 0 || hash.length < 16) {
                    error = t("Please check both values")
                } else {
                    Prefs.apiId = n
                    Prefs.apiHash = hash
                    if (Tg.accounts.value.isNotEmpty()) {
                        restartApp(ctx)
                    } else {
                        Tg.startAll()
                        Tg.updateService(ctx)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(t("Continue")) }
    }
}

@Composable
fun LoginScreen(acc: TgAccount) {
    val st by acc.auth.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var input by remember(st) { mutableStateOf("") }
    var input2 by remember(st) { mutableStateOf("") }

    fun run(block: suspend () -> String?) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            val e = block()
            busy = false
            if (e != null) error = friendly(e)
        }
    }

    AuthPage {
        when (val s = st) {
            is AuthorizationStateWaitPhoneNumber -> {
                Text(t("Your phone number"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Include the country code, for example +964…"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = input, onValueChange = { v -> input = v.filter { it.isDigit() || it == '+' || it == ' ' } },
                    label = { Text(t("Phone number")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { run { acc.sendPhone(input.replace(" ", "")) } },
                    enabled = !busy && input.count { it.isDigit() } >= 6,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(t("Next")) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { Prefs.apiId = 0; Prefs.apiHash = "" }) { Text(t("Change API keys")) }
            }
            is AuthorizationStateWaitCode -> {
                val where = when (s.codeInfo.type) {
                    is AuthenticationCodeTypeTelegramMessage -> t("We sent the code to your Telegram app on another device.")
                    is AuthenticationCodeTypeSms -> t("We sent the code by SMS.")
                    else -> t("Enter the code you received.")
                }
                Text(s.codeInfo.phoneNumber, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(where, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = input, onValueChange = { v -> input = v.filter { it.isDigit() }.take(8) },
                    label = { Text(t("Code")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { run { acc.sendCode(input) } },
                    enabled = !busy && input.length >= 4,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(t("Sign in")) }
                if (s.codeInfo.nextType != null) {
                    TextButton(onClick = { run { acc.resendCode() } }) { Text(t("Send the code another way")) }
                }
            }
            is AuthorizationStateWaitPassword -> {
                Text(t("Two-step verification"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Your account has an extra password.") +
                        if (s.passwordHint.isNotBlank()) "\n" + t("Hint") + ": " + s.passwordHint else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text(t("Password")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { run { acc.sendPassword(input) } },
                    enabled = !busy && input.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(t("Sign in")) }
            }
            is AuthorizationStateWaitRegistration -> {
                Text(t("New account"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(64) },
                    label = { Text(t("First name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input2, onValueChange = { input2 = it.take(64) },
                    label = { Text(t("Last name (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { run { acc.register(input.trim(), input2.trim()) } },
                    enabled = !busy && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(t("Create account")) }
            }
            is AuthorizationStateWaitEmailAddress, is AuthorizationStateWaitEmailCode,
            is AuthorizationStateWaitOtherDeviceConfirmation -> {
                Text(
                    t("This account needs a sign-in method Riv Post doesn't support yet. Please sign in with the phone number on another Telegram app first, then try again."),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(t("Connecting…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(Modifier.size(28.dp))
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (Tg.previousIndex >= 0) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { Tg.cancelAdd() }) {
                    Icon(Ic.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(t("Cancel adding account"))
                }
            }
        }
    }
}

private fun friendly(e: String): String = when {
    e.contains("PHONE_NUMBER_INVALID") -> t("This phone number is not valid.")
    e.contains("PHONE_CODE_INVALID") -> t("Wrong code. Please try again.")
    e.contains("PHONE_CODE_EXPIRED") -> t("The code expired. Ask for a new one.")
    e.contains("PASSWORD_HASH_INVALID") -> t("Wrong password.")
    e.contains("FLOOD") || e.contains("Too Many") -> t("Too many tries. Please wait and try again later.")
    e.contains("API_ID") || e.contains("api_id") -> t("The API keys are not valid. Check them in Settings.")
    else -> e
}
