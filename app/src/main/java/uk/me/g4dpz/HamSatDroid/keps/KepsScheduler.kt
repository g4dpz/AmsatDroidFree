// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.keps

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules or cancels the daily keps refresh.
 *
 * The work is unique, so applying the schedule repeatedly (at every app start, and whenever the setting changes) never
 * creates a second job. `ExistingPeriodicWorkPolicy.UPDATE` keeps the original enqueue time, so opening the app does
 * not push the next refresh back. WorkManager keeps the job across reboots and app updates.
 */
class KepsScheduler(private val workManager: WorkManager) {

    constructor(context: Context) : this(WorkManager.getInstance(context.applicationContext))

    /** Schedules the refresh if [enabled], otherwise cancels it. */
    fun apply(enabled: Boolean) {
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<KepsRefreshWorker>(
                REPEAT_HOURS, TimeUnit.HOURS,
                FLEX_HOURS, TimeUnit.HOURS
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            workManager.cancelUniqueWork(UNIQUE_NAME)
        }
    }

    companion object {
        const val UNIQUE_NAME = "keps-refresh"
        const val REPEAT_HOURS = 24L
        const val FLEX_HOURS = 6L
        const val BACKOFF_MINUTES = 15L
    }
}
