package com.example.miniblog.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** App-level preferences: theme mode, feed sort order, recent searches. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ----- Theme -------------------------------------------------------------

    fun getThemeMode(): Int =
        prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME, mode).apply()
    }

    // ----- Sort order ----------------------------------------------------------

    fun getSortOrder(): String = prefs.getString(KEY_SORT, SORT_NEWEST) ?: SORT_NEWEST

    fun setSortOrder(order: String) {
        prefs.edit().putString(KEY_SORT, order).apply()
    }

    // ----- Recent searches -----------------------------------------------------

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return raw.split("\u0001").filter { it.isNotBlank() }
    }

    /** Adds a query to the top of the recent list (max [MAX_RECENT] entries). */
    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = listOf(trimmed) + getRecentSearches().filter {
            !it.equals(trimmed, ignoreCase = true)
        }
        prefs.edit()
            .putString(KEY_RECENT_SEARCHES, updated.take(MAX_RECENT).joinToString("\u0001"))
            .apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    companion object {
        private const val PREFS_NAME = "miniblog_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SORT = "sort_order"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT = 5

        const val SORT_NEWEST = "newest"
        const val SORT_OLDEST = "oldest"
        const val SORT_MOST_LIKED = "most_liked"
        const val SORT_MOST_VIEWED = "most_viewed"
    }
}
