package com.rivers.rivpost.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import kotlinx.coroutines.launch

/** Built-in camera: full-quality photos sent straight to the chat. */
@Composable
fun CameraScreen(acc: TgAccount, chatId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onClose() }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) { toast(ctx, t("Camera permission is needed")); onClose() }
    }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }
    var back by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                            if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview, capture,
                        )
                    } catch (_: Throwable) { }
                }, ContextCompat.getMainExecutor(ctx))
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color(0x66000000), CircleShape)) { Icon(Ic.Close, null, tint = Color.White) }
            IconButton(onClick = { flash = !flash }, modifier = Modifier.background(Color(0x66000000), CircleShape)) {
                Text(if (flash) "⚡" else "⚡̸", color = Color.White, fontSize = 20.sp)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp))
            Box(
                Modifier.size(78.dp).clip(CircleShape).border(5.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (busy) Color.Gray else Color.White)
                    .clickable(enabled = !busy) {
                        busy = true
                        val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                        val f = java.io.File(dir, "riv_${System.currentTimeMillis()}.jpg")
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    acc.scope.launch {
                                        val r = acc.client.sendMessage(chatId = chatId, inputMessageContent = photoContent(f.absolutePath))
                                        if (r is TdlResult.Failure) android.os.Handler(android.os.Looper.getMainLooper()).post { toast(ctx, r.message) }
                                    }
                                    busy = false
                                    onClose()
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    toast(ctx, t("Couldn't take the photo"))
                                }
                            },
                        )
                    },
            )
            IconButton(onClick = { back = !back }, modifier = Modifier.size(48.dp).background(Color(0x66000000), CircleShape)) {
                Icon(Ic.Refresh, null, tint = Color.White)
            }
        }
    }
}
