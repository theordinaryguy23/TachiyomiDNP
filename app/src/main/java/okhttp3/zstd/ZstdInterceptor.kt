package okhttp3.zstd

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ZstdInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // No-op stub for compatibility
        return chain.proceed(chain.request())
    }

    companion object {
        @JvmStatic
        fun create(): ZstdInterceptor = ZstdInterceptor()
    }
}
