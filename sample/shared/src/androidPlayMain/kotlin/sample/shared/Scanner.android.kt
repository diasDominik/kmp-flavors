package sample.shared

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal actual fun platformScanner(): Scanner? = MlKitScanner

/** Google ML Kit, closed source: only in play builds. Bundled models, works offline. */
private object MlKitScanner : Scanner {
    override val engine = "Google ML Kit (closed source), src/androidPlayMain"
    override val readsText = true

    override suspend fun scan(image: ByteArray): ScanResult {
        val input = InputImage.fromBitmap(BitmapFactory.decodeByteArray(image, 0, image.size), 0)
        val codes = BarcodeScanning.getClient().process(input).await().mapNotNull { it.rawValue }
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(input).await().text
        return ScanResult(codes, text.trim().ifEmpty { null })
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
