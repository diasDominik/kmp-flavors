package sample.shared

import androidx.compose.ui.graphics.Color

/** Google Play builds. src/commonOssMain has the FOSS counterpart with the same members. */
internal object Store {
    const val NAME = "Google Play build"
    const val POLICY = "May use closed-source Google SDKs: Google ML Kit scans codes and text on Android."
    val COLOR = Color(0xFF1565C0)
}
