package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.migration.MigrationController
import eu.kanade.tachiyomi.ui.source.browse.repos.RepoController
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy

class SettingsBrowseController : SettingsController() {
    val sourceManager: SourceManager by injectLazy()
    var updatedExtNotifPref: SwitchPreferenceCompat? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.browse

            preferenceCategory {
                switchPreference {
                    bindTo(preferences.hideInLibraryItems())
                    titleRes = R.string.hide_in_library_items
                }
            }

            preferenceCategory {
                titleRes = R.string.extensions
                switchPreference {
                    key = PreferenceKeys.automaticExtUpdates
                    titleRes = R.string.check_for_extension_updates
                    defaultValue = true

                    onChange {
                        it as Boolean
                        ExtensionUpdateJob.setupTask(context, it)
                        true
                    }
                }
                preference {
                    key = "pref_edit_extension_repos"

                    val repoCount = preferences.extensionRepos().get().count()
                    titleRes = R.string.extension_repos
                    if (repoCount > 0) summary = context.resources.getQuantityString(R.plurals.num_repos, repoCount, repoCount)

                    onClick { router.pushController(RepoController().withFadeTransaction()) }
                }
                if (ExtensionManager.canAutoInstallUpdates()) {
                    val intPref =
                        intListPreference(activity) {
                            key = PreferenceKeys.autoUpdateExtensions
                            titleRes = R.string.auto_update_extensions
                            entryRange = 0..2
                            entriesRes =
                                arrayOf(
                                    R.string.over_any_network,
                                    R.string.over_wifi_only,
                                    R.string.dont_auto_update,
                                )
                            defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                        }
                    val infoPref =
                        if (preferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU) {
                            infoPreference(R.string.some_extensions_may_not_update)
                        } else {
                            null
                        }
                    val switchPref =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            switchPreference {
                                key = "notify_ext_updated"
                                isPersistent = false
                                titleRes = R.string.notify_extension_updated
                                isChecked =
                                    Notifications.isNotificationChannelEnabled(
                                        context,
                                        Notifications.CHANNEL_EXT_UPDATED,
                                    )
                                updatedExtNotifPref = this
                                onChange {
                                    false
                                }
                                onClick {
                                    val intent =
                                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                                            putExtra(
                                                Settings.EXTRA_CHANNEL_ID,
                                                Notifications.CHANNEL_EXT_UPDATED,
                                            )
                                        }
                                    startActivity(intent)
                                }
                            }
                        } else {
                            null
                        }
                    preferences.automaticExtUpdates().asImmediateFlowIn(viewScope) { value ->
                        arrayOf(intPref, infoPref, switchPref).forEach { it?.isVisible = value }
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.pref_global_search
                switchPreference {
                    key = PreferenceKeys.onlySearchPinned
                    titleRes = R.string.only_search_pinned_when
                }
            }

            preferenceCategory {
                titleRes = R.string.migration

                preference {
                    titleRes = R.string.source_migration
                    onClick { router.pushController(MigrationController().withFadeTransaction()) }
                }
                if (preferences.skipPreMigration().get() ||
                    preferences
                        .migrationSources()
                        .isSet()
                ) {
                    switchPreference {
                        key = PreferenceKeys.skipPreMigration
                        titleRes = R.string.skip_pre_migration
                        summaryRes = R.string.use_last_saved_migration_preferences
                        defaultValue = false
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.nsfw_sources

                switchPreference {
                    key = PreferenceKeys.showNsfwSource
                    titleRes = R.string.show_in_sources_and_extensions
                    summaryRes = R.string.requires_app_restart
                    defaultValue = true
                }
                infoPreference(R.string.does_not_prevent_unofficial_nsfw)
            }
        }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatedExtNotifPref?.isChecked = Notifications.isNotificationChannelEnabled(activity, Notifications.CHANNEL_EXT_UPDATED)
    }
}
