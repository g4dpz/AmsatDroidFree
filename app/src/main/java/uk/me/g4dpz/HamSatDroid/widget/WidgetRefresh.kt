package uk.me.g4dpz.HamSatDroid.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Recomputes and redraws every placed next-pass widget. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NextPassWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/**
 * Starts or stops the periodic widget refresh. The work is unique, so starting it repeatedly never creates a second
 * job; it is started when the first widget is added and stopped when the last is removed.
 */
class WidgetRefreshScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun start() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_MINUTES, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun stop() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    private companion object {
        const val UNIQUE_NAME = "widget-refresh"
        const val REFRESH_MINUTES = 30L
    }
}

/**
 * Redraws the widgets now. Call after a keps refresh or when the selected satellite or location changes, so the
 * widget does not wait for the next periodic run. Safe to call when no widget is placed (it simply updates none).
 */
object WidgetRefresh {
    suspend fun updateAll(context: Context) {
        NextPassWidget().updateAll(context.applicationContext)
    }
}
