package okhttp3.zstd

import okhttp3.CompressionInterceptor
import okhttp3.CompressionInterceptor.DecompressionAlgorithm
import okio.BufferedSource
import okio.Source

object Zstd : DecompressionAlgorithm {
    override val encoding: String get() = "zstd"

    override fun decompress(compressedSource: BufferedSource): Source {
        // Stub: no-op to avoid dependency on native Zstd decoder.
        return compressedSource
    }
}
