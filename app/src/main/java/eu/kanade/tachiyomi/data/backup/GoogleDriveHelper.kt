package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream
import com.google.api.services.drive.model.File as DriveFile

/**
 * Helper class to manage Google Drive backup operations.
 */
class GoogleDriveHelper(private val context: Context) {

    private val preferences: PreferencesHelper = Injekt.get()

    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    /**
     * Returns the intent to start the Google Sign-in process.
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Signs out the current user from Google.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign out from Google")
        }
    }

    /**
     * Returns the currently signed-in Google account, or null if not signed in.
     */
    fun getGoogleSignInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Returns the Drive service instance for the currently signed-in user.
     */
    fun getDriveService(): Drive? {
        val account = getGoogleSignInAccount()?.account ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    /**
     * Uploads a backup file to Google Drive.
     *
     * @param uri The URI of the backup file to upload.
     */
    suspend fun uploadBackup(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: throw Exception("Not signed in to Google Drive")

            // 1. Find or create the "TachiyomiDNP-Backups" folder
            val folderId = findOrCreateBackupFolder(driveService)

            // 2. Determine the filename
            val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
            } ?: Backup.getBackupFilename()

            // 3. Prepare file metadata
            val fileMetadata = DriveFile().apply {
                name = filename
                parents = listOf(folderId)
            }

            // 4. Create a temporary file to upload
            val tempFile = java.io.File(context.cacheDir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 5. Upload the file
            val mediaContent = FileContent("application/octet-stream", tempFile)
            driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            // 6. Cleanup temporary file
            tempFile.delete()

            // 7. Update last sync time in preferences
            preferences.googleDriveBackupLastSync().set(System.currentTimeMillis())

            // 8. Delete old backups if they exceed the limit
            deleteOldBackups(driveService, folderId)

        } catch (e: Exception) {
            Timber.e(e, "Google Drive backup upload failed")
            throw e
        }
    }

    /**
     * Finds the "TachiyomiDNP-Backups" folder in Google Drive or creates it if it doesn't exist.
     */
    private fun findOrCreateBackupFolder(driveService: Drive): String {
        val folderName = "TachiyomiDNP-Backups"
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        val folderId = result.files?.firstOrNull()?.id
        if (folderId != null) {
            return folderId
        }

        val folderMetadata = DriveFile().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }
        val newFolder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return newFolder.id
    }

    /**
     * Deletes old backups in the specified folder, keeping only the number of backups specified in preferences.
     */
    private fun deleteOldBackups(driveService: Drive, folderId: String) {
        val maxBackups = preferences.numberOfBackups().get()
        if (maxBackups <= 0) return

        val query = "'$folderId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, createdTime)")
            .execute()

        val backupFiles = result.files?.filter { file ->
            val name = file.name ?: ""
            Backup.filenameRegex.matches(name)
        }?.sortedByDescending { it.createdTime?.value ?: 0L } ?: emptyList()

        if (backupFiles.size > maxBackups) {
            backupFiles.drop(maxBackups).forEach { file ->
                val fileId = file.id ?: return@forEach
                try {
                    driveService.files().delete(fileId).execute()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete old backup: ${file.name}")
                }
            }
        }
    }
}
