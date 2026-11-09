package com.rivers.rivpost.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.music.Lyrics
import com.rivers.rivpost.music.LyricsRepo
import com.rivers.rivpost.music.Music
import com.rivers.rivpost.music.MusicLib
import com.rivers.rivpost.music.Track
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberMusicPosition(): Long {
    var pos by remember { mutableLongStateOf(Music.position) }
    LaunchedEffect(Music.current?.key) {
        while (true) { pos = Music.position; delay(250) }
    }
    return pos
}

@Composable
fun TrackArt(t: Track, modifier: Modifier, shape: Shape, iconSize: Dp = 24.dp) {
    val acc = Tg.accounts.value[t.acc]
    val f = if (acc != null) rememberFile(acc, t.cover, autoDownload = true, priority = 4) else null
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), MaterialTheme.colorScheme.surfaceVariant))
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (f != null && f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
            AsyncImage(model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Ic.Note, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Color from the cover picture, used for the player background. */
@Composable
fun coverColor(t: Track?): Color? {
    val acc = t?.let { Tg.accounts.value[it.acc] }
    val f = if (acc != null && t != null) rememberFile(acc, t.cover, autoDownload = true) else null
    val path = f?.local?.path?.takeIf { f.local.isDownloadingCompleted && it.isNotEmpty() }
    val c by produceState<Color?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeFile(path) ?: return@withContext null
                val p = Palette.from(bmp).generate()
                val sw = p.vibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
                sw?.let { Color(it.rgb) }
            } catch (_: Throwable) { null }
        }
    }
    return c
}

@Composable
fun MiniPlayer(modifier: Modifier = Modifier, compact: Boolean = false) {
    val s = Music.current ?: return
    if (!Prefs.mMini) return
    if (!compact && Prefs.miniStyle == "card") { MusicCard(s, modifier); return }
    val cs = MaterialTheme.colorScheme
    val style = LocalStyle.current
    val pos = rememberMusicPosition()
    val dur = Music.duration
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp))
            .background(style.navBar)
            .clickable { Music.playerOpen = true }
            .pointerInput(s.key) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag < -120) Music.next() else if (drag > 120) Music.previous()
                    drag = 0f
                }) { _, d -> drag += d }
            },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArt(s, Modifier.size(46.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = style.navBarFg)
                Text(s.artist, fontSize = 13.sp, color = style.navBarFg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (Prefs.mFormat && Music.format.isNotEmpty()) Text(Music.format, fontSize = 10.sp, color = cs.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(cs.primary).clickable { Music.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (Music.loading) CircularProgressIndicator(Modifier.size(24.dp), color = cs.onPrimary, strokeWidth = 2.dp)
                else Icon(if (Music.isPlaying) Ic.Pause else Ic.Play, null, tint = cs.onPrimary)
            }
            IconButton(onClick = { Music.next() }) { Icon(Ic.SkipNext, null, tint = style.navBarFg) }
            IconButton(onClick = { Music.stop() }, modifier = Modifier.size(36.dp)) { Icon(Ic.Close, null, Modifier.size(18.dp), tint = style.navBarFg.copy(alpha = 0.6f)) }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(3.dp).clip(CircleShape).background(cs.outlineVariant)) {
            Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(cs.primary))
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun SkipIcon(back: Boolean, secs: Int, tint: Color) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Ic.Replay, null, tint = tint, modifier = Modifier.size(30.dp).graphicsLayer { if (!back) scaleX = -1f })
        Text("$secs", color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Full-screen player, same layout as Riv Tunes Player. */
@Composable
fun NowPlayingScreen() {
    val s = Music.current
    if (s == null) { LaunchedEffect(Unit) { Music.playerOpen = false }; return }
    BackHandler { Music.playerOpen = false }
    val cs = MaterialTheme.colorScheme
    val tint = coverColor(s) ?: cs.primary
    val anim by animateColorAsState(tint, tween(600), label = "bg")
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Music.updateOutput() }
    Box(Modifier.fillMaxSize().background(cs.background)) {
        when {
            Prefs.mBg == "blur" && Build.VERSION.SDK_INT >= 31 -> {
                TrackArt(s, Modifier.fillMaxSize().scale(1.3f).blur(70.dp), RoundedCornerShape(0.dp))
                Box(Modifier.fillMaxSize().background(cs.background.copy(alpha = 0.62f)))
            }
            Prefs.mBg == "solid" -> {}
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(lerp(anim, Color.Black, 0.55f), lerp(anim, cs.background, 0.85f), cs.background))
                )
            )
        }
        var dragY by remember { mutableFloatStateOf(0f) }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { if (dragY > 150) Music.playerOpen = false; dragY = 0f }) { _, d -> dragY += d }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chip = cs.surfaceContainerHigh.copy(alpha = 0.7f)
                IconButton(onClick = { Music.playerOpen = false }, modifier = Modifier.background(chip, CircleShape)) {
                    Icon(Ic.Down, null, Modifier.size(30.dp))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) { PageChips() }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.background(chip, CircleShape)) { Icon(Ic.More, null) }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t("Go to message")) }, onClick = {
                            menu = false
                            Music.playerOpen = false
                            MusicNav.openChat?.invoke(s.acc, s.chatId)
                        })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t("Stop and close")) }, onClick = { menu = false; Music.stop() })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (if (Music.page == 1 && !Prefs.mLyrics) 0 else Music.page) {
                    1 -> LyricsPage(s)
                    2 -> QueuePage()
                    else -> CoverPage(s)
                }
            }
            Controls(s)
        }
    }
    if (MusicNav.sleep) SleepDialog { MusicNav.sleep = false }
    if (MusicNav.speed) SpeedDialog { MusicNav.speed = false }
    if (MusicNav.eq) EqDialog { MusicNav.eq = false }
    Music.error?.let { e -> LaunchedEffect(e) { toast(ctx, t(e)); Music.error = null } }
}

/** Small UI state for the player (dialogs, chat opener). */
object MusicNav {
    var sleep by mutableStateOf(false)
    var speed by mutableStateOf(false)
    var eq by mutableStateOf(false)
    var openChat: ((Int, Long) -> Unit)? = null
    var openPage: ((String) -> Unit)? = null
}

/** Music library tabs, in the user's order. */
object MusicTabs {
    val all = listOf("all", "selected", "playlists", "albums", "artists", "favorites")
    fun label(k: String) = when (k) {
        "all" -> t("All chats"); "selected" -> t("Selected chats"); "playlists" -> t("Playlists")
        "albums" -> t("Albums"); "artists" -> t("Artists"); else -> t("Favorites")
    }
    fun order(): List<String> {
        val saved = Prefs.musicTabs.split(',').map { it.trim() }.filter { it in all }
        return (saved + all.filter { it !in saved }).distinct()
    }
    fun visible(): List<String> {
        val off = Prefs.musicTabsOff.split(',').toSet()
        return order().filter { it !in off }.ifEmpty { listOf("all") }
    }
    fun move(k: String, by: Int) {
        val l = order().toMutableList()
        val i = l.indexOf(k); val j = (i + by).coerceIn(0, l.lastIndex)
        if (i < 0 || i == j) return
        l.removeAt(i); l.add(j, k)
        Prefs.musicTabs = l.joinToString(",")
    }
    fun toggle(k: String) {
        val off = Prefs.musicTabsOff.split(',').filter { it.isNotBlank() }.toMutableSet()
        if (!off.remove(k)) off.add(k)
        Prefs.musicTabsOff = off.joinToString(",")
    }
}

@Composable
private fun PageChips() {
    val cs = MaterialTheme.colorScheme
    val pages = buildList {
        add(0 to t("Playing"))
        if (Prefs.mLyrics) add(1 to t("Lyrics"))
        add(2 to t("Queue"))
    }
    Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = 0.7f)).padding(4.dp)) {
        for ((id, name) in pages) {
            val sel = Music.page == id
            Text(
                name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (sel) cs.onPrimary else cs.onSurface,
                modifier = Modifier.clip(CircleShape).background(if (sel) cs.primary else Color.Transparent)
                    .clickable { Music.page = id }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CoverPage(s: Track) {
    var drag by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(if (Music.isPlaying) 1f else 0.9f, label = "art")
    val glow = coverColor(s)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (Prefs.playerGlow && glow != null) Box(
            Modifier.fillMaxWidth(0.95f).aspectRatio(1f).scale(scale * 1.05f)
                .background(Brush.radialGradient(listOf(glow.copy(alpha = if (Music.isPlaying) 0.55f else 0.3f), Color.Transparent))),
        )
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TrackArt(
            s,
            Modifier.padding(horizontal = 28.dp).widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f).scale(scale)
                .pointerInput(s.key) {
                    detectHorizontalDragGestures(onDragEnd = {
                        if (drag < -150) Music.next() else if (drag > 150) Music.previous()
                        drag = 0f
                    }) { _, d -> drag += d }
                },
            RoundedCornerShape(36.dp), 96.dp,
        )
    }
}

@Composable
private fun Controls(s: Track) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val fg = LocalContentColor.current
    val accent = cs.primary
    val dim = fg.copy(alpha = 0.65f)
    val pos = rememberMusicPosition()
    val dur = Music.duration
    var seeking by remember { mutableStateOf<Float?>(null) }
    val skip = Prefs.mSkipButtons
    val secs = Prefs.mSkipSecs
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(s.artist, fontSize = 19.sp, color = accent, maxLines = 1, modifier = Modifier.basicMarquee())
                if (Prefs.mFormat && Music.format.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(Music.format, fontSize = 13.sp, color = Color(0xFFFFC14D), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            val fav = Music.isFavorite(s)
            IconButton(onClick = { Music.toggleFavorite(s) }) {
                Icon(if (fav) Ic.Heart else Ic.HeartOutline, null, tint = if (fav) accent else fg, modifier = Modifier.size(30.dp))
            }
        }
        val prog = seeking ?: (if (dur > 0) pos.toFloat() / dur else 0f).coerceIn(0f, 1f)
        if (Prefs.mWavy) WavySeekBar(
            prog, Music.isPlaying, accent, cs.surfaceContainerHighest,
            onSeek = { seeking = it },
            onSeekEnd = { seeking?.let { Music.seekTo((it * dur).toLong()) }; seeking = null },
        ) else Slider(
            value = prog, onValueChange = { seeking = it },
            onValueChangeFinished = { seeking?.let { Music.seekTo((it * dur).toLong()) }; seeking = null },
        )
        Row {
            Text(Fmt.duration(((seeking?.let { (it * dur).toLong() } ?: pos) / 1000).toInt()), fontSize = 13.sp, color = dim)
            Spacer(Modifier.weight(1f))
            Text(Fmt.duration((dur / 1000).toInt()), fontSize = 13.sp, color = dim)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (skip) IconButton(onClick = { Music.seekBy(-secs * 1000L) }) { SkipIcon(true, secs, fg) }
            else IconButton(onClick = { Music.toggleShuffle() }) { Icon(Ic.Shuffle, null, tint = if (Music.shuffle) accent else dim) }
            IconButton(onClick = { Music.previous() }, modifier = Modifier.size(58.dp)) { Icon(Ic.SkipPrev, null, Modifier.size(34.dp)) }
            val corner by animateDpAsState(if (Music.isPlaying) 26.dp else 42.dp, label = "corner")
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(corner)).background(accent).clickable { Music.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (Music.loading) CircularProgressIndicator(Modifier.size(36.dp), color = cs.onPrimary)
                else Icon(if (Music.isPlaying) Ic.Pause else Ic.Play, null, Modifier.size(44.dp), tint = cs.onPrimary)
            }
            IconButton(onClick = { Music.next() }, modifier = Modifier.size(58.dp)) { Icon(Ic.SkipNext, null, Modifier.size(34.dp)) }
            if (skip) IconButton(onClick = { Music.seekBy(secs * 1000L) }) { SkipIcon(false, secs, fg) }
            else IconButton(onClick = { Music.cycleRepeat() }) { RepeatIcon(accent, dim) }
        }
        if (Prefs.mOutput) {
            val name = when (Music.outputKind) {
                "bt" -> Music.outputName.ifBlank { t("Bluetooth") }
                "wired" -> t("Wired headphones")
                else -> t("Phone speaker")
            }
            Row(
                Modifier.clip(CircleShape).clickable { Music.openOutputSwitcher(ctx) }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (Music.outputKind == "speaker") Ic.PhoneAndroid else Ic.Headphones, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, color = accent, fontSize = 15.sp, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(CircleShape)
                .background(cs.surfaceContainerHigh.copy(alpha = 0.7f)).padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (skip) {
                IconButton(onClick = { Music.toggleShuffle() }) { Icon(Ic.Shuffle, null, tint = if (Music.shuffle) accent else fg) }
                IconButton(onClick = { Music.cycleRepeat() }) { RepeatIcon(accent, fg) }
            }
            IconButton(onClick = { MusicNav.sleep = true }) { Icon(Ic.Moon, null, tint = if (Music.sleepEnd > 0 || Music.sleepAfterTrack) accent else fg) }
            IconButton(onClick = { MusicNav.speed = true }) { Icon(Ic.Speed, null, tint = if (Prefs.mSpeed != 1f || Prefs.mPitch != 1f) accent else fg) }
            IconButton(onClick = { MusicNav.eq = true }) { Icon(Ic.Eq, null, tint = if (Prefs.eqOn) accent else fg) }
            if (Prefs.mLyrics) IconButton(onClick = { Music.page = if (Music.page == 1) 0 else 1 }) {
                Icon(Ic.Lyrics, null, tint = if (Music.page == 1) accent else fg)
            }
            IconButton(onClick = { Music.page = if (Music.page == 2) 0 else 2 }) { Icon(Ic.Queue, null, tint = if (Music.page == 2) accent else fg) }
            IconButton(onClick = { Music.abTap() }) {
                Text(
                    when { Music.abA < 0 -> "A-B"; Music.abB < 0 -> "A-"; else -> "A-B" }, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (Music.abA >= 0) accent else fg,
                )
            }
        }
    }
}

@Composable
private fun RepeatIcon(on: Color, off: Color) {
    Icon(if (Music.repeat == 2) Ic.RepeatOne else Ic.Repeat, null, tint = if (Music.repeat != 0) on else off)
}

/** Seek bar whose played part waves while music plays (same as RTP). */
@Composable
fun WavySeekBar(progress: Float, playing: Boolean, color: Color, track: Color, onSeek: (Float) -> Unit, onSeekEnd: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "amp")
    fun frac(x: Float, w: Float) = (x / w).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
    Canvas(
        Modifier.fillMaxWidth().height(40.dp)
            .pointerInput(rtl) { detectTapGestures { onSeek(frac(it.x, size.width.toFloat())); onSeekEnd() } }
            .pointerInput(rtl) {
                detectHorizontalDragGestures(onDragEnd = onSeekEnd, onDragCancel = onSeekEnd) { change, _ ->
                    onSeek(frac(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val w = size.width
        val cy = size.height / 2
        val stroke = 4.dp.toPx()
        val a = 3.5.dp.toPx() * amp
        val len = 26.dp.toPx()
        val px = progress.coerceIn(0f, 1f) * w
        scale(if (rtl) -1f else 1f, 1f) {
            drawLine(track, Offset(px, cy), Offset(w, cy), stroke, StrokeCap.Round)
            val path = Path()
            var x = 0f
            path.moveTo(0f, cy + a * kotlin.math.sin(phase))
            while (x < px) {
                x = minOf(x + 2f, px)
                path.lineTo(x, cy + a * kotlin.math.sin(x / len * 2f * Math.PI.toFloat() - phase))
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            drawRoundRect(color, Offset(px - 2.5.dp.toPx(), cy - 10.dp.toPx()), Size(5.dp.toPx(), 20.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable
private fun LyricsPage(s: Track) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var lyrics by remember(s.key) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(s.key) { mutableStateOf(true) }
    LaunchedEffect(s.key) {
        loading = true
        lyrics = LyricsRepo.load(ctx, s, Prefs.mOnlineLyrics)
        loading = false
    }
    val l = lyrics
    if (l == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Ic.Note, null, Modifier.size(48.dp), tint = cs.primary)
            Spacer(Modifier.height(12.dp))
            Text(if (loading) t("Looking for lyrics…") else t("No lyrics found"), color = cs.onSurfaceVariant)
        }
        return
    }
    val pos = rememberMusicPosition()
    val current = if (l.synced) l.lines.indexOfLast { it.time <= pos + 300 } else -1
    val state = rememberLazyListState()
    LaunchedEffect(current) { if (current >= 0) state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 40.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(l.lines) { i, line ->
            val active = i == current
            val alpha by animateFloatAsState(if (!l.synced || active) 1f else 0.45f, label = "lyric")
            Text(
                line.text.ifEmpty { "♪" },
                fontSize = if (active) 26.sp else 22.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                lineHeight = 30.sp, color = (if (active) cs.primary else cs.onSurface).copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = l.synced) { Music.seekTo(line.time) }.padding(vertical = 8.dp),
            )
        }
        item { Text(t("Lyrics from") + " " + l.source, fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
    }
}

@Composable
private fun QueuePage() {
    val q = Music.queue
    val cs = MaterialTheme.colorScheme
    val state = rememberLazyListState(initialFirstVisibleItemIndex = Music.index.coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Text(t("Up next") + " · ${q.size}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            itemsIndexed(q, key = { i, s -> "${s.key}_$i" }) { i, s ->
                val cur = i == Music.index
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (cur) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                        .clickable { Music.jumpTo(i) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(s, Modifier.size(44.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (cur) cs.primary else cs.onSurface)
                        Text(s.artist, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { Music.move(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(34.dp)) { Icon(Ic.Up, null) }
                    IconButton(onClick = { Music.move(i, i + 1) }, enabled = i < q.lastIndex, modifier = Modifier.size(34.dp)) { Icon(Ic.Down, null) }
                    if (!cur) IconButton(onClick = { Music.removeAt(i) }, modifier = Modifier.size(34.dp)) { Icon(Ic.Close, null) }
                }
            }
        }
    }
}

@Composable
private fun SleepDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Sleep timer")) },
        text = {
            Column {
                for (m in listOf(10, 15, 30, 45, 60, 90)) {
                    Text("$m " + t("minutes"), fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { Music.sleepIn(m); onDismiss() }.padding(vertical = 10.dp))
                }
                Text(t("End of this song"), fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { Music.cancelSleep(); Music.sleepAfterTrack = true; onDismiss() }.padding(vertical = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
        dismissButton = { if (Music.sleepEnd > 0 || Music.sleepAfterTrack) TextButton(onClick = { Music.cancelSleep(); onDismiss() }) { Text(t("Turn off")) } },
    )
}

@Composable
private fun SpeedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Speed and pitch")) },
        text = {
            Column {
                Text(t("Speed") + ": " + "%.2f".format(Prefs.mSpeed) + "×")
                Slider(value = Prefs.mSpeed, onValueChange = { Prefs.mSpeed = (it * 20).toInt() / 20f; Music.applySpeed() }, valueRange = 0.5f..2f)
                Text(t("Pitch") + ": " + "%.2f".format(Prefs.mPitch))
                Slider(value = Prefs.mPitch, onValueChange = { Prefs.mPitch = (it * 20).toInt() / 20f; Music.applySpeed() }, valueRange = 0.5f..2f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
        dismissButton = { TextButton(onClick = { Prefs.mSpeed = 1f; Prefs.mPitch = 1f; Music.applySpeed() }) { Text(t("Reset")) } },
    )
}

@Composable
private fun EqDialog(onDismiss: () -> Unit) {
    val eq = Music.equalizer
    var tick by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Equalizer")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("On"), modifier = Modifier.weight(1f))
                    Switch(checked = Prefs.eqOn, onCheckedChange = { Music.setEq(it) })
                }
                if (eq == null) {
                    Text(t("This phone doesn't offer an equalizer."))
                } else {
                    val n = try { eq.numberOfPresets.toInt() } catch (_: Throwable) { 0 }
                    if (n > 0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        for (p in 0 until n) {
                            val name = try { eq.getPresetName(p.toShort()) } catch (_: Throwable) { "" }
                            TextButton(onClick = { Music.usePreset(p); tick++ }) { Text(name) }
                        }
                    }
                    val range = try { eq.bandLevelRange } catch (_: Throwable) { shortArrayOf(-1500, 1500) }
                    val bands = try { eq.numberOfBands.toInt() } catch (_: Throwable) { 0 }
                    for (b in 0 until bands) {
                        val hz = try { eq.getCenterFreq(b.toShort()) / 1000 } catch (_: Throwable) { 0 }
                        var lvl by remember(b, tick) { mutableFloatStateOf(try { eq.getBandLevel(b.toShort()).toFloat() } catch (_: Throwable) { 0f }) }
                        Text((if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz") + "  " + "%+.1f dB".format(lvl / 100f), fontSize = 13.sp)
                        Slider(
                            value = lvl, onValueChange = { lvl = it; Music.setBand(b, it.toInt().toShort()) },
                            valueRange = range[0].toFloat()..range[1].toFloat(),
                        )
                    }
                    Text(t("Bass boost"), fontSize = 13.sp)
                    Slider(value = Prefs.bass.toFloat(), onValueChange = { Music.setBass(it.toInt()) }, valueRange = 0f..1000f)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}


// ---------------------------------------------------------------- library

@Composable
fun MusicLibraryPage(acc: TgAccount, onPickSources: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val style = LocalStyle.current
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf(false) }
    LaunchedEffect(acc) { MusicLib.load(acc) }
    LaunchedEffect(Prefs.musicSources) { if (MusicLib.loadedFor == acc.index) MusicLib.loadPicked(acc) }
    @Suppress("UNUSED_VARIABLE") val watch = Prefs.hiddenSongs + Prefs.hiddenChats + Prefs.hideShort + Prefs.minSongSecs + Prefs.playlists
    val q = query.trim()
    fun filt(l: List<Track>) = l.filter { Music.visible(it) && (q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true)) }

    val g = openGroup
    if (g != null) {
        BackHandler { openGroup = null }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { openGroup = null }) { Icon(Ic.Back, null) }
                Text(g.first, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { Music.play(g.second.shuffled(), 0) }) { Icon(Ic.Shuffle, null) }
            }
            SongList(filt(g.second))
        }
        return
    }
    val pl = openPlaylist
    if (pl != null) {
        BackHandler { openPlaylist = null }
        val keys = Music.playlists().firstOrNull { it.first == pl }?.second.orEmpty()
        val byKey = MusicLib.everything().associateBy { it.key }
        val list = keys.mapNotNull { byKey[it] }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { openPlaylist = null }) { Icon(Ic.Back, null) }
                Text(pl, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (list.isNotEmpty()) Music.play(list.shuffled(), 0) }) { Icon(Ic.Shuffle, null) }
                IconButton(onClick = { Music.deletePlaylist(pl); openPlaylist = null }) { Icon(Ic.Delete, null) }
            }
            SongList(list, playlist = pl)
        }
        return
    }

    // tabs: when "selected" is split, each picked chat becomes its own tab
    val keys = MusicTabs.visible().flatMap { k ->
        if (k == "selected" && Prefs.selectedSplit && MusicLib.picked.isNotEmpty()) MusicLib.picked.keys.map { "chat:$it" } else listOf(k)
    }
    val labels = keys.map { k -> if (k.startsWith("chat:")) (acc.chats[k.removePrefix("chat:").toLongOrNull() ?: 0L]?.title ?: "?") else MusicTabs.label(k) }
    if (tab > keys.lastIndex) tab = 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(t("Search songs")) },
                leadingIcon = { Icon(Ic.Search, null) },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = style.navBar, unfocusedContainerColor = style.navBar,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            )
            IconButton(onClick = { scope.launch { MusicLib.load(acc, force = true) } }) { Icon(Ic.Refresh, null) }
            IconButton(onClick = { MusicNav.openPage?.invoke("hidden") }) { Icon(Ic.Hide, null) }
            IconButton(onClick = onPickSources) { Icon(Ic.Settings, null) }
        }
        PillTabs(labels, tab) { tab = it }
        if (MusicLib.loading && MusicLib.songs.isEmpty() && MusicLib.picked.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(t("Collecting songs from your chats…"), color = cs.onSurfaceVariant)
                }
            }
            return
        }
        val all = filt(MusicLib.songs)
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab.coerceIn(0, keys.lastIndex)) { keys.size }
        LaunchedEffect(tab) { if (pager.currentPage != tab && tab < keys.size) pager.animateScrollToPage(tab) }
        LaunchedEffect(pager.settledPage) { if (tab != pager.settledPage) tab = pager.settledPage }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = Prefs.swipeTabs) { pageIndex ->
        val k = keys.getOrElse(pageIndex) { "all" }
        when {
            k.startsWith("chat:") -> AllSongs(filt(MusicLib.picked[k.removePrefix("chat:").toLongOrNull() ?: 0L].orEmpty()))
            k == "selected" -> {
                if (MusicLib.picked.isEmpty()) Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(t("Pick the chats and channels you want here."), color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = onPickSources) { Text(t("Pick chats")) }
                } else AllSongs(filt(MusicLib.picked.values.flatten().distinctBy { it.key }.sortedByDescending { it.date }))
            }
            k == "playlists" -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { newList = true }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(Ic.Add, null, tint = cs.onPrimary) }
                        Spacer(Modifier.width(12.dp))
                        Text(t("New playlist"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val byKey = MusicLib.everything().associateBy { it.key }
                items(Music.playlists(), key = { it.first }) { (name, ks) ->
                    val first = ks.firstNotNullOfOrNull { byKey[it] }
                    Row(Modifier.fillMaxWidth().clickable { openPlaylist = name }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (first != null) TrackArt(first, Modifier.size(54.dp), RoundedCornerShape(14.dp))
                        else Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Ic.Queue, null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ks.size} " + t("songs"), fontSize = 13.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
            k == "albums" -> GroupList(
                filt(MusicLib.everything()).groupBy { it.chatId }.toList().sortedByDescending { it.second.size }
                    .map { (id, l) -> (acc.chats[id]?.title ?: "?") to l },
                square = true,
            ) { openGroup = it }
            k == "artists" -> GroupList(all.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }) { openGroup = it }
            k == "favorites" -> SongList(filt(MusicLib.everything()).filter { Music.isFavorite(it) })
            else -> AllSongs(all)
        }
        }
    }
    if (newList) NameDialog(t("New playlist"), onDismiss = { newList = false }) { Music.newPlaylist(it); newList = false }
}

@Composable
private fun AllSongs(songs: List<Track>) {
    val cs = MaterialTheme.colorScheme
    Column {
        if (songs.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${songs.size} " + t("songs"), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { Music.play(songs.shuffled(), 0) }) { Icon(Ic.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Shuffle all")) }
        }
        SongList(songs)
    }
}

@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onOk(name.trim()) }) { Text(t("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun SongList(songs: List<Track>, playlist: String? = null) {
    val cs = MaterialTheme.colorScheme
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t("No songs yet"), color = cs.onSurfaceVariant) }
        return
    }
    var addTo by remember { mutableStateOf<Track?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, s -> s.key }) { i, s ->
            val cur = Music.current?.key == s.key
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable { Music.play(songs, i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(s, Modifier.size(50.dp), RoundedCornerShape(14.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (cur) cs.primary else cs.onSurface)
                        Text(s.artist + (if (s.duration > 0) " · " + Fmt.duration(s.duration) else "") + " · " + s.ext, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (Music.isFavorite(s)) Icon(Ic.Heart, null, Modifier.size(16.dp), tint = cs.primary)
                    IconButton(onClick = { menu = true }) { Icon(Ic.More, null) }
                }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Play next")) }, onClick = { menu = false; Music.playNext(s) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Add to queue")) }, onClick = { menu = false; Music.addToQueue(s) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Add to playlist")) }, onClick = { menu = false; addTo = s })
                    if (playlist != null) androidx.compose.material3.DropdownMenuItem(text = { Text(t("Remove from playlist")) }, onClick = { menu = false; Music.removeFromPlaylist(playlist, s.key) })
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (Music.isFavorite(s)) t("Remove from favorites") else t("Add to favorites")) },
                        onClick = { menu = false; Music.toggleFavorite(s) },
                    )
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Hide song")) }, onClick = { menu = false; Music.hide(listOf(s.key)) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Hide all from this chat")) }, onClick = { menu = false; Music.hideChat(s.chatId) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("Go to message")) }, onClick = { menu = false; MusicNav.openChat?.invoke(s.acc, s.chatId) })
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
    addTo?.let { s ->
        var newName by remember { mutableStateOf(false) }
        if (newName) NameDialog(t("New playlist"), onDismiss = { newName = false }) { n -> Music.newPlaylist(n); Music.addToPlaylist(n, listOf(s.key)); newName = false; addTo = null }
        else AlertDialog(
            onDismissRequest = { addTo = null },
            title = { Text(t("Add to playlist")) },
            text = {
                Column {
                    for ((n, _) in Music.playlists()) {
                        Text(n, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { Music.addToPlaylist(n, listOf(s.key)); addTo = null }.padding(vertical = 10.dp))
                    }
                    Text("+ " + t("New playlist"), fontSize = 16.sp, color = cs.primary, modifier = Modifier.fillMaxWidth().clickable { newName = true }.padding(vertical = 10.dp))
                }
            },
            confirmButton = { TextButton(onClick = { addTo = null }) { Text(t("Close")) } },
        )
    }
}

@Composable
private fun GroupList(groups: List<Pair<String, List<Track>>>, square: Boolean = false, onOpen: (Pair<String, List<Track>>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.first + "#" + it.second.first().chatId }) { g ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(g) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArt(g.second.first(), Modifier.size(54.dp), if (square) RoundedCornerShape(14.dp) else CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.first, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.second.size} " + t("songs"), fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

/** Rounded pill tabs (same look as the folder tabs). */
@Composable
fun PillTabs(names: List<String>, selected: Int, counts: List<Int>? = null, onSelect: (Int) -> Unit) {
    val style = LocalStyle.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(CircleShape).background(style.navBar)
            .horizontalScroll(rememberScrollState()).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, n ->
            val sel = i == selected
            Row(
                Modifier.clip(CircleShape).background(if (sel) style.pill else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (sel) style.pillFg else style.navBarFg.copy(alpha = 0.7f), maxLines = 1)
                val c = counts?.getOrNull(i) ?: 0
                if (c > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(CircleShape).background(if (sel) style.pillFg else style.navBarFg.copy(alpha = 0.3f)).padding(horizontal = 7.dp, vertical = 1.dp),
                    ) { Text(if (c > 999) "999+" else c.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) style.pill else style.navBarFg) }
                }
            }
        }
    }
}


/** Big player card (same layout as the phone's media panel). */
@Composable
fun MusicCard(s: Track, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val base = coverColor(s) ?: LocalStyle.current.accent
    val bg = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.45f)
    val fg = Color.White
    val pos = rememberMusicPosition()
    val dur = Music.duration
    var seeking by remember { mutableStateOf<Float?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(30.dp)).background(bg)
            .clickable { Music.playerOpen = true }
            .pointerInput(s.key) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag < -120) Music.next() else if (drag > 120) Music.previous()
                    drag = 0f
                }) { _, d -> drag += d }
            },
    ) {
        TrackArt(s, Modifier.align(Alignment.Center).size(150.dp).graphicsLayer { alpha = 0.55f }, CircleShape, 40.dp)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Play, null, tint = fg, modifier = Modifier.size(18.dp).clip(CircleShape).background(fg.copy(alpha = 0.2f)).padding(2.dp))
                Spacer(Modifier.width(8.dp))
                val name = when (Music.outputKind) {
                    "bt" -> Music.outputName.ifBlank { t("Bluetooth") }
                    "wired" -> t("Wired headphones")
                    else -> t("Phone speaker")
                }
                Text(name, color = fg.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    t("Media output"), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)).clickable { Music.openOutputSwitcher(ctx) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(s.title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.6f))
            Text(s.artist, color = fg.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(8.dp))
            val prog = seeking ?: (if (dur > 0) pos.toFloat() / dur else 0f).coerceIn(0f, 1f)
            WavySeekBar(
                prog, Music.isPlaying && Prefs.mWavy, fg.copy(alpha = 0.9f), fg.copy(alpha = 0.25f),
                onSeek = { seeking = it },
                onSeekEnd = { seeking?.let { Music.seekTo((it * dur).toLong()) }; seeking = null },
            )
            Row {
                Text(Fmt.duration(((seeking?.let { (it * dur).toLong() } ?: pos) / 1000).toInt()), color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(Fmt.duration((dur / 1000).toInt()), color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Music.toggleShuffle() }) { Icon(Ic.Shuffle, null, tint = if (Music.shuffle) fg else fg.copy(alpha = 0.5f)) }
                IconButton(onClick = { Music.previous() }) { Icon(Ic.SkipPrev, null, tint = fg, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { Music.toggle() }, modifier = Modifier.size(56.dp)) {
                    if (Music.loading) CircularProgressIndicator(Modifier.size(30.dp), color = fg, strokeWidth = 2.dp)
                    else Icon(if (Music.isPlaying) Ic.Pause else Ic.Play, null, tint = fg, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { Music.next() }) { Icon(Ic.SkipNext, null, tint = fg, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { Music.cycleRepeat() }) { RepeatIcon(fg, fg.copy(alpha = 0.5f)) }
            }
        }
        IconButton(onClick = { Music.stop() }, modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)) {
            Icon(Ic.Close, null, tint = fg.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}
