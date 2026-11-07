package com.rpginsurance.desk.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rpginsurance.brief.IST
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules the brief for about 8:00 AM IST every day, whatever time zone the phone is in. */
object DailyScheduler {
    private const val WORK_NAME = "daily-brief"
    val RUN_AT: LocalTime = LocalTime.of(8, 0)

    /** Keeps an existing schedule; call on every app start. */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.KEEP)

    /** Re-anchors the schedule to the next 8 AM, e.g. after the user turns it back on. */
    fun reschedule(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<DailyBriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNextRun().toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun delayUntilNextRun(now: ZonedDateTime = ZonedDateTime.now(IST)): Duration {
        val ist = now.withZoneSameInstant(IST)
        var next = ist.with(RUN_AT).withSecond(0).withNano(0)
        if (!next.isAfter(ist)) next = next.plusDays(1)
        return Duration.between(ist, next)
    }
}
