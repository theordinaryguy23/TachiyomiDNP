package okhttp3.zstd

import okhttp3.CompressionInterceptor
import okhttp3.CompressionInterceptor.DecompressionAlgorithm
import okio.BufferedSource
import okio.Source

/**
 * No-op Zstd decompression shim for extension compatibility.
 *
 * OkHttp does not ship an `okhttp-zstd` artifact, so extensions that reference
 * `okhttp3.zstd.Zstd` would crash with NoClassDefFoundError. This stub satisfies
 * the class reference without pulling in a native Zstd decoder.
 */
object Zstd : DecompressionAlgorithm {
    override val encoding: String get() = "zstd"

    override fun decompress(compressedSource: BufferedSource): Source {
        return compressedSource
    }
}
