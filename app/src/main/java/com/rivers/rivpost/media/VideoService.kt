package com.rivers.rivpost.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R

/** Puts the playing video in the phone's media panel (notification shade / lock screen). */
class VideoService : MediaSessionService() {
    companion object {
        var player: Player? = null
        private var instance: VideoService? = null

        fun start(c: Context, p: Player) {
            player = p
            try { c.startService(Intent(c, VideoService::class.java)) } catch (_: Throwable) { }
            instance?.attach()
        }

        fun stop(c: Context) {
            player = null
            instance?.let { it.session?.release(); it.session = null; it.stopSelf() }
        }
    }

    private var session: MediaSession? = null

    private fun attach() {
        val p = player ?: return
        if (session?.player === p) return
        session?.let { removeSession(it); it.release() }
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, p).setId("riv_video").setSessionActivity(open).build().also { addSession(it) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_notif) })
        attach()
        if (session == null) stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) { player?.pause(); stopSelf() }

    override fun onDestroy() {
        instance = null
        session?.release()
        session = null
        super.onDestroy()
    }
}
