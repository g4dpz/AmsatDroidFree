package uk.me.g4dpz.HamSatDroid.keps

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import uk.me.g4dpz.HamSatDroid.data.RefreshResult
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.SharedPreferencesStore
import uk.me.g4dpz.HamSatDroid.data.TransponderSyncResult
import uk.me.g4dpz.HamSatDroid.data.createKepsRepository
import uk.me.g4dpz.HamSatDroid.data.createTransponderDatabaseRepository

/**
 * Downloads the user's selected keps source in the background, about once a day (see [KepsScheduler]).
 *
 * All the rules (two hour minimum, no retry of refusals, keep old data on failure) live in the repository and in
 * [KepsRefreshDecision]; this class only connects them to WorkManager.
 */
class KepsRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val settings = SettingsRepository(SharedPreferencesStore(applicationContext))
        if (!settings.autoKepsRefresh.value) return Result.success() // switched off since this run was scheduled

        val source = settings.kepsSource.value
        val result = createKepsRepository(applicationContext).refresh(source)
        val decision = KepsRefreshDecision.decide(result, runAttemptCount)
        Log.i(TAG, "Refresh of $source: ${describe(result)} -> $decision (run $runAttemptCount)")

        // The transponder database rides along on the same schedule. Its own minimum-interval rule applies, so this is
        // cheap when it ran recently. A sync failure never affects the keps result; the old database is kept.
        val syncResult = createTransponderDatabaseRepository(applicationContext).sync()
        Log.i(TAG, "Transponder database sync: ${describe(syncResult)}")

        return when (decision) {
            WorkerDecision.SUCCESS -> Result.success()
            WorkerDecision.RETRY -> Result.retry()
            WorkerDecision.FAILURE -> Result.failure()
        }
    }

    private fun describe(result: RefreshResult): String = when (result) {
        is RefreshResult.Updated -> "updated ${result.count} satellites"
        is RefreshResult.TooSoon -> "too soon, next allowed at ${result.nextAllowedAtMillis}"
        is RefreshResult.Failed -> "failed: ${result.outcome}"
    }

    private fun describe(result: TransponderSyncResult): String = when (result) {
        is TransponderSyncResult.Updated -> "updated ${result.count} satellites"
        is TransponderSyncResult.TooSoon -> "too soon, next allowed at ${result.nextAllowedAtMillis}"
        is TransponderSyncResult.Failed -> "failed: ${result.outcome}"
    }

    private companion object {
        const val TAG = "KepsRefreshWorker"
    }
}
