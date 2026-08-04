package okhttp3.zstd

object Zstd {
    @JvmStatic
    fun isAvailable(): Boolean = false

    @JvmStatic
    fun compress(source: ByteArray): ByteArray = source

    @JvmStatic
    fun decompress(source: ByteArray): ByteArray = source
}
