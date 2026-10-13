package eu.kanade.tachiyomi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: Long,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val status: Int = 0,
)
