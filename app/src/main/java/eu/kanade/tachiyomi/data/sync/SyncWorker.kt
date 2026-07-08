package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import androidx.work.*
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker for background history + library sync.
 * Handles upload (local -> cloud) and download/merge (cloud -> local).
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
        private const val ID_SYNC_PROGRESS = -510
        private const val ID_SYNC_COMPLETE = -511

        const val SYNC_TYPE_UPLOAD = "upload"
        const val SYNC_TYPE_DOWNLOAD = "download"
        const val SYNC_TYPE_FULL = "full"
        const val SYNC_TYPE_HISTORY = "history"
        const val SYNC_TYPE_LIBRARY = "library"

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
    private val historySyncManager = HistorySyncManager(db, prefs)
    private val librarySyncManager = LibrarySyncManager(db, prefs)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString(KEY_USER_ID) ?: return@withContext Result.failure()
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL

            Timber.d("Starting sync: type=$syncType, user=$userId, attempt=$runAttemptCount")
            showProgressNotification()

            when (syncType) {
                SYNC_TYPE_HISTORY -> {
                    historySyncManager.uploadAllHistory(userId)
                    val merged = historySyncManager.mergeHistory(userId)
                    Timber.d("History sync complete: $merged entries merged")
                }
                SYNC_TYPE_LIBRARY -> {
                    librarySyncManager.uploadAllMangas(userId)
                    librarySyncManager.uploadCategories(userId)
                    librarySyncManager.uploadMangaCategories(userId)
                    val mangas = librarySyncManager.mergeMangas(userId)
                    val cats = librarySyncManager.mergeCategories(userId)
                    val mc = librarySyncManager.mergeMangaCategories(userId)
                    Timber.d("Library sync complete: $mangas mangas, $cats categories, $mc manga-categories")
                }
                SYNC_TYPE_UPLOAD -> {
                    historySyncManager.uploadAllHistory(userId)
                    librarySyncManager.uploadAllMangas(userId)
                    librarySyncManager.uploadCategories(userId)
                    librarySyncManager.uploadMangaCategories(userId)
                }
                SYNC_TYPE_DOWNLOAD -> {
                    historySyncManager.mergeHistory(userId)
                    librarySyncManager.mergeMangas(userId)
                    librarySyncManager.mergeCategories(userId)
                    librarySyncManager.mergeMangaCategories(userId)
                }
                SYNC_TYPE_FULL -> {
                    // History first (most important)
                    historySyncManager.uploadAllHistory(userId)
                    val historyMerged = historySyncManager.mergeHistory(userId)

                    // Then library
                    librarySyncManager.uploadAllMangas(userId)
                    librarySyncManager.uploadCategories(userId)
                    librarySyncManager.uploadMangaCategories(userId)
                    val mangas = librarySyncManager.mergeMangas(userId)
                    val cats = librarySyncManager.mergeCategories(userId)
                    val mc = librarySyncManager.mergeMangaCategories(userId)

                    Timber.d("Full sync complete: history=$historyMerged, mangas=$mangas, cats=$cats, mc=$mc")
                }
            }

            showCompleteNotification()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Sync worker failed")
            if (runAttemptCount < 3) {
                applicationContext.notificationManager.cancel(ID_SYNC_PROGRESS)
                Result.retry()
            } else {
                showErrorNotification(e.message)
                Result.failure()
            }
        }
    }

    private fun showProgressNotification() {
        val builder = applicationContext.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
            setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_tachij2k_notification)
            setContentTitle(applicationContext.getString(R.string.pref_sync_title))
            setContentText(applicationContext.getString(R.string.syncing))
            setProgress(0, 0, true)
            setOngoing(true)
            setAutoCancel(false)
        }
        applicationContext.notificationManager.notify(ID_SYNC_PROGRESS, builder.build())
    }

    private fun showCompleteNotification() {
        applicationContext.notificationManager.cancel(ID_SYNC_PROGRESS)
        val builder = applicationContext.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
            setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_tachij2k_notification)
            setContentTitle(applicationContext.getString(R.string.pref_sync_title))
            setContentText(applicationContext.getString(R.string.sync_completed))
            setAutoCancel(true)
        }
        applicationContext.notificationManager.notify(ID_SYNC_COMPLETE, builder.build())
    }

    private fun showErrorNotification(error: String?) {
        applicationContext.notificationManager.cancel(ID_SYNC_PROGRESS)
        val builder = applicationContext.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
            setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_tachij2k_notification)
            setContentTitle(applicationContext.getString(R.string.sync_failed))
            setContentText(error ?: applicationContext.getString(R.string.snack_backup_install_error))
            setAutoCancel(true)
        }
        applicationContext.notificationManager.notify(ID_SYNC_COMPLETE, builder.build())
    }
}
