package com.rivers.rivpost.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rivers.rivpost.data.t
import kotlin.math.roundToInt

private class Stroke2(val color: Color, val width: Float, val pts: MutableList<Offset>)  // points in image pixels
private class Label(var text: String, var color: Color, var pos: Offset, val size: Float)    // pos in image pixels

private val EditColors = listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0))

private fun loadScaled(path: String, max: Int = 2560): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (o.outWidth / sample > max || o.outHeight / sample > max) sample *= 2
    val b = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // respect the photo's rotation tag
    val deg = try {
        when (androidx.exifinterface.media.ExifInterface(path).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
    } catch (_: Throwable) { 0f }
    return if (deg == 0f) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)
}

/**
 * Edit a photo before sending: rotate, crop, draw, add text and a caption.
 * Returns the edited file and caption.
 */
@Composable
fun PhotoEditor(path: String, caption: String, onDone: (String, String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var bmp by remember(path) { mutableStateOf(loadScaled(path)) }
    val strokes = remember { mutableStateListOf<Stroke2>() }
    val labels = remember { mutableStateListOf<Label>() }
    var mode by remember { mutableStateOf("") }      // "", draw, crop
    var color by remember { mutableStateOf(Color(0xFFE53935)) }
    var brush by remember { mutableStateOf(12f) }
    var crop by remember { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }  // l t r b (0..1)
    var textDialog by remember { mutableStateOf(false) }
    var cap by remember { mutableStateOf(caption) }
    var redraw by remember { mutableStateOf(0) }

    /** Paint drawings and text onto the picture itself. */
    fun bake(): Bitmap? {
        val b = bmp ?: return null
        if (strokes.isEmpty() && labels.isEmpty()) return b
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(out)
        for (s in strokes) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = s.color.toArgb(); style = Paint.Style.STROKE; strokeWidth = s.width
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val path2 = android.graphics.Path()
            s.pts.forEachIndexed { i, o -> if (i == 0) path2.moveTo(o.x, o.y) else path2.lineTo(o.x, o.y) }
            if (s.pts.size == 1) path2.lineTo(s.pts[0].x + 0.1f, s.pts[0].y)
            c.drawPath(path2, p)
        }
        for (l in labels) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = l.color.toArgb(); textSize = l.size; typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(l.size / 10f, 0f, 0f, if (l.color == Color.Black) 0x88FFFFFF.toInt() else 0x99000000.toInt())
                textAlign = Paint.Align.CENTER
            }
            var y = l.pos.y
            for (line in l.text.split('\n')) { c.drawText(line, l.pos.x, y, p); y += l.size * 1.2f }
        }
        strokes.clear(); labels.clear()
        return out
    }

    BackHandler { if (mode != "") mode = "" else onCancel() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Ic.Close, null, tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (mode == "draw" && strokes.isNotEmpty()) TextButton(onClick = { strokes.removeAt(strokes.lastIndex) }) { Text(t("Undo"), color = Color.White) }
                if (mode == "crop") {
                    TextButton(onClick = { crop = floatArrayOf(0f, 0f, 1f, 1f) }) { Text(t("Reset"), color = Color.White) }
                    TextButton(onClick = {
                        val b = bake()
                        if (b != null) {
                            val l = (crop[0] * b.width).roundToInt(); val tp = (crop[1] * b.height).roundToInt()
                            val w = ((crop[2] - crop[0]) * b.width).roundToInt().coerceAtLeast(16)
                            val h = ((crop[3] - crop[1]) * b.height).roundToInt().coerceAtLeast(16)
                            bmp = Bitmap.createBitmap(b, l.coerceIn(0, b.width - w), tp.coerceIn(0, b.height - h), w.coerceAtMost(b.width), h.coerceAtMost(b.height))
                        }
                        crop = floatArrayOf(0f, 0f, 1f, 1f); mode = ""
                    }) { Text(t("Apply"), color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold) }
                }
            }
            // ---------- picture ----------
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val b = bmp
                if (b == null) { Text(t("Couldn't open the photo"), color = Color.White); return@BoxWithConstraints }
                val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
                val scale = minOf(boxW / b.width, boxH / b.height)
                val w = b.width * scale; val h = b.height * scale
                val wDp = with(density) { w.toDp() }; val hDp = with(density) { h.toDp() }
                @Suppress("UNUSED_VARIABLE") val r = redraw
                Box(Modifier.size(wDp, hDp)) {
                    Image(b.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(mode, color, brush) {
                            if (mode != "draw") return@pointerInput
                            detectDragGestures(
                                onDragStart = { o -> strokes.add(Stroke2(color, brush / scale * density.density / 2f, mutableListOf(o / scale))); redraw++ },
                            ) { ch, _ -> strokes.lastOrNull()?.pts?.add(ch.position / scale); redraw++ }
                        },
                    ) {
                        @Suppress("UNUSED_VARIABLE") val rr = redraw
                        for (s in strokes) {
                            val p = Path()
                            s.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x * scale, o.y * scale) else p.lineTo(o.x * scale, o.y * scale) }
                            if (s.pts.size == 1) p.lineTo(s.pts[0].x * scale + 0.1f, s.pts[0].y * scale)
                            drawPath(p, s.color, style = Stroke(s.width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        if (mode == "crop") {
                            val l = crop[0] * size.width; val tp = crop[1] * size.height; val rt = crop[2] * size.width; val bt = crop[3] * size.height
                            val dim = Color(0x99000000)
                            drawRect(dim, Offset.Zero, Size(size.width, tp))
                            drawRect(dim, Offset(0f, bt), Size(size.width, size.height - bt))
                            drawRect(dim, Offset(0f, tp), Size(l, bt - tp))
                            drawRect(dim, Offset(rt, tp), Size(size.width - rt, bt - tp))
                            drawRect(Color.White, Offset(l, tp), Size(rt - l, bt - tp), style = Stroke(2.dp.toPx()))
                            for (i in 1..2) {
                                drawLine(Color(0x88FFFFFF), Offset(l + (rt - l) * i / 3, tp), Offset(l + (rt - l) * i / 3, bt))
                                drawLine(Color(0x88FFFFFF), Offset(l, tp + (bt - tp) * i / 3), Offset(rt, tp + (bt - tp) * i / 3))
                            }
                        }
                    }
                    // text labels (drag to move, tap to delete)
                    for (lb in labels) {
                        Text(
                            lb.text, color = lb.color, fontSize = with(density) { (lb.size * scale).toSp() }, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset { IntOffset((lb.pos.x * scale).roundToInt() - 40, (lb.pos.y * scale - lb.size * scale).roundToInt()) }
                                .pointerInput(lb) {
                                    detectDragGestures { ch, d -> ch.consume(); lb.pos = lb.pos + d / scale; redraw++ }
                                }.clickable { labels.remove(lb) },
                        )
                    }
                    if (mode == "crop") {
                        // drag anywhere: the nearest corner follows the finger
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                var corner = 0
                                detectDragGestures(
                                    onDragStart = { o ->
                                        val x = o.x / size.width; val y = o.y / size.height
                                        val cs = listOf(crop[0] to crop[1], crop[2] to crop[1], crop[0] to crop[3], crop[2] to crop[3])
                                        corner = cs.indices.minBy { val (cx, cy) = cs[it]; (cx - x) * (cx - x) + (cy - y) * (cy - y) }
                                    },
                                ) { ch, _ ->
                                    val x = (ch.position.x / size.width).coerceIn(0f, 1f); val y = (ch.position.y / size.height).coerceIn(0f, 1f)
                                    val c = crop.copyOf()
                                    when (corner) {
                                        0 -> { c[0] = minOf(x, c[2] - 0.05f); c[1] = minOf(y, c[3] - 0.05f) }
                                        1 -> { c[2] = maxOf(x, c[0] + 0.05f); c[1] = minOf(y, c[3] - 0.05f) }
                                        2 -> { c[0] = minOf(x, c[2] - 0.05f); c[3] = maxOf(y, c[1] + 0.05f) }
                                        else -> { c[2] = maxOf(x, c[0] + 0.05f); c[3] = maxOf(y, c[1] + 0.05f) }
                                    }
                                    crop = c
                                }
                            },
                        )
                    }
                }
            }
            // ---------- colors / brush ----------
            if (mode == "draw") Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                for (c in EditColors) {
                    Box(Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(c).border(if (c == color) 3.dp else 1.dp, Color.White, CircleShape).clickable { color = c })
                }
                Spacer(Modifier.width(8.dp))
                for (bw in listOf(6f, 12f, 24f)) {
                    Box(Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(if (brush == bw) Color(0x55FFFFFF) else Color.Transparent).clickable { brush = bw }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size((bw / 1.5f).dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            if (mode == "crop") Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                for ((label, ratio) in listOf(t("Free") to 0f, "1:1" to 1f, "4:3" to 4f / 3f, "16:9" to 16f / 9f)) {
                    TextButton(onClick = {
                        val b = bmp ?: return@TextButton
                        if (ratio == 0f) { crop = floatArrayOf(0f, 0f, 1f, 1f); return@TextButton }
                        val imgR = b.width.toFloat() / b.height
                        crop = if (imgR > ratio) { val wf = ratio / imgR; floatArrayOf((1 - wf) / 2, 0f, (1 + wf) / 2, 1f) }
                        else { val hf = imgR / ratio; floatArrayOf(0f, (1 - hf) / 2, 1f, (1 + hf) / 2) }
                    }) { Text(label, color = Color.White) }
                }
            }
            // ---------- caption ----------
            TextField(
                value = cap, onValueChange = { cap = it }, placeholder = { Text(t("Add a caption…")) }, maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1E), unfocusedContainerColor = Color(0xFF1C1C1E),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(20.dp)),
            )
            // ---------- tools ----------
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool(Ic.Crop, mode == "crop") { if (mode == "crop") mode = "" else { strokes.size; mode = "crop" } }
                Tool(Ic.Rotate, false) {
                    val bb = bake()
                    if (bb != null) bmp = Bitmap.createBitmap(bb, 0, 0, bb.width, bb.height, Matrix().apply { postRotate(-90f) }, true)
                }
                Tool(Ic.Brush, mode == "draw") { mode = if (mode == "draw") "" else "draw" }
                Tool(Ic.TextIcon, false) { mode = ""; textDialog = true }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF2196F3)).clickable {
                        val b = bake() ?: return@clickable
                        val out = java.io.File(ctx.cacheDir, "edit_" + System.currentTimeMillis() + ".jpg")
                        try {
                            out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            onDone(out.absolutePath, cap)
                        } catch (_: Throwable) { toast(ctx, t("Couldn't save the photo")) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.Check, null, tint = Color.White) }
            }
        }
        if (textDialog) {
            var txt by remember { mutableStateOf("") }
            var col by remember { mutableStateOf(Color.White) }
            AlertDialog(
                onDismissRequest = { textDialog = false },
                title = { Text(t("Add text")) },
                text = {
                    Column {
                        OutlinedTextField(value = txt, onValueChange = { txt = it }, maxLines = 4)
                        Row(Modifier.padding(top = 8.dp)) {
                            for (c in EditColors) Box(Modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(c).border(if (c == col) 3.dp else 1.dp, Color.Gray, CircleShape).clickable { col = c })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val b = bmp
                        if (txt.isNotBlank() && b != null) labels.add(Label(txt.trim(), col, Offset(b.width / 2f, b.height / 2f), b.width / 12f))
                        textDialog = false
                    }) { Text(t("Add")) }
                },
                dismissButton = { TextButton(onClick = { textDialog = false }) { Text(t("Cancel")) } },
            )
        }
    }
}

@Composable
private fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(4.dp).size(48.dp).clip(CircleShape).background(if (on) Color(0x44FFFFFF) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White) }
}
