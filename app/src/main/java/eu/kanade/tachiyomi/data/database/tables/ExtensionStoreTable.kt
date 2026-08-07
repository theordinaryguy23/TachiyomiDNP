package eu.kanade.tachiyomi.data.database.tables

object ExtensionStoreTable {
    const val TABLE = "extension_store"

    const val COL_INDEX_URL = "index_url"
    const val COL_NAME = "name"
    const val COL_BADGE_LABEL = "badge_label"
    const val COL_SIGNING_KEY = "signing_key"
    const val COL_CONTACT_WEBSITE = "contact_website"
    const val COL_CONTACT_DISCORD = "contact_discord"
    const val COL_IS_LEGACY = "is_legacy"
    const val COL_EXTENSION_LIST_URL = "extension_list_url"

    val ALL_COLUMNS = arrayOf(
        COL_INDEX_URL,
        COL_NAME,
        COL_BADGE_LABEL,
        COL_SIGNING_KEY,
        COL_CONTACT_WEBSITE,
        COL_CONTACT_DISCORD,
        COL_IS_LEGACY,
        COL_EXTENSION_LIST_URL,
    )

    val createTableQuery = """
        CREATE TABLE IF NOT EXISTS ${TABLE} (
            ${COL_INDEX_URL} TEXT NOT NULL UNIQUE,
            ${COL_NAME} TEXT NOT NULL,
            ${COL_BADGE_LABEL} TEXT NOT NULL,
            ${COL_SIGNING_KEY} TEXT,
            ${COL_CONTACT_WEBSITE} TEXT,
            ${COL_CONTACT_DISCORD} TEXT,
            ${COL_IS_LEGACY} INTEGER NOT NULL DEFAULT 0,
            ${COL_EXTENSION_LIST_URL} TEXT
        );
    """.trimIndent()
}