package eu.kanade.tachiyomi.data.sync

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Manages syncing library state (manga, categories, favorites) between devices via Firestore.
 *
 * Firestore structure:
 *   users/{userId}/library/mangas/{mangaId} -> { source, url, title, favorite, dateAdded, lastUpdate, viewerFlags, chapterFlags }
 *   users/{userId}/library/categories/{categoryId} -> { name, order }
 *   users/{userId}/library/mangaCategories/{mangaId} -> { categoryIds: [...] }
 */
class LibrarySyncManager(
    private val db: DatabaseHelper,
    private val preferences: PreferencesHelper,
) {
    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_LIBRARY = "library"
        private const val COLLECTION_MANGAS = "mangas"
        private const val COLLECTION_CATEGORIES = "categories"
        private const val COLLECTION_MANGA_CATEGORIES = "mangaCategories"
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Uploads a single manga's library entry to Firestore.
     */
    suspend fun uploadManga(userId: String, manga: Manga) {
        try {
            val data = hashMapOf(
                "source" to manga.source,
                "url" to manga.url,
                "title" to manga.title,
                "favorite" to manga.favorite,
                "dateAdded" to manga.date_added,
                "lastUpdate" to manga.last_update,
                "viewerFlags" to manga.viewer_flags,
                "chapterFlags" to manga.chapter_flags,
                "updatedAt" to Timestamp.now(),
            )
            getMangaDoc(userId, manga.id!!)
                .set(data, SetOptions.merge())
                .await()
            Timber.d("Uploaded manga: ${manga.title}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload manga: ${manga.title}")
        }
    }

    /**
     * Downloads all library manga from Firestore for a user.
     * Returns list of LibrarySyncData for merging.
     */
    suspend fun downloadMangas(userId: String): List<LibraryMangaSyncData> {
        return try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_LIBRARY)
                .collection(COLLECTION_MANGAS)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(LibraryMangaSyncData::class.java)?.copy(
                    mangaId = doc.id.toLongOrNull() ?: return@mapNotNull null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download mangas")
            emptyList()
        }
    }

    /**
     * Uploads all local library manga to Firestore (full sync).
     */
    suspend fun uploadAllMangas(userId: String) {
        try {
            val libraryMangas = db.getLibraryMangas().executeAsBlocking()
            var count = 0
            val batches = libraryMangas.chunked(500)
            for (batch in batches) {
                val writeBatch = firestore.batch()
                batch.forEach { manga ->
                    val data = hashMapOf(
                        "source" to manga.source,
                        "url" to manga.url,
                        "title" to manga.title,
                        "favorite" to manga.favorite,
                        "dateAdded" to manga.date_added,
                        "lastUpdate" to manga.last_update,
                        "viewerFlags" to manga.viewer_flags,
                        "chapterFlags" to manga.chapter_flags,
                        "updatedAt" to Timestamp.now(),
                    )
                    writeBatch.set(getMangaDoc(userId, manga.id!!), data, SetOptions.merge())
                }
                writeBatch.commit().await()
                count += batch.size
            }
            Timber.d("Full library sync: uploaded $count manga entries")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload all mangas")
        }
    }

    /**
     * Merges remote manga library with local.
     * For each remote manga:
     * - If exists locally: sync favorite status, date_added, and flags (keep most recent)
     * - If not exists locally: add to library (as unfavorited, with source/url/title)
     * Returns number of entries merged/updated.
     */
    suspend fun mergeMangas(userId: String): Int {
        val remoteMangas = downloadMangas(userId)
        if (remoteMangas.isEmpty()) return 0

        var mergedCount = 0
        for (remote in remoteMangas) {
            try {
                val localManga = remote.mangaId.let { db.getMangaById(it).executeAsBlocking() }

                if (localManga == null) {
                    // Manga not in local library — add it (unfavorited, preserve source/url)
                    val newManga = MangaImpl().apply {
                        id = remote.mangaId
                        source = remote.source
                        url = remote.url
                        title = remote.title
                        favorite = false  // Don't auto-favorite synced manga
                        date_added = remote.dateAdded
                        last_update = remote.lastUpdate
                        viewer_flags = remote.viewerFlags
                        chapterFlags = remote.chapterFlags
                    }
                    db.insertManga(newManga).executeAsBlocking()
                    mergedCount++
                } else {
                    // Manga exists — merge: keep most recent update, sync favorite
                    val updated = remote.updatedAt?.seconds ?: 0
                    val localUpdated = localManga.last_update

                    if (updated > localUpdated) {
                        localManga.favorite = remote.favorite
                        localManga.date_added = remote.dateAdded
                        localManga.viewer_flags = remote.viewerFlags
                        localManga.chapter_flags = remote.chapterFlags
                        db.updateManga(localManga).executeAsBlocking()
                        mergedCount++
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge manga: ${remote.title}")
            }
        }
        Timber.d("Library merge complete: $mergedCount entries updated/added")
        return mergedCount
    }

    /**
     * Uploads all categories to Firestore.
     */
    suspend fun uploadCategories(userId: String) {
        try {
            val categories = db.getCategories().executeAsBlocking()
            val batches = categories.chunked(500)
            for (batch in batches) {
                val writeBatch = firestore.batch()
                batch.forEach { category ->
                    val data = hashMapOf(
                        "name" to category.name,
                        "order" to category.order,
                        "updatedAt" to Timestamp.now(),
                    )
                    writeBatch.set(getCategoryDoc(userId, category.id!!), data, SetOptions.merge())
                }
                writeBatch.commit().await()
            }
            Timber.d("Uploaded ${categories.size} categories")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload categories")
        }
    }

    /**
     * Downloads categories from Firestore.
     */
    suspend fun downloadCategories(userId: String): List<CategorySyncData> {
        return try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_LIBRARY)
                .collection(COLLECTION_CATEGORIES)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(CategorySyncData::class.java)?.copy(
                    categoryId = doc.id.toLongOrNull() ?: return@mapNotNull null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download categories")
            emptyList()
        }
    }

    /**
     * Merges remote categories with local.
     * Creates missing categories and updates order of existing ones.
     */
    suspend fun mergeCategories(userId: String): Int {
        val remoteCategories = downloadCategories(userId)
        if (remoteCategories.isEmpty()) return 0

        var mergedCount = 0
        for (remote in remoteCategories) {
            try {
                val localCategory = remote.categoryId.let { db.getCategoryById(it).executeAsBlocking() }

                if (localCategory == null) {
                    // Category doesn't exist locally — create it
                    val newCategory = CategoryImpl().apply {
                        id = remote.categoryId
                        name = remote.name
                        order = remote.order
                    }
                    db.insertCategory(newCategory).executeAsBlocking()
                    mergedCount++
                } else {
                    // Update order if remote is newer
                    localCategory.order = remote.order
                    db.updateCategory(localCategory).executeAsBlocking()
                    mergedCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge category: ${remote.name}")
            }
        }
        Timber.d("Categories merge complete: $mergedCount entries updated/added")
        return mergedCount
    }

    /**
     * Uploads manga-category relationships.
     */
    suspend fun uploadMangaCategories(userId: String) {
        try {
            val mangaCategories = db.getMangaCategories().executeAsBlocking()
            val batches = mangaCategories.chunked(500)
            for (batch in batches) {
                val writeBatch = firestore.batch()
                batch.forEach { mc ->
                    val data = hashMapOf(
                        "mangaId" to mc.manga_id,
                        "categoryId" to mc.category_id,
                        "updatedAt" to Timestamp.now(),
                    )
                    writeBatch.set(getMangaCategoryDoc(userId, mc.manga_id, mc.category_id), data, SetOptions.merge())
                }
                writeBatch.commit().await()
            }
            Timber.d("Uploaded ${mangaCategories.size} manga-category relationships")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload manga-categories")
        }
    }

    /**
     * Downloads manga-category relationships from Firestore.
     */
    suspend fun downloadMangaCategories(userId: String): List<MangaCategorySyncData> {
        return try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_LIBRARY)
                .collection(COLLECTION_MANGA_CATEGORIES)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(MangaCategorySyncData::class.java)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download manga-categories")
            emptyList()
        }
    }

    /**
     * Merges manga-category relationships.
     */
    suspend fun mergeMangaCategories(userId: String): Int {
        val remote = downloadMangaCategories(userId)
        if (remote.isEmpty()) return 0

        var mergedCount = 0
        for (item in remote) {
            try {
                val exists = db.getMangaCategory(item.mangaId, item.categoryId).executeAsBlocking()
                if (exists == null) {
                    db.insertMangaCategory(MangaCategory.create(item.mangaId, item.categoryId)).executeAsBlocking()
                    mergedCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge manga-category: ${item.mangaId} -> ${item.categoryId}")
            }
        }
        Timber.d("Manga-categories merge complete: $mergedCount entries added")
        return mergedCount
    }

    /**
     * Deletes all library data for the user from Firestore.
     */
    suspend fun clearCloudLibrary(userId: String) {
        try {
            val collections = listOf(COLLECTION_MANGAS, COLLECTION_CATEGORIES, COLLECTION_MANGA_CATEGORIES)
            for (collection in collections) {
                val docs = firestore.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(COLLECTION_LIBRARY)
                    .collection(collection)
                    .get()
                    .await()
                val batch = firestore.batch()
                docs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Timber.d("Cleared all cloud library for user: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cloud library")
        }
    }

    private fun getMangaDoc(userId: String, mangaId: Long) =
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_LIBRARY)
            .collection(COLLECTION_MANGAS)
            .document("m_$mangaId")

    private fun getCategoryDoc(userId: String, categoryId: Long) =
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_LIBRARY)
            .collection(COLLECTION_CATEGORIES)
            .document("c_$categoryId")

    private fun getMangaCategoryDoc(userId: String, mangaId: Long, categoryId: Long) =
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_LIBRARY)
            .collection(COLLECTION_MANGA_CATEGORIES)
            .document("mc_${mangaId}_$categoryId")
}

/**
 * Data class for syncing manga library entries.
 */
data class LibraryMangaSyncData(
    val mangaId: Long = 0L,
    val source: Long = 0L,
    val url: String = "",
    val title: String = "",
    val favorite: Boolean = false,
    val dateAdded: Long = 0L,
    val lastUpdate: Long = 0L,
    val viewerFlags: Int = 0,
    val chapterFlags: Int = 0,
    val updatedAt: Timestamp? = null,
)

/**
 * Data class for syncing categories.
 */
data class CategorySyncData(
    val categoryId: Long = 0L,
    val name: String = "",
    val order: Int = 0,
    val updatedAt: Timestamp? = null,
)

/**
 * Data class for syncing manga-category relationships.
 */
data class MangaCategorySyncData(
    val mangaId: Long = 0L,
    val categoryId: Long = 0L,
    val updatedAt: Timestamp? = null,
)
