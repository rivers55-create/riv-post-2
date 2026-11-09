package com.rivers.rivpost.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Fmt
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageVideoNote
import dev.g000sha256.tdl.dto.InputVideoNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Record a round video message (hold the mic button to open this). */
@SuppressLint("MissingPermission")
@Composable
fun VideoNoteScreen(acc: TgAccount, chatId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(has(Manifest.permission.CAMERA) && has(Manifest.permission.RECORD_AUDIO)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.all { it } || (has(Manifest.permission.CAMERA) && has(Manifest.permission.RECORD_AUDIO))
        if (!granted) { toast(ctx, t("Camera and microphone permission are needed")); onClose() }
    }
    LaunchedEffect(Unit) { if (!granted) perm.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }

    var front by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var secs by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    val capture = remember {
        val rec = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.SD, Quality.LOWEST))).build()
        VideoCapture.withOutput(rec)
    }
    val out = remember { java.io.File(ctx.cacheDir, "vnote_" + System.currentTimeMillis() + ".mp4") }

    fun stop(cancel: Boolean) {
        cancelled = cancel
        recording?.stop()
        recording = null
    }

    fun start() {
        val opts = FileOutputOptions.Builder(out).build()
        val started = System.currentTimeMillis()
        recording = capture.output.prepareRecording(ctx, opts).withAudioEnabled()
            .start(ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    val dur = ((System.currentTimeMillis() - started) / 1000).toInt().coerceAtLeast(1)
                    if (cancelled || ev.hasError() && ev.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                        if (!cancelled) toast(ctx, t("Couldn't record video"))
                        out.delete()
                        onClose()
                    } else {
                        sending = true
                        acc.scope.launch {
                            val r = acc.client.sendMessage(
                                chatId = chatId, replyTo = null,
                                inputMessageContent = InputMessageVideoNote(
                                    videoNote = InputVideoNote(videoNote = InputFileLocal(path = out.absolutePath), thumbnail = null, duration = dur, length = 384),
                                    selfDestructType = null,
                                ),
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (r is TdlResult.Failure) toast(ctx, t("Couldn't send") + ": " + r.message)
                                onClose()
                            }
                        }
                    }
                }
            }
    }

    LaunchedEffect(recording) {
        secs = 0
        while (recording != null) {
            delay(1000)
            secs++
            if (secs >= 60) stop(false)
        }
    }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }
    BackHandler { if (recording != null) stop(true) else onClose() }

    Box(Modifier.fillMaxSize().background(Color(0xEE000000))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (recording != null) stop(true) else onClose() }) { Icon(Ic.Close, null, tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text(if (recording != null) "● " + Fmt.duration(secs) + " / 1:00" else t("Video message"), color = Color.White, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(300.dp).clip(CircleShape).border(3.dp, if (recording != null) Color(0xFFE53935) else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) AndroidView(
                    factory = { c -> PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    update = { view ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            try {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    ctx as LifecycleOwner,
                                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, capture,
                                )
                            } catch (_: Throwable) { }
                        }, ContextCompat.getMainExecutor(ctx))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (sending) CircularProgressIndicator(color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (recording == null) front = !front }) { Icon(Ic.Refresh, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Box(
                    Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(
                        if (recording != null) RoundedCornerShape(8.dp) else CircleShape,
                    ).background(Color(0xFFE53935)).clickable(enabled = !sending) { if (recording == null) start() else stop(false) },
                )
                Spacer(Modifier.size(48.dp))
            }
            Text(if (recording != null) t("Tap to send") else t("Tap to start"), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 16.dp).height(22.dp))
        }
    }
}
