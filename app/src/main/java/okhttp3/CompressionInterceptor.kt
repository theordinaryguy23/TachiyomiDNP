package okhttp3

import okhttp3.Interceptor
import okio.Buffer
import java.io.IOException

class CompressionInterceptor(
    private val algorithms: Array<DecompressionAlgorithm>,
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // No-op stub for compatibility with extensions expecting CompressionInterceptor
        return chain.proceed(chain.request())
    }

    interface DecompressionAlgorithm

    object Brotli : DecompressionAlgorithm {
        @JvmStatic
        val INSTANCE: DecompressionAlgorithm = Brotli
    }

    object Gzip : DecompressionAlgorithm {
        @JvmStatic
        val INSTANCE: DecompressionAlgorithm = Gzip
    }

    object Zstd : DecompressionAlgorithm {
        @JvmStatic
        val INSTANCE: DecompressionAlgorithm = Zstd
    }

    companion object {
        @JvmStatic
        fun create(vararg algorithms: DecompressionAlgorithm): CompressionInterceptor {
            return CompressionInterceptor(algorithms.clone())
        }
    }
}
