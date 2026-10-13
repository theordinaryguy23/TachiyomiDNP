package eu.kanade.tachiyomi.desktop.source

import eu.kanade.tachiyomi.shared.model.Manga
import eu.kanade.tachiyomi.shared.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * Small JVM-native MangaDex adapter used to prove the desktop search path end to end.
 * This intentionally does not depend on Android's CatalogueSource or extension runtime.
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
        val url = "https://api.mangadex.org/manga?title=$encodedQuery&limit=10&contentRating[]=safe&contentRating[]=suggestive&includes[]=cover_art"
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

        val payload = Json.parseToJsonElement(response.body()).jsonObject
        val entries = payload["data"]?.jsonArray ?: JsonArray(emptyList())
        entries.mapNotNull { entry ->
            val item = entry.jsonObject
            val mangaId = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attributes = item["attributes"]?.jsonObject ?: return@mapNotNull null
            val titleMap = attributes["title"]?.jsonObject
            val title = titleMap?.get("en")?.jsonPrimitive?.contentOrNull
                ?: titleMap?.get("ja-ro")?.jsonPrimitive?.contentOrNull
                ?: titleMap?.values?.firstNotNullOfOrNull { value ->
                    runCatching { value.jsonPrimitive.content }.getOrNull()
                }
                ?: "Untitled manga"

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
                description = attributes["description"]?.jsonObject
                    ?.get("en")?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}
