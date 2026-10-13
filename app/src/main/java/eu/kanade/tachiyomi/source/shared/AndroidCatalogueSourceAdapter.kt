package eu.kanade.tachiyomi.source.shared

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.shared.model.Manga
import eu.kanade.tachiyomi.shared.source.MangaSource

/**
 * Android adapter that exposes the existing CatalogueSource implementation to the
 * platform-neutral shared search service.
 */
class AndroidCatalogueSourceAdapter(
    private val source: CatalogueSource,
) : MangaSource {
    override val id: Long
        get() = source.id

    override val name: String
        get() = source.name

    override suspend fun search(query: String): List<Manga> {
        val filters = try {
            source.getFilterList()
        } catch (_: Throwable) {
            FilterList()
        }

        return source
            .getSearchManga(1, query, filters)
            .mangas
            .take(10)
            .map { manga ->
                Manga(
                    id = 0L,
                    title = manga.title,
                    url = manga.url,
                    thumbnailUrl = manga.thumbnail_url,
                    author = manga.author,
                    artist = manga.artist,
                    description = manga.description,
                    status = manga.status,
                )
            }
    }
}
