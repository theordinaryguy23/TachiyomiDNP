package eu.kanade.tachiyomi.extension.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ExtensionRepoIndex(
    @ProtoNumber(1)
    val extensions: List<ExtensionProtoObject> = emptyList(),
)

@Serializable
data class ExtensionProtoObject(
    @ProtoNumber(1)
    val name: String = "",
    @ProtoNumber(2)
    val pkg: String = "",
    @ProtoNumber(3)
    val apk: String = "",
    @ProtoNumber(4)
    val lang: String = "",
    @ProtoNumber(5)
    val code: Long = 0,
    @ProtoNumber(6)
    val version: String = "",
    @ProtoNumber(7)
    val nsfw: Int = 0,
    @ProtoNumber(8)
    val sources: List<ExtensionSource> = emptyList(),
)

@Serializable
data class ExtensionSource(
    @ProtoNumber(1)
    val id: Long = 0,
    @ProtoNumber(2)
    val lang: String = "",
    @ProtoNumber(3)
    val name: String = "",
    @ProtoNumber(4)
    val baseUrl: String = "",
)
