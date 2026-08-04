package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okhttp3.CompressionInterceptor.DecompressionAlgorithm
import okio.BufferedSource
import okio.Source

object Brotli : DecompressionAlgorithm {
    override val encoding: String get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source {
        // Stub: no-op to avoid dependency on native Brotli decoder.
        return compressedSource
    }
}
