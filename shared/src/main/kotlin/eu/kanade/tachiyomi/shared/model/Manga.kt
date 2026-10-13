package eu.kanade.tachiyomi.shared.model

import kotlinx.serialization.Serializable

/**
 * Platform-neutral manga metadata returned by a source.
 *
 * A manga's source URL plus source ID identifies it across platforms. Database IDs,
 * favourite state, and other persistence details belong to platform-specific layers.
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
