package com.rpginsurance.desk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rpginsurance.desk.Notifications
import com.rpginsurance.desk.app

/** Runs once a day: rebuilds the brief from Airtable and posts it as a notification. */
class DailyBriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.settings.hasToken) {
            Notifications.showError(applicationContext, "Add your Airtable token in the app's Settings.")
            return Result.success()
        }
        val brief = app.repository.refresh()
        val everythingFailed = brief.sections.isEmpty() && brief.failures.size >= 5
        if (everythingFailed && runAttemptCount < 2) return Result.retry()
        Notifications.showBrief(applicationContext, brief)
        return Result.success()
    }
}
