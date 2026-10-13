package eu.kanade.tachiyomi.shared.search

import eu.kanade.tachiyomi.shared.model.Manga
import eu.kanade.tachiyomi.shared.source.MangaSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalSearchServiceTest {
    @Test
    fun blankQueryDoesNotCallSources() = runTest {
        var called = false
        val service = GlobalSearchService(sources = {
            called = true
            emptyList()
        })

        assertTrue(service.search("   ").isEmpty())
        assertEquals(false, called)
    }

    @Test
    fun searchesAllSourcesAndPreservesSourceErrors() = runTest {
        val good = object : MangaSource {
            override val id = 1L
            override val name = "Good"

            override suspend fun search(query: String) =
                listOf(
                    Manga(
                        sourceId = id,
                        title = query,
                        url = "/$query",
                    ),
                )
        }

        val bad = object : MangaSource {
            override val id = 2L
            override val name = "Bad"

            override suspend fun search(query: String): List<Manga> {
                error("boom")
            }
        }

        val results = GlobalSearchService(sources = { listOf(good, bad) }).search("naruto")

        assertEquals(2, results.size)
        assertEquals("naruto", results[0].manga.single().title)
        assertEquals(1L, results[0].manga.single().sourceId)
        assertEquals("/naruto", results[0].manga.single().url)
        assertEquals("boom", results[1].error)
    }
}
