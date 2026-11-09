package com.rivers.rivpost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.Screen
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import kotlinx.coroutines.launch

/** Swipe through every photo and video in the chat. */
@Composable
fun MediaGallery(acc: TgAccount, act: MainActivity, chatId: Long, startId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(startId) {
        val r = (acc.client.searchChatMessages(
            chatId = chatId, query = "", fromMessageId = startId, offset = -30, limit = 60, filter = SearchMessagesFilterPhotoAndVideo(),
        ) as? TdlResult.Success)?.result?.messages?.filterNotNull().orEmpty()
        var list = r.sortedBy { it.id }
        if (list.none { it.id == startId }) {
            (acc.client.getMessage(chatId = chatId, messageId = startId) as? TdlResult.Success)?.result?.let { list = (list + it).sortedBy { m -> m.id } }
        }
        items = list
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { onClose() }
        val l = items
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (l == null) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White); return@Box }
            val start = l.indexOfFirst { it.id == startId }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start) { l.size }
            var chrome by remember { mutableStateOf(true) }
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                val m = l[i]
                when (val c = m.content) {
                    is MessagePhoto -> ZoomPhoto(acc, c.photo) { chrome = !chrome }
                    is MessageVideo -> Box(Modifier.fillMaxSize().clickable {
                        act.push(Screen.Video(PlayRequest(acc.index, c.video.video, c.caption.text.take(60).ifBlank { c.video.fileName.ifBlank { t("Video") } }, "${m.chatId}:${m.id}")))
                        onClose()
                    }, contentAlignment = Alignment.Center) {
                        val th = rememberFile(acc, c.video.thumbnail?.file, autoDownload = true)
                        if (th != null && th.local.isDownloadingCompleted) AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                            Icon(Ic.Play, null, tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                    }
                    else -> Unit
                }
            }
            val cur = l.getOrNull(pager.currentPage)
            if (chrome && cur != null) {
                Row(Modifier.fillMaxWidth().background(Color(0x88000000)).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Ic.Back, null, tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(acc.senderName(cur.senderId), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(Fmt.listTime(cur.date) + " " + Fmt.clock(cur.date) + "  ·  ${pager.currentPage + 1}/${l.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        mediaFileOf(cur.content)?.let { (f, kind) -> saveToPhone(ctx, acc, f, kind, acc.chats[chatId]) }
                    }) { Icon(Ic.Download, null, tint = Color.White) }
                    IconButton(onClick = { onClose(); act.push(Screen.Forward(chatId, longArrayOf(cur.id))) }) { Icon(Ic.Forward, null, tint = Color.White) }
                    IconButton(onClick = { onClose(); act.push(Screen.Chat(chatId, jump = cur.id)) }) { Icon(Ic.Chat, null, tint = Color.White) }
                }
                val cap = when (val c = cur.content) { is MessagePhoto -> c.caption.text; is MessageVideo -> c.caption.text; else -> "" }
                if (cap.isNotBlank()) Text(
                    cap, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x88000000)).navigationBarsPadding().padding(14.dp),
                )
            }
        }
    }
}

@Composable
fun ZoomPhoto(acc: TgAccount, photo: Photo, onTap: () -> Unit) = ZoomSizes(acc, photo.sizes, photo.minithumbnail?.data, onTap)

@Composable
fun ZoomSizes(acc: TgAccount, sizes: Array<dev.g000sha256.tdl.dto.PhotoSize>, miniData: ByteArray?, onTap: () -> Unit) {
    val best = sizes.maxByOrNull { it.width * it.height }
    val f = rememberFile(acc, best?.photo, autoDownload = true, priority = 16)
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val st = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        if (scale > 1f) { offX += pan.x; offY += pan.y } else { offX = 0f; offY = 0f }
    }
    Box(
        Modifier.fillMaxSize().then(if (scale > 1f) Modifier.transformable(st) else Modifier.transformable(st, canPan = { false })).clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        if (f != null && f.local.isDownloadingCompleted) AsyncImage(
            model = java.io.File(f.local.path), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offX; translationY = offY },
        ) else {
            val mini = remember(miniData) { miniData?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
            if (mini != null) androidx.compose.foundation.Image(mini.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            CircularProgressIndicator(color = Color.White)
        }
    }
}




/** Someone's profile photos, newest first. Your own can be deleted. */
@Composable
fun ProfilePhotos(acc: TgAccount, userId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var photos by remember { mutableStateOf<List<dev.g000sha256.tdl.dto.ChatPhoto>?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { photos = (acc.client.getUserProfilePhotos(userId = userId, offset = 0, limit = 100) as? TdlResult.Success)?.result?.photos?.toList().orEmpty() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val l = photos
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (l == null) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White); return@Box }
            if (l.isEmpty()) { Text(t("No photos"), color = Color.White, modifier = Modifier.align(Alignment.Center)); }
            val pager = rememberPagerState { l.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i -> ZoomSizes(acc, l[i].sizes, l[i].minithumbnail?.data) { } }
            Row(Modifier.fillMaxWidth().background(Color(0x88000000)).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Ic.Back, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(acc.userName(userId), color = Color.White, fontWeight = FontWeight.SemiBold)
                    l.getOrNull(pager.currentPage)?.let { Text(Fmt.listTime(it.addedDate) + "  ·  ${pager.currentPage + 1}/${l.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp) }
                }
                if (userId == acc.myId && l.isNotEmpty()) IconButton(onClick = {
                    val p = l.getOrNull(pager.currentPage) ?: return@IconButton
                    acc.scope.launch {
                        val r = acc.client.deleteProfilePhoto(profilePhotoId = p.id)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (r is TdlResult.Failure) toast(ctx, r.message) else { toast(ctx, t("Photo deleted")); tick++ }
                        }
                    }
                }) { Icon(Ic.Delete, null, tint = Color.White) }
            }
        }
    }
}
