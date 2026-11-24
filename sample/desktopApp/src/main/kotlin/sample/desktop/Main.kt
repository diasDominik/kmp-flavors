package sample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import sample.shared.App

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "kmp-flavors sample") { App() }
    }
