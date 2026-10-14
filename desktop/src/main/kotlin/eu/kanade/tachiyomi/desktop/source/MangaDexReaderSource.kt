package eu.kanade.tachiyomi.desktop.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * JVM-native MangaDex chapter reader support.
 *
 * Page URLs are resolved per chapter from MangaDex@Home; its returned base URL
 * is temporary and must not be persisted.
 */
class MangaDexReaderSource {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    suspend fun pages(chapterId: String, dataSaver: Boolean = true): List<String> =
        withContext(Dispatchers.IO) {
            require(isUuid(chapterId)) { "Invalid MangaDex chapter ID." }
            val payload = getJson("https://api.mangadex.org/at-home/server/$chapterId")
            val baseUrl = payload["baseUrl"]?.jsonPrimitive?.contentOrNull
                ?: error("MangaDex did not return an image server URL.")
            val chapter = payload["chapter"]?.jsonObject
                ?: error("MangaDex did not return chapter image metadata.")
            val hash = chapter["hash"]?.jsonPrimitive?.contentOrNull
                ?: error("MangaDex did not return a chapter image hash.")
            val key = if (dataSaver) "dataSaver" else "data"
            val filenames = chapter[key]?.jsonArray
                ?: error("MangaDex did not return image filenames.")
            val qualityPath = if (dataSaver) "data-saver" else "data"
            filenames.map { it.jsonPrimitive.content }.map { filename ->
                "$baseUrl/$qualityPath/$hash/$filename"
            }
        }

    suspend fun loadImage(url: String): ImageBitmap = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Only HTTPS image URLs are supported." }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "TachiyomiDNP-Desktop/1.9.3")
            .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("MangaDex image server returned HTTP ${response.statusCode()}.")
        }
        val decoded: BufferedImage = ImageIO.read(ByteArrayInputStream(response.body()))
            ?: error("The chapter page could not be decoded as an image.")
        decoded.toComposeImageBitmap()
    }

    private fun getJson(url: String) = run {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "TachiyomiDNP-Desktop/1.9.3")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("MangaDex returned HTTP ${response.statusCode()}.")
        }
        Json.parseToJsonElement(response.body()).jsonObject
    }

    private fun isUuid(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
}
