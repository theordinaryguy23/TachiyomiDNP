package eu.kanade.tachiyomi.source.model

class SChapterImpl : SChapter {
    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    private var _memo: kotlinx.serialization.json.JsonObject? = null
    override var memo: kotlinx.serialization.json.JsonObject?
        get() = _memo ?: kotlinx.serialization.json.buildJsonObject { }
        set(value) {
            _memo = value
        }
}
