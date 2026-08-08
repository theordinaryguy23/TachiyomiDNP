package eu.kanade.tachiyomi.source.model

/**
 * Combined result of a remote manga refresh.
 *
 * Introduced by extensions-lib 1.6, which replaced the separate
 * `fetchMangaDetails` / `fetchChapterList` calls with a single
 * [eu.kanade.tachiyomi.source.Source.getMangaUpdate] call so a source can fetch
 * details and chapters concurrently (or in one request).
 *
 * The class shape must match the extension API exactly — extensions compile
 * against it with `compileOnly` and construct it themselves at runtime.
 */
@Suppress("UNUSED")
class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
