package sample.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point for iosApp. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
