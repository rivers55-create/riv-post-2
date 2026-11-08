package com.rivers.rivpost.tg

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.rivers.rivpost.data.Prefs

/** Keeps the app running so new messages arrive while it is closed. */
class TgService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.channels(this)
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
            ServiceCompat.startForeground(this, Notifier.SERVICE_ID, Notifier.serviceNotification(this), type)
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Prefs.background || !Prefs.hasKeys) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Tg.accounts.value.isEmpty()) Tg.startAll()
        return START_STICKY
    }
}
