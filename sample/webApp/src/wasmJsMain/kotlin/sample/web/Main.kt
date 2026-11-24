package sample.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import sample.shared.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("app") { App() }
}
