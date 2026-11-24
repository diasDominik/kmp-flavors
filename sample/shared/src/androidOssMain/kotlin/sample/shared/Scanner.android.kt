package sample.shared

import android.graphics.BitmapFactory
import zxingcpp.BarcodeReader

internal actual fun platformScanner(): Scanner? = ZxingCppScanner

/** zxing-cpp (Apache-2.0): the FOSS build's scanner. Barcodes only; there is no OCR. */
private object ZxingCppScanner : Scanner {
    override val engine = "zxing-cpp (Apache-2.0), src/androidOssMain"
    override val readsText = false

    override suspend fun scan(image: ByteArray): ScanResult {
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
        return ScanResult(BarcodeReader().read(bitmap).mapNotNull { it.text }, text = null)
    }
}
