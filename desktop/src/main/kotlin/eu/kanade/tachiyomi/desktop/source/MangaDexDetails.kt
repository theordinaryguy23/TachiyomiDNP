package eu.kanade.tachiyomi.desktop.source

data class MangaDetails(
    val id: String,
    val title: String,
    val description: String?,
    val status: String?,
    val authors: List<String>,
    val artists: List<String>,
    val coverUrl: String?,
)

data class MangaChapter(
    val id: String,
    val title: String,
    val chapter: String?,
    val volume: String?,
    val language: String?,
)
