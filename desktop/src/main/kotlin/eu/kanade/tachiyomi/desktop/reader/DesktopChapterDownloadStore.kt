package eu.kanade.tachiyomi.desktop.reader

import eu.kanade.tachiyomi.desktop.source.MangaChapter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

data class DownloadedChapter(
    val id: String,
    val title: String,
    val chapter: String?,
    val volume: String?,
    val pageCount: Int,
) {
    fun asMangaChapter() = MangaChapter(
        id = id,
        title = title,
        chapter = chapter,
        volume = volume,
        language = "en",
    )
}

/**
 * Stores complete chapters under the user's local app-data directory.
 * Only image bytes and stable chapter metadata are stored; temporary CDN URLs are not.
 */
class DesktopChapterDownloadStore(
    private val root: Path = defaultRoot(),
) {
    @Synchronized
    fun listDownloads(): List<DownloadedChapter> {
        if (!Files.isDirectory(root)) return emptyList()
        return try {
            Files.list(root).use { paths ->
                paths.filter { Files.isDirectory(it) && Files.isRegularFile(it.resolve(METADATA_FILE)) }
                    .map { readMetadata(it) }
                    .filter { it != null }
                    .map { it!! }
                    .sortedWith(compareBy<DownloadedChapter> { it.title.lowercase() }.thenBy { it.chapter ?: "" })
                    .toList()
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @Synchronized
    fun find(chapterId: String): DownloadedChapter? {
        if (!isValidChapterId(chapterId)) return null
        return readMetadata(root.resolve(chapterId))
    }

    fun isDownloaded(chapterId: String): Boolean = find(chapterId) != null

    @Synchronized
    fun readPageBytes(chapterId: String, pageIndex: Int): ByteArray {
        val chapter = find(chapterId) ?: error("This chapter is not downloaded.")
        require(pageIndex in 0 until chapter.pageCount) { "Invalid downloaded page index." }
        return Files.readAllBytes(pagePath(chapterId, pageIndex))
    }

    @Synchronized
    fun saveChapter(chapter: MangaChapter, pageBytes: List<ByteArray>) {
        require(isValidChapterId(chapter.id)) { "Invalid MangaDex chapter ID." }
        require(pageBytes.isNotEmpty()) { "Cannot save a chapter with no pages." }
        require(pageBytes.all { it.isNotEmpty() }) { "A downloaded page is empty." }

        Files.createDirectories(root)
        val staging = Files.createTempDirectory(root, "${chapter.id}.tmp-")
        try {
            pageBytes.forEachIndexed { index, bytes ->
                Files.write(staging.resolve(pageFileName(index)), bytes)
            }
            val properties = Properties().apply {
                setProperty("id", chapter.id)
                setProperty("title", chapter.title)
                setProperty("chapter", chapter.chapter.orEmpty())
                setProperty("volume", chapter.volume.orEmpty())
                setProperty("pageCount", pageBytes.size.toString())
            }
            Files.newOutputStream(staging.resolve(METADATA_FILE)).use { properties.store(it, "TachiyomiDNP offline chapter") }

            val destination = root.resolve(chapter.id)
            if (Files.exists(destination)) deleteRecursively(destination)
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(staging, destination)
            }
        } catch (failure: Exception) {
            if (Files.exists(staging)) runCatching { deleteRecursively(staging) }
            throw failure
        }
    }

    @Synchronized
    fun delete(chapterId: String) {
        if (!isValidChapterId(chapterId)) return
        val destination = root.resolve(chapterId)
        if (Files.exists(destination)) deleteRecursively(destination)
    }

    private fun readMetadata(directory: Path): DownloadedChapter? = try {
        val file = directory.resolve(METADATA_FILE)
        if (!Files.isRegularFile(file)) return null
        val properties = Properties().apply { Files.newInputStream(file).use(::load) }
        val id = properties.getProperty("id") ?: return null
        val pageCount = properties.getProperty("pageCount")?.toIntOrNull() ?: return null
        if (!isValidChapterId(id) || pageCount <= 0 || directory.fileName.toString() != id) return null
        DownloadedChapter(
            id = id,
            title = properties.getProperty("title", "Untitled chapter"),
            chapter = properties.getProperty("chapter")?.ifBlank { null },
            volume = properties.getProperty("volume")?.ifBlank { null },
            pageCount = pageCount,
        )
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun pagePath(chapterId: String, pageIndex: Int) =
        root.resolve(chapterId).resolve(pageFileName(pageIndex))

    private fun pageFileName(index: Int) = "page-${(index + 1).toString().padStart(5, '0')}.bin"

    private fun deleteRecursively(path: Path) {
        if (Files.isDirectory(path)) {
            Files.list(path).use { children -> children.forEach(::deleteRecursively) }
        }
        Files.deleteIfExists(path)
    }

    private fun isValidChapterId(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

    companion object {
        private const val METADATA_FILE = "chapter.properties"

        private fun defaultRoot(): Path {
            val root = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")
            return Path.of(root, "TachiyomiDNP", "downloads")
        }
    }
}
