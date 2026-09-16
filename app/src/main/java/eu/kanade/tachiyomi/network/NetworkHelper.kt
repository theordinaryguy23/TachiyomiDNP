package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

object UserAgentPool {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15",
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Android 14; Mobile; rv:135.0) Gecko/135.0 Firefox/135.0",
    )

    fun getNextUserAgent(current: String): String {
        val trimmed = current.trim()
        val index = USER_AGENTS.indexOf(trimmed)
        return if (index != -1 && index + 1 < USER_AGENTS.size) {
            USER_AGENTS[index + 1]
        } else {
            USER_AGENTS.first()
        }
    }
}

class NetworkHelper(
    val context: Context,
) {
    private val preferences: PreferencesHelper by injectLazy()

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 50L * 1024 * 1024 // 50 MiB

    val cookieJar = AndroidCookieJar()

    private val userAgentInterceptor by lazy { UserAgentInterceptor(::defaultUserAgent) }
    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(context, cookieJar, ::defaultUserAgent, ::rotateUserAgent)
    }

    fun rotateUserAgent(): String {
        val currentUa = defaultUserAgent
        val nextUa = UserAgentPool.getNextUserAgent(currentUa)
        preferences.defaultUserAgent().set(nextUa)
        Timber.d("Rotated User-Agent to: $nextUa")
        return nextUa
    }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder =
                OkHttpClient
                    .Builder()
                    .cookieJar(cookieJar)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(2, TimeUnit.MINUTES)
                    .addInterceptor(UncaughtExceptionInterceptor())
                    .addInterceptor(userAgentInterceptor)
                    .addInterceptor(cloudflareInterceptor)
                    .apply {
                        when (preferences.dohProvider()) {
                            PREF_DOH_CLOUDFLARE -> dohCloudflare()
                            PREF_DOH_GOOGLE -> dohGoogle()
                            PREF_DOH_ADGUARD -> dohAdGuard()
                            PREF_DOH_QUAD9 -> dohQuad9()
                        }
                    }

            return builder
        }

    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }

    @Suppress("UNUSED")
    val cloudflareClient by lazy {
        client
            .newBuilder()
            .addInterceptor(cloudflareInterceptor)
            .build()
    }

    val defaultUserAgent
        get() =
            preferences
                .defaultUserAgent()
                .get()
                .replace("\n", " ")
                .trim()

    companion object {
        val DEFAULT_USER_AGENT = UserAgentPool.DEFAULT_USER_AGENT
    }
}

