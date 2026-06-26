package eu.kanade.tachiyomi.ui.setting.sync

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.sync.GoogleAuthManager
import eu.kanade.tachiyomi.data.sync.SyncWorker
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.titleRes
import eu.kanade.tachiyomi.ui.setting.summaryRes
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Controller for the History & Library Sync settings screen.
 */
class SyncSettingsController(
    private val prefs: PreferencesHelper = Injekt.get(),
) : SettingsController() {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        with(screen) {
            titleRes = R.string.pref_sync_title

            val tintColor = context.getResourceColor(R.attr.colorSecondary)

            // Account section
            preferenceCategory {
                titleRes = R.string.pref_sync_account
            }

            // Account info
            preference {
                key = "sync_account_info"
                titleRes = R.string.pref_sync_account_none
                summaryRes = R.string.pref_sync_account_summary
                isSelectable = false
            }

            // Sign in button
            preference {
                key = "sync_sign_in"
                titleRes = R.string.pref_sync_sign_in
                summaryRes = R.string.pref_sync_sign_in_summary
                iconRes = R.drawable.ic_arrow_forward_24dp
                iconTint = tintColor
                onClick {
                    launchSignIn()
                }
            }

            // Sign out button
            preference {
                key = "sync_sign_out"
                titleRes = R.string.pref_sync_sign_out
                summaryRes = R.string.pref_sync_sign_out_summary
                iconRes = R.drawable.ic_arrow_back_24dp
                iconTint = tintColor
                isVisible = false
                onClick {
                    launchIO {
                        googleAuthManager.signOut()
                        updateAccountUI()
                        SyncWorker.cancelPeriodic(requireContext())
                        requireContext().toast(R.string.pref_sync_signed_out)
                    }
                }
            }

            // Sync settings section
            preferenceCategory {
                titleRes = R.string.pref_sync_settings
            }

            // Enable sync switch
            switchPreference {
                key = PreferenceKeys.syncEnabled
                titleRes = R.string.pref_sync_enabled
                summaryRes = R.string.pref_sync_enabled_summary
                setDefaultValue(false)
                onChange { newValue ->
                    val enabled = newValue as Boolean
                    if (enabled) {
                        if (googleAuthManager.isSignedIn()) {
                            startSync()
                        } else {
                            updateSyncUI(false)
                            requireContext().toast(R.string.pref_sync_sign_in_first)
                        }
                    } else {
                        stopSync()
                    }
                    true
                }
            }

            // Auto sync switch
            switchPreference {
                key = PreferenceKeys.autoSync
                titleRes = R.string.pref_sync_auto
                summaryRes = R.string.pref_sync_auto_summary
                setDefaultValue(false)
                dependency = PreferenceKeys.syncEnabled
                onChange { newValue ->
                    val autoEnabled = newValue as Boolean
                    val userId = googleAuthManager.getUserId()
                    if (userId != null) {
                        if (autoEnabled) {
                            SyncWorker.schedulePeriodic(requireContext(), userId)
                        } else {
                            SyncWorker.cancelPeriodic(requireContext())
                        }
                    }
                    true
                }
            }

            // Sync now button
            preference {
                key = "sync_now"
                titleRes = R.string.pref_sync_now
                summaryRes = R.string.pref_sync_now_summary
                iconRes = R.drawable.ic_sync_24dp
                iconTint = tintColor
                dependency = PreferenceKeys.syncEnabled
                onClick {
                    val userId = googleAuthManager.getUserId()
                    if (userId != null) {
                        SyncWorker.runImmediate(requireContext(), userId, SyncWorker.SYNC_TYPE_FULL)
                        requireContext().toast(R.string.pref_sync_started)
                    } else {
                        requireContext().toast(R.string.pref_sync_sign_in_first)
                    }
                }
            }

            this
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Register for activity result before creating preferences
        signInLauncher = (activity as? androidx.activity.ComponentActivity)
            ?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handleSignInResult(result.data)
            }

        googleAuthManager = GoogleAuthManager(preferenceScreen.context)
        super.onCreatePreferences(savedInstanceState, rootKey)
        updateAccountUI()
    }

    private fun launchSignIn() {
        val signInIntent = googleAuthManager.getSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                launchIO {
                    val result = googleAuthManager.signInWithGoogle(account)
                    if (result.isSuccess) {
                        updateAccountUI()
                        updateSyncUI(prefs.syncEnabled().get())
                        requireContext().toast(R.string.pref_sync_signed_in)
                    } else {
                        requireContext().toast(requireContext().getString(R.string.pref_sync_sign_in_error, result.exceptionOrNull()?.message))
                    }
                }
            } else {
                Timber.e("Sign-in failed: ${task.exception}")
                requireContext().toast(R.string.pref_sync_sign_in_failed)
            }
        } catch (e: Exception) {
            Timber.e(e, "Sign-in result error")
            requireContext().toast(R.string.pref_sync_sign_in_failed)
        }
    }

    private fun updateAccountUI() {
        val isSignedIn = googleAuthManager.isSignedIn()
        val displayName = googleAuthManager.getDisplayName()

        val signInPref = findPreference("sync_sign_in")
        val signOutPref = findPreference("sync_sign_out")
        val accountInfo = findPreference("sync_account_info")

        if (isSignedIn && displayName != null) {
            signInPref?.isVisible = false
            signOutPref?.isVisible = true
            accountInfo?.title = requireContext().getString(R.string.pref_sync_account_logged_in, displayName)
            accountInfo?.summary = requireContext().getString(R.string.pref_sync_account_sync_enabled)
        } else {
            signInPref?.isVisible = true
            signOutPref?.isVisible = false
            accountInfo?.setTitle(R.string.pref_sync_account_none)
            accountInfo?.setSummary(R.string.pref_sync_account_summary)
        }
    }

    private fun updateSyncUI(enabled: Boolean) {
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.syncEnabled)?.isChecked = enabled
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.autoSync)?.isEnabled = enabled
        findPreference("sync_now")?.isEnabled = enabled
    }

    private fun startSync() {
        val userId = googleAuthManager.getUserId() ?: return
        val ctx = preferenceScreen.context
        if (prefs.autoSync().get()) {
            SyncWorker.schedulePeriodic(ctx, userId)
        }
        // Do an initial full sync
        SyncWorker.runImmediate(ctx, userId, SyncWorker.SYNC_TYPE_FULL)
    }

    private fun stopSync() {
        SyncWorker.cancelPeriodic(preferenceScreen.context)
    }
}
