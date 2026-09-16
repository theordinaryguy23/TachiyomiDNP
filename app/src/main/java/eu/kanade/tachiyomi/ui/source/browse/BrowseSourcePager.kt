package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList

open class BrowseSourcePager(
    val source: CatalogueSource,
    val query: String,
    val filters: FilterList,
) : Pager() {
    override suspend fun requestNextPage() {
        val page = currentPage

        val mangasPage = try {
            if (query.isBlank() && filters.isEmpty()) {
                source.getPopularManga(page)
            } else {
                source.getSearchManga(page, query, filters)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw if (e is Exception) e else Exception(e.message ?: e.toString(), e)
        }

        val mangas = mangasPage?.mangas
        if (!mangas.isNullOrEmpty()) {
            onPageReceived(mangasPage)
        } else {
            throw NoResultsException()
        }
    }
}
