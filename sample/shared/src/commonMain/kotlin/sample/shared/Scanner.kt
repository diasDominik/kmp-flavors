package sample.shared

/** What a scan of an image found. */
data class ScanResult(
    val codes: List<String>,
    /** Recognised text, or null when the engine can't read text. */
    val text: String?,
)

/** Reads QR codes / barcodes and, if [readsText], text from an image. */
interface Scanner {
    val engine: String
    val readsText: Boolean

    suspend fun scan(image: ByteArray): ScanResult
}

/**
 * The scanner of this platform and store, or null when there is none:
 * - Android play: Google ML Kit (src/androidPlayMain), Android oss: zxing-cpp (src/androidOssMain)
 * - iOS: zxing-cpp (src/iosMain), desktop and web: none
 */
internal expect fun platformScanner(): Scanner?
