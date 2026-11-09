package com.rivers.rivpost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.data.t

private val Swatches = listOf(
    0xFF000000, 0xFF120B1C, 0xFF17212B, 0xFF0B141A, 0xFF1C1C1E, 0xFF2A0D4D, 0xFF3A2F4A, 0xFF5A4E6B,
    0xFFFFFFFF, 0xFFFAF8FD, 0xFFF0F2F5, 0xFFEFEAE2, 0xFFD6E2EC, 0xFFEDE7F6, 0xFFDDD5E8, 0xFFB0A8BC,
    0xFF7C4DFF, 0xFFAB47BC, 0xFFB388FF, 0xFF5E35B1, 0xFF2F80ED, 0xFF2B5278, 0xFF64B5F6, 0xFF00ACC1,
    0xFF00A884, 0xFF005C4B, 0xFF25D366, 0xFF43A047, 0xFFD9FDD3, 0xFFEFFDDE, 0xFFC0A060, 0xFFAD8A56,
    0xFFFF8A3D, 0xFFFFB300, 0xFFE5508A, 0xFFE53935, 0xFF8D6E63, 0xFF607D8B, 0xFF9E9E9E, 0xFF424242,
).map { Color(it) }

/** Choose a color: swatches, hue/saturation/brightness sliders, or a hex code. Null = automatic. */
@Composable
fun ColorPickerDialog(title: String, initial: Color?, onPick: (Color?) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: Color(0xFF7C4DFF)
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf("") }
    val cur = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    fun set(c: Color) {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), a)
        h = a[0]; s = a[1]; v = a[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(cur)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#%06X".format(cur.toArgb() and 0xFFFFFF), color = onColor(cur), fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in Swatches) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(c)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { set(c) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(t("Hue"), fontSize = 13.sp)
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..360f)
                Text(t("Saturation"), fontSize = 13.sp)
                Slider(value = s, onValueChange = { s = it }, valueRange = 0f..1f)
                Text(t("Brightness"), fontSize = 13.sp)
                Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hex, onValueChange = { x -> hex = x.filter { it.isLetterOrDigit() }.take(6) },
                        label = { Text("Hex") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        hex.toLongOrNull(16)?.let { if (hex.length == 6) set(Color(0xFF000000 or it)) }
                    }) { Text(t("Use")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(cur) }) { Text(t("Save")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text(t("Automatic")) }
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
            }
        },
    )
}

/** A settings row showing a color; tap to change. 0 = automatic. */
@Composable
fun ColorRow(title: String, value: Int, auto: Color, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value == 0) Text(t("Auto") + "  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (value != 0) Color(value) else auto)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
    if (open) ColorPickerDialog(
        title, if (value != 0) Color(value) else auto,
        onPick = { c -> onChange(c?.toArgb() ?: 0); open = false },
        onDismiss = { open = false },
    )
}
