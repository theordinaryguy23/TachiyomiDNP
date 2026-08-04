package okhttp3.brotli

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class BrotliInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
