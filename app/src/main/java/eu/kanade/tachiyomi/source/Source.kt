package eu.kanade.tachiyomi.source

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.awaitSingle
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface Source {
    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the updated manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga = fetchMangaDetails(manga).awaitSingle()

    /**
     * Fetches updated information for a manga: details, chapters, or both.
     *
     * This is the single entry point the host uses to refresh a manga.
     *
     * - extensions-lib **1.6** sources override this directly.
     * - extensions-lib **1.4** sources do not, and fall through to the default
     *   implementation in [CatalogueSource], which translates the call back into
     *   the legacy `fetchMangaDetails` / `fetchChapterList` pair.
     *
     * Callers must never invoke `fetchChapterList` directly: in lib 1.6 that
     * method is a stub that throws, which surfaced as "Unknown error" and an
     * empty chapter list on sources such as Weeb Central.
     *
     * @since extensions-lib 1.6
     * @param manga the manga to fetch updates for.
     * @param chapters existing chapters of the manga, returned as-is when
     *   [fetchChapters] is false.
     * @param fetchDetails whether to fetch updated manga details.
     * @param fetchChapters whether to fetch the available chapters.
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        if (fetchDetails) getMangaDetails(manga) else manga,
        if (fetchChapters) getChapterList(manga) else chapters,
    )

    /**
     * Get all the available chapters for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the chapters for the manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapterList(manga).awaitSingle()

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> = fetchPageList(chapter).awaitSingle()

    fun includeLangInName(
        enabledLanguages: Set<String>,
        extensionManager: ExtensionManager? = null,
    ): Boolean {
        val httpSource = this as? HttpSource ?: return true
        val extManager = extensionManager ?: Injekt.get()
        val allExt = httpSource.getExtension(extManager)?.lang == "all"
        val onlyAll = httpSource.extOnlyHasAllLanguage(extManager)
        val isMultiLingual = enabledLanguages.filterNot { it == "all" }.size > 1
        return (isMultiLingual && allExt) || (lang == "all" && !onlyAll)
    }

    fun nameBasedOnEnabledLanguages(
        enabledLanguages: Set<String>,
        extensionManager: ExtensionManager? = null,
    ): String = if (includeLangInName(enabledLanguages, extensionManager)) toString() else name


    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getMangaDetails"),
    )
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPageList"),
    )
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw IllegalStateException("Not used")
}

fun Source.icon(): Drawable? = Injekt.get<ExtensionManager>().getAppIconForSource(this)
fun Source.pkgName() = Injekt.get<ExtensionManager>().getPackageName(id)

fun Source.preferenceKey(): String = "source_$id"

/**
 * Fetches a manga's chapter list through the extensions-lib 1.6 [Source.getMangaUpdate] API.
 *
 * Host code must use this rather than calling [Source.getChapterList] directly.
 * A lib 1.6 extension (e.g. Weeb Central) does not implement `fetchChapterList` at
 * all — in lib 1.6 it is a stub that throws — so the legacy path yields "Unknown
 * error" and zero chapters. Routing through `getMangaUpdate` lets 1.6 extensions
 * serve the request directly while 1.4 extensions fall through to the compatibility
 * bridge in [CatalogueSource].
 */
suspend fun Source.awaitChapterList(manga: SManga): List<SChapter> = getMangaUpdate(
    manga = manga,
    chapters = emptyList(),
    fetchDetails = false,
    fetchChapters = true,
).chapters
