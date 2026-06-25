package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.*
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker for background history sync.
 * Handles both upload (local -> cloud) and download/merge (cloud -> local).
 *
 * Battery-aware strategy:
 * - Charging: 15 min interval
 * - Battery > 50%: 30 min interval
 * - Battery 20-50%: 60 min interval
 * - Battery < 20%: skip auto-sync (manual sync still works)
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "history_sync"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SYNC_TYPE = "sync_type"

        const val SYNC_TYPE_UPLOAD = "upload"
        const val SYNC_TYPE_DOWNLOAD = "download"
        const val SYNC_TYPE_FULL = "full"

        fun schedulePeriodic(context: Context, userId: String) {
            val intervalMinutes = getOptimalIntervalMinutes(context)

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
            ).setInputData(
                workDataOf(
                    KEY_USER_ID to userId,
                    KEY_SYNC_TYPE to SYNC_TYPE_FULL,
                )
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)  // Skip if battery < 15%
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Timber.d("Scheduled periodic sync every $intervalMinutes minutes")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Cancelled periodic sync")
        }

        fun runImmediate(context: Context, userId: String, syncType: String) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_USER_ID to userId,
                        KEY_SYNC_TYPE to syncType,
                    )
                ).setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()

            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Determines optimal sync interval based on battery state.
         */
        private fun getOptimalIntervalMinutes(context: Context): Long {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 50
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            return when {
                isCharging -> 15L
                batteryPct > 50 -> 30L
                batteryPct > 20 -> 60L
                else -> 120L  // Very low battery — minimal auto-sync
            }
        }
    }

    private val db = DatabaseHelper(applicationContext)
    private val prefs = PreferencesHelper(applicationContext)
    private val syncManager = HistorySyncManager(db, prefs)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString(KEY_USER_ID) ?: return@withContext Result.failure()
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL

            Timber.d("Starting sync: type=$syncType, user=$userId, attempt=$runAttemptCount")

            when (syncType) {
                SYNC_TYPE_UPLOAD -> {
                    syncManager.uploadAllHistory(userId)
                }
                SYNC_TYPE_DOWNLOAD -> {
                    syncManager.mergeHistory(userId)
                }
                SYNC_TYPE_FULL -> {
                    // Upload first, then merge (so local changes go up, then pull remote changes)
                    syncManager.uploadAllHistory(userId)
                    val merged = syncManager.mergeHistory(userId)
                    Timber.d("Full sync complete: $merged entries merged")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Sync worker failed")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
