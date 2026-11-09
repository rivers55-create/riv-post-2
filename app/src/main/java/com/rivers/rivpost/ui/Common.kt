package com.rivers.rivpost.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.File

private val AvatarColors = listOf(
    Color(0xFFE17076), Color(0xFFFAA74A), Color(0xFF7F6CDB), Color(0xFF6BCB77),
    Color(0xFF4FB2E0), Color(0xFF5BA7D9), Color(0xFFDB6FB3), Color(0xFF8E85EE),
)

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    val a = parts[0].codePointAt(0)
    val first = String(Character.toChars(a))
    val second = if (parts.size > 1) String(Character.toChars(parts[1].codePointAt(0))) else ""
    return (first + second).uppercase()
}

fun miniBitmap(data: ByteArray?): ImageBitmap? = try {
    if (data == null || data.isEmpty()) null
    else BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
} catch (_: Throwable) { null }

/** Watches a TDLib file and returns its latest state. */
@Composable
fun rememberFile(acc: TgAccount, f: File?, autoDownload: Boolean, priority: Int = 1): File? {
    if (f == null) return null
    val flow = remember(acc, f.id) { acc.fileFlow(f) }
    val cur by flow.collectAsState()
    LaunchedEffect(cur.id, autoDownload) {
        if (autoDownload) acc.download(cur, priority)
    }
    return cur
}

@Composable
fun Avatar(acc: TgAccount, id: Long, title: String, photo: File?, mini: ByteArray?, size: Dp) {
    val color = AvatarColors[((id % AvatarColors.size).toInt() + AvatarColors.size) % AvatarColors.size]
    val f = rememberFile(acc, photo, autoDownload = true, priority = 2)
    val mb = remember(mini) { miniBitmap(mini) }
    Box(
        Modifier.size(size).clip(LocalStyle.current.avatarShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (f != null && f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
            AsyncImage(
                model = java.io.File(f.local.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (mb != null) {
            Image(mb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initials(title), color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.38f).sp, maxLines = 1,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ClickRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun toast(c: Context, s: String) {
    Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
}

/** Opens a downloaded file in another app (video player, PDF reader…). */
fun openFile(c: Context, path: String, mime: String?) {
    try {
        val f = java.io.File(path)
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val type = mime?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
            ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        c.startActivity(Intent.createChooser(i, t("Open with")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        toast(c, t("No app can open this file"))
    }
}

/** Restarts the app (used after changing the API keys). */
fun restartApp(c: Context) {
    val i = c.packageManager.getLaunchIntentForPackage(c.packageName)
    if (i != null) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        c.startActivity(i)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
}
