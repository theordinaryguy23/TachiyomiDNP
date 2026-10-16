package eu.kanade.tachiyomi.desktop.reader

import eu.kanade.tachiyomi.desktop.source.MangaChapter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopChapterDownloadStoreTest {
    private val chapter = MangaChapter(
        id = "12345678-1234-1234-1234-123456789abc",
        title = "Test chapter",
        chapter = "12",
        volume = "2",
        language = "en",
    )

    @Test
    fun downloadedChapterCanBeListedAndReadAfterCreatingANewStore() {
        val root = Files.createTempDirectory("tachiyomi-downloads")
        val firstPage = byteArrayOf(1, 2, 3, 4)
        val secondPage = byteArrayOf(5, 6, 7)

        DesktopChapterDownloadStore(root).saveChapter(chapter, listOf(firstPage, secondPage))
        val reopenedStore = DesktopChapterDownloadStore(root)

        assertEquals(1, reopenedStore.listDownloads().size)
        assertEquals(2, reopenedStore.find(chapter.id)?.pageCount)
        assertContentEquals(firstPage, reopenedStore.readPageBytes(chapter.id, 0))
        assertContentEquals(secondPage, reopenedStore.readPageBytes(chapter.id, 1))
    }

    @Test
    fun deletingChapterRemovesItFromDownloads() {
        val root = Files.createTempDirectory("tachiyomi-downloads")
        val store = DesktopChapterDownloadStore(root)
        store.saveChapter(chapter, listOf(byteArrayOf(9)))

        store.delete(chapter.id)

        assertNull(store.find(chapter.id))
        assertEquals(0, store.listDownloads().size)
    }

    @Test
    fun emptyChapterDownloadsAreRejected() {
        val root = Files.createTempDirectory("tachiyomi-downloads")
        val store = DesktopChapterDownloadStore(root)

        assertFailsWith<IllegalArgumentException> {
            store.saveChapter(chapter, emptyList())
        }
    }

    @Test
    fun invalidChapterIdsAreRejected() {
        val root = Files.createTempDirectory("tachiyomi-downloads")
        val store = DesktopChapterDownloadStore(root)
        val invalid = chapter.copy(id = "../not-a-uuid")

        assertFailsWith<IllegalArgumentException> {
            store.saveChapter(invalid, listOf(byteArrayOf(1)))
        }
    }
}
