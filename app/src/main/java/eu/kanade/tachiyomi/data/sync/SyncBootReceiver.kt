package eu.kanade.tachiyomi.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import timber.log.Timber

/**
 * Receiver that schedules sync on device boot if the user is signed in
 * and auto-sync is enabled.
 */
class SyncBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, checking sync status...")
            val prefs = PreferencesHelper(context)
            val googleAuthManager = GoogleAuthManager(context)

            if (googleAuthManager.isSignedIn() && prefs.syncEnabled().get()) {
                val userId = googleAuthManager.getUserId()
                if (userId != null && prefs.autoSync().get()) {
                    SyncWorker.schedulePeriodic(context, userId)
                    Timber.d("Resumed periodic sync after boot")
                }
            }
        }
    }
}
