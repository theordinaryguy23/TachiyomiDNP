package okhttp3

import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.BufferedSource
import okio.GzipSource
import okio.Source
import okio.buffer

open class CompressionInterceptor(
    vararg val algorithms: DecompressionAlgorithm,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (algorithms.isEmpty()) return chain.proceed(chain.request())
        val request = chain.request()
        if (request.header("Accept-Encoding") != null) {
            return chain.proceed(request)
        }

        val acceptEncoding =
            algorithms.joinToString(separator = ", ") { it.encoding }

        val newRequest = request.newBuilder()
            .header("Accept-Encoding", acceptEncoding)
            .build()

        val response = chain.proceed(newRequest)

        return decompress(response)
    }

    protected open fun decompress(response: Response): Response {
        if (response.body == null) return response
        val body = response.body!!
        val encoding = response.header("Content-Encoding") ?: return response
        val algorithm = lookupDecompressor(encoding) ?: return response

        val decompressedSource = algorithm.decompress(body.source()).buffer()
        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(decompressedSource.asResponseBody(body.contentType(), -1L))
            .build()
    }

    protected open fun lookupDecompressor(encoding: String): DecompressionAlgorithm? {
        return algorithms.find { it.encoding.equals(encoding, ignoreCase = true) }
    }

    interface DecompressionAlgorithm {
        val encoding: String

        fun decompress(compressedSource: BufferedSource): Source
    }

    companion object {
        @JvmStatic
        fun create(vararg algorithms: DecompressionAlgorithm): CompressionInterceptor {
            return CompressionInterceptor(algorithms = algorithms)
        }
    }
}
