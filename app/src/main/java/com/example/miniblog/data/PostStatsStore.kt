package com.example.miniblog.data

import android.content.Context

/**
 * Local like/view tracking for posts.
 *
 * The backend (JSONPlaceholder) has no like or view APIs, so appreciation and
 * view counts are tracked cleanly on-device instead of faking server calls.
 * View counting is throttled: a post is only counted once per app process.
 */
class PostStatsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Posts liked during this process — prevents double counting per session. */
    private val sessionCountedViews = mutableSetOf<Int>()

    fun isLiked(postId: Int): Boolean = likedIds().contains(postId.toString())

    /** Toggles the like state and returns true when the post is now liked. */
    fun toggleLike(postId: Int): Boolean {
        val key = postId.toString()
        val liked = likedIds()
        val nowLiked = if (liked.contains(key)) {
            liked.remove(key)
            false
        } else {
            liked.add(key)
            true
        }
        val counts = likeCounts().toMutableMap()
        if (nowLiked) {
            counts[key] = (counts[key] ?: 0) + 1
        } else {
            counts[key] = (counts[key] ?: 1) - 1
            if (counts[key]!! <= 0) counts.remove(key)
        }
        prefs.edit()
            .putStringSet(KEY_LIKED, liked)
            .putString(KEY_LIKE_COUNTS, mapToJson(counts))
            .apply()
        return nowLiked
    }

    fun likeCount(postId: Int): Int =
        likeCounts()[postId.toString()] ?: 0

    fun viewCount(postId: Int): Int =
        viewCounts()[postId.toString()] ?: 0

    /** Increments the view counter at most once per process per post. */
    fun recordView(postId: Int) {
        if (sessionCountedViews.contains(postId)) return
        sessionCountedViews.add(postId)
        val counts = viewCounts().toMutableMap()
        val key = postId.toString()
        counts[key] = (counts[key] ?: 0) + 1
        prefs.edit().putString(KEY_VIEW_COUNTS, mapToJson(counts)).apply()
    }

    fun totalLikes(): Int = likeCounts().values.sum()

    fun totalViews(): Int = viewCounts().values.sum()

    fun likedPostCount(): Int = likedIds().size

    private fun likedIds(): MutableSet<String> =
        prefs.getStringSet(KEY_LIKED, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun likeCounts(): Map<String, Int> =
        jsonToMap(prefs.getString(KEY_LIKE_COUNTS, null))

    private fun viewCounts(): Map<String, Int> =
        jsonToMap(prefs.getString(KEY_VIEW_COUNTS, null))

    private fun mapToJson(map: Map<String, Int>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { index, entry ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(entry.key).append("\":").append(entry.value)
        }
        return sb.append("}").toString()
    }

    private fun jsonToMap(json: String?): Map<String, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optInt(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val PREFS_NAME = "miniblog_stats"
        private const val KEY_LIKED = "liked_ids"
        private const val KEY_LIKE_COUNTS = "like_counts"
        private const val KEY_VIEW_COUNTS = "view_counts"
    }
}
