package com.rivers.rivpost.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.BotCommand
import dev.g000sha256.tdl.dto.CallbackQueryPayloadData
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCallback
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeCopyText
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeLoginUrl
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeSwitchInline
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeUrl
import dev.g000sha256.tdl.dto.InlineKeyboardButtonTypeWebApp
import dev.g000sha256.tdl.dto.InlineQueryResult
import dev.g000sha256.tdl.dto.InlineQueryResultAnimation
import dev.g000sha256.tdl.dto.InlineQueryResultArticle
import dev.g000sha256.tdl.dto.InlineQueryResultAudio
import dev.g000sha256.tdl.dto.InlineQueryResultDocument
import dev.g000sha256.tdl.dto.InlineQueryResultPhoto
import dev.g000sha256.tdl.dto.InlineQueryResultVideo
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.KeyboardButtonTypeWebApp
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.ReplyMarkupInlineKeyboard
import dev.g000sha256.tdl.dto.ReplyMarkupShowKeyboard
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.dto.WebAppOpenModeFullSize
import dev.g000sha256.tdl.dto.WebAppOpenParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mini app request for the web-app screen. */
data class WebAppRequest(val acc: Int, val title: String, val url: String)

private fun openUrl(ctx: Context, url: String) {
    if (LinkEditCheck.isProxy(url)) { LinkEdit.open(url); return }
    try {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) { toast(ctx, t("Couldn't open the link")) }
}

private object LinkEditCheck {
    fun isProxy(u: String) = com.rivers.rivpost.tg.Proxies.parseLink(u) != null
}

suspend fun openWebApp(acc: TgAccount, act: MainActivity, chatId: Long, botId: Long, url: String, title: String) {
    val params = WebAppOpenParameters(theme = null, applicationName = "Riv Post", mode = WebAppOpenModeFullSize())
    val r = acc.client.openWebApp(chatId = chatId, botUserId = botId, url = url, parameters = params)
    val link = (r as? TdlResult.Success)?.result?.url?.url
        ?: (acc.client.getWebAppUrl(botUserId = botId, url = url, parameters = params) as? TdlResult.Success)?.result?.url
    withContext(Dispatchers.Main) {
        if (link != null) act.push(Screen.WebApp(WebAppRequest(acc.index, title, link)))
        else if (r is TdlResult.Failure) toast(act, r.message)
    }
}

/** Buttons attached under a bot's message. */
@Composable
fun InlineButtons(acc: TgAccount, act: MainActivity, m: Message, onSwitchInline: (String) -> Unit) {
    val kb = m.replyMarkup as? ReplyMarkupInlineKeyboard ?: return
    val ctx = LocalContext.current
    val style = LocalStyle.current
    Column(Modifier.widthIn310().padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in kb.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (b in row) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(style.navBar.copy(alpha = 0.9f))
                            .clickable {
                                when (val ty = b.type) {
                                    is InlineKeyboardButtonTypeCallback -> acc.scope.launch {
                                        val r = acc.client.getCallbackQueryAnswer(chatId = m.chatId, messageId = m.id, payload = CallbackQueryPayloadData(data = ty.data))
                                        val ans = (r as? TdlResult.Success)?.result
                                        withContext(Dispatchers.Main) {
                                            if (ans != null) {
                                                if (ans.url.isNotBlank()) openUrl(ctx, ans.url)
                                                if (ans.text.isNotBlank()) {
                                                    if (ans.showAlert) BotAlert.text = ans.text else toast(ctx, ans.text)
                                                }
                                            }
                                        }
                                    }
                                    is InlineKeyboardButtonTypeUrl -> openUrl(ctx, ty.url)
                                    is InlineKeyboardButtonTypeLoginUrl -> openUrl(ctx, ty.url)
                                    is InlineKeyboardButtonTypeWebApp -> acc.scope.launch {
                                        val bot = (m.senderId as? dev.g000sha256.tdl.dto.MessageSenderUser)?.userId ?: m.viaBotUserId
                                        openWebApp(acc, act, m.chatId, bot, ty.url, b.text)
                                    }
                                    is InlineKeyboardButtonTypeSwitchInline -> {
                                        val bot = (m.senderId as? dev.g000sha256.tdl.dto.MessageSenderUser)?.userId ?: m.viaBotUserId
                                        val name = acc.users[bot]?.usernames?.activeUsernames?.firstOrNull() ?: ""
                                        onSwitchInline("@$name ${ty.query}")
                                    }
                                    is InlineKeyboardButtonTypeCopyText -> {
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("bot", ty.text))
                                        toast(ctx, t("Copied"))
                                    }
                                    else -> toast(ctx, t("This button isn't supported yet"))
                                }
                            }.padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(b.text, color = style.navBarFg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun Modifier.widthIn310(): Modifier = this.then(Modifier.width(310.dp))

object BotAlert {
    var text by mutableStateOf<String?>(null)
}

@Composable
fun BotAlertDialog() {
    val txt = BotAlert.text ?: return
    AlertDialog(
        onDismissRequest = { BotAlert.text = null },
        text = { Text(txt) },
        confirmButton = { TextButton(onClick = { BotAlert.text = null }) { Text("OK") } },
    )
}

/** The bot keyboard that replaces the phone keyboard (e.g. a numbered song list). */
@Composable
fun BotKeyboard(acc: TgAccount, act: MainActivity, chatId: Long, m: Message, onSend: (String) -> Unit) {
    val kb = m.replyMarkup as? ReplyMarkupShowKeyboard ?: return
    val style = LocalStyle.current
    Column(
        Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in kb.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (b in row) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(style.navBar)
                            .clickable {
                                val ty = b.type
                                if (ty is KeyboardButtonTypeWebApp) {
                                    val bot = (m.senderId as? dev.g000sha256.tdl.dto.MessageSenderUser)?.userId ?: 0L
                                    acc.scope.launch { openWebApp(acc, act, chatId, bot, ty.url, b.text) }
                                } else onSend(b.text)
                            }.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(b.text, color = style.navBarFg, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Commands for the bot "Menu" button. */
suspend fun botCommands(acc: TgAccount, userId: Long): Pair<List<BotCommand>, dev.g000sha256.tdl.dto.BotMenuButton?> {
    val info = (acc.client.getUserFullInfo(userId = userId) as? TdlResult.Success)?.result?.botInfo ?: return emptyList<BotCommand>() to null
    return info.commands.toList() to info.menuButton
}

fun isBot(acc: TgAccount, userId: Long): Boolean = acc.users[userId]?.type is UserTypeBot

/** Results of "@bot query" typed in the message box. */
@Composable
fun InlineResults(acc: TgAccount, chatId: Long, text: String, onSent: () -> Unit) {
    val ctx = LocalContext.current
    val m = Regex("^@(\\w{4,})\\s(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
    var results by remember { mutableStateOf<Pair<Long, List<InlineQueryResult>>?>(null) }
    LaunchedEffect(text) {
        if (m == null) { results = null; return@LaunchedEffect }
        delay(400)
        val bot = (acc.client.searchPublicChat(username = m.groupValues[1]) as? TdlResult.Success)?.result
        val botId = (bot?.type as? dev.g000sha256.tdl.dto.ChatTypePrivate)?.userId ?: run { results = null; return@LaunchedEffect }
        val r = (acc.client.getInlineQueryResults(botUserId = botId, chatId = chatId, query = m.groupValues[2], offset = "") as? TdlResult.Success)?.result
        results = if (r == null) null else r.inlineQueryId to r.results.toList()
    }
    val res = results ?: return
    if (res.second.isEmpty()) return
    val style = LocalStyle.current
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).background(style.navBar)) {
        items(res.second) { r ->
            val (title, sub, thumb) = describe(r)
            Row(
                Modifier.fillMaxWidth().clickable {
                    acc.scope.launch {
                        val sent = acc.client.sendInlineQueryResultMessage(chatId = chatId, queryId = res.first, resultId = idOf(r), hideViaBot = false)
                        withContext(Dispatchers.Main) { if (sent is TdlResult.Failure) toast(ctx, sent.message) else onSent() }
                    }
                }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val f = thumb?.let { rememberFile(acc, it, autoDownload = true) }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(style.accent.copy(alpha = 0.3f))) {
                    if (f != null && f.local.isDownloadingCompleted) AsyncImage(model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, color = style.navBarFg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotBlank()) Text(sub, color = style.navBarFg.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun idOf(r: InlineQueryResult): String = when (r) {
    is InlineQueryResultArticle -> r.id
    is InlineQueryResultPhoto -> r.id
    is InlineQueryResultAudio -> r.id
    is InlineQueryResultVideo -> r.id
    is InlineQueryResultDocument -> r.id
    is InlineQueryResultAnimation -> r.id
    else -> ""
}

private fun describe(r: InlineQueryResult): Triple<String, String, dev.g000sha256.tdl.dto.File?> = when (r) {
    is InlineQueryResultArticle -> Triple(r.title, r.description, r.thumbnail?.file)
    is InlineQueryResultPhoto -> Triple(r.title.ifBlank { t("Photo") }, r.description, r.photo.sizes.firstOrNull()?.photo)
    is InlineQueryResultAudio -> Triple(r.audio.title.ifBlank { r.audio.fileName }, r.audio.performer, r.audio.albumCoverThumbnail?.file)
    is InlineQueryResultVideo -> Triple(r.title, r.description, r.video.thumbnail?.file)
    is InlineQueryResultDocument -> Triple(r.title, r.description, r.document.thumbnail?.file)
    is InlineQueryResultAnimation -> Triple(r.title.ifBlank { "GIF" }, "", r.animation.thumbnail?.file)
    else -> Triple(t("Result"), "", null)
}

/** A bot's mini app, opened inside Riv Post. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppScreen(req: WebAppRequest, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Ic.Close, null) }
            Text(req.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(req.url)
                    web = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
