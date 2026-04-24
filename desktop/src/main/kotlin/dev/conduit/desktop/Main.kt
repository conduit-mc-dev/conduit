package dev.conduit.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Conduit MC",
    ) {
        MaterialTheme {
            Surface {
                Text("Hello from Conduit MC Desktop")
            }
        }
    }
}
