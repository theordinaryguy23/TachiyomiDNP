package eu.kanade.tachiyomi.shared.model

import kotlinx.serialization.Serializable

/**
 * Platform-neutral manga metadata returned by a source.
 *
 * This is a domain model, not a database row: persistent database IDs and Android-specific
 * state belong to platform repositories. A manga's source + URL identify its source entry.
 */
@Serializable
data class Manga(
    val sourceId: Long,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val status: Int = 0,
)
