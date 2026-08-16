package eu.kanade.tachiyomi.data.sync

import com.google.firebase.auth.FirebaseAuth
import eu.kanade.tachiyomi.data.preference.PreferencesHelper

object SyncOnboardingHelper {
    fun shouldShow(preferences: PreferencesHelper): Boolean {
        if (preferences.shownSyncOnboarding().get()) return false
        return FirebaseAuth.getInstance().currentUser == null
    }

    fun markShown(preferences: PreferencesHelper) {
        preferences.shownSyncOnboarding().set(true)
    }
}
