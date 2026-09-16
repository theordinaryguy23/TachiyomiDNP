package eu.kanade.tachiyomi.util

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * A [SharedPreferences] wrapper that sanitizes invalid/corrupted `"null"` string values
 * that may have been serialized by extensions or libraries into source preferences.
 */
class SanitizedSharedPreferences(
    private val delegate: SharedPreferences,
) : SharedPreferences by delegate {

    override fun getString(key: String, defValue: String?): String? {
        val value = delegate.getString(key, defValue)
        if ((value == "null") && (defValue != "null")) {
            delegate.edit { remove(key) }
            return defValue
        }
        return value
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return try {
            delegate.getStringSet(key, defValues)
        } catch (_: ClassCastException) {
            delegate.edit { remove(key) }
            defValues
        }
    }

    override fun getAll(): Map<String, *> {
        val map = delegate.all
        val result = mutableMapOf<String, Any?>()
        val keysToRemove = mutableListOf<String>()
        for ((key, value) in map) {
            if (value == "null") {
                keysToRemove.add(key)
            } else {
                result[key] = value
            }
        }
        if (keysToRemove.isNotEmpty()) {
            delegate.edit {
                keysToRemove.forEach { remove(it) }
            }
        }
        return result
    }
}
