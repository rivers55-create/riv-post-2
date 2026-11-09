package com.rivers.rivpost.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FileType
import dev.g000sha256.tdl.dto.FileTypeAudio
import dev.g000sha256.tdl.dto.FileTypeDocument
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.FileTypeVoiceNote
import dev.g000sha256.tdl.dto.InputMessageLocation
import dev.g000sha256.tdl.dto.StorageStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- auto-download rules

object AutoDl {
    private fun network(): String = try {
        val cm = Tg.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wifi"
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> "roaming"
            else -> "mobile"
        }
    } catch (_: Throwable) { "wifi" }

    private fun netOk(): Boolean = when (network()) {
        "wifi" -> true
        "mobile" -> Prefs.dlMobile
        "roaming" -> Prefs.dlRoaming
        else -> false
    }

    fun photos() = Prefs.autoPhotos && netOk()
    fun gifs() = Prefs.autoGifs && netOk()
    fun voice() = Prefs.autoVoice && netOk()
    fun video(size: Long) = Prefs.autoVideos && netOk() && size in 1..(Prefs.autoVideoMb * 1_048_576L)
    fun file(size: Long) = Prefs.autoFiles && netOk() && size in 1..(Prefs.autoFileMb * 1_048_576L)
}

// ---------------------------------------------------------------- storage

private val typeNames: List<Pair<String, (FileType) -> Boolean>> = listOf(
    "Photos" to { it is FileTypePhoto }, "Videos" to { it is FileTypeVideo }, "Music" to { it is FileTypeAudio },
    "Voice" to { it is FileTypeVoiceNote }, "Files" to { it is FileTypeDocument },
)
private val ringColors = listOf(Color(0xFF42A5F5), Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF66BB6A), Color(0xFFFFA726), Color(0xFF9E9E9E))

@Composable
fun StorageScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<StorageStatistics?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (!busy) stats = (acc.client.getStorageStatistics(chatLimit = 30) as? TdlResult.Success)?.result }
    Page(t("Storage"), onBack) {
        val s = stats
        if (s == null) { CircularProgressIndicator(Modifier.padding(20.dp)); return@Page }
        val all = s.byChat.flatMap { it.byFileType.toList() }
        val parts = typeNames.map { (n, f) -> n to all.filter { f(it.fileType) }.sumOf { it.size } }
        val other = (s.size - parts.sumOf { it.second }).coerceAtLeast(0)
        val slices = parts + ("Other" to other)
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                var start = -90f
                val total = slices.sumOf { it.second }.coerceAtLeast(1)
                slices.forEachIndexed { i, (_, v) ->
                    val sweep = 360f * v / total
                    if (sweep > 0.3f) drawArc(ringColors[i], start, sweep - 1f, false, style = Stroke(26.dp.toPx()), size = Size(size.width, size.height))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.size(s.size), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${s.count} " + t("files"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        slices.forEachIndexed { i, (n, v) ->
            if (v <= 0) return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(ringColors[i]))
                Spacer(Modifier.width(10.dp))
                Text(t(n), modifier = Modifier.weight(1f))
                Text(Fmt.size(v), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i < typeNames.size) TextButton(enabled = !busy, onClick = {
                    busy = true
                    acc.scope.launch {
                        val ft: FileType = when (i) { 0 -> FileTypePhoto(); 1 -> FileTypeVideo(); 2 -> FileTypeAudio(); 3 -> FileTypeVoiceNote(); else -> FileTypeDocument() }
                        acc.client.optimizeStorage(size = 0, ttl = 0, count = 0, immunityDelay = 0, fileTypes = arrayOf(ft), chatIds = LongArray(0), excludeChatIds = LongArray(0), returnDeletedFileStatistics = false, chatLimit = 0)
                        withContext(Dispatchers.Main) { busy = false; toast(ctx, t("Cleared")) }
                    }
                }) { Text(t("Clear")) }
            }
        }
        SectionTitle(t("By chat"))
        for (c in s.byChat.sortedByDescending { it.size }.take(30)) {
            if (c.size <= 0) continue
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(acc.chats[c.chatId]?.title ?: if (c.chatId == 0L) t("Other") else c.chatId.toString(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.size(c.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.chatId != 0L) TextButton(enabled = !busy, onClick = {
                    busy = true
                    acc.scope.launch {
                        acc.client.optimizeStorage(size = 0, ttl = 0, count = 0, immunityDelay = 0, fileTypes = emptyArray(), chatIds = longArrayOf(c.chatId), excludeChatIds = LongArray(0), returnDeletedFileStatistics = false, chatLimit = 0)
                        withContext(Dispatchers.Main) { busy = false; toast(ctx, t("Cleared")) }
                    }
                }) { Text(t("Clear")) }
            }
        }
    }
}

// ---------------------------------------------------------------- scan QR to log in on another device

@Composable
fun QrScanScreen(acc: TgAccount, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (!it) onBack() }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }
    var found by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(found) {
        if (found.isEmpty()) return@LaunchedEffect
        when (val r = acc.client.confirmQrCodeAuthentication(link = found)) {
            is TdlResult.Success -> { done = true; toast(ctx, t("Logged in on") + " " + r.result.deviceModel.ifBlank { r.result.applicationName }) ; onBack() }
            is TdlResult.Failure -> { toast(ctx, r.message); kotlinx.coroutines.delay(2500); found = "" }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) AndroidView(
            factory = { c ->
                val view = PreviewView(c)
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val reader = com.google.zxing.qrcode.QRCodeReader()
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { img ->
                            try {
                                if (found.isEmpty()) {
                                    val plane = img.planes[0]
                                    val buf = plane.buffer
                                    val data = ByteArray(buf.remaining()).also { buf.get(it) }
                                    val src = com.google.zxing.PlanarYUVLuminanceSource(data, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                                    val res = reader.decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(src)))
                                    if (res.text.startsWith("tg://login")) found = res.text
                                }
                            } catch (_: Throwable) { } finally { img.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(c as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Throwable) { }
                }, ContextCompat.getMainExecutor(c))
                view
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, Color.White, RoundedCornerShape(20.dp)))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Ic.Back, null, tint = Color.White) }
            Text(
                t("On your computer open Telegram (desktop or web.telegram.org), choose \"Log in by QR code\", then point the camera at it."),
                color = Color.White, modifier = Modifier.padding(16.dp),
            )
        }
        if (found.isNotEmpty() && !done) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
}

// ---------------------------------------------------------------- pick a place on a map

@Composable
fun MapPickScreen(acc: TgAccount, chatId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    var center by remember { mutableStateOf<org.osmdroid.util.GeoPoint?>(null) }
    var mapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    var title by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                org.osmdroid.config.Configuration.getInstance().userAgentValue = "RivPost"
                org.osmdroid.config.Configuration.getInstance().osmdroidBasePath = java.io.File(c.cacheDir, "osm")
                org.osmdroid.views.MapView(c).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    val start = try {
                        val lm = c.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                        @Suppress("MissingPermission")
                        (lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER))
                    } catch (_: Throwable) { null }
                    controller.setCenter(org.osmdroid.util.GeoPoint(start?.latitude ?: 33.3152, start?.longitude ?: 44.3661))
                    mapRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text("📍", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color(0xAA000000), CircleShape)) { Icon(Ic.Back, null, tint = Color.White) }
            Text(t("Move the map to choose a place"), color = Color.White, modifier = Modifier.padding(start = 10.dp).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(8.dp))
        }
        Button(
            onClick = {
                val m = mapRef ?: return@Button
                val p = m.mapCenter
                acc.scope.launch {
                    val r = acc.client.sendMessage(chatId = chatId, inputMessageContent = InputMessageLocation(
                        location = dev.g000sha256.tdl.dto.Location(latitude = p.latitude, longitude = p.longitude, horizontalAccuracy = 0.0),
                    ))
                    withContext(Dispatchers.Main) { if (r is TdlResult.Failure) toast(ctx, r.message) else onBack() }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        ) { Text(t("Send this place")) }
        Text(
            "© OpenStreetMap", color = Color.Black, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xAAFFFFFF)).padding(3.dp),
        )
    }
}

// ---------------------------------------------------------------- earpiece for voice notes

/** Holding the phone to your ear switches voice notes to the earpiece. */
object Earpiece : SensorEventListener {
    private var sm: SensorManager? = null
    private var wake: android.os.PowerManager.WakeLock? = null
    var near = false
        private set

    fun start(c: Context) {
        if (!Prefs.earpiece || sm != null) return
        val s = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val p = s.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        sm = s
        s.registerListener(this, p, SensorManager.SENSOR_DELAY_NORMAL)
        try {
            val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wake = pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "riv:ear")
        } catch (_: Throwable) { }
    }

    fun stop(c: Context) {
        sm?.unregisterListener(this); sm = null
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Throwable) { }
        route(c, false)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val n = e.values[0] < (e.sensor.maximumRange.coerceAtMost(5f))
        if (n == near) return
        near = n
        route(Tg.app, n)
        try { if (n) wake?.acquire(10 * 60 * 1000L) else if (wake?.isHeld == true) wake?.release() } catch (_: Throwable) { }
    }

    private fun route(c: Context, ear: Boolean) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (ear) { am.mode = AudioManager.MODE_IN_COMMUNICATION; @Suppress("DEPRECATION") am.isSpeakerphoneOn = false }
            else { am.mode = AudioManager.MODE_NORMAL; @Suppress("DEPRECATION") am.isSpeakerphoneOn = true }
        } catch (_: Throwable) { }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

// ---------------------------------------------------------------- night mode schedule

fun nightNow(): Boolean {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val from = Prefs.nightFrom; val to = Prefs.nightTo
    return if (from <= to) h in from until to else h >= from || h < to
}

// ---------------------------------------------------------------- theme export / import

object ThemeFile {
    private val skip = Regex("(?i)(pin|hash|api|key|token|secret|pass|fb_|tmdb|smb|proxy|account|lock|vault|hidden|source|playlist|favorite|wallpaper$)")

    fun export(c: Context) {
        val sp = c.getSharedPreferences("riv_settings", Context.MODE_PRIVATE)
        val o = org.json.JSONObject()
        for ((k, v) in sp.all) {
            if (skip.containsMatchIn(k)) continue
            if (!(k.startsWith("c_") || k.startsWith("style") || k.contains("bubble") || k.contains("font") || k.contains("radius") ||
                    k.contains("nav") || k.contains("bar") || k.contains("theme") || k.contains("accent") || k.contains("oled") || k.contains("glass") ||
                    k.contains("avatar") || k.contains("tabs") || k.contains("text_size") || k.contains("wall"))) continue
            o.put(k, org.json.JSONObject().put("t", v?.javaClass?.simpleName ?: "").put("v", v))
        }
        val f = java.io.File(c.cacheDir, "RivPost-theme.rivtheme")
        f.writeText(o.toString(2))
        val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json").putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(android.content.Intent.createChooser(i, t("Share theme")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun import(c: Context, uri: android.net.Uri): Boolean = try {
        val txt = c.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val o = org.json.JSONObject(txt)
        val e = c.getSharedPreferences("riv_settings", Context.MODE_PRIVATE).edit()
        for (k in o.keys()) {
            if (skip.containsMatchIn(k)) continue
            val item = o.getJSONObject(k)
            when (item.optString("t")) {
                "Boolean" -> e.putBoolean(k, item.getBoolean("v"))
                "Integer" -> e.putInt(k, item.getInt("v"))
                "Float" -> e.putFloat(k, item.getDouble("v").toFloat())
                "Long" -> e.putLong(k, item.getLong("v"))
                else -> e.putString(k, item.optString("v"))
            }
        }
        e.commit()
        true
    } catch (_: Throwable) { false }
}

// ---------------------------------------------------------------- save edited photos to gallery

fun saveToGallery(c: Context, path: String) {
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "RivPost_" + System.currentTimeMillis() + ".jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Riv Post")
        }
        val uri = c.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        c.contentResolver.openOutputStream(uri)?.use { out -> java.io.File(path).inputStream().use { it.copyTo(out) } }
    } catch (_: Throwable) { }
}

// ---------------------------------------------------------------- send options (before sending media)

object SendOpts {
    var compress by mutableStateOf(false)
    var asFile by mutableStateOf(false)
    var spoiler by mutableStateOf(false)
    var captionAbove by mutableStateOf(false)
}


/** Re-encodes a video to 720p H.264 before sending (when "Compress" is on). */
suspend fun compressVideo(ctx: Context, path: String): String = withContext(Dispatchers.Main) {
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val out = java.io.File(ctx.cacheDir, "cmp_" + System.currentTimeMillis() + ".mp4")
            val item = androidx.media3.transformer.EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
                .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(androidx.media3.effect.Presentation.createForHeight(720)))).build()
            val tr = androidx.media3.transformer.Transformer.Builder(ctx).setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                .addListener(object : androidx.media3.transformer.Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        if (cont.isActive) cont.resume(out.absolutePath) {}
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        if (cont.isActive) cont.resume(path) {}
                    }
                }).build()
            tr.start(item, out.absolutePath)
        } catch (_: Throwable) { if (cont.isActive) cont.resume(path) {} }
    }
}

fun documentContent(path: String, caption: String = ""): dev.g000sha256.tdl.dto.InputMessageContent = dev.g000sha256.tdl.dto.InputMessageDocument(
    document = dev.g000sha256.tdl.dto.InputDocument(document = dev.g000sha256.tdl.dto.InputFileLocal(path = path), thumbnail = null, disableContentTypeDetection = true),
    caption = if (caption.isBlank()) null else dev.g000sha256.tdl.dto.FormattedText(text = caption, entities = emptyArray()),
)
