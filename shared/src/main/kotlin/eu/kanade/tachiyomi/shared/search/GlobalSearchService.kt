package eu.kanade.tachiyomi.shared.search

import eu.kanade.tachiyomi.shared.model.SearchResult
import eu.kanade.tachiyomi.shared.source.MangaSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GlobalSearchService(
    private val sources: () -> List<MangaSource>,
) {
    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@coroutineScope emptyList()

        sources().map { source ->
            async {
                runCatching {
                    SearchResult(
                        manga = source.search(normalized),
                        sourceId = source.id,
                        sourceName = source.name,
                    )
                }.getOrElse { error ->
                    SearchResult(
                        manga = emptyList(),
                        sourceId = source.id,
                        sourceName = source.name,
                        error = error.message ?: error::class.simpleName,
                    )
                }
            }
        }.awaitAll()
    }
}
