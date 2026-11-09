package com.rivers.rivpost.tg

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InputMessageLiveLocation
import dev.g000sha256.tdl.dto.LiveLocation
import kotlinx.coroutines.launch

/** Sends a live location and keeps it updated while Riv Post is running. */
object LiveLoc {
    private class Share(val acc: TgAccount, val chatId: Long, val msgId: Long, val until: Long)
    private val shares = mutableListOf<Share>()
    private var listening = false

    private fun loc(l: Location, period: Int) = LiveLocation(
        location = dev.g000sha256.tdl.dto.Location(latitude = l.latitude, longitude = l.longitude, horizontalAccuracy = l.accuracy.toDouble()),
        livePeriod = period, heading = 0, proximityAlertRadius = 0,
    )

    @SuppressLint("MissingPermission")
    fun start(c: Context, acc: TgAccount, chatId: Long, seconds: Int, first: Location, onError: (String) -> Unit) {
        acc.scope.launch {
            val r = acc.client.sendMessage(chatId = chatId, inputMessageContent = InputMessageLiveLocation(location = loc(first, seconds)))
            when (r) {
                is TdlResult.Success -> synchronized(shares) {
                    shares.add(Share(acc, chatId, r.result.id, System.currentTimeMillis() + seconds * 1000L))
                }
                is TdlResult.Failure -> android.os.Handler(Looper.getMainLooper()).post { onError(r.message) }
            }
            android.os.Handler(Looper.getMainLooper()).post { listen(c) }
        }
    }

    private val listener = LocationListener { l -> push(l) }

    @SuppressLint("MissingPermission")
    private fun listen(c: Context) {
        if (listening) return
        val lm = c.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestLocationUpdates(provider, 30_000L, 20f, listener, Looper.getMainLooper())
            listening = true
            appCtx = c.applicationContext
        } catch (_: Throwable) { }
    }

    private var appCtx: Context? = null

    private fun push(l: Location) {
        val now = System.currentTimeMillis()
        val live = synchronized(shares) { shares.removeAll { it.until < now }; shares.toList() }
        if (live.isEmpty()) {
            try { (appCtx?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(listener) } catch (_: Throwable) { }
            listening = false
            return
        }
        for (s in live) s.acc.scope.launch {
            s.acc.client.editMessageLiveLocation(chatId = s.chatId, messageId = s.msgId, location = loc(l, 0))
        }
    }
}
