package eu.kanade.tachiyomi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val id: Long,
    val mangaId: Long,
    val name: String,
    val url: String,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val dateUpload: Long = 0L,
)
