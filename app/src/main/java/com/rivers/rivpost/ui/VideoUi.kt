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
data class PlayRequest(val acc: Int, val file: File, val title: String, val key: String)

fun VideoItem.request(): PlayRequest = PlayRequest(acc, file, title, key)

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
                    Text(hero.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(acc.chats[hero.chatId]?.title ?: "", color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
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
                        Text(v.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
private fun Poster(acc: TgAccount, v: VideoItem, modifier: Modifier, shape: RoundedCornerShape) {
    val th = rememberFile(acc, v.thumb, autoDownload = true, priority = 2)
    val mb = remember(v.key) { miniBitmap(v.mini) }
    Box(modifier.clip(shape).background(Color(0xFF2A2236)), contentAlignment = Alignment.Center) {
        when {
            th != null && th.local.isDownloadingCompleted && th.local.path.isNotEmpty() ->
                AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mb != null -> Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(Ic.Movie, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ---------------------------------------------------------------- player

/** Full-screen built-in video player. Streams from Telegram while it downloads. */
@Composable
fun VideoPlayerScreen(req: PlayRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val acc = Tg.accounts.value[req.acc]
    BackHandler { onClose() }
    if (acc == null) { LaunchedEffect(Unit) { onClose() }; return }
    val player = remember(req.key) {
        ExoPlayer.Builder(ctx).build().apply {
            val f = req.file
            val local = f.local.isDownloadingCompleted && f.local.path.isNotEmpty()
            if (local) {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(f.local.path))))
            } else {
                val src = ProgressiveMediaSource.Factory { TdlDataSource(acc, f) }
                    .createMediaSource(MediaItem.fromUri(Uri.parse("tdl://file/" + f.id)))
                setMediaSource(src)
            }
            val start = VideoLib.position(ctx, req.key)
            if (start > 5000) seekTo(start)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(req.key) {
        val win = act?.window
        val ctl = win?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (player.duration > 0) VideoLib.savePosition(ctx, req.key, player.currentPosition, player.duration)
            player.release()
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    var landscape by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.Back, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Text(req.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = {
                landscape = !landscape
                act?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.Refresh, null, tint = Color.White) }
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
    LottieAnimation(comp, iterations = LottieConstants.IterateForever, modifier = modifier)
}
