package com.rivers.rivpost.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.Tg
import com.rivers.rivpost.tg.TgAccount

/** Keeps the microphone and the call alive while the app is in the background. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CallNotif.channels(this)
        val u = Calls.ui.value
        // Android requires startForeground() right away, even if the call already ended.
        val n = if (u != null) CallNotif.ongoing(this, u) else CallNotif.placeholder(this)
        var ok = false
        if (Build.VERSION.SDK_INT >= 30) {
            for (type in listOf(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                0,
            )) {
                try {
                    if (type == 0) startForeground(CallNotif.ONGOING_ID, n) else ServiceCompat.startForeground(this, CallNotif.ONGOING_ID, n, type)
                    ok = true; break
                } catch (_: Throwable) { }
            }
        } else {
            try { startForeground(CallNotif.ONGOING_ID, n); ok = true } catch (_: Throwable) { }
        }
        running = true
        starting = false
        if (pendingStop || u == null || u.ended || !ok) {
            pendingStop = false
            running = false
            try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile private var running = false
        @Volatile private var starting = false
        @Volatile private var pendingStop = false

        fun start(c: Context) {
            if (running || starting) { pendingStop = false; return }
            try {
                starting = true
                pendingStop = false
                val i = Intent(c, CallService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (_: Throwable) {
            } finally {
                // onStartCommand will run soon; it clears this.
                android.os.Handler(c.mainLooper).postDelayed({ starting = false }, 3000)
            }
        }

        /** Never stops the service before it has called startForeground, which would crash the app. */
        fun stop(c: Context) {
            if (!running) { pendingStop = true; return }
            try { c.stopService(Intent(c, CallService::class.java)) } catch (_: Throwable) { }
            running = false
        }
    }
}

/** Hang up / decline from the notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotif.ACTION_HANGUP -> Calls.hangUp()
        }
    }
}

object CallNotif {
    const val CH_CALLS = "calls_incoming"
    const val CH_ONGOING = "calls_ongoing"
    const val INCOMING_ID = 7701
    const val ONGOING_ID = 7702
    const val ACTION_HANGUP = "com.rivers.rivpost.CALL_HANGUP"

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_CALLS, t("Incoming calls"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, t("Ongoing calls"), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
    }

    private fun openIntent(c: Context, extra: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            c, code,
            Intent(c, MainActivity::class.java).putExtra(extra, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun hangupIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 33, Intent(c, CallActionReceiver::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun person(acc: TgAccount?, userId: Long): Person =
        Person.Builder().setName(acc?.userName(userId)?.ifBlank { null } ?: t("Unknown")).setImportant(true).build()

    fun showIncoming(c: Context, acc: TgAccount, callId: Int, userId: Long) {
        channels(c)
        val p = person(acc, userId)
        val b = NotificationCompat.Builder(c, CH_CALLS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(p.name)
            .setContentText(t("Incoming call"))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openIntent(c, "call_open", 31))
            .setFullScreenIntent(openIntent(c, "call_open", 32), true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(p, hangupIntent(c), openIntent(c, "call_accept", 34)))
        try { c.getSystemService(NotificationManager::class.java).notify(INCOMING_ID, b.build()) } catch (_: Throwable) { }
    }

    fun placeholder(c: Context) = NotificationCompat.Builder(c, CH_ONGOING)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle(t("Voice call"))
        .setOngoing(true)
        .build()

    fun cancelIncoming(c: Context) {
        try { c.getSystemService(NotificationManager::class.java).cancel(INCOMING_ID) } catch (_: Throwable) { }
    }

    fun ongoing(c: Context, u: CallUi) = NotificationCompat.Builder(c, CH_ONGOING)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle(Tg.get(u.acc).userName(u.userId))
        .setContentText(t("Voice call"))
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setOngoing(true)
        .setContentIntent(openIntent(c, "call_open", 35))
        .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(Tg.get(u.acc), u.userId), hangupIntent(c)))
        .build()
}
