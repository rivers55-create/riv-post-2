package com.rivers.rivpost.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.rivers.rivpost.data.t
import dev.g000sha256.tdl.dto.Contact
import dev.g000sha256.tdl.dto.InputAnimation
import dev.g000sha256.tdl.dto.InputAudio
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageAnimation
import dev.g000sha256.tdl.dto.InputMessageAudio
import dev.g000sha256.tdl.dto.InputMessageContact
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.InputMessageLocation
import dev.g000sha256.tdl.dto.InputMessagePhoto
import dev.g000sha256.tdl.dto.InputMessageVideo
import dev.g000sha256.tdl.dto.InputPhoto
import dev.g000sha256.tdl.dto.InputVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GalleryItem(val uri: Uri, val video: Boolean)

fun photoContent(path: String): InputMessageContent {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    return InputMessagePhoto(
        photo = InputPhoto(
            photo = InputFileLocal(path = path), thumbnail = null, video = null,
            addedStickerFileIds = IntArray(0), width = o.outWidth.coerceAtLeast(0), height = o.outHeight.coerceAtLeast(0),
        ),
        caption = null, showCaptionAboveMedia = false, selfDestructType = null, hasSpoiler = false,
    )
}

private fun videoContent(path: String): InputMessageContent = InputMessageVideo(
    video = InputVideo(
        video = InputFileLocal(path = path), thumbnail = null, cover = null, startTimestamp = 0,
        addedStickerFileIds = IntArray(0), duration = 0, width = 0, height = 0, supportsStreaming = true,
    ),
    caption = null, showCaptionAboveMedia = false, selfDestructType = null, hasSpoiler = false,
)

private fun hasGalleryPermission(c: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(c, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)
    } else ContextCompat.checkSelfPermission(c, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun loadGallery(c: Context): List<GalleryItem> {
    val out = ArrayList<Pair<Long, GalleryItem>>()
    fun q(uri: Uri, video: Boolean) {
        try {
            c.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED), null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC",
            )?.use { cur ->
                var n = 0
                while (cur.moveToNext() && n < 300) {
                    out.add(cur.getLong(1) to GalleryItem(ContentUris.withAppendedId(uri, cur.getLong(0)), video))
                    n++
                }
            }
        } catch (_: Throwable) { }
    }
    q(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    q(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    return out.sortedByDescending { it.first }.map { it.second }
}

@SuppressLint("MissingPermission")
private fun lastLocation(c: Context): Location? {
    val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.getProviders(true).mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Throwable) { null } }
        .maxByOrNull { it.time }
}

/** The 📎 sheet: gallery grid with camera, and File / Location / Contact / Music / GIF tabs. */
@Composable
fun AttachSheet(onDismiss: () -> Unit, onSend: (InputMessageContent) -> Unit, onCamera: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var allowed by remember { mutableStateOf(hasGalleryPermission(ctx)) }
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    val picked = remember { mutableStateListOf<GalleryItem>() }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(allowed) { if (allowed) items = withContext(Dispatchers.IO) { loadGallery(ctx) } }

    fun sendUris(list: List<Pair<Uri, Boolean>>) {
        scope.launch {
            for ((u, video) in list) {
                val f = copyToCache(ctx, u) ?: continue
                onSend(if (video) videoContent(f.absolutePath) else photoContent(f.absolutePath))
            }
            onDismiss()
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = hasGalleryPermission(ctx) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) { onSend(photoContent(f.absolutePath)); onDismiss() }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(ctx, uri)?.let { f ->
                onSend(InputMessageDocument(document = InputDocument(document = InputFileLocal(path = f.absolutePath), thumbnail = null, disableContentTypeDetection = false), caption = null))
            }
            onDismiss()
        }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(ctx, uri)?.let { f ->
                val base = f.nameWithoutExtension
                val parts = base.split(" - ", limit = 2)
                onSend(
                    InputMessageAudio(
                        audio = InputAudio(
                            audio = InputFileLocal(path = f.absolutePath), albumCoverThumbnail = null, duration = 0,
                            title = if (parts.size == 2) parts[1] else base, performer = if (parts.size == 2) parts[0] else "",
                        ),
                        caption = null,
                    )
                )
            }
            onDismiss()
        }
    }
    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(ctx, uri)?.let { f ->
                onSend(
                    InputMessageAnimation(
                        animation = InputAnimation(animation = InputFileLocal(path = f.absolutePath), thumbnail = null, addedStickerFileIds = IntArray(0), duration = 0, width = 0, height = 0),
                        caption = null, showCaptionAboveMedia = false, hasSpoiler = false,
                    )
                )
            }
            onDismiss()
        }
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                ctx.contentResolver.query(
                    uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: ""
                        val num = c.getString(1) ?: ""
                        val parts = name.split(" ", limit = 2)
                        onSend(InputMessageContact(contact = Contact(phoneNumber = num, firstName = parts[0], lastName = parts.getOrElse(1) { "" }, vcard = "", userId = 0)))
                    }
                }
            } catch (_: Throwable) { toast(ctx, t("Couldn't read the contact")) }
            onDismiss()
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) {
            val l = lastLocation(ctx)
            if (l == null) toast(ctx, t("Turn on location and try again"))
            else {
                onSend(InputMessageLocation(location = dev.g000sha256.tdl.dto.Location(latitude = l.latitude, longitude = l.longitude, horizontalAccuracy = l.accuracy.toDouble())))
                onDismiss()
            }
        } else toast(ctx, t("Location permission is needed"))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color(0xFF0E0E10)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                if (!allowed) {
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(t("Allow access to your photos to pick them here."), color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            perm.launch(
                                if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                                else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            )
                        }) { Text(t("Allow")) }
                    }
                } else {
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
                        item {
                            Box(
                                Modifier.aspectRatio(1f).padding(1.dp).background(Color.Black).clickable {
                                    if (onCamera != null && com.rivers.rivpost.data.Prefs.builtInCamera) { onDismiss(); onCamera(); return@clickable }
                                    try {
                                        val f = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }.let { java.io.File(it, "cam_${System.currentTimeMillis()}.jpg") }
                                        cameraFile = f
                                        camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
                                    } catch (_: Throwable) { toast(ctx, t("No camera app found")) }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("📷", fontSize = 36.sp) }
                        }
                        items(items, key = { it.uri.toString() }) { g ->
                            val idx = picked.indexOf(g)
                            Box(Modifier.aspectRatio(1f).padding(1.dp).clickable { if (idx >= 0) picked.remove(g) else picked.add(g) }) {
                                AsyncImage(model = g.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (g.video) Text("▶", color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                                        .background(if (idx >= 0) style.accent else Color(0x33000000))
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (idx >= 0) Text("${idx + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                if (picked.isNotEmpty()) Button(
                    onClick = { sendUris(picked.map { it.uri to it.video }) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                ) { Text(t("Send") + " (${picked.size})") }
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(34.dp)).background(style.navBar)
                    .horizontalScroll(rememberScrollState()).padding(5.dp),
            ) {
                AttachTab(Ic.Image, t("Gallery"), true) { }
                AttachTab(Ic.Doc, t("File"), false) { pickFile.launch("*/*") }
                AttachTab(Ic.Info, t("Location"), false) {
                    locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                AttachTab(Ic.Account, t("Contact"), false) {
                    pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }
                AttachTab(Ic.Note, t("Music"), false) { pickMusic.launch("audio/*") }
                AttachTab(Ic.Movie, "GIF", false) { pickGif.launch("image/gif") }
            }
        }
    }
}

@Composable
private fun AttachTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val style = LocalStyle.current
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(28.dp)).background(if (selected) style.pill else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) style.pillFg else style.navBarFg, modifier = Modifier.size(28.dp))
        Text(label, fontSize = 14.sp, color = if (selected) style.pillFg else style.navBarFg, maxLines = 1)
    }
}
