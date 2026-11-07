package com.rpginsurance.desk

import android.app.Application
import com.rpginsurance.desk.data.BriefRepository
import com.rpginsurance.desk.data.Settings
import com.rpginsurance.desk.work.DailyScheduler

class DeskApp : Application() {
    lateinit var repository: BriefRepository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repository = BriefRepository(this, settings)
        Notifications.createChannel(this)
        if (settings.dailyNotification) DailyScheduler.ensureScheduled(this)
    }
}

val android.content.Context.app: DeskApp get() = applicationContext as DeskApp
