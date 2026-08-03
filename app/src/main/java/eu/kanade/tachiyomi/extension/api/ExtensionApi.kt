package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

internal class ExtensionApi {
    private val json: Json by injectLazy()
    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            val repos = preferences.extensionRepos().get()
            if (repos.isEmpty()) {
                return@withIOContext emptyList()
            }
            val extensions = repos.flatMap { getExtensions(it) }

            if (extensions.isEmpty()) {
                throw Exception()
            }

            extensions
        }
    }

    private suspend fun getExtensions(repoBaseUrl: String): List<Extension.Available> {
        val baseUrl = repoBaseUrl.trimEnd('/')

        // Detect if URL already points directly to a known index file
        val directRepoJson = baseUrl.endsWith("/repo.json")
        val directIndexPb = baseUrl.endsWith("/index.pb")
        val directIndexMinJson = baseUrl.endsWith("/index.min.json")

        // Extract base directory for constructing relative paths
        val baseDir = when {
            directRepoJson -> baseUrl.substringBeforeLast("/")
            directIndexPb -> baseUrl.substringBeforeLast("/")
            directIndexMinJson -> baseUrl.substringBeforeLast("/")
            else -> baseUrl
        }

        // 1. Try repo.json -> check index_v2 -> fetch protobuf
        if (!directIndexPb && !directIndexMinJson) {
            try {
                val repoJsonUrl = if (directRepoJson) baseUrl else "$baseDir/repo.json"
                val response =
                    networkService.client
                        .newCall(GET(repoJsonUrl))
                        .awaitSuccess()

                val bodyBytes = response.body?.bytes() ?: return emptyList()
                val data = decompressIfGzip(bodyBytes)

                if (data.isJson()) {
                    val text = data.toString(Charsets.UTF_8)
                    val repoInfo = json.decodeFromString<RepoInfo>(text)
                    val indexV2 = repoInfo.indexV2
                    if (indexV2 != null) {
                        return fetchIndex(indexV2)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch repo.json from $repoBaseUrl")
            }
        }

        // 2. Try index.pb directly
        try {
            val indexPbUrl = if (directIndexPb) baseUrl else "$baseDir/index.pb"
            val extensions = fetchIndex(indexPbUrl)
            if (extensions.isNotEmpty()) return extensions
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch index.pb from $repoBaseUrl")
        }

        // 3. Fallback to index.min.json (legacy)
        if (!directIndexPb && !directRepoJson) {
            try {
                val indexMinJsonUrl = if (directIndexMinJson) baseUrl else "$baseDir/index.min.json"
                val response =
                    networkService.client
                        .newCall(GET(indexMinJsonUrl))
                        .awaitSuccess()

                val bodyBytes = response.body?.bytes() ?: return emptyList()
                val data = decompressIfGzip(bodyBytes)

                if (data.isJson()) {
                    val text = data.toString(Charsets.UTF_8)
                    val extensions = json.decodeFromString<List<ExtensionJsonObject>>(text).toExtensions(baseDir)
                    if (extensions.isNotEmpty()) return extensions
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch index.min.json from $repoBaseUrl")
            }
        }

        return emptyList()
    }

    private suspend fun fetchIndex(url: String): List<Extension.Available> {
        val response =
            networkService.client
                .newCall(GET(url))
                .awaitSuccess()

        val bodyBytes = response.body?.bytes() ?: return emptyList()
        val data = decompressIfGzip(bodyBytes)

        val repoUrl = url.substringBeforeLast("/")

        return when {
            data.isEmpty() -> emptyList()
            data.isJson() -> {
                val text = data.toString(Charsets.UTF_8)
                if (text.startsWith("{")) {
                    val repoInfo = json.decodeFromString<RepoInfo>(text)
                    val indexV2 = repoInfo.indexV2
                    if (indexV2 != null) {
                        fetchIndex(indexV2)
                    } else {
                        emptyList()
                    }
                } else {
                    json.decodeFromString<List<ExtensionJsonObject>>(text).toExtensions(repoUrl)
                }
            }
            else -> parseProtobuf(data, repoUrl)
        }
    }

    private fun decompressIfGzip(data: ByteArray): ByteArray {
        return if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            ByteArrayInputStream(data).use { gzipInput ->
                InflaterInputStream(gzipInput).use { inflater ->
                    inflater.readBytes()
                }
            }
        } else {
            data
        }
    }

    private fun ByteArray.isJson(): Boolean {
        return isNotEmpty() && (first() == '['.code.toByte() || first() == '{'.code.toByte())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseProtobuf(data: ByteArray, repoUrl: String): List<Extension.Available> {
        return try {
            val store = ProtoBuf.decodeFromByteArray(NetworkExtensionStore.serializer(), data)
            store.toExtensions(repoUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse protobuf extension index")
            emptyList()
        }
    }

    private fun NetworkExtensionStore.toExtensions(repoUrl: String): List<Extension.Available> {
        return extensionList?.extensions?.map { ext ->
            Extension.Available(
                name = ext.name,
                pkgName = ext.packageName,
                versionName = ext.versionName,
                versionCode = ext.versionCode,
                libVersion = ext.extensionLib.toDoubleOrNull() ?: 0.0,
                lang = ext.sources.firstOrNull()?.language ?: "",
                isNsfw = ext.contentWarning == ContentWarning.NSFW,
                sources = ext.sources.map { source ->
                    Extension.AvailableSource(
                        name = source.name,
                        id = source.id,
                        lang = source.language,
                        baseUrl = source.homeUrl ?: "",
                    )
                },
                apkName = "",
                apkUrl = ext.resources.apkUrl,
                iconUrl = ext.resources.iconUrl,
                repoUrl = repoUrl,
            )
        } ?: emptyList()
    }

    private fun String.extractLibVersion(): Double = substringBeforeLast('.').toDoubleOrNull() ?: 0.0

    suspend fun checkForUpdates(
        context: Context,
        prefetchedExtensions: List<Extension.Available>? = null,
    ): List<Extension.Available> =
        withIOContext {
            val extensions = prefetchedExtensions ?: findExtensions()

            val extensionManager: ExtensionManager = Injekt.get()
            val installedExtensions =
                extensionManager.installedExtensionsFlow.value.ifEmpty {
                    ExtensionLoader
                        .loadExtensionAsync(context)
                        .filterIsInstance<LoadResult.Success>()
                        .map { it.extension }
                }

            val extensionsWithUpdate = mutableListOf<Extension.Available>()
            for (installedExt in installedExtensions) {
                val pkgName = installedExt.pkgName
                val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
                val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
                val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
                val hasUpdate = hasUpdatedVer || hasUpdatedLib
                if (hasUpdate) {
                    extensionsWithUpdate.add(availableExt)
                }
            }

            extensionsWithUpdate
        }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> =
        this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }.map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }

    fun getApkUrl(extension: ExtensionManager.ExtensionInfo): String =
        extension.apkUrl.takeIf { !it.isNullOrEmpty() }
            ?: "${extension.repoUrl}/apk/${extension.apkName}"

    private fun ExtensionJsonObject.extractLibVersion(): Double = version.substringBeforeLast('.').toDouble()
}

@Serializable
private data class RepoInfo(
    val indexV2: String? = null,
    val meta: MetaInfo? = null,
)

@Serializable
private data class MetaInfo(
    val name: String? = null,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<Extension.AvailableSource>?,
)

@Serializable
private data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String? = null,
    @ProtoNumber(3) val signingKey: String? = null,
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
)

@Serializable
private data class Contact(
    @ProtoNumber(1) val website: String? = null,
    @ProtoNumber(2) val discord: String? = null,
)

@Serializable
private data class ExtensionList(
    @ProtoNumber(1) val extensions: List<NetworkExtension> = emptyList(),
)

@Serializable
private data class NetworkExtension(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val packageName: String,
    @ProtoNumber(3) val resources: Resources,
    @ProtoNumber(4) val extensionLib: String,
    @ProtoNumber(5) val versionCode: Long,
    @ProtoNumber(6) val versionName: String,
    @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
    @ProtoNumber(8) val sources: List<Source> = emptyList(),
)

@Serializable
private data class Resources(
    @ProtoNumber(1) val apkUrl: String,
    @ProtoNumber(2) val iconUrl: String,
)

@Serializable
private data class Source(
    @ProtoNumber(1) val id: Long,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val language: String,
    @ProtoNumber(4) val homeUrl: String? = null,
    @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
    @ProtoNumber(7) val message: String? = null,
)

@Suppress("unused")
@Serializable
private enum class ContentWarning {
    @ProtoNumber(0) UNSPECIFIED,
    @ProtoNumber(1) SAFE,
    @ProtoNumber(2) MIXED,
    @ProtoNumber(3) NSFW,
}
