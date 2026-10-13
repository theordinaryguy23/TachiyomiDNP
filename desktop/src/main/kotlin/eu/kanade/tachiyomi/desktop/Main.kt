package eu.kanade.tachiyomi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import eu.kanade.tachiyomi.desktop.source.MangaDexSource
import eu.kanade.tachiyomi.shared.model.SearchResult
import eu.kanade.tachiyomi.shared.search.GlobalSearchService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    var selectedSection by remember { mutableStateOf("Search") }
    val searchService = remember {
        GlobalSearchService(sources = { listOf(MangaDexSource()) })
    }

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

            listOf("Library", "Browse", "Search", "Updates", "History", "Downloads", "Settings")
                .forEach { item ->
                    Button(
                        onClick = { selectedSection = item },
                        modifier = Modifier.width(188.dp),
                    ) {
                        Text(item)
                    }
                }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (selectedSection) {
                "Search" -> DesktopSearchScreen(searchService)
                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(selectedSection, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "This section is planned for a later desktop phase. Search is available now.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchScreen(searchService: GlobalSearchService) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Global Search", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Search MangaDex from Windows. Results use the shared search service and domain model.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Manga title") },
                singleLine = true,
            )
            Button(
                enabled = query.isNotBlank() && !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        message = null
                        results = emptyList()
                        try {
                            results = searchService.search(query)
                            if (results.all { it.manga.isEmpty() && it.error == null }) {
                                message = "No manga found. Try another title."
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            message = error.message ?: "Search failed."
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Searching…" else "Search")
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        results.forEach { result ->
            Text(result.sourceName, style = MaterialTheme.typography.titleLarge)
            result.error?.let { error ->
                Text("Source error: $error", color = MaterialTheme.colorScheme.error)
            }
            result.manga.forEach { manga ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(manga.title, style = MaterialTheme.typography.titleMedium)
                    Text(manga.url, style = MaterialTheme.typography.bodySmall)
                    manga.author?.let { Text("Author: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
