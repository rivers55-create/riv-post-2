package com.rivers.rivpost.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R

/** Next/previous in the notification go through our own queue. */
private class QueuePlayer(p: Player) : ForwardingPlayer(p) {
    private val extra = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*extra).build()

    override fun isCommandAvailable(command: Int): Boolean = command in extra || super.isCommandAvailable(command)
    override fun seekToNext() { Music.next() }
    override fun seekToNextMediaItem() { Music.next() }
    override fun seekToPrevious() { Music.previous() }
    override fun seekToPreviousMediaItem() { Music.previous() }
}

class MusicService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Music.init(applicationContext)
        val p = Music.exo ?: return
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).putExtra("open_player", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, QueuePlayer(p)).setSessionActivity(open).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_music) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
