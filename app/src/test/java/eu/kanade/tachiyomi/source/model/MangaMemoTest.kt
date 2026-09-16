package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MangaMemoTest {

    @Test
    fun `manga memo is non-null empty JsonObject by default`() {
        val manga = MangaImpl()
        assertNotNull(manga.memo)
        assertNull(manga.memo?.get("non_existent_key"))
    }

    @Test
    fun `chapter memo is non-null empty JsonObject by default`() {
        val chapter = ChapterImpl()
        assertNotNull(chapter.memo)
        assertNull(chapter.memo?.get("non_existent_key"))
    }

    @Test
    fun `custom memo can be set and retrieved on manga`() {
        val manga = MangaImpl()
        val customMemo = buildJsonObject { put("source_id", "123") }
        manga.memo = customMemo

        assertEquals("123", manga.memo?.get("source_id")?.jsonPrimitive?.content)
    }

    @Test
    fun `manga copy preserves memo and non-null default`() {
        val manga = MangaImpl()
        manga.url = "/manga/123"
        manga.title = "Test"

        val copied = manga.copy()
        assertNotNull(copied.memo)
        assertNull(copied.memo?.get("key"))
    }
}
