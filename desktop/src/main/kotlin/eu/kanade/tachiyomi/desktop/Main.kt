package eu.kanade.tachiyomi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TachiyomiDNP — Windows Desktop",
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopShell()
            }
        }
    }
}

@Composable
private fun DesktopShell() {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("TachiyomiDNP", style = MaterialTheme.typography.titleLarge)
            Text("Windows Edition", style = MaterialTheme.typography.bodyMedium)

            listOf(
                "Library",
                "Browse",
                "Search",
                "Updates",
                "History",
                "Downloads",
                "Settings",
            ).forEach { item ->
                Button(onClick = { }, modifier = Modifier.width(188.dp)) {
                    Text(item)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "TachiyomiDNP Windows Desktop Edition",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("Foundation Preview", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Desktop shell is ready. Shared manga, source, search, library, reader, and download logic will be integrated in subsequent stages.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
