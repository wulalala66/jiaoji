package com.wulalala66.jiaoji

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.I.init(this)
        RingNotif.ensureChannel(this)
    }
}
