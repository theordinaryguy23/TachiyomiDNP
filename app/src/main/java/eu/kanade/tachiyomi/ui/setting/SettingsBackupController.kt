package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupConst
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import eu.kanade.tachiyomi.data.backup.GoogleDriveSyncHelper
import eu.kanade.tachiyomi.data.backup.BackupCreator
import eu.kanade.tachiyomi.util.system.launchUI
import java.io.File

class SettingsBackupController : SettingsController() {
    /**
     * Flags containing information of what to backup.
     */
    private var backupFlags = 0

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requestFilePermissionsSafe(500, preferences)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.backup_and_restore

            preference {
                key = "pref_create_backup"
                titleRes = R.string.create_backup
                summaryRes = R.string.can_be_used_to_restore

                onClick {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        context.toast(R.string.restore_miui_warning, Toast.LENGTH_LONG)
                    }

                    if (!BackupCreatorJob.isManualJobRunning(context)) {
                        showBackupCreateDialog()
                    } else {
                        context.toast(R.string.backup_in_progress)
                    }
                }
            }
            preference {
                key = "pref_restore_backup"
                titleRes = R.string.restore_backup
                summaryRes = R.string.restore_from_backup_file

                onClick {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        context.toast(R.string.restore_miui_warning, Toast.LENGTH_LONG)
                    }

                    if (!BackupRestoreJob.isRunning(context)) {
                        (activity as? MainActivity)?.getExtensionUpdates(true)
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "*/*"
                        val title = resources?.getString(R.string.select_backup_file)
                        val chooser = Intent.createChooser(intent, title)
                        startActivityForResult(chooser, CODE_BACKUP_RESTORE)
                    } else {
                        context.toast(R.string.restore_in_progress)
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.automatic_backups

                intListPreference(activity) {
                    bindTo(preferences.backupInterval())
                    titleRes = R.string.backup_frequency
                    entriesRes =
                        arrayOf(
                            R.string.manual,
                            R.string.every_6_hours,
                            R.string.every_12_hours,
                            R.string.daily,
                            R.string.every_2_days,
                            R.string.weekly,
                        )
                    entryValues = listOf(0, 6, 12, 24, 48, 168)

                    onChange { newValue ->
                        val interval = newValue as Int
                        BackupCreatorJob.setupTask(context, interval)
                        true
                    }
                }
                preference {
                    bindTo(preferences.backupsDirectory())
                    titleRes = R.string.backup_location

                    onClick {
                        try {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            startActivityForResult(intent, CODE_BACKUP_DIR)
                        } catch (e: ActivityNotFoundException) {
                            activity?.toast(R.string.file_picker_error)
                        }
                    }

                    visibleIf(preferences.backupInterval()) { it > 0 }

                    preferences
                        .backupsDirectory()
                        .asFlow()
                        .onEach { path ->
                            val dir = UniFile.fromUri(context, path.toUri())
                            summary = dir.filePath + "/automatic"
                        }.launchIn(viewScope)
                }
                intListPreference(activity) {
                    bindTo(preferences.numberOfBackups())
                    titleRes = R.string.max_auto_backups
                    entries = (1..5).map(Int::toString)
                    entryRange = 1..5

                    visibleIf(preferences.backupInterval()) { it > 0 }
                }
            }

            preferenceCategory {
                titleRes = R.string.google_drive_sync

                preference {
                    key = "google_drive_account_pref"
                    titleRes = R.string.google_drive_account
                    
                    val email = preferences.googleSyncAccount().get()
                    if (email.isNotEmpty()) {
                        summary = context.getString(R.string.google_drive_signed_in_as, email)
                    } else {
                        summaryRes = R.string.google_drive_sign_in_summary
                    }

                    onClick {
                        val activity = activity ?: return@onClick
                        if (preferences.googleSyncAccount().get().isNotEmpty()) {
                            // Show sign out dialog
                            activity.materialAlertDialog()
                                .setTitle(R.string.google_drive_sign_out_title)
                                .setMessage(R.string.google_drive_sign_out_confirm)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    GoogleDriveSyncHelper.signOut(context) {
                                        activity.toast(R.string.successfully_logged_out)
                                        activity.recreate()
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        } else {
                            try {
                                val client = GoogleDriveSyncHelper.getGoogleSignInClient(activity)
                                startActivityForResult(client.signInIntent, CODE_GOOGLE_SIGN_IN)
                            } catch (e: Exception) {
                                activity.toast(R.string.google_drive_sign_in_failed)
                            }
                        }
                    }
                }

                switchPreference {
                    bindTo(preferences.googleSyncEnabled())
                    titleRes = R.string.google_drive_auto_sync
                    summaryRes = R.string.google_drive_auto_sync_summary
                    defaultValue = false
                    
                    preferences.googleSyncAccount().asFlow().onEach { email ->
                        isVisible = email.isNotEmpty()
                    }.launchIn(viewScope)
                }

                preference {
                    key = "google_drive_sync_now_pref"
                    titleRes = R.string.google_drive_sync_now
                    summaryRes = R.string.google_drive_sync_now_summary
                    
                    preferences.googleSyncAccount().asFlow().onEach { email ->
                        isVisible = email.isNotEmpty()
                    }.launchIn(viewScope)

                    onClick {
                        val activity = activity ?: return@onClick
                        activity.toast(R.string.creating_backup)
                        
                        viewScope.launchUI {
                            try {
                                val tempFile = File(context.cacheDir, "google_sync_temp.tachibk")
                                try {
                                    val uniFile = UniFile.fromFile(tempFile)
                                        ?: throw Exception("Could not create temp file")
                                    val location = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        BackupCreator(context).createBackup(uniFile.uri, BackupConst.BACKUP_ALL, false)
                                    }
                                    
                                    activity.toast(R.string.google_drive_uploading)
                                    GoogleDriveSyncHelper.uploadSyncBackup(
                                        context = context,
                                        backupUri = Uri.parse(location),
                                        onProgress = { _ -> },
                                        onSuccess = {
                                            activity.runOnUiThread {
                                                activity.toast(R.string.google_drive_sync_success)
                                            }
                                        },
                                        onError = { error ->
                                            activity.runOnUiThread {
                                                activity.toast(context.getString(R.string.google_drive_sync_failed, error.localizedMessage))
                                            }
                                        }
                                    )
                                } finally {
                                    tempFile.delete()
                                }
                            } catch (e: Exception) {
                                activity.toast(context.getString(R.string.google_drive_sync_failed, e.localizedMessage))
                            }
                        }
                    }
                }

                preference {
                    key = "google_drive_restore_now_pref"
                    titleRes = R.string.google_drive_restore_now
                    summaryRes = R.string.google_drive_restore_now_summary
                    
                    preferences.googleSyncAccount().asFlow().onEach { email ->
                        isVisible = email.isNotEmpty()
                    }.launchIn(viewScope)

                    onClick {
                        val activity = activity ?: return@onClick
                        activity.toast(R.string.google_drive_restore_started)
                        
                        viewScope.launchUI {
                            GoogleDriveSyncHelper.downloadAndRestoreSyncBackup(
                                context = context,
                                onProgress = { _ -> },
                                onSuccess = {
                                    activity.runOnUiThread {
                                        activity.toast(R.string.restoring_backup)
                                    }
                                },
                                onError = { error ->
                                    activity.runOnUiThread {
                                        activity.toast(context.getString(R.string.google_drive_restore_failed, error.localizedMessage))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            infoPreference(R.string.backup_info)
        }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.settings_backup, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_backup_help -> activity?.openInBrowser(HELP_URL)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (data != null && resultCode == Activity.RESULT_OK) {
            val activity = activity ?: return

            if (requestCode == CODE_GOOGLE_SIGN_IN) {
                val email = GoogleDriveSyncHelper.handleSignInResult(activity, data)
                if (email != null) {
                    activity.toast(activity.getString(R.string.google_drive_signed_in_as, email))
                    activity.recreate()
                } else {
                    activity.toast(R.string.google_drive_sign_in_failed)
                }
                return
            }

            val uri = data.data

            if (uri == null) {
                activity.toast(R.string.backup_restore_invalid_uri)
                return
            }

            when (requestCode) {
                CODE_BACKUP_DIR -> {
                    // Get UriPermission so it's possible to write files
                    val flags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                    preferences.backupsDirectory().set(uri.toString())
                }

                CODE_BACKUP_CREATE -> {
                    val flags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                    activity.toast(R.string.creating_backup)
                    BackupCreatorJob.startNow(activity, uri, backupFlags)
                }

                CODE_BACKUP_RESTORE -> {
                    (activity as? MainActivity)?.showNotificationPermissionPrompt(true)
                    showBackupRestoreDialog(uri)
                }
            }
        }
    }

    fun createBackup(flags: Int) {
        backupFlags = flags
        try {
            // Use Android's built-in file creator
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/*")
                    .putExtra(Intent.EXTRA_TITLE, Backup.getBackupFilename())

            startActivityForResult(intent, CODE_BACKUP_CREATE)
        } catch (e: ActivityNotFoundException) {
            activity?.toast(R.string.file_picker_error)
        }
    }

    private fun showBackupCreateDialog() {
        val activity = activity ?: return
        val options =
            arrayOf(
                R.string.library_entries,
                R.string.categories,
                R.string.chapters,
                R.string.tracking,
                R.string.history,
                R.string.app_settings,
                R.string.source_settings,
                R.string.custom_manga_info,
                R.string.all_read_manga,
            ).map { activity.getString(it) }

        activity
            .materialAlertDialog()
            .setTitle(R.string.what_should_backup)
            .setMultiChoiceItems(
                options.toTypedArray(),
                options.map { true }.toBooleanArray(),
            ) { dialog, position, _ ->
                if (position == 0) {
                    val listView = (dialog as AlertDialog).listView
                    listView.setItemChecked(position, true)
                }
            }.setPositiveButton(R.string.create) { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                var flags = 0
                for (i in 1 until listView.count) {
                    if (listView.isItemChecked(i)) {
                        when (i) {
                            1 -> flags = flags or BackupConst.BACKUP_CATEGORY
                            2 -> flags = flags or BackupConst.BACKUP_CHAPTER
                            3 -> flags = flags or BackupConst.BACKUP_TRACK
                            4 -> flags = flags or BackupConst.BACKUP_HISTORY
                            5 -> flags = flags or BackupConst.BACKUP_APP_PREFS
                            6 -> flags = flags or BackupConst.BACKUP_SOURCE_PREFS
                            7 -> flags = flags or BackupConst.BACKUP_CUSTOM_INFO
                            8 -> flags = flags or BackupConst.BACKUP_READ_MANGA
                        }
                    }
                }
                createBackup(flags)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .apply {
                disableItems(arrayOf(options.first()))
            }
    }

    private fun showBackupRestoreDialog(uri: Uri) {
        val activity = activity ?: return

        try {
            val results = BackupFileValidator().validate(activity, uri)

            var message = activity.getString(R.string.restore_content_full)
            if (results.missingSources.isNotEmpty()) {
                message += "\n\n${activity.getString(R.string.restore_missing_sources)}\n${
                    results.missingSources.joinToString(
                        "\n",
                    ) { "- $it" }
                }"
            }
            if (results.missingTrackers.isNotEmpty()) {
                message += "\n\n${activity.getString(R.string.restore_missing_trackers)}\n${
                    results.missingTrackers.joinToString(
                        "\n",
                    ) { "- $it" }
                }"
            }

            activity
                .materialAlertDialog()
                .setTitle(R.string.restore_backup)
                .setMessage(message)
                .setPositiveButton(R.string.restore) { _, _ ->
                    val context = applicationContext
                    if (context != null) {
                        activity.toast(R.string.restoring_backup)
                        BackupRestoreJob.start(context, uri)
                    }
                }.show()
        } catch (e: Exception) {
            activity
                .materialAlertDialog()
                .setTitle(R.string.invalid_backup_file)
                .setMessage(e.message)
                .setPositiveButton(android.R.string.cancel, null)
                .show()
        }
    }
}

private const val CODE_BACKUP_DIR = 503
private const val CODE_BACKUP_CREATE = 504
private const val CODE_BACKUP_RESTORE = 505
private const val CODE_GOOGLE_SIGN_IN = 506

private const val HELP_URL = "https://tachiyomi.org/docs/guides/backups"
