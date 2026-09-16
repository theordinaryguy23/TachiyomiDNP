package eu.kanade.tachiyomi.data.preference

import android.content.SharedPreferences
import eu.kanade.tachiyomi.util.SanitizedSharedPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PreferenceSanitizationTest {

    private lateinit var basePrefs: FakeSharedPreferences
    private lateinit var sanitizedPrefs: SanitizedSharedPreferences

    @Before
    fun setUp() {
        basePrefs = FakeSharedPreferences()
        sanitizedPrefs = SanitizedSharedPreferences(basePrefs)
    }

    @Test
    fun `SanitizedSharedPreferences removes literal null string and returns default`() {
        basePrefs.putString("filter_data", "null")
        assertEquals("null", basePrefs.getString("filter_data", null))

        val value = sanitizedPrefs.getString("filter_data", null)
        assertNull(value)

        // Verify the bad entry was removed from the underlying SharedPreferences
        assertNull(basePrefs.getString("filter_data", null))
    }

    @Test
    fun `SanitizedSharedPreferences keeps valid non-null strings`() {
        val validJson = "[{\"name\":\"Action\"}]"
        basePrefs.putString("filter_data", validJson)

        val value = sanitizedPrefs.getString("filter_data", null)
        assertEquals(validJson, value)
    }

    @Test
    fun `Json deserialization handles null input smoothly with leniency`() {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
            coerceInputValues = true
        }

        val raw = sanitizedPrefs.getString("corrupted_key", null)
        val result: List<String> = if (raw == null || raw == "null") {
            emptyList()
        } else {
            json.decodeFromString(raw)
        }

        assertEquals(emptyList<String>(), result)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        fun putString(key: String, value: String?) {
            map[key] = value
        }

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String, defValue: String?): String? {
            return if (map.containsKey(key)) map[key] as String? else defValue
        }

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return if (map.containsKey(key)) map[key] as MutableSet<String>? else defValues
        }

        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                map[key] = value
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                map[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                map[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                map[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                map[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                map[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                map.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                map.clear()
                return this
            }

            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}
