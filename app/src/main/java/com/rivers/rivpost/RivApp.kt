package com.rivers.rivpost

import android.app.Application
import com.rivers.rivpost.data.Lang
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.Notifier
import com.rivers.rivpost.tg.Tg

class RivApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Lang.update()
        Notifier.channels(this)
        Tg.init(this)
    }
}
