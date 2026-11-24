package sample.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import zxingcpp.BarcodeReader
import zxingcpp.ImageFormat
import zxingcpp.ImageView

internal actual fun platformScanner(): Scanner? = ZxingCppScanner

/** zxing-cpp is open source, so iOS uses it in play and oss builds alike. */
private object ZxingCppScanner : Scanner {
    override val engine = "zxing-cpp (Apache-2.0, Kotlin/Native), src/iosMain"
    override val readsText = false

    override suspend fun scan(image: ByteArray): ScanResult {
        val (pixels, width, height) = grayscale(image)
        val codes = BarcodeReader.read(ImageView(pixels, width, height, ImageFormat.Lum)).mapNotNull { it.text }
        return ScanResult(codes, text = null)
    }
}

/** Decodes an image into 8-bit luminance pixels. */
@OptIn(ExperimentalForeignApi::class)
private fun grayscale(encoded: ByteArray): Triple<ByteArray, Int, Int> {
    val data = encoded.usePinned { NSData.dataWithBytes(it.addressOf(0), encoded.size.toULong()) }
    val image = UIImage(data = data).CGImage ?: error("Not an image")
    val width = CGImageGetWidth(image).toInt()
    val height = CGImageGetHeight(image).toInt()
    val pixels = ByteArray(width * height)
    pixels.usePinned { pinned ->
        val space = CGColorSpaceCreateDeviceGray()
        val context =
            CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8u,
                width.toULong(),
                space,
                CGImageAlphaInfo.kCGImageAlphaNone.value,
            )
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
    }
    return Triple(pixels, width, height)
}
