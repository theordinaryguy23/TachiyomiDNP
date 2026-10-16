package eu.kanade.tachiyomi.desktop.reader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopReaderProgressStoreTest {
    private val chapterId = "12345678-1234-1234-1234-123456789abc"

    @Test
    fun savedPageIndexCanBeLoadedAfterCreatingANewStore() {
        val directory = Files.createTempDirectory("tachiyomi-reader-progress")
        val progressFile = directory.resolve("reader-progress.properties")

        DesktopReaderProgressStore(progressFile).savePageIndex(chapterId, 12)

        assertEquals(12, DesktopReaderProgressStore(progressFile).loadPageIndex(chapterId))
    }

    @Test
    fun missingProgressDefaultsToFirstPage() {
        val directory = Files.createTempDirectory("tachiyomi-reader-progress")

        assertEquals(
            0,
            DesktopReaderProgressStore(directory.resolve("missing.properties")).loadPageIndex(chapterId),
        )
    }

    @Test
    fun negativePageIndexesAreRejected() {
        val directory = Files.createTempDirectory("tachiyomi-reader-progress")
        val store = DesktopReaderProgressStore(directory.resolve("reader-progress.properties"))

        assertFailsWith<IllegalArgumentException> {
            store.savePageIndex(chapterId, -1)
        }
    }

    @Test
    fun invalidChapterIdsAreRejectedWhenSaving() {
        val directory = Files.createTempDirectory("tachiyomi-reader-progress")
        val store = DesktopReaderProgressStore(directory.resolve("reader-progress.properties"))

        assertFailsWith<IllegalArgumentException> {
            store.savePageIndex("not-a-uuid", 3)
        }
    }
}
