package com.rivers.rivpost.tg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rivers.rivpost.MainActivity
import com.rivers.rivpost.R
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import dev.g000sha256.tdl.dto.Message
import java.util.concurrent.ConcurrentHashMap

object Notifier {
    const val CH_MSG = "messages"
    const val CH_SERVICE = "service"
    const val SERVICE_ID = 1

    private val lines = ConcurrentHashMap<String, MutableList<String>>()

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, t("Messages"), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, t("Connection"), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
    }

    private fun key(a: TgAccount, chatId: Long) = "${a.index}:$chatId"
    private fun id(a: TgAccount, chatId: Long) = (chatId xor (a.index.toLong() shl 48)).hashCode()

    fun onNewMessage(c: Context, a: TgAccount, m: Message) {
        if (m.isOutgoing || !Prefs.background) return
        if (System.currentTimeMillis() / 1000 - m.date > 3600) return
        if (Tg.foreground && a.openChatId == m.chatId && Tg.activeIndex.value == a.index) return
        val chat = a.chats[m.chatId] ?: return
        if (a.isMuted(chat)) return

        var text = if (Prefs.notifPreview) Fmt.preview(a, m) else t("New message")
        if (Prefs.notifPreview && chat.isGroup) {
            val who = a.senderName(m.senderId)
            if (who.isNotBlank()) text = "$who: $text"
        }
        val k = key(a, m.chatId)
        val list = lines.getOrPut(k) { mutableListOf() }
        synchronized(list) {
            list.add(text)
            while (list.size > 6) list.removeAt(0)
        }

        val open = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("acc", a.index)
            putExtra("chat", m.chatId)
        }
        val pi = PendingIntent.getActivity(
            c, id(a, m.chatId), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle()
        synchronized(list) { for (l in list) style.addLine(l) }
        val n = NotificationCompat.Builder(c, CH_MSG)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(chat.title)
            .setContentText(text)
            .setStyle(style)
            .setNumber(list.size)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(m.date * 1000L)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(id(a, m.chatId), n)
        } catch (_: SecurityException) { }
    }

    fun clear(c: Context, a: TgAccount, chatId: Long) {
        lines.remove(key(a, chatId))
        try { NotificationManagerCompat.from(c).cancel(id(a, chatId)) } catch (_: Throwable) { }
    }

    fun serviceNotification(c: Context): android.app.Notification {
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Riv Post")
            .setContentText(t("Connected for new messages"))
            .setOngoing(true)
            .setContentIntent(pi)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(
                0, t("Hide this notice"),
                PendingIntent.getActivity(
                    c, 1,
                    Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CH_SERVICE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
