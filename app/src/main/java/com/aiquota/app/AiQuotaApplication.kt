package com.aiquota.app

import android.app.Application
import com.aiquota.app.core.notify.QuotaNotifier
import com.aiquota.app.work.QuotaSyncScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiQuotaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        QuotaNotifier.createChannel(this)
        // 启动周期后台同步（WorkManager，最小 15 分钟一次）
        QuotaSyncScheduler.schedule(this)
    }
}