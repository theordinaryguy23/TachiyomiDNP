package eu.kanade.tachiyomi.data.sync

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date

/**
 * Manages syncing reading history between devices via Firestore.
 *
 * Firestore structure:
 *   users/{userId}/history/{chapterId} -> { mangaId, mangaTitle, mangaUrl, chapterUrl, lastRead, timeRead, updatedAt }
 */
class HistorySyncManager(
    private val db: DatabaseHelper,
    private val preferences: PreferencesHelper,
) {
    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_HISTORY = "history"
        private const val COLLECTION_MANGAS = "mangas"
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Uploads a single history entry to Firestore when a chapter is read.
     * Called from ReaderViewModel after saving history locally.
     */
    suspend fun uploadHistoryEntry(userId: String, history: History, manga: Manga) {
        try {
            val chapter = db.getChapter(history.chapter_id).executeAsBlocking() ?: return
            val data = hashMapOf(
                "mangaId" to manga.id,
                "mangaTitle" to manga.title,
                "mangaUrl" to manga.url,
                "chapterId" to history.chapter_id,
                "chapterUrl" to chapter.url,
                "chapterName" to chapter.name,
                "lastRead" to history.last_read,
                "timeRead" to history.time_read,
                "updatedAt" to Timestamp.now(),
            )
            getHistoryDoc(userId, history.chapter_id)
                .set(data, SetOptions.merge())
                .await()
            Timber.d("Uploaded history: ${manga.title} - ${chapter.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload history entry")
        }
    }

    /**
     * Downloads all history entries from Firestore for a user.
     */
    suspend fun downloadHistoryEntries(userId: String): List<HistorySyncData> {
        return try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_HISTORY)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(HistorySyncData::class.java)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download history entries")
            emptyList()
        }
    }

    /**
     * Uploads all local history to Firestore (full sync).
     * Uses batch writes for efficiency.
     */
    suspend fun uploadAllHistory(userId: String) {
        try {
            val recentHistory = db.getAllRecentsTypes(
                search = "",
                includeRead = true,
                endless = true,
                offset = 0,
                isResuming = false,
                customLimit = 10000,
            ).executeAsBlocking()

            var count = 0
            val batches = recentHistory.chunked(500)
            for (batch in batches) {
                val writeBatch = firestore.batch()
                batch.forEach { item ->
                    val chapterId = item.history.chapter_id
                    val data = hashMapOf(
                        "mangaId" to item.manga.id,
                        "mangaTitle" to item.manga.title,
                        "mangaUrl" to item.manga.url,
                        "chapterId" to chapterId,
                        "chapterUrl" to item.chapter.url,
                        "chapterName" to item.chapter.name,
                        "lastRead" to item.history.last_read,
                        "timeRead" to item.history.time_read,
                        "updatedAt" to Timestamp.now(),
                    )
                    writeBatch.set(getHistoryDoc(userId, chapterId), data, SetOptions.merge())
                }
                writeBatch.commit().await()
                count += batch.size
            }
            Timber.d("Full sync: uploaded $count history entries")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload all history")
        }
    }

    /**
     * Merges remote history with local history.
     * For each remote entry:
     * - If chapter exists locally: keep the most recent last_read
     * - If chapter doesn't exist locally: skip (can't sync without local manga)
     * Returns the number of entries merged/updated.
     */
    suspend fun mergeHistory(userId: String): Int {
        val remoteEntries = downloadHistoryEntries(userId)
        if (remoteEntries.isEmpty()) return 0

        var mergedCount = 0
        for (remote in remoteEntries) {
            try {
                val localHistory = db.getHistoryByChapterId(remote.chapterId).executeAsBlocking()

                if (localHistory == null) {
                    // Chapter not in local library — skip
                    continue
                }

                if (remote.lastRead > localHistory.last_read) {
                    // Remote is newer — update local
                    val updatedHistory = History.create(
                        localHistory.let {
                            // We need to get the chapter to create history
                            db.getChapter(remote.chapterId).executeAsBlocking() ?: continue
                        }
                        ).apply {
                            id = localHistory.id
                            last_read = remote.lastRead
                            time_read = maxOf(time_read, remote.timeRead)
                        }
                    db.upsertHistoryLastRead(updatedHistory).executeAsBlocking()
                    mergedCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge history for chapterId: ${remote.chapterId}")
            }
        }
        Timber.d("Merge complete: $mergedCount entries updated")
        return mergedCount
    }

    /**
     * Deletes all cloud history for the user.
     */
    suspend fun clearCloudHistory(userId: String) {
        try {
            val docs = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_HISTORY)
                .get()
                .await()
            val batch = firestore.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Timber.d("Cleared all cloud history for user: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cloud history")
        }
    }

    /**
     * Returns the Firestore document reference for a history entry by chapter ID.
     */
    private fun getHistoryDoc(userId: String, chapterId: Long) =
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_HISTORY)
            .document("ch_$chapterId")
}

/**
 * Data class for syncing history entries to/from Firestore.
 */
data class HistorySyncData(
    val mangaId: Long? = null,
    val mangaTitle: String = "",
    val mangaUrl: String = "",
    val chapterId: Long = 0L,
    val chapterUrl: String = "",
    val chapterName: String = "",
    val lastRead: Long = 0L,
    val timeRead: Long = 0L,
    val updatedAt: Timestamp? = null,
)
