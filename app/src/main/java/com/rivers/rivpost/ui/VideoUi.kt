package com.rivers.rivpost.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.media.TdlDataSource
import com.rivers.rivpost.media.Cue
import com.rivers.rivpost.media.Engine
import com.rivers.rivpost.media.SubtitleParser
import com.rivers.rivpost.media.VideoState
import com.rivers.rivpost.media.VideoItem
import com.rivers.rivpost.media.VideoLib
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

/** What the video player should play. */
data class PlayRequest(
    val acc: Int, val file: File?, val title: String, val key: String,
    val url: String = "", val userAgent: String = "", val referer: String = "", val live: Boolean = false,
)

fun VideoItem.request(): PlayRequest = PlayRequest(acc, file, com.rivers.rivpost.media.Meta.title(this), key)

// ---------------------------------------------------------------- library (Netflix style)

@Composable
fun VideoLibraryPage(acc: TgAccount, onPlay: (PlayRequest) -> Unit, onPickSources: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(acc, Prefs.videoSources) { VideoLib.load(acc) }
    @Suppress("UNUSED_VARIABLE") val v = VideoLib.version
    val cont = VideoLib.continueWatching(ctx)
    val hero = cont.firstOrNull() ?: VideoLib.mine.firstOrNull() ?: VideoLib.rows.firstOrNull()?.second?.firstOrNull()

    if (VideoLib.mine.isEmpty() && VideoLib.rows.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (VideoLib.loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(t("Loading your videos…"), color = cs.onSurfaceVariant)
            } else {
                Icon(Ic.Movie, null, Modifier.size(64.dp), tint = cs.primary)
                Spacer(Modifier.height(14.dp))
                Text(t("Your video library is empty"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Long-press a video in any chat and choose \"Add to Videos\", or add a whole channel or group so it gets its own row."),
                    color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onPickSources) { Text(t("Add channels or groups")) }
            }
        }
        return
    }

    VideoDetailsNav.open?.let { dv -> VideoDetailsDialog(acc, dv, onPlay) { VideoDetailsNav.open = null } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        if (hero != null) item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                Poster(acc, hero, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, cs.background))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    val info = rememberOnline(hero)
                    Text(com.rivers.rivpost.media.Meta.title(hero), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            info?.year?.takeIf { it.isNotBlank() }, info?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                            info?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                            info?.runtime?.takeIf { it > 0 }?.let { "$it " + t("min") },
                            if (info == null) acc.chats[hero.chatId]?.title else null,
                        ).joinToString("  ·  "),
                        color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1,
                    )
                    val about = info?.overview?.takeIf { it.isNotBlank() } ?: com.rivers.rivpost.media.Meta.description(hero)
                    if (about.isNotBlank()) Text(about, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(
                            onClick = { onPlay(hero.request()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) { Icon(Ic.Play, null); Spacer(Modifier.width(6.dp)); Text(t("Play")) }
                        Spacer(Modifier.width(10.dp))
                        val inList = VideoLib.inMyList(hero)
                        Button(
                            onClick = { VideoLib.toggleMyList(hero) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                        ) { Icon(if (inList) Ic.Check else Ic.Add, null); Spacer(Modifier.width(6.dp)); Text(t("My List")) }
                        Spacer(Modifier.width(10.dp))
                        IconButton(onClick = { VideoDetailsNav.open = hero }, modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                            Icon(Ic.Info, null, tint = Color.White)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = { scope.launch { VideoLib.load(acc) } }, modifier = Modifier.background(Color(0x66000000), CircleShape)) {
                        Icon(Ic.Refresh, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onPickSources, modifier = Modifier.background(Color(0x66000000), CircleShape)) {
                        Icon(Ic.Settings, null, tint = Color.White)
                    }
                }
            }
        }
        if (cont.isNotEmpty()) item { VideoRow(acc, t("Continue watching"), cont, onPlay) }
        if (VideoLib.mine.isNotEmpty()) item { VideoRow(acc, t("My List"), VideoLib.mine, onPlay) }
        for ((chatId, list) in VideoLib.rows) {
            item(key = "row$chatId") { VideoRow(acc, acc.chats[chatId]?.title ?: t("Videos"), list, onPlay) }
        }
        if (VideoLib.loading) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun VideoRow(acc: TgAccount, title: String, list: List<VideoItem>, onPlay: (PlayRequest) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 14.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), maxLines = 1)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.key }) { v ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(
                        Modifier.width(150.dp).combinedClickable(onClick = { onPlay(v.request()) }, onLongClick = { menu = true }),
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                            Poster(acc, v, Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
                            if (v.duration > 0) Text(
                                Fmt.duration(v.duration), color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                            val p = VideoLib.progress(ctx, v.key)
                            if (p > 0.01f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) {
                                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Color(0xFFE53935)))
                            }
                        }
                        Text(com.rivers.rivpost.media.Meta.title(v), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        val oi = rememberOnline(v)
                        if (!oi?.epTitle.isNullOrBlank()) Text(oi!!.epTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (com.rivers.rivpost.media.Meta.hasOnline()) DropdownMenuItem(text = { Text(t("Details")) }, onClick = { menu = false; VideoDetailsNav.open = v })
                        DropdownMenuItem(
                            text = { Text(if (VideoLib.inMyList(v)) t("Remove from My List") else t("Add to My List")) },
                            onClick = { menu = false; VideoLib.toggleMyList(v) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(acc: TgAccount, v: VideoItem, modifier: Modifier, shape: RoundedCornerShape, wide: Boolean = true) {
    val th = rememberFile(acc, v.thumb, autoDownload = true, priority = 2)
    val mb = remember(v.key) { miniBitmap(v.mini) }
    val info = rememberOnline(v)
    val art = if (wide) info?.backdrop?.ifBlank { info.poster } else info?.poster
    Box(modifier.clip(shape).background(Color(0xFF2A2236)), contentAlignment = Alignment.Center) {
        when {
            !art.isNullOrBlank() -> AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            th != null && th.local.isDownloadingCompleted && th.local.path.isNotEmpty() ->
                AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mb != null -> Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(Ic.Movie, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ---------------------------------------------------------------- player

/** Full-screen built-in video player. Streams from Telegram, links, IPTV or shared folders. */
@Composable
fun VideoPlayerScreen(req: PlayRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val acc = Tg.accounts.value[req.acc]
    BackHandler { onClose() }
    if (req.file != null && acc == null) { LaunchedEffect(Unit) { onClose() }; return }
    val player = remember(req.key) {
        Engine.build(ctx, req.userAgent, req.referer).apply {
            val f = req.file
            val md = androidx.media3.common.MediaMetadata.Builder().setTitle(req.title).setArtist(if (req.live) "Live" else "Riv Post").build()
            fun item(u: Uri) = MediaItem.Builder().setUri(u).setMediaId(req.key).setMediaMetadata(md).build()
            when {
                f == null -> setMediaItem(item(Uri.parse(req.url)))
                f.local.isDownloadingCompleted && f.local.path.isNotEmpty() -> setMediaItem(item(Uri.fromFile(java.io.File(f.local.path))))
                else -> {
                    val src = ProgressiveMediaSource.Factory { TdlDataSource(acc!!, f) }
                        .createMediaSource(item(Uri.parse("tdl://file/" + f.id)))
                    setMediaSource(src)
                }
            }
            if (!req.live) {
                val start = VideoLib.position(ctx, req.key)
                if (start > 5000) seekTo(start)
            }
            prepare()
            playWhenReady = true
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = e.errorCodeName + "\n" + (e.cause?.message ?: e.message ?: "") }
            override fun onIsPlayingChanged(isPlaying: Boolean) { VideoState.playing = isPlaying }
        }
        player.addListener(l)
        if (Prefs.videoPanel) com.rivers.rivpost.media.VideoService.start(ctx, player)
        onDispose { player.removeListener(l); com.rivers.rivpost.media.VideoService.stop(ctx) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP && !Prefs.bgVideo && !VideoState.inPip) player.pause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(req.key) {
        val win = act?.window
        val ctl = win?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (!req.live && player.duration > 0) VideoLib.savePosition(ctx, req.key, player.currentPosition, player.duration)
            player.release()
            VideoState.playing = false
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // subtitles
    var cues by remember(req.key) { mutableStateOf<List<Cue>>(emptyList()) }
    var subDelay by remember(req.key) { mutableStateOf(0L) }
    var subText by remember { mutableStateOf("") }
    val pickSub = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cues = try { SubtitleParser.load(ctx, uri) } catch (_: Throwable) { emptyList() }
            toast(ctx, if (cues.isEmpty()) t("Couldn't read subtitles") else t("Subtitles loaded") + " (${cues.size})")
        }
    }
    LaunchedEffect(cues, subDelay) {
        while (cues.isNotEmpty()) {
            subText = SubtitleParser.textAt(cues, player.currentPosition - subDelay)
            kotlinx.coroutines.delay(150)
        }
        subText = ""
    }
    // A-B repeat for videos
    var abA by remember { mutableStateOf(-1L) }
    var abB by remember { mutableStateOf(-1L) }
    LaunchedEffect(abB) {
        while (abB > 0) {
            if (player.currentPosition >= abB) player.seekTo(abA)
            kotlinx.coroutines.delay(120)
        }
    }
    var landscape by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controls = v == android.view.View.VISIBLE })
                }
            },
            update = { v -> v.useController = !VideoState.inPip },
            modifier = Modifier.fillMaxSize(),
        )
        if (subText.isNotBlank()) {
            val col = if (Prefs.subColor == 0) Color.White else Color(Prefs.subColor)
            Text(
                subText, color = col, fontSize = Prefs.subSize.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (controls && !VideoState.inPip) 90.dp else 24.dp, start = 20.dp, end = 20.dp)
                    .let { if (Prefs.subBg) it.clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 2.dp) else it },
            )
        }
        error?.let { e ->
            Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(16.dp)) {
                Text(t("Couldn't play this video"), color = Color.White, fontWeight = FontWeight.Bold)
                Text(e, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(t("Try another decoder from the ⋮ menu."), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        if (controls && !VideoState.inPip) Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.Back, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Text(req.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (abA >= 0) Text(if (abB > 0) "A-B" else "A-", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = {
                landscape = !landscape
                act?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.Refresh, null, tint = Color.White) }
            Spacer(Modifier.width(6.dp))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.More, null, tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(t("Load subtitles")) }, onClick = { menu = false; pickSub.launch("*/*") })
                    if (cues.isNotEmpty()) {
                        DropdownMenuItem(text = { Text(t("Subtitles earlier") + " (−0.5s)") }, onClick = { subDelay -= 500; toast(ctx, t("Delay") + " " + subDelay / 1000f + "s") })
                        DropdownMenuItem(text = { Text(t("Subtitles later") + " (+0.5s)") }, onClick = { subDelay += 500; toast(ctx, t("Delay") + " " + subDelay / 1000f + "s") })
                        DropdownMenuItem(text = { Text(t("Turn off subtitles")) }, onClick = { menu = false; cues = emptyList() })
                    }
                    if (!req.live) DropdownMenuItem(
                        text = { Text(when { abA < 0 -> t("A-B repeat: set A"); abB < 0 -> t("A-B repeat: set B"); else -> t("A-B repeat: off") }) },
                        onClick = {
                            menu = false
                            when {
                                abA < 0 -> { abA = player.currentPosition }
                                abB < 0 -> { if (player.currentPosition > abA + 500) abB = player.currentPosition }
                                else -> { abA = -1; abB = -1 }
                            }
                        },
                    )
                    DropdownMenuItem(text = { Text(t("Picture-in-picture")) }, onClick = { menu = false; (act as? com.rivers.rivpost.MainActivity)?.enterPip() })
                    for ((k, label) in listOf("hw" to "HW", "hwplus" to "HW+", "sw" to "SW")) {
                        DropdownMenuItem(
                            text = { Text(t("Decoder") + ": " + label + if (Prefs.decoder == k) "  ✓" else "") },
                            onClick = { menu = false; Prefs.decoder = k; toast(ctx, t("Reopen the video to use the new decoder")) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- GIFs and animated stickers

/** Plays a downloaded MP4 GIF or WEBM sticker on loop, muted. */
@Composable
fun LoopVideo(path: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(path) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier,
    )
}

/** Animated .tgs sticker (gzipped Lottie). */
@Composable
fun TgsSticker(path: String, modifier: Modifier) {
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try { GZIPInputStream(java.io.FileInputStream(path)).bufferedReader().use { it.readText() } } catch (_: Throwable) { null }
        }
    }
    val j = json
    if (j == null) { Box(modifier); return }
    val comp by rememberLottieComposition(LottieCompositionSpec.JsonString(j))
    LottieAnimation(comp, iterations = if (Prefs.loopStickers && !Prefs.reduceMotion) LottieConstants.IterateForever else 1, modifier = modifier)
}


@Composable
private fun rememberOnline(v: VideoItem): com.rivers.rivpost.media.OnlineInfo? {
    val ctx = LocalContext.current
    if (!com.rivers.rivpost.media.Meta.hasOnline()) return null
    val info by produceState<com.rivers.rivpost.media.OnlineInfo?>(null, v.key, Prefs.tmdbKey, Prefs.tvdbKey, Prefs.showSource) { value = com.rivers.rivpost.media.Meta.lookup(ctx, v) }
    return info
}


object VideoDetailsNav {
    var open by androidx.compose.runtime.mutableStateOf<VideoItem?>(null)
}

/** Full info for one video: description, episode, cast. From TMDb or TheTVDB. */
@Composable
fun VideoDetailsDialog(acc: TgAccount, v: VideoItem, onPlay: (PlayRequest) -> Unit, onDismiss: () -> Unit) {
    val info = rememberOnline(v)
    val cs = MaterialTheme.colorScheme
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        Poster(acc, v, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, cs.background))))
                        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).background(Color(0x66000000), CircleShape)) {
                            Icon(Ic.Close, null, tint = Color.White)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        Text(info?.title?.ifBlank { null } ?: com.rivers.rivpost.media.Meta.title(v), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                info?.year?.takeIf { it.isNotBlank() }, info?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                                info?.runtime?.takeIf { it > 0 }?.let { "$it " + t("min") },
                            ).joinToString("  ·  "),
                            color = cs.onSurfaceVariant, fontSize = 14.sp,
                        )
                        if (!info?.genres.isNullOrEmpty()) Text(info!!.genres.joinToString(", "), color = cs.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = { onDismiss(); onPlay(v.request()) }) { Icon(Ic.Play, null); Spacer(Modifier.width(6.dp)); Text(t("Play")) }
                            Spacer(Modifier.width(10.dp))
                            val inList = VideoLib.inMyList(v)
                            OutlinedButton(onClick = { VideoLib.toggleMyList(v) }) { Icon(if (inList) Ic.Check else Ic.Add, null); Spacer(Modifier.width(6.dp)); Text(t("My List")) }
                        }
                        if (info == null) Text(t("No online info found for this title."), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                        if (!info?.overview.isNullOrBlank()) Text(info!!.overview, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
                if (info != null && (info.epTitle.isNotBlank() || info.season > 0)) item {
                    Column(Modifier.padding(18.dp)) {
                        Text(t("This episode"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            val pic = info.epStill.ifBlank { info.seasonPoster }
                            if (pic.isNotBlank()) AsyncImage(
                                model = pic, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(140.dp).aspectRatio(if (info.epStill.isNotBlank()) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Season") + " ${info.season}" + (if (info.episode > 0) " · " + t("Episode") + " ${info.episode}" else ""), color = cs.onSurfaceVariant, fontSize = 13.sp)
                                if (info.epTitle.isNotBlank()) Text(info.epTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                if (info.epOverview.isNotBlank()) Text(info.epOverview, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (!info?.cast.isNullOrEmpty()) item {
                    Column(Modifier.padding(top = 6.dp)) {
                        Text(t("Cast"), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(info!!.cast) { p ->
                                Column(Modifier.width(86.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(76.dp).clip(CircleShape).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                                        if (p.photo.isNotBlank()) AsyncImage(model = p.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Text(p.name.take(1), fontSize = 24.sp)
                                    }
                                    Text(p.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    if (p.role.isNotBlank()) Text(p.role, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (info != null) item {
                    Text(t("Info from") + " " + info.source, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(18.dp))
                }
            }
        }
    }
}
