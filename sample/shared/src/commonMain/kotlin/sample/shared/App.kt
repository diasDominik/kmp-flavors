package sample.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import sample.shared.res.Res
import sample.shared.res.scan_sample

/** The whole app, in common code. Only [Store] (per store flavor) and [platformScanner] (per platform) differ. */
@Composable
fun App() {
    MaterialTheme(colorScheme = lightColorScheme(primary = Store.COLOR)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(Store.NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Store.COLOR)
                Text("Variant ${BuildConfig.VARIANT}", style = MaterialTheme.typography.bodySmall)
                Text(Store.POLICY)
                val scanner = remember { platformScanner() }
                if (scanner != null) ScanSample(scanner) else Text("No scanner on this platform.")
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ScanSample(scanner: Scanner) {
    var request by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        if (request == 0) return@LaunchedEffect
        result =
            runCatching { scanner.scan(Res.readBytes("drawable/scan_sample.png")) }.fold(
                onSuccess = { scan ->
                    val text = scan.text ?: if (scanner.readsText) "none found" else "not supported by this engine"
                    "Codes: ${scan.codes.joinToString().ifEmpty { "none" }}\nText: $text"
                },
                onFailure = { "Failed: ${it.message ?: it}" },
            )
    }

    Text("Scanner: ${scanner.engine}", style = MaterialTheme.typography.titleSmall)
    Image(painterResource(Res.drawable.scan_sample), null, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Fit)
    Button(onClick = { request++ }) { Text("Scan this image") }
    result?.let { Text(it) }
}
