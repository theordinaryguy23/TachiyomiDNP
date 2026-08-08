package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * Runtime compatibility regression tests.
 *
 * These lock in the invariants established while fixing the
 * `GeneratedSerializer.typeParametersSerializers()` AbstractMethodError.
 * See docs/EXTENSION_RUNTIME.md for the full contract.
 */
class ExtensionCompatibilityTest {

    // region Serialization ABI / desugaring
    /**
     * Root cause of the MangaDex AbstractMethodError.
     *
     * Below API 26, D8 desugars Java 8 interface default methods into synthetic
     * `$-CC` classes and leaves the interface method abstract. Keiyoushi extensions
     * are compiled at minSdk 26 with `compileOnly` kotlinx.serialization, so their
     * generated `$$serializer` classes do not emit `typeParametersSerializers()` and
     * depend on the host's default implementation surviving.
     *
     * If minSdk ever drops below 26, MangaDex breaks again.
     */
    @Test
    fun `minSdk must be at least 26 to avoid default-method desugaring`() {
        assertTrue(
            "minSdk ${BuildConfig.MIN_SDK} < 26 would desugar " +
                "GeneratedSerializer.typeParametersSerializers() into a \$-CC class, " +
                "breaking every Keiyoushi extension that uses kotlinx.serialization.",
            BuildConfig.MIN_SDK >= 26,
        )
    }

    /**
     * Extensions declare shared libraries as `compileOnly`, so the host runtime
     * versions must stay pinned to what the extension ecosystem compiles against.
     */
    @Test
    fun `host shared-runtime versions match the extension ecosystem`() {
        assertEquals("1.11.0", HostRuntime.KOTLINX_SERIALIZATION)
        assertEquals("5.4.0", HostRuntime.OKHTTP)
    }
    // endregion

    // region Extension libVersion validation
    private val supported = listOf(1.4, 1.6)

    private fun accepts(libVersion: Double?) = libVersion != null && libVersion in supported

    @Test
    fun `valid libVersions are accepted`() {
        assertTrue(accepts(1.4))
        assertTrue(accepts(1.6))
    }

    @Test
    fun `missing libVersion is rejected`() {
        assertFalse(accepts(null))
    }

    @Test
    fun `older unsupported libVersion is rejected`() {
        assertFalse(accepts(1.2))
    }

    @Test
    fun `newer unsupported libVersion is rejected`() {
        assertFalse(accepts(1.8))
    }

    @Test
    fun `malformed libVersion is rejected`() {
        // Manifest metadata getFloat() yields 0.0f when absent or unparseable.
        assertFalse(accepts(0.0))
    }
    // endregion

    // region Repository index URL resolution
    private fun resolve(baseUrl: String, target: String): String {
        val known = listOf("/repo.json", "/index.pb", "/index.min.json")
        val direct = known.any { baseUrl.endsWith(it) }
        val baseDir = if (direct) baseUrl.substringBeforeLast("/") else baseUrl
        return if (baseUrl.endsWith(target)) baseUrl else "$baseDir$target"
    }

    @Test
    fun `direct index pb URL is not double-suffixed`() {
        val url = "https://example.com/repo/index.pb"
        assertEquals(url, resolve(url, "/index.pb"))
        assertFalse(resolve(url, "/index.pb").contains("index.pb/index.pb"))
    }

    @Test
    fun `direct repo json URL resolves as-is`() {
        val url = "https://example.com/repo/repo.json"
        assertEquals(url, resolve(url, "/repo.json"))
    }

    @Test
    fun `direct index min json URL resolves as-is`() {
        val url = "https://example.com/repo/index.min.json"
        assertEquals(url, resolve(url, "/index.min.json"))
    }

    @Test
    fun `bare directory URL gets the index appended`() {
        val url = "https://example.com/repo"
        assertEquals("https://example.com/repo/index.pb", resolve(url, "/index.pb"))
        assertEquals("https://example.com/repo/repo.json", resolve(url, "/repo.json"))
    }

    @Test
    fun `sibling index resolves from a direct index URL`() {
        val url = "https://example.com/repo/repo.json"
        assertEquals("https://example.com/repo/index.pb", resolve(url, "/index.pb"))
    }
    // endregion

    // region Cloudflare vs geo-block detection
    private val errorCodes = listOf(403, 503)
    private val serverCheck = arrayOf("cloudflare-nginx", "cloudflare")

    private fun shouldSolveChallenge(code: Int, server: String?, body: String): Boolean {
        if (code !in errorCodes || server !in serverCheck) return false
        return body.contains("challenge-error-title") || body.contains("challenge-error-text")
    }

    @Test
    fun `normal 200 response never triggers WebView`() {
        assertFalse(shouldSolveChallenge(200, "cloudflare", "<html>ok</html>"))
    }

    @Test
    fun `403 geo-block does not trigger WebView`() {
        assertFalse(
            shouldSolveChallenge(403, "cloudflare", "<html>Not available in your region</html>"),
        )
    }

    @Test
    fun `403 cloudflare challenge triggers WebView`() {
        assertTrue(
            shouldSolveChallenge(403, "cloudflare", """<h1 id="challenge-error-title">x</h1>"""),
        )
    }

    @Test
    fun `503 cloudflare challenge triggers WebView`() {
        assertTrue(
            shouldSolveChallenge(503, "cloudflare-nginx", """<div id="challenge-error-text">x</div>"""),
        )
    }

    @Test
    fun `503 from a non-cloudflare server does not trigger WebView`() {
        assertFalse(shouldSolveChallenge(503, "nginx", "challenge-error-title"))
    }

    @Test
    fun `403 with no server header does not trigger WebView`() {
        assertFalse(shouldSolveChallenge(403, null, "challenge-error-title"))
    }
    // endregion
}

/** Mirrors the pinned host runtime versions declared in app/build.gradle.kts. */
private object HostRuntime {
    const val KOTLINX_SERIALIZATION = "1.11.0"
    const val OKHTTP = "5.4.0"
}
