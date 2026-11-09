package com.rivers.rivpost.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays one voice message at a time. */
object VoicePlayer {
    var playingFile by mutableIntStateOf(0)
    var position by mutableFloatStateOf(0f)
    var paused by mutableStateOf(false)
    private var mp: MediaPlayer? = null
    private var job: Job? = null
    private val scope = MainScope()

    fun toggle(fileId: Int, path: String) {
        val cur = mp
        if (playingFile == fileId && cur != null) {
            try {
                if (cur.isPlaying) { cur.pause(); paused = true } else { cur.start(); paused = false }
            } catch (_: Throwable) { stop() }
            return
        }
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(path)
            p.prepare()
            p.setOnCompletionListener { stop() }
            p.start()
            mp = p
            playingFile = fileId
            com.rivers.rivpost.ui.Earpiece.start(com.rivers.rivpost.tg.Tg.app)
            paused = false
            job = scope.launch {
                while (isActive && mp === p) {
                    try {
                        if (p.duration > 0) position = p.currentPosition.toFloat() / p.duration
                    } catch (_: Throwable) { }
                    delay(150)
                }
            }
        } catch (_: Throwable) {
            stop()
        }
    }

    fun stop() {
        com.rivers.rivpost.ui.Earpiece.stop(com.rivers.rivpost.tg.Tg.app)
        job?.cancel()
        job = null
        try { mp?.release() } catch (_: Throwable) { }
        mp = null
        playingFile = 0
        position = 0f
        paused = false
    }
}

/** Records a voice message (OGG/Opus on Android 10+, AAC before that). */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    var file: java.io.File? = null
        private set
    var startedAt = 0L
        private set
    val isOpus: Boolean get() = Build.VERSION.SDK_INT >= 29

    fun start(): Boolean {
        cancel()
        return try {
            val dir = java.io.File(ctx.cacheDir, "voice").apply { mkdirs() }
            val f = java.io.File(dir, "voice_${System.currentTimeMillis()}." + if (isOpus) "ogg" else "m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (isOpus) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                r.setAudioSamplingRate(48000)
                r.setAudioEncodingBitRate(32000)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(64000)
            }
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (_: Throwable) {
            cancel()
            false
        }
    }

    /** Stops and returns the file and its length in seconds, or null if too short. */
    fun finish(): Pair<java.io.File, Int>? {
        val r = rec ?: return null
        val f = file ?: return null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        try { r.stop() } catch (_: Throwable) { }
        try { r.release() } catch (_: Throwable) { }
        rec = null
        file = null
        if (secs < 1 || !f.exists()) { f.delete(); return null }
        return f to secs
    }

    fun cancel() {
        try { rec?.stop() } catch (_: Throwable) { }
        try { rec?.release() } catch (_: Throwable) { }
        rec = null
        file?.delete()
        file = null
    }
}
