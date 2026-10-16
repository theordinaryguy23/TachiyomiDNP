package eu.kanade.tachiyomi.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import eu.kanade.tachiyomi.desktop.reader.DesktopChapterDownloadStore
import eu.kanade.tachiyomi.desktop.reader.DesktopReaderProgressStore
import eu.kanade.tachiyomi.desktop.source.MangaChapter
import eu.kanade.tachiyomi.desktop.source.MangaDexReaderSource
import eu.kanade.tachiyomi.desktop.source.MangaDexSource
import eu.kanade.tachiyomi.desktop.source.MangaDetails
import eu.kanade.tachiyomi.shared.model.Manga
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
    val mangaDexSource = remember { MangaDexSource() }
    val readerSource = remember { MangaDexReaderSource() }
    val progressStore = remember { DesktopReaderProgressStore() }
    val downloadStore = remember { DesktopChapterDownloadStore() }
    var requestedChapter by remember { mutableStateOf<MangaChapter?>(null) }
    val searchService = remember {
        GlobalSearchService(sources = { listOf(mangaDexSource) })
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
                "Search" -> DesktopSearchScreen(
                    searchService = searchService,
                    mangaDexSource = mangaDexSource,
                    readerSource = readerSource,
                    progressStore = progressStore,
                    downloadStore = downloadStore,
                    requestedChapter = requestedChapter,
                    onRequestedChapterConsumed = { requestedChapter = null },
                )
                "Downloads" -> DownloadsScreen(
                    downloadStore = downloadStore,
                    onRead = { downloaded ->
                        requestedChapter = downloaded.asMangaChapter()
                        selectedSection = "Search"
                    },
                )
                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(selectedSection, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "This section is planned for a later desktop phase. Search, manga details, and chapter reading are available from Search.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchScreen(
    searchService: GlobalSearchService,
    mangaDexSource: MangaDexSource,
    readerSource: MangaDexReaderSource,
    progressStore: DesktopReaderProgressStore,
    downloadStore: DesktopChapterDownloadStore,
    requestedChapter: MangaChapter?,
    onRequestedChapterConsumed: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedManga by remember { mutableStateOf<Manga?>(null) }
    var selectedChapter by remember { mutableStateOf<MangaChapter?>(null) }
    var downloadingChapterId by remember { mutableStateOf<String?>(null) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedChapter) {
        if (requestedChapter != null) {
            selectedChapter = requestedChapter
            onRequestedChapterConsumed()
        }
    }

    var details by remember { mutableStateOf<MangaDetails?>(null) }
    var chapters by remember { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }

    val chapterToRead = selectedChapter
    if (chapterToRead != null) {
        ReaderScreen(
            chapter = chapterToRead,
            readerSource = readerSource,
            progressStore = progressStore,
            downloadStore = downloadStore,
            onBack = { selectedChapter = null },
        )
        return
    }

    selectedManga?.let { manga ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = {
                selectedManga = null
                details = null
                chapters = emptyList()
                detailError = null
            }) {
                Text("← Back to search")
            }
            Text(manga.title, style = MaterialTheme.typography.headlineMedium)
            Text(manga.url, style = MaterialTheme.typography.bodySmall)
            if (detailLoading) CircularProgressIndicator()
            detailError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            details?.let { item ->
                item.status?.let { Text("Status: $it") }
                if (item.authors.isNotEmpty()) Text("Author: ${item.authors.joinToString()}")
                if (item.artists.isNotEmpty()) Text("Artist: ${item.artists.joinToString()}")
                item.description?.let { Text(it) }
                Text("Chapters", style = MaterialTheme.typography.titleLarge)
                if (chapters.isEmpty() && !detailLoading) {
                    Text("No English chapters were returned for this title.")
                }
                downloadStatus?.let { Text(it, color = if (it.startsWith("Download failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                chapters.forEach { chapter ->
                    val label = buildString {
                        append("Chapter ")
                        append(chapter.chapter ?: "—")
                        if (chapter.volume != null) append("  •  Volume ${chapter.volume}")
                        if (chapter.title.isNotBlank()) append("  •  ${chapter.title}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        Button(onClick = { selectedChapter = chapter }) {
                            Text("Read")
                        }
                        Button(
                            enabled = downloadingChapterId == null && !downloadStore.isDownloaded(chapter.id),
                            onClick = {
                                scope.launch {
                                    downloadingChapterId = chapter.id
                                    downloadStatus = "Preparing chapter download…"
                                    try {
                                        val pageUrls = readerSource.pages(chapter.id, dataSaver = true)
                                        if (pageUrls.isEmpty()) error("MangaDex returned no pages.")
                                        val pageBytes = ArrayList<ByteArray>(pageUrls.size)
                                        pageUrls.forEachIndexed { index, url ->
                                            downloadStatus = "Downloading page ${index + 1} of ${pageUrls.size}…"
                                            pageBytes += readerSource.downloadPageBytes(url)
                                        }
                                        downloadStore.saveChapter(chapter, pageBytes)
                                        downloadStatus = "Download complete: ${pageUrls.size} pages saved for offline reading."
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (failure: Exception) {
                                        downloadStatus = "Download failed: ${failure.message ?: "unknown error"}"
                                    } finally {
                                        downloadingChapterId = null
                                    }
                                }
                            },
                        ) {
                            Text(if (downloadingChapterId == chapter.id) "Downloading…" else if (downloadStore.isDownloaded(chapter.id)) "Downloaded" else "Download")
                        }
                    }
                }
            }
        }
    } ?: Column(
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

        if (loading) CircularProgressIndicator()
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
                    Button(onClick = {
                        selectedManga = manga
                        details = null
                        chapters = emptyList()
                        detailLoading = true
                        detailError = null
                        val mangaId = manga.url.substringAfterLast('/')
                        scope.launch {
                            try {
                                details = mangaDexSource.details(mangaId)
                                chapters = mangaDexSource.chapters(mangaId)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                detailError = error.message ?: "Could not load manga details."
                            } finally {
                                detailLoading = false
                            }
                        }
                    }) {
                        Text("Details & chapters")
                    }
                }
            }
        }
    }
}


@Composable
private fun DownloadsScreen(
    downloadStore: DesktopChapterDownloadStore,
    onRead: (eu.kanade.tachiyomi.desktop.reader.DownloadedChapter) -> Unit,
) {
    var refreshKey by remember { mutableStateOf(0) }
    val downloads = remember(refreshKey) { downloadStore.listDownloads() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        Text("Chapters saved on this PC. Downloaded pages can be read without an internet connection.")
        if (downloads.isEmpty()) {
            Text("No downloaded chapters yet. Open Search → Details & chapters → Download.")
        }
        downloads.forEach { downloaded ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append("Chapter ")
                            append(downloaded.chapter ?: "—")
                            if (downloaded.title.isNotBlank()) append(" · ${downloaded.title}")
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("${downloaded.pageCount} pages · Available offline", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onRead(downloaded) }) { Text("Read") }
                Button(onClick = {
                    downloadStore.delete(downloaded.id)
                    refreshKey++
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    chapter: MangaChapter,
    readerSource: MangaDexReaderSource,
    progressStore: DesktopReaderProgressStore,
    downloadStore: DesktopChapterDownloadStore,
    onBack: () -> Unit,
) {
    var pages by remember(chapter.id) { mutableStateOf<List<String>>(emptyList()) }
    var offline by remember(chapter.id) { mutableStateOf(false) }
    var pageIndex by remember(chapter.id) { mutableStateOf(progressStore.loadPageIndex(chapter.id)) }
    var loadingPages by remember(chapter.id) { mutableStateOf(true) }
    var loadingImage by remember(chapter.id) { mutableStateOf(false) }
    var error by remember(chapter.id) { mutableStateOf<String?>(null) }
    var imageError by remember(chapter.id) { mutableStateOf<String?>(null) }
    var image by remember(chapter.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(chapter.id) {
        loadingPages = true
        error = null
        try {
            val downloaded = downloadStore.find(chapter.id)
            if (downloaded != null) {
                offline = true
                pages = List(downloaded.pageCount) { "" }
            } else {
                offline = false
                pages = readerSource.pages(chapter.id, dataSaver = true)
            }
            if (pages.isEmpty()) error = "MangaDex returned no pages for this chapter."
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load chapter pages."
        } finally {
            loadingPages = false
        }
    }

    LaunchedEffect(chapter.id, pages.size) {
        if (pages.isNotEmpty()) {
            pageIndex = pageIndex.coerceIn(0, pages.lastIndex)
        }
    }

    LaunchedEffect(chapter.id, pages.size, pageIndex) {
        if (pages.isNotEmpty()) {
            progressStore.savePageIndex(chapter.id, pageIndex)
        }
    }

    LaunchedEffect(pages, pageIndex) {
        val url = pages.getOrNull(pageIndex)
        if (url == null) {
            image = null
            return@LaunchedEffect
        }
        loadingImage = true
        imageError = null
        image = null
        try {
            image = if (offline) {
                readerSource.decodeImage(downloadStore.readPageBytes(chapter.id, pageIndex))
            } else {
                readerSource.loadImage(url)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            imageError = failure.message ?: "Could not load this page."
        } finally {
            loadingImage = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("← Chapters") }
            Text(
                text = buildString {
                    append("Chapter ")
                    append(chapter.chapter ?: "—")
                    if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            Button(enabled = pageIndex > 0 && !loadingPages, onClick = { pageIndex-- }) {
                Text("Previous")
            }
            Button(enabled = pageIndex < pages.lastIndex && !loadingPages, onClick = { pageIndex++ }) {
                Text("Next")
            }
        }

        when {
            loadingPages -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            else -> {
                Text("Page ${pageIndex + 1} of ${pages.size} · ${if (offline) "Offline download" else "Data Saver quality"} · Progress saved automatically")
                imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loadingImage) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    image?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}
