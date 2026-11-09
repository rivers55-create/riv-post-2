package com.rivers.rivpost.tg

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.rivers.rivpost.data.Prefs
import dev.g000sha256.tdl.dto.LogStreamEmpty
import dev.g000sha256.tdl.dto.LogStreamFile
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A log you can send when something breaks: app crashes, errors, and Telegram's own log. */
object DebugLog {
    private lateinit var dir: File
    val appFile: File get() = File(dir, "riv_app_log.txt")
    val tdFile: File get() = File(dir, "riv_tdlib_log.txt")

    fun init(c: Context) {
        dir = File(c.filesDir, "logs").apply { mkdirs() }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("CRASH in ${t.name}: " + android.util.Log.getStackTraceString(e))
            prev?.uncaughtException(t, e)
        }
    }

    fun write(line: String) {
        if (!::dir.isInitialized) return
        try {
            val f = appFile
            if (f.length() > 1_000_000) f.writeText(f.readText().takeLast(400_000))
            f.appendText(SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()) + "  " + line + "\n")
        } catch (_: Throwable) { }
    }

    /** Also turn on Telegram's engine log (only while the switch is on). */
    fun applyTdlib(acc: TgAccount) {
        acc.scope.launch {
            if (Prefs.debugLog) {
                acc.client.setLogStream(logStream = LogStreamFile(path = tdFile.absolutePath, maxFileSize = 5_000_000, redirectStderr = false))
                acc.client.setLogVerbosityLevel(newVerbosityLevel = 3)
            } else {
                acc.client.setLogStream(logStream = LogStreamEmpty())
                acc.client.setLogVerbosityLevel(newVerbosityLevel = 1)
            }
        }
    }

    fun share(c: Context) {
        val files = listOf(appFile, tdFile).filter { it.exists() && it.length() > 0 }
        if (files.isEmpty()) { android.widget.Toast.makeText(c, "The log is empty", android.widget.Toast.LENGTH_SHORT).show(); return }
        val info = "Riv Post " + try { c.packageManager.getPackageInfo(c.packageName, 0).versionName } catch (_: Throwable) { "" } +
            " · Android " + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
        val uris = ArrayList(files.map { FileProvider.getUriForFile(c, c.packageName + ".files", it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .putExtra(Intent.EXTRA_TEXT, info)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(Intent.createChooser(i, "Send log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clear() { try { appFile.delete(); tdFile.delete() } catch (_: Throwable) { } }
}
