package eu.kanade.tachiyomi.shared.search

import eu.kanade.tachiyomi.shared.model.SearchResult
import eu.kanade.tachiyomi.shared.source.MangaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GlobalSearchService(
    private val sources: () -> List<MangaSource>,
    private val maxConcurrentSources: Int = 5,
) {
    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@coroutineScope emptyList()

        val semaphore = Semaphore(maxConcurrentSources.coerceAtLeast(1))
        sources().map { source ->
            async {
                semaphore.withPermit {
                    try {
                        SearchResult(
                            manga = source.search(normalized),
                            sourceId = source.id,
                            sourceName = source.name,
                        )
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        SearchResult(
                            manga = emptyList(),
                            sourceId = source.id,
                            sourceName = source.name,
                            error = error.message ?: error::class.simpleName,
                        )
                    }
                }
            }
        }.awaitAll()
    }
}
