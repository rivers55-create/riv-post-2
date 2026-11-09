package com.rivers.rivpost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.BackgroundTypeFill
import dev.g000sha256.tdl.dto.BackgroundTypePattern
import dev.g000sha256.tdl.dto.BackgroundTypeWallpaper
import dev.g000sha256.tdl.dto.BackgroundFillSolid
import dev.g000sha256.tdl.dto.BackgroundFillGradient
import dev.g000sha256.tdl.dto.BackgroundFillFreeformGradient
import dev.g000sha256.tdl.dto.Background
import dev.g000sha256.tdl.dto.EmojiStatus
import dev.g000sha256.tdl.dto.EmojiStatusTypeCustomEmoji
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputChecklist
import dev.g000sha256.tdl.dto.InputChecklistTask
import dev.g000sha256.tdl.dto.InputMessageChecklist
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.Location
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.NotificationSettingsScopeChannelChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopeGroupChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopePrivateChats
import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.ScopeNotificationSettings
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private fun ft(s: String) = FormattedText(text = s, entities = emptyArray<TextEntity>())

private suspend fun <T> TdlResult<T>.note(ctx: android.content.Context, ok: String? = null): T? = when (this) {
    is TdlResult.Success -> { if (ok != null) withContext(Dispatchers.Main) { toast(ctx, ok) }; result }
    is TdlResult.Failure -> { withContext(Dispatchers.Main) { toast(ctx, message) }; null }
}

// ---------------------------------------------------------------- notification defaults (Telegram side)

@Composable
fun NotifDefaultsScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scopes = listOf(t("Private chats") to NotificationSettingsScopePrivateChats(), t("Groups") to NotificationSettingsScopeGroupChats(), t("Channels") to NotificationSettingsScopeChannelChats())
    val values = remember { mutableStateMapOf<Int, ScopeNotificationSettings>() }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { scopes.forEachIndexed { i, (_, sc) -> (acc.client.getScopeNotificationSettings(scope = sc) as? TdlResult.Success)?.result?.let { values[i] = it } } }
    Page(t("Notification defaults"), onBack) {
        Text(t("Saved in your Telegram account, so they apply on all your devices."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        scopes.forEachIndexed { i, (name, sc) ->
            val v = values[i] ?: return@forEachIndexed
            SectionTitle(name)
            fun save(n: ScopeNotificationSettings) { values[i] = n; acc.scope.launch { acc.client.setScopeNotificationSettings(scope = sc, notificationSettings = n).note(ctx) } }
            SwitchRow(t("Notifications"), null, v.muteFor == 0) { on -> save(copy(v, muteFor = if (on) 0 else Int.MAX_VALUE / 2)) }
            SwitchRow(t("Message preview"), null, v.showPreview) { on -> save(copy(v, showPreview = on)) }
            SwitchRow(t("Pinned message alerts"), null, !v.disablePinnedMessageNotifications) { on -> save(copy(v, pinned = !on)) }
            SwitchRow(t("@mention alerts"), null, !v.disableMentionNotifications) { on -> save(copy(v, mentions = !on)) }
        }
        ClickRow(t("Reset all notifications"), t("Removes every chat's custom setting")) {
            acc.scope.launch { acc.client.resetAllNotificationSettings().note(ctx, t("Done")); tick++ }
        }
    }
}

private fun copy(v: ScopeNotificationSettings, muteFor: Int = v.muteFor, showPreview: Boolean = v.showPreview, pinned: Boolean = v.disablePinnedMessageNotifications, mentions: Boolean = v.disableMentionNotifications) =
    ScopeNotificationSettings(
        muteFor = muteFor, soundId = v.soundId, showPreview = showPreview, useDefaultMuteStories = v.useDefaultMuteStories, muteStories = v.muteStories,
        storySoundId = v.storySoundId, showStoryPoster = v.showStoryPoster, disablePinnedMessageNotifications = pinned, disableMentionNotifications = mentions,
    )

// ---------------------------------------------------------------- languages

@Composable
fun LanguageScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var packs by remember { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    var busy by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        packs = (acc.client.getLocalizationTargetInfo(onlyLocal = false) as? TdlResult.Success)?.result?.languagePacks
            ?.filter { it.isOfficial && it.baseLanguagePackId.isEmpty() }?.map { Triple(it.id, it.name, it.nativeName) }?.sortedBy { it.second }.orEmpty()
    }
    Page(t("Language"), onBack) {
        Text(
            t("English and Arabic are built in. Other languages are translated once with Telegram's translator, then saved on your phone. Some words may be off."),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
        )
        if (busy.isNotEmpty()) {
            Text(t("Translating…") + " " + (progress * 100).toInt() + "%", modifier = Modifier.padding(horizontal = 20.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(20.dp))
        }
        for ((code, name) in listOf("system" to t("Phone language"), "en" to "English", "ar" to "العربية")) {
            ClickRow(name, if (Prefs.lang == code) "✓" else null) { Prefs.lang = code; Lang.update() }
        }
        val l = packs
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else for ((id, name, native) in l) {
            if (id == "en" || id == "ar") continue
            val have = Lang.file(id).exists()
            ClickRow("$native ($name)", if (Prefs.lang == id) "✓" else if (have) t("Saved") else null) {
                if (busy.isNotEmpty()) return@ClickRow
                if (have) { Prefs.lang = id; Lang.update(); return@ClickRow }
                busy = id; progress = 0f
                acc.scope.launch {
                    val keys = Lang.allKeys.toList()
                    val out = java.util.concurrent.ConcurrentHashMap<String, String>()
                    val sem = Semaphore(4)
                    var done = 0
                    var failed = ""
                    coroutineScope {
                        keys.chunked(25).map { chunk ->
                            async {
                                sem.withPermit {
                                    val joined = chunk.joinToString("\n") { it.replace("\n", " ") }
                                    when (val r = acc.client.translateText(text = ft(joined), toLanguageCode = id, tone = "")) {
                                        is TdlResult.Success -> {
                                            val lines = r.result.text.split("\n")
                                            if (lines.size == chunk.size) chunk.forEachIndexed { i, k -> out[k] = lines[i] }
                                        }
                                        is TdlResult.Failure -> failed = r.message
                                    }
                                    done += chunk.size
                                    progress = done.toFloat() / keys.size
                                }
                            }
                        }.awaitAll()
                    }
                    withContext(Dispatchers.Main) {
                        busy = ""
                        if (out.size < keys.size / 3) toast(ctx, t("Couldn't translate") + if (failed.isNotBlank()) ": $failed" else "")
                        else { Lang.save(id, out); Prefs.lang = id; Lang.update() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Telegram wallpapers

@Composable
fun WallpapersScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Background>?>(null) }
    var applying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        list = (acc.client.getInstalledBackgrounds(forDarkTheme = true) as? TdlResult.Success)?.result?.backgrounds?.toList().orEmpty() +
            (acc.client.getInstalledBackgrounds(forDarkTheme = false) as? TdlResult.Success)?.result?.backgrounds?.toList().orEmpty()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null) }
            Text(t("Telegram wallpapers"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (applying) LinearProgressIndicator(Modifier.fillMaxWidth())
        val l = list?.distinctBy { it.id }
        if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
            items(l.size) { i ->
                val b = l[i]
                Box(Modifier.padding(3.dp).aspectRatio(0.62f).clip(RoundedCornerShape(10.dp)).clickable {
                    applying = true
                    acc.scope.launch {
                        val path = renderBackground(acc, ctx, b)
                        withContext(Dispatchers.Main) {
                            applying = false
                            if (path == null) toast(ctx, t("Couldn't load this wallpaper"))
                            else { Prefs.wallpaper.takeIf { it.isNotEmpty() && it.contains("tgwall_") && it != path }?.let { java.io.File(it).delete() }; Prefs.wallpaper = path; toast(ctx, t("Wallpaper set")); onBack() }
                        }
                    }
                }) { BackgroundThumb(acc, b) }
            }
        }
    }
}

private fun fillColors(f: Any): List<Int> = when (f) {
    is BackgroundFillSolid -> listOf(f.color, f.color)
    is BackgroundFillGradient -> listOf(f.topColor, f.bottomColor)
    is BackgroundFillFreeformGradient -> f.colors.toList()
    else -> listOf(0x333333, 0x111111)
}

@Composable
private fun BackgroundThumb(acc: TgAccount, b: Background) {
    val type = b.type
    val colors = when (type) { is BackgroundTypePattern -> fillColors(type.fill); is BackgroundTypeFill -> fillColors(type.fill); else -> emptyList() }
    val th = rememberFile(acc, b.document?.thumbnail?.file ?: b.document?.document, autoDownload = true)
    Box(Modifier.fillMaxSize().background(
        if (colors.size >= 2) androidx.compose.ui.graphics.Brush.verticalGradient(colors.map { Color(0xFF000000.toInt() or it) })
        else androidx.compose.ui.graphics.SolidColor(Color(0xFF222222)),
    )) {
        if (th != null && th.local.isDownloadingCompleted) AsyncImage(
            model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (type is BackgroundTypePattern) 0.35f else 1f },
        )
    }
}

/** Makes a picture file from a Telegram background (photo, or colors + pattern). */
private suspend fun renderBackground(acc: TgAccount, ctx: android.content.Context, b: Background): String? = withContext(Dispatchers.IO) {
    try {
        val w = 1080; val h = 2160
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val type = b.type
        val colors = when (type) { is BackgroundTypePattern -> fillColors(type.fill); is BackgroundTypeFill -> fillColors(type.fill); else -> emptyList() }
        if (colors.isNotEmpty()) {
            val argb = colors.map { 0xFF000000.toInt() or it }.toIntArray()
            val p = android.graphics.Paint()
            p.shader = if (argb.size >= 2) android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), argb, null, android.graphics.Shader.TileMode.CLAMP)
            else null
            if (argb.size < 2) c.drawColor(argb[0]) else c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        val doc = b.document?.document
        if (doc != null) {
            val f = acc.downloadSync(doc.id, 32)
            val path = f?.local?.path
            if (path != null && java.io.File(path).exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    if (type is BackgroundTypePattern) {
                        p.alpha = (kotlin.math.abs(type.intensity) * 255 / 100).coerceIn(30, 255)
                        p.colorFilter = android.graphics.PorterDuffColorFilter(if (type.intensity < 0) android.graphics.Color.BLACK else 0x55000000, android.graphics.PorterDuff.Mode.SRC_IN)
                        // tile the pattern
                        val tile = android.graphics.Bitmap.createScaledBitmap(bmp, w, (bmp.height * w.toFloat() / bmp.width).toInt().coerceAtLeast(1), true)
                        var y = 0
                        while (y < h) { c.drawBitmap(tile, 0f, y.toFloat(), p); y += tile.height }
                    } else {
                        val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                        val m = android.graphics.Matrix().apply { postScale(scale, scale); postTranslate((w - bmp.width * scale) / 2, (h - bmp.height * scale) / 2) }
                        c.drawBitmap(bmp, m, p)
                    }
                }
            }
        } else if (colors.isEmpty()) return@withContext null
        val file = java.io.File(ctx.filesDir, "tgwall_" + b.id + ".jpg")
        file.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    } catch (_: Throwable) { null }
}

// ---------------------------------------------------------------- emoji status

@Composable
fun EmojiStatusScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stickers by remember { mutableStateOf<List<Sticker>?>(null) }
    LaunchedEffect(Unit) {
        val ids = ((acc.client.getDefaultEmojiStatuses() as? TdlResult.Success)?.result?.customEmojiIds?.toList().orEmpty() +
            (acc.client.getThemedEmojiStatuses() as? TdlResult.Success)?.result?.customEmojiIds?.toList().orEmpty()).distinct().take(200)
        stickers = (acc.client.getCustomEmojiStickers(customEmojiIds = ids.toLongArray()) as? TdlResult.Success)?.result?.stickers?.toList().orEmpty()
    }
    Page(t("Emoji status"), onBack) {
        Text(t("Shown next to your name. Telegram only allows this with Premium."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        TextButton(onClick = { acc.scope.launch { acc.client.setEmojiStatus(emojiStatus = null).note(ctx, t("Removed")) } }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(t("Remove status")) }
        val l = stickers
        if (l == null) CircularProgressIndicator(Modifier.padding(20.dp))
        else for (row in l.chunked(6)) {
            Row(Modifier.padding(horizontal = 12.dp)) {
                for (s in row) {
                    val id = (s.fullType as? dev.g000sha256.tdl.dto.StickerFullTypeCustomEmoji)?.customEmojiId ?: 0L
                    Box(Modifier.size(52.dp).padding(4.dp).clickable {
                        acc.scope.launch { acc.client.setEmojiStatus(emojiStatus = EmojiStatus(type = EmojiStatusTypeCustomEmoji(customEmojiId = id), expirationDate = 0)).note(ctx, t("Saved")) }
                    }) { StickerImage(acc, s, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Instant View

object IvNav { var url by mutableStateOf("") }

@Composable
fun InstantViewScreen(acc: TgAccount, act: MainActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    val url = IvNav.url
    var blocks by remember { mutableStateOf<List<PageBlock>?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(url) {
        when (val r = acc.client.getWebPageInstantView(url = url, onlyLocal = false)) {
            is TdlResult.Success -> blocks = r.result.blocks.toList()
            is TdlResult.Failure -> { err = r.message; blocks = emptyList() }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null) }
            Text("⚡ " + url.removePrefix("https://").substringBefore('/'), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
            }) { Icon(Ic.Link, null) }
        }
        val l = blocks
        if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (l.isEmpty()) Text(err.ifBlank { t("No Instant View for this page") }, modifier = Modifier.padding(20.dp))
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(l) { b -> IvBlock(acc, b) }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

/** Plain text of a Telegram rich text, read generically. */
private fun rich(o: Any?): String {
    if (o == null) return ""
    if (o is String) return o
    if (o is Array<*>) return o.joinToString("") { rich(it) }
    val cls = o.javaClass
    for (name in listOf("texts", "text")) {
        try { val f = cls.getDeclaredField(name); f.isAccessible = true; return rich(f.get(o)) } catch (_: NoSuchFieldException) { }
    }
    return ""
}

private fun field(o: Any, name: String): Any? = try { o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o) } } catch (_: Throwable) { null }

@Composable
private fun IvBlock(acc: TgAccount, b: Any) {
    val n = b.javaClass.simpleName.removePrefix("PageBlock")
    when (n) {
        "Title" -> Text(rich(field(b, "title")), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        "Subtitle" -> Text(rich(field(b, "subtitle")), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        "Header" -> Text(rich(field(b, "header")), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        "Subheader" -> Text(rich(field(b, "subheader")), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        "Kicker" -> Text(rich(field(b, "kicker")).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        "Paragraph" -> Text(rich(field(b, "text")), fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 5.dp))
        "Preformatted" -> Text(rich(field(b, "text")), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color(0x22000000)).padding(8.dp))
        "Footer" -> Text(rich(field(b, "footer")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        "AuthorDate" -> Text(rich(field(b, "author")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        "BlockQuote", "PullQuote" -> Text("“" + rich(field(b, "text")) + "”", fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(12.dp))
        "Divider" -> androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 10.dp))
        "List" -> Column {
            (field(b, "items") as? Array<*>)?.forEach { item ->
                val label = rich(field(item!!, "label"))
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(label.ifBlank { "•" } + " ", fontSize = 16.sp)
                    Column { (field(item, "pageBlocks") as? Array<*>)?.forEach { IvBlock(acc, it!!) } }
                }
            }
        }
        "Photo" -> {
            val photo = field(b, "photo") as? dev.g000sha256.tdl.dto.Photo
            if (photo != null) {
                val best = photo.sizes.filter { maxOf(it.width, it.height) <= 1280 }.maxByOrNull { it.width } ?: photo.sizes.lastOrNull()
                val f = rememberFile(acc, best?.photo, autoDownload = true)
                if (f != null && f.local.isDownloadingCompleted) AsyncImage(model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)))
                else Box(Modifier.fillMaxWidth().height(160.dp).background(Color(0x22000000)))
            }
            val cap = field(b, "caption")?.let { rich(field(it, "text")) } ?: ""
            if (cap.isNotBlank()) Text(cap, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "Cover" -> (field(b, "cover"))?.let { IvBlock(acc, it) }
        "Details" -> Column {
            Text("▸ " + rich(field(b, "header")), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
            (field(b, "pageBlocks") as? Array<*>)?.forEach { IvBlock(acc, it!!) }
        }
        "Collage", "Slideshow" -> (field(b, "pageBlocks") as? Array<*>)?.forEach { IvBlock(acc, it!!) }
        else -> {
            val txt = rich(field(b, "text"))
            if (txt.isNotBlank()) Text(txt, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- map preview

@Composable
fun MapThumb(acc: TgAccount, lat: Double, lon: Double, chatId: Long) {
    if (!Prefs.mapPreviews) return
    var path by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lat, lon) {
        val f = (acc.client.getMapThumbnailFile(location = Location(latitude = lat, longitude = lon, horizontalAccuracy = 0.0), zoom = 15, width = 300, height = 150, scale = 2, chatId = chatId) as? TdlResult.Success)?.result
        if (f != null) path = acc.downloadSync(f.id, 8)?.local?.path
    }
    val p = path
    Box(Modifier.width(260.dp).height(130.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22000000)), contentAlignment = Alignment.Center) {
        if (p != null) AsyncImage(model = java.io.File(p), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Text("📍", fontSize = 28.sp)
    }
}

// ---------------------------------------------------------------- quote reply

@Composable
fun QuoteDialog(original: String, onDismiss: () -> Unit, onQuote: (String, Int) -> Unit) {
    var q by remember { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Quote part of the message")) },
        text = { Column { Text(t("Delete what you don't want to quote."), fontSize = 13.sp); OutlinedTextField(q, { q = it }, modifier = Modifier.heightIn(max = 300.dp)) } },
        confirmButton = {
            TextButton(onClick = {
                val part = q.trim()
                val pos = original.indexOf(part)
                if (part.isNotEmpty() && pos >= 0) onQuote(part, pos) else onDismiss()
            }) { Text(t("Reply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- translations (auto-translate chat, swipe-translate)

object Translations {
    val cache = mutableStateMapOf<String, String>()
    val forced = mutableStateListOf<Long>()
    private val busy = HashSet<String>()

    fun key(m: Message) = "${m.chatId}:${m.id}:" + (if (Lang.arabic) "ar" else Prefs.lang.takeIf { it.length == 2 } ?: "en")

    fun request(acc: TgAccount, m: Message, text: String) {
        val k = key(m)
        if (cache.containsKey(k) || !busy.add(k)) return
        val to = if (Lang.arabic) "ar" else Prefs.lang.takeIf { it.length == 2 } ?: "en"
        acc.scope.launch {
            val r = acc.client.translateMessageText(chatId = m.chatId, messageId = m.id, toLanguageCode = to, tone = "")
            val out = (r as? TdlResult.Success)?.result?.text
                ?: (acc.client.translateText(text = ft(text), toLanguageCode = to, tone = "") as? TdlResult.Success)?.result?.text
            withContext(Dispatchers.Main) { if (out != null && out != text) cache[k] = out; busy.remove(k) }
        }
    }
}

@Composable
fun TranslatedLine(acc: TgAccount, m: Message, text: String, fg: Color, force: Boolean) {
    val auto = m.chatId in Prefs.idSet(Prefs.autoTranslateChats)
    if (!auto && !force) return
    if (text.isBlank()) return
    LaunchedEffect(m.id) { Translations.request(acc, m, text) }
    val tr = Translations.cache[Translations.key(m)] ?: return
    Text("🌐 $tr", color = fg.copy(alpha = 0.8f), fontSize = (Prefs.textSize - 1).sp, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp))
}

// ---------------------------------------------------------------- checklists

@Composable
fun ChecklistView(acc: TgAccount, m: Message, c: MessageChecklist, fg: Color, meta: Color) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    Column(Modifier.widthIn(min = 220.dp, max = 300.dp).padding(8.dp)) {
        Text("☑ " + c.list.title.text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        for (task in c.list.tasks) {
            val done = task.completedBy != null
            Row(
                Modifier.fillMaxWidth().clickable(enabled = c.list.canMarkTasksAsDone) {
                    acc.scope.launch {
                        acc.client.markChecklistTasksAsDone(
                            chatId = m.chatId, messageId = m.id,
                            markedAsDoneTaskIds = if (done) IntArray(0) else intArrayOf(task.id),
                            markedAsNotDoneTaskIds = if (done) intArrayOf(task.id) else IntArray(0),
                        ).note(ctx)
                    }
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (done) style.accent else meta.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    if (done) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(task.text.text, color = fg, textDecoration = if (done) TextDecoration.LineThrough else null)
                    task.completedBy?.let { Text(acc.senderName(it), color = meta, fontSize = 11.sp) }
                }
            }
        }
        if (!c.list.canMarkTasksAsDone) Text(t("Only some people can tick items"), color = meta, fontSize = 11.sp)
    }
}

@Composable
fun CreateChecklistDialog(onDismiss: () -> Unit, onSend: (InputMessageContent) -> Unit) {
    var title by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf("", "") }
    var othersMark by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("New checklist")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(t("Title")) }, singleLine = true) }
                items(tasks.size) { i ->
                    OutlinedTextField(tasks[i], { v -> tasks[i] = v; if (i == tasks.lastIndex && v.isNotBlank() && tasks.size < 30) tasks.add("") },
                        label = { Text(t("Item") + " ${i + 1}") }, singleLine = true)
                }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(othersMark, { othersMark = it }); Text(t("Others can tick items")) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val items = tasks.map { it.trim() }.filter { it.isNotEmpty() }
                if (title.isBlank() || items.isEmpty()) return@TextButton
                onSend(InputMessageChecklist(checklist = InputChecklist(
                    title = ft(title.trim()), tasks = items.mapIndexed { i, s -> InputChecklistTask(id = i + 1, text = ft(s)) }.toTypedArray(),
                    othersCanAddTasks = false, othersCanMarkTasksAsDone = othersMark,
                )))
                onDismiss()
            }) { Text(t("Send")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- big reaction burst

@Composable
fun ReactionBurst(emoji: String, onDone: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(emoji) {
        launch { scale.animateTo(if (Prefs.reduceMotion) 1.5f else 3.2f, tween(if (Prefs.reduceMotion) 200 else 650)) }
        alpha.animateTo(0f, tween(if (Prefs.reduceMotion) 300 else 800))
        onDone()
    }
    Text(emoji, fontSize = 40.sp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value })
}

// ---------------------------------------------------------------- own formatting (live while typing)

/** **bold** __italic__ ++underline++ ~~strike~~ ||spoiler|| `code` ```pre``` [text](url) */
object Fmt2 {
    private data class Mark(val open: String, val close: String, val kind: String)
    private val marks = listOf(
        Mark("```", "```", "pre"), Mark("**", "**", "b"), Mark("__", "__", "i"), Mark("++", "++", "u"),
        Mark("~~", "~~", "s"), Mark("||", "||", "sp"), Mark("`", "`", "code"),
    )
    private val link = Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)")

    fun hasMarks(s: String) = marks.any { s.contains(it.open) } || link.containsMatchIn(s)

    /** Turns marked-up text into Telegram text with formatting. */
    fun parse(src: String): FormattedText {
        val out = StringBuilder()
        val ents = ArrayList<TextEntity>()
        var i = 0
        while (i < src.length) {
            val lm = link.find(src, i)?.takeIf { it.range.first == i }
            if (lm != null) {
                val start = out.length
                out.append(lm.groupValues[1])
                ents.add(TextEntity(offset = start, length = lm.groupValues[1].length, type = TextEntityTypeTextUrl(url = lm.groupValues[2])))
                i = lm.range.last + 1
                continue
            }
            val m = marks.firstOrNull { src.startsWith(it.open, i) }
            if (m != null) {
                val end = src.indexOf(m.close, i + m.open.length)
                if (end > i + m.open.length) {
                    val inner = src.substring(i + m.open.length, end)
                    val start = out.length
                    val nested = if (m.kind == "pre" || m.kind == "code") FormattedText(inner, emptyArray()) else parse(inner)
                    out.append(nested.text)
                    nested.entities.forEach { e -> ents.add(TextEntity(offset = start + e.offset, length = e.length, type = e.type)) }
                    val type = when (m.kind) {
                        "b" -> TextEntityTypeBold(); "i" -> TextEntityTypeItalic(); "u" -> TextEntityTypeUnderline()
                        "s" -> TextEntityTypeStrikethrough(); "sp" -> TextEntityTypeSpoiler(); "code" -> TextEntityTypeCode()
                        else -> TextEntityTypePre()
                    }
                    ents.add(TextEntity(offset = start, length = nested.text.length, type = type))
                    i = end + m.close.length
                    continue
                }
            }
            out.append(src[i]); i++
        }
        return FormattedText(text = out.toString(), entities = ents.sortedBy { it.offset }.toTypedArray())
    }

    /** Shows formatting while typing; the marks stay but fade. */
    fun visual(accent: Color): VisualTransformation = VisualTransformation { text ->
        val s = text.text
        val b = androidx.compose.ui.text.AnnotatedString.Builder(s)
        val faint = SpanStyle(color = accent.copy(alpha = 0.45f))
        for (m in marks) {
            var from = 0
            while (true) {
                val a = s.indexOf(m.open, from); if (a < 0) break
                val e = s.indexOf(m.close, a + m.open.length); if (e < 0) break
                val style = when (m.kind) {
                    "b" -> SpanStyle(fontWeight = FontWeight.Bold); "i" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "u" -> SpanStyle(textDecoration = TextDecoration.Underline); "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "sp" -> SpanStyle(background = Color(0x55888888)); else -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22000000))
                }
                b.addStyle(style, a + m.open.length, e)
                b.addStyle(faint, a, a + m.open.length)
                b.addStyle(faint, e, e + m.close.length)
                from = e + m.close.length
            }
        }
        TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    /** Wraps the selected part with a mark. */
    fun wrap(v: androidx.compose.ui.text.input.TextFieldValue, kind: String, url: String = ""): androidx.compose.ui.text.input.TextFieldValue {
        val sel = v.selection
        if (sel.collapsed) return v
        val a = minOf(sel.start, sel.end); val e = maxOf(sel.start, sel.end)
        val inner = v.text.substring(a, e)
        val wrapped = when (kind) {
            "b" -> "**$inner**"; "i" -> "__${inner}__"; "u" -> "++$inner++"; "s" -> "~~$inner~~"; "sp" -> "||$inner||"; "code" -> "`$inner`"
            "link" -> "[$inner]($url)"; else -> inner
        }
        val t2 = v.text.substring(0, a) + wrapped + v.text.substring(e)
        return androidx.compose.ui.text.input.TextFieldValue(t2, TextRange(a + wrapped.length))
    }
}

// ---------------------------------------------------------------- code colors & simple math

object CodeColors {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "if", "else", "when", "for", "while", "return", "import", "package", "def", "function", "let", "const",
        "public", "private", "static", "void", "int", "string", "bool", "true", "false", "null", "None", "True", "False", "new", "try", "catch", "finally",
        "from", "as", "in", "is", "not", "and", "or", "async", "await", "struct", "enum", "interface", "extends", "implements", "self", "this", "lambda", "print",
    )
    private val token = Regex("(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|(//[^\\n]*|#[^\\n]*)|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)")

    fun apply(b: androidx.compose.ui.text.AnnotatedString.Builder, text: String, start: Int, end: Int) {
        val part = text.substring(start, end)
        for (m in token.findAll(part)) {
            val a = start + m.range.first; val e = start + m.range.last + 1
            when {
                m.groups[1] != null -> b.addStyle(SpanStyle(color = Color(0xFF98C379)), a, e)
                m.groups[2] != null -> b.addStyle(SpanStyle(color = Color(0xFF7F848E), fontStyle = FontStyle.Italic), a, e)
                m.groups[3] != null -> b.addStyle(SpanStyle(color = Color(0xFFD19A66)), a, e)
                m.groups[4] != null && m.value in keywords -> b.addStyle(SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.SemiBold), a, e)
            }
        }
    }
}

object SimpleMath {
    private val sym = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\pi" to "π", "\\sigma" to "σ", "\\phi" to "φ", "\\omega" to "ω", "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\infty" to "∞",
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±", "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\approx" to "≈",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\partial" to "∂", "\\nabla" to "∇", "\\rightarrow" to "→", "\\to" to "→", "\\in" to "∈",
        "\\forall" to "∀", "\\exists" to "∃", "\\sqrt" to "√", "\\degree" to "°", "\\le" to "≤", "\\ge" to "≥",
    )
    private val sup = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ')
    private val sub = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', 'i' to 'ᵢ', 'n' to 'ₙ', 'x' to 'ₓ')
    private val inline = Regex("\\$\\$(.+?)\\$\\$|\\$([^$\\n]+?)\\$")

    fun has(s: String) = Prefs.mathFormulas && inline.containsMatchIn(s) && s.contains('\\') || Prefs.mathFormulas && Regex("\\$[^$]*[\\^_][^$]*\\$").containsMatchIn(s)

    fun render(s: String): String = inline.replace(s) { m -> convert(m.groupValues[1].ifEmpty { m.groupValues[2] }) }

    private fun convert(src: String): String {
        var s = src
        s = Regex("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}").replace(s) { "(${it.groupValues[1]})⁄(${it.groupValues[2]})" }
        s = Regex("\\\\sqrt\\{([^}]*)\\}").replace(s) { "√(${it.groupValues[1]})" }
        for ((k, v) in sym.entries.sortedByDescending { it.key.length }) s = s.replace(k, v)
        s = Regex("\\^\\{([^}]*)\\}|\\^(.)").replace(s) { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).map { sup[it] ?: it }.joinToString("") }
        s = Regex("_\\{([^}]*)\\}|_(.)").replace(s) { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).map { sub[it] ?: it }.joinToString("") }
        return s.replace("{", "").replace("}", "").replace("\\", "")
    }
}

// ---------------------------------------------------------------- custom emoji

object CustomEmoji {
    val stickers = mutableStateMapOf<Long, Sticker>()
    private val asked = HashSet<Long>()
    fun want(acc: TgAccount, ids: List<Long>) {
        val need = ids.filter { it !in stickers && asked.add(it) }
        if (need.isEmpty()) return
        acc.scope.launch {
            val r = (acc.client.getCustomEmojiStickers(customEmojiIds = need.toLongArray()) as? TdlResult.Success)?.result?.stickers ?: return@launch
            withContext(Dispatchers.Main) {
                for (s in r) (s.fullType as? dev.g000sha256.tdl.dto.StickerFullTypeCustomEmoji)?.customEmojiId?.let { stickers[it] = s }
            }
        }
    }
}

/** Only emoji (up to 3), no other text. */
fun isEmojiOnly(s: String): Boolean {
    val t2 = s.trim()
    if (t2.isEmpty() || t2.length > 24) return false
    if (t2.any { it.isLetterOrDigit() && Character.getType(it) != Character.OTHER_SYMBOL.toInt() }) return false
    val it = java.text.BreakIterator.getCharacterInstance(); it.setText(t2)
    var n = 0; var p = it.first()
    while (it.next().also { p = it } != java.text.BreakIterator.DONE) n++
    return n in 1..3 && t2.none { c -> c.isWhitespace() && false }
}
