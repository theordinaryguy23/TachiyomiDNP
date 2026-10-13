package eu.kanade.tachiyomi.desktop.source

import eu.kanade.tachiyomi.shared.model.Manga
import eu.kanade.tachiyomi.shared.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

/**
 * JVM-native MangaDex adapter. It deliberately does not depend on Android CatalogueSource
 * or the Android extension runtime.
 */
class MangaDexSource : MangaSource {
    override val id: Long = -1_000_001L
    override val name: String = "MangaDex"

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override suspend fun search(query: String): List<Manga> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(normalized, UTF_8)
        val url = "https://api.mangadex.org/manga?title=$encodedQuery&limit=10&contentRating%5B%5D=safe&contentRating%5B%5D=suggestive&includes%5B%5D=cover_art"
        val payload = getJson(url)
        val entries = payload["data"]?.jsonArray ?: JsonArray(emptyList())

        entries.mapNotNull { entry ->
            val item = entry.jsonObject
            val mangaId = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attributes = item["attributes"]?.jsonObject ?: return@mapNotNull null
            val title = localizedValue(attributes["title"]?.jsonObject) ?: "Untitled manga"
            val coverFile = item["relationships"]?.jsonArray
                ?.firstOrNull { relation ->
                    relation.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "cover_art"
                }
                ?.jsonObject?.get("attributes")?.jsonObject
                ?.get("fileName")?.jsonPrimitive?.contentOrNull

            Manga(
                sourceId = id,
                title = title,
                url = "https://mangadex.org/title/$mangaId",
                thumbnailUrl = coverFile?.let { "https://uploads.mangadex.org/covers/$mangaId/$it.256.jpg" },
                description = localizedValue(attributes["description"]?.jsonObject),
            )
        }
    }

    suspend fun details(mangaId: String): MangaDetails = withContext(Dispatchers.IO) {
        require(isUuid(mangaId)) { "Invalid MangaDex manga ID." }
        val payload = getJson("https://api.mangadex.org/manga/$mangaId?includes%5B%5D=cover_art&includes%5B%5D=author&includes%5B%5D=artist")
        val item = payload["data"]?.jsonObject ?: error("MangaDex response did not contain manga data.")
        val attributes = item["attributes"]?.jsonObject ?: error("MangaDex response did not contain manga attributes.")
        val relationships = item["relationships"]?.jsonArray ?: JsonArray(emptyList())

        fun relationshipNames(type: String): List<String> = relationships.mapNotNull { relation ->
            val obj = relation.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != type) return@mapNotNull null
            obj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        }

        val coverFile = relationships.firstOrNull { relation ->
            relation.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "cover_art"
        }?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull

        MangaDetails(
            id = mangaId,
            title = localizedValue(attributes["title"]?.jsonObject) ?: "Untitled manga",
            description = localizedValue(attributes["description"]?.jsonObject),
            status = attributes["status"]?.jsonPrimitive?.contentOrNull,
            authors = relationshipNames("author"),
            artists = relationshipNames("artist"),
            coverUrl = coverFile?.let { "https://uploads.mangadex.org/covers/$mangaId/$it.512.jpg" },
        )
    }

    suspend fun chapters(mangaId: String, limit: Int = 30): List<MangaChapter> = withContext(Dispatchers.IO) {
        require(isUuid(mangaId)) { "Invalid MangaDex manga ID." }
        val safeLimit = limit.coerceIn(1, 100)
        val url = "https://api.mangadex.org/chapter?manga=$mangaId&limit=$safeLimit&translatedLanguage%5B%5D=en&order%5Bchapter%5D=asc&order%5Bvolume%5D=asc"
        val payload = getJson(url)
        val entries = payload["data"]?.jsonArray ?: JsonArray(emptyList())

        entries.mapNotNull { entry ->
            val item = entry.jsonObject
            val chapterId = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attributes = item["attributes"]?.jsonObject ?: return@mapNotNull null
            MangaChapter(
                id = chapterId,
                title = attributes["title"]?.jsonPrimitive?.contentOrNull ?: "",
                chapter = attributes["chapter"]?.jsonPrimitive?.contentOrNull,
                volume = attributes["volume"]?.jsonPrimitive?.contentOrNull,
                language = attributes["translatedLanguage"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun getJson(url: String): JsonObject {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "TachiyomiDNP-Desktop/1.9.3")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("MangaDex returned HTTP ${response.statusCode()}")
        }
        return Json.parseToJsonElement(response.body()).jsonObject
    }

    private fun localizedValue(values: JsonObject?): String? =
        values?.get("en")?.jsonPrimitive?.contentOrNull
            ?: values?.get("ja-ro")?.jsonPrimitive?.contentOrNull
            ?: values?.values?.firstNotNullOfOrNull { value ->
                runCatching { value.jsonPrimitive.content }.getOrNull()
            }

    private fun isUuid(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
}
