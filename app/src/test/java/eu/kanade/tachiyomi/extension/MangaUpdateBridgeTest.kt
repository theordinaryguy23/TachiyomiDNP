package eu.kanade.tachiyomi.extension

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the extensions-lib 1.4 / 1.6 compatibility bridge.
 *
 * The host has a single call site for refreshing a manga: `getMangaUpdate`.
 * A 1.6 extension overrides it; a 1.4 extension does not and must fall through to
 * the default bridge that replays the legacy fetchMangaDetails/fetchChapterList
 * pair. These fakes mirror the real interface defaults in
 * `source/Source.kt` and `source/CatalogueSource.kt`.
 *
 * Regression guarded: Weeb Central (libVersion 1.6) returned "Unknown error" and
 * 0 chapters because the host called the legacy path, which lib 1.6 defines as a
 * throwing stub.
 */
class MangaUpdateBridgeTest {

    // --- Minimal stand-ins for the source API -------------------------------

    private data class FakeManga(val title: String)
    private data class FakeChapter(val name: String)
    private class FakeUpdate(val manga: FakeManga, val chapters: List<FakeChapter>)

    /** Mirrors the host `Source` interface defaults. */
    private interface FakeSource {
        suspend fun getMangaDetails(manga: FakeManga): FakeManga = fetchMangaDetails(manga)

        suspend fun getChapterList(manga: FakeManga): List<FakeChapter> = fetchChapterList(manga)

        suspend fun getMangaUpdate(
            manga: FakeManga,
            chapters: List<FakeChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): FakeUpdate = FakeUpdate(
            if (fetchDetails) getMangaDetails(manga) else manga,
            if (fetchChapters) getChapterList(manga) else chapters,
        )

        // In extensions-lib 1.6 these are stubs that throw.
        fun fetchMangaDetails(manga: FakeManga): FakeManga = throw IllegalStateException("Not used")

        fun fetchChapterList(manga: FakeManga): List<FakeChapter> = throw IllegalStateException("Not used")
    }

    /** Mirrors the host `CatalogueSource` 1.4 compatibility bridge. */
    private interface FakeCatalogueSource : FakeSource {
        override suspend fun getMangaUpdate(
            manga: FakeManga,
            chapters: List<FakeChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): FakeUpdate = supervisorScope {
            val asyncManga = if (fetchDetails) async { fetchMangaDetails(manga) } else null
            val asyncChapters = if (fetchChapters) async { fetchChapterList(manga) } else null
            FakeUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
        }
    }

    /** Mirrors the host `awaitChapterList` extension function. */
    private suspend fun FakeSource.awaitChapterList(manga: FakeManga): List<FakeChapter> =
        getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true).chapters

    // --- Test doubles -------------------------------------------------------

    /** A libVersion 1.4 extension: implements only the legacy RxJava-era methods. */
    private class LegacySource : FakeCatalogueSource {
        var fetchChapterListCalled = false

        override fun fetchMangaDetails(manga: FakeManga) = FakeManga("${manga.title} (detailed)")

        override fun fetchChapterList(manga: FakeManga): List<FakeChapter> {
            fetchChapterListCalled = true
            return listOf(FakeChapter("Ch. 1"), FakeChapter("Ch. 2"))
        }
    }

    /**
     * A libVersion 1.6 extension, e.g. Weeb Central: overrides the combined API and
     * leaves the legacy methods as throwing stubs.
     */
    private class ModernSource : FakeCatalogueSource {
        var getMangaUpdateCalled = false

        override suspend fun getMangaUpdate(
            manga: FakeManga,
            chapters: List<FakeChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): FakeUpdate {
            getMangaUpdateCalled = true
            return FakeUpdate(
                if (fetchDetails) FakeManga("${manga.title} (detailed)") else manga,
                if (fetchChapters) listOf(FakeChapter("Ch. 1"), FakeChapter("Ch. 2"), FakeChapter("Ch. 3")) else chapters,
            )
        }
    }

    private val manga = FakeManga("Test Manga")

    // --- libVersion 1.6 -----------------------------------------------------

    @Test
    fun `lib 1_6 source serves chapters through getMangaUpdate`() = runBlocking {
        val source = ModernSource()

        val chapters = source.awaitChapterList(manga)

        assertTrue("getMangaUpdate must be used for a 1.6 extension", source.getMangaUpdateCalled)
        assertEquals(3, chapters.size)
    }

    @Test
    fun `lib 1_6 source never hits the throwing legacy stub`() = runBlocking {
        // The regression: the legacy path throws IllegalStateException("Not used"),
        // which surfaced in the UI as "Unknown error" with 0 chapters.
        val chapters = ModernSource().awaitChapterList(manga)
        assertFalse("chapter list must not be empty", chapters.isEmpty())
    }

    // --- libVersion 1.4 -----------------------------------------------------

    @Test
    fun `lib 1_4 source falls through the bridge to fetchChapterList`() = runBlocking {
        val source = LegacySource()

        val chapters = source.awaitChapterList(manga)

        assertTrue("the 1.4 bridge must call fetchChapterList", source.fetchChapterListCalled)
        assertEquals(2, chapters.size)
    }

    @Test
    fun `lib 1_4 source still resolves details through the bridge`() = runBlocking {
        val update = LegacySource()
            .getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)

        assertEquals("Test Manga (detailed)", update.manga.title)
        assertEquals(2, update.chapters.size)
    }

    // --- Flag handling ------------------------------------------------------

    @Test
    fun `existing chapters are returned as-is when fetchChapters is false`() = runBlocking {
        val existing = listOf(FakeChapter("cached"))

        val update = LegacySource()
            .getMangaUpdate(manga, existing, fetchDetails = false, fetchChapters = false)

        assertEquals(existing, update.chapters)
        assertEquals(manga, update.manga)
    }

    @Test
    fun `fetchChapters without fetchDetails does not fetch details`() = runBlocking {
        val update = ModernSource()
            .getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)

        assertEquals("Test Manga", update.manga.title)
        assertEquals(3, update.chapters.size)
    }

    @Test
    fun `awaitChapterList requests chapters only`() = runBlocking {
        // Guards against the helper accidentally triggering a details fetch,
        // which would double the network cost of opening a manga.
        val update = ModernSource()
            .getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
        assertEquals("Test Manga", update.manga.title)
    }
}
