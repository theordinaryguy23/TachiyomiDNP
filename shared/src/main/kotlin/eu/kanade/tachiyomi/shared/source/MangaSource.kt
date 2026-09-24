package eu.kanade.tachiyomi.shared.source

import eu.kanade.tachiyomi.shared.model.Manga

interface MangaSource {
    val id: Long
    val name: String

    suspend fun search(query: String): List<Manga>
}
