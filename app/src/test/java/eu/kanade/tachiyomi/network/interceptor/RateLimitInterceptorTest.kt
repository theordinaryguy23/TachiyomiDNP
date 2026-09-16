package eu.kanade.tachiyomi.network.interceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RateLimitInterceptorTest {

    @Test
    fun `rateLimit throttles requests exceeding permits within period`() {
        val requestTimestamps = mutableListOf<Long>()

        val client = OkHttpClient.Builder()
            .rateLimit(permits = 2, period = 1, unit = TimeUnit.SECONDS)
            .addInterceptor { chain ->
                requestTimestamps.add(System.currentTimeMillis())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder().url("https://example.com").build()

        client.newCall(request).execute().close()
        client.newCall(request).execute().close()
        client.newCall(request).execute().close()

        assertEquals(3, requestTimestamps.size)
        // Third request must be delayed because permits=2 per 1 second
        val duration = requestTimestamps[2] - requestTimestamps[0]
        assertTrue("Expected duration >= 900ms, got ${duration}ms", duration >= 900)
    }

    @Test
    fun `rateLimit retries on 429 response`() {
        var attempts = 0

        val client = OkHttpClient.Builder()
            .rateLimit(permits = 5, period = 1, unit = TimeUnit.SECONDS)
            .addInterceptor { chain ->
                attempts++
                val code = if (attempts < 2) 429 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Too Many Requests")
                    .header("Retry-After", "1")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder().url("https://example.com").build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, attempts)
        response.close()
    }

    @Test
    fun `rateLimitHost throttles requests matching specific host`() {
        val requestTimestamps = mutableListOf<Long>()
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.manga.com").build()

        val client = OkHttpClient.Builder()
            .rateLimitHost(url, permits = 2, period = 1, unit = TimeUnit.SECONDS)
            .addInterceptor { chain ->
                requestTimestamps.add(System.currentTimeMillis())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder().url("https://api.manga.com/search").build()

        client.newCall(request).execute().close()
        client.newCall(request).execute().close()
        client.newCall(request).execute().close()

        assertEquals(3, requestTimestamps.size)
        val duration = requestTimestamps[2] - requestTimestamps[0]
        assertTrue("Expected duration >= 900ms, got ${duration}ms", duration >= 900)
    }
}
