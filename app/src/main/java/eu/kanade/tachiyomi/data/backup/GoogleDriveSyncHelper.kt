package eu.kanade.tachiyomi.data.backup

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object GoogleDriveSyncHelper {

    private const val FILE_NAME = "tachiyomi_sync_backup.tachibk"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    /** Dedicated exception for expired/invalid OAuth tokens. */
    private class TokenExpiredException : IOException("OAuth token expired or invalid")

    @Serializable
    private data class DriveFileList(val files: List<DriveFile>)

    @Serializable
    private data class DriveFile(val id: String, val name: String)

    fun isLoggedIn(context: Context): Boolean {
        val preferences = Injekt.get<PreferencesHelper>()
        return preferences.googleSyncAccount().get().isNotEmpty()
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val preferences = Injekt.get<PreferencesHelper>()
        return isLoggedIn(context) && preferences.googleSyncEnabled().get()
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun handleSignInResult(context: Context, data: Intent?): String? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrEmpty()) {
                val preferences = Injekt.get<PreferencesHelper>()
                preferences.googleSyncAccount().set(email)
                preferences.googleSyncEnabled().set(true)
                email
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Google Sign-In failed")
            null
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        val client = getGoogleSignInClient(context)
        client.signOut().addOnCompleteListener {
            val preferences = Injekt.get<PreferencesHelper>()
            preferences.googleSyncAccount().set("")
            preferences.googleSyncEnabled().set(false)
            preferences.googleSyncLastTime().set(0L)
            onComplete()
        }
    }

    suspend fun getAccessToken(context: Context, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val preferences = Injekt.get<PreferencesHelper>()
        val email = preferences.googleSyncAccount().get()
        if (email.isEmpty()) {
            throw IllegalStateException("Google account not linked")
        }
        val account = Account(email, "com.google")

        if (forceRefresh) {
            try {
                val token = GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
                GoogleAuthUtil.clearToken(context, token)
            } catch (e: Exception) {
                Timber.e(e, "Error clearing old token")
            }
        }

        GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
    }

    private fun getAccessTokenSync(context: Context, forceRefresh: Boolean = false): String {
        val preferences = Injekt.get<PreferencesHelper>()
        val email = preferences.googleSyncAccount().get()
        if (email.isEmpty()) {
            throw IllegalStateException("Google account not linked")
        }
        val account = Account(email, "com.google")

        if (forceRefresh) {
            try {
                val token = GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
                GoogleAuthUtil.clearToken(context, token)
            } catch (e: Exception) {
                Timber.e(e, "Error clearing old token")
            }
        }

        return GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
    }

    /**
     * Searches for our sync backup file in the appDataFolder.
     * Returns the file ID, or null if it doesn't exist.
     * Orders by modifiedTime desc to always return the latest file.
     */
    private fun findBackupFileIdSync(client: OkHttpClient, token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?q=name='$FILE_NAME'+and+'appDataFolder'+in+parents" +
            "&spaces=appDataFolder" +
            "&orderBy=modifiedTime+desc" +
            "&pageSize=1"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw TokenExpiredException()
            }
            if (!response.isSuccessful) {
                throw IOException("Google Drive search failed: ${response.code} ${response.message}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response from Google Drive")
            val fileList = jsonParser.decodeFromString<DriveFileList>(bodyString)
            return fileList.files.firstOrNull()?.id
        }
    }

    /** Wraps findBackupFileIdSync with automatic token refresh on 401. */
    private fun findBackupFileIdWithRetry(context: Context, client: OkHttpClient, tokenHolder: TokenHolder): String? {
        return try {
            findBackupFileIdSync(client, tokenHolder.token)
        } catch (e: TokenExpiredException) {
            tokenHolder.token = getAccessTokenSync(context, forceRefresh = true)
            findBackupFileIdSync(client, tokenHolder.token)
        }
    }

    /** Suspend wrapper for findBackupFileIdSync */
    private suspend fun findBackupFileId(context: Context, client: OkHttpClient, token: String): String? = withContext(Dispatchers.IO) {
        findBackupFileIdSync(client, token)
    }

    /** Mutable token wrapper so callees can update the token after refresh. */
    private class TokenHolder(var token: String)

    /**
     * Creates a new file in the appDataFolder and returns its ID.
     */
    private fun createDriveFile(client: OkHttpClient, token: String): String {
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val metadataRequest = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $token")
            .post(metadata.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(metadataRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Metadata creation failed: ${response.code} ${response.message}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response on metadata creation")
            val driveFile = jsonParser.decodeFromString<DriveFile>(bodyString)
            return driveFile.id
        }
    }

    /**
     * Uploads bytes to an existing file ID.
     */
    private fun uploadFileContent(client: OkHttpClient, token: String, fileId: String, fileBytes: ByteArray) {
        val uploadRequestBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())
        val uploadRequest = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(uploadRequestBody)
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Content upload failed: ${response.code} ${response.message}")
            }
        }
    }

    /**
     * Uploads the backup file to Google Drive.
     */
    suspend fun uploadSyncBackup(
        context: Context,
        backupUri: Uri,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Getting credentials...")
                val tokenHolder = TokenHolder(getAccessToken(context))
                val networkHelper = Injekt.get<NetworkHelper>()
                val client = networkHelper.client

                // Read bytes from backupUri
                onProgress("Reading local backup...")
                val contentResolver = context.contentResolver
                val fileBytes = contentResolver.openInputStream(backupUri)?.use { it.readBytes() }
                    ?: throw IOException("Could not read backup file at $backupUri")

                val fileId = findBackupFileIdWithRetry(context, client, tokenHolder)
                    ?: run {
                        onProgress("Creating remote file...")
                        createDriveFile(client, tokenHolder.token)
                    }

                onProgress("Uploading data to cloud...")
                uploadFileContent(client, tokenHolder.token, fileId, fileBytes)

                // Update sync metadata
                val preferences = Injekt.get<PreferencesHelper>()
                preferences.googleSyncLastTime().set(System.currentTimeMillis())

                onSuccess()
            } catch (t: Throwable) {
                Timber.e(t, "Google Drive upload sync failed")
                onError(t)
            }
        }
    }

    /**
     * Uploads sync backup synchronously (to be used inside worker/jobs).
     * Includes token refresh retry logic on 401.
     */
    fun uploadSyncBackupSync(context: Context, backupUri: Uri) {
        try {
            val tokenHolder = TokenHolder(getAccessTokenSync(context))
            val networkHelper = Injekt.get<NetworkHelper>()
            val client = networkHelper.client
            val contentResolver = context.contentResolver
            val fileBytes = contentResolver.openInputStream(backupUri)?.use { it.readBytes() }
            if (fileBytes == null) {
                Timber.w("uploadSyncBackupSync: could not read backup file at $backupUri")
                return
            }

            val fileId = findBackupFileIdWithRetry(context, client, tokenHolder)
                ?: createDriveFile(client, tokenHolder.token)

            uploadFileContent(client, tokenHolder.token, fileId, fileBytes)

            val preferences = Injekt.get<PreferencesHelper>()
            preferences.googleSyncLastTime().set(System.currentTimeMillis())
            Timber.i("Auto sync upload completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Synchronous auto sync upload failed")
        }
    }

    /**
     * Downloads the backup file from Google Drive and restores it.
     */
    suspend fun downloadAndRestoreSyncBackup(
        context: Context,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Getting credentials...")
                val tokenHolder = TokenHolder(getAccessToken(context))
                val networkHelper = Injekt.get<NetworkHelper>()
                val client = networkHelper.client

                onProgress("Checking cloud backup...")
                val fileId = findBackupFileIdWithRetry(context, client, tokenHolder)
                    ?: throw IOException("No cloud backup found on Google Drive")

                onProgress("Downloading cloud backup...")
                val downloadRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer ${tokenHolder.token}")
                    .get()
                    .build()

                client.newCall(downloadRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Cloud backup download failed: ${response.code} ${response.message}")
                    }

                    val responseBody = response.body ?: throw IOException("Empty download response body")

                    // Save to a temporary file in the cache directory
                    val tempFile = File(context.cacheDir, "cloud_restore_temp.tachibk")
                    try {
                        FileOutputStream(tempFile).use { fos ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(fos)
                            }
                        }

                        // Start the restore job using the file's Uri
                        onProgress("Restoring backup data...")
                        val backupUri = Uri.fromFile(tempFile)
                        BackupRestoreJob.start(context, backupUri)

                        onSuccess()
                    } catch (e: Exception) {
                        tempFile.delete()
                        throw e
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Google Drive restore sync failed")
                onError(t)
            }
        }
    }
}
