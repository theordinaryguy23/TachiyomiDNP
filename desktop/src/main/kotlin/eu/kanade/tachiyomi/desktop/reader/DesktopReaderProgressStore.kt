package eu.kanade.tachiyomi.desktop.reader

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Stores the last viewed page for each chapter in the user's local app-data folder.
 *
 * Only chapter IDs and page numbers are persisted. Temporary MangaDex@Home URLs
 * are deliberately not saved because those URLs expire.
 */
class DesktopReaderProgressStore(
    private val progressFile: Path = defaultProgressFile(),
) {
    @Synchronized
    fun loadPageIndex(chapterId: String): Int {
        if (!isValidChapterId(chapterId) || !Files.isRegularFile(progressFile)) return 0

        return try {
            Files.newInputStream(progressFile).use { input ->
                Properties().apply { load(input) }.getProperty(chapterId)?.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0
            }
        } catch (_: IOException) {
            0
        } catch (_: SecurityException) {
            0
        }
    }

    @Synchronized
    fun savePageIndex(chapterId: String, pageIndex: Int) {
        require(isValidChapterId(chapterId)) { "Invalid chapter ID." }
        require(pageIndex >= 0) { "Page index cannot be negative." }

        val properties = Properties()
        if (Files.isRegularFile(progressFile)) {
            try {
                Files.newInputStream(progressFile).use(properties::load)
            } catch (_: IOException) {
                // Recover from a missing/corrupt progress file by writing a fresh one.
            } catch (_: SecurityException) {
                return
            }
        }
        properties.setProperty(chapterId, pageIndex.toString())

        try {
            Files.createDirectories(progressFile.parent)
            val temporary = progressFile.resolveSibling("${progressFile.fileName}.tmp")
            Files.newOutputStream(temporary).use { output ->
                properties.store(output, "TachiyomiDNP reader progress")
            }
            try {
                Files.move(
                    temporary,
                    progressFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, progressFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: IOException) {
            // Reading should remain available even when the profile is read-only.
        } catch (_: SecurityException) {
            // Reading should remain available even when the profile is read-only.
        }
    }

    private fun isValidChapterId(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

    companion object {
        private fun defaultProgressFile(): Path {
            val root = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")
            return Path.of(root, "TachiyomiDNP", "data", "reader-progress.properties")
        }
    }
}
