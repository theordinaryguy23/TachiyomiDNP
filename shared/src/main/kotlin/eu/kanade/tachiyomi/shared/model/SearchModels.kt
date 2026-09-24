package eu.kanade.tachiyomi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchQuery(
    val query: String,
)

@Serializable
data class SearchResult(
    val manga: List<Manga>,
    val sourceId: Long,
    val sourceName: String,
    val isLoading: Boolean = false,
    val error: String? = null,
)
