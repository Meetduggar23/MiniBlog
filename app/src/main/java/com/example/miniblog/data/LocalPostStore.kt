package com.example.miniblog.data

import android.content.Context
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for user-created posts.
 *
 * Posts are stored as JSON in SharedPreferences. Deletes are soft: a deleted
 * post keeps its data with a [Post.deletedAt] timestamp so it can be restored
 * from the Trash. The feed is always returned pinned-first, then newest-first,
 * so the order survives process death and restarts.
 */
class LocalPostStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All active (non-deleted) posts: pinned first, then newest first. */
    fun getPosts(): List<Post> {
        return readAll().filter { it.deletedAt == null }.sortFeed()
    }

    /** Soft-deleted posts, most recently deleted first. */
    fun getTrash(): List<Post> {
        return readAll().filter { it.deletedAt != null }
            .sortedByDescending { it.deletedAt }
    }

    /** Inserts or updates a post (upsert by id). */
    fun addPost(post: Post) {
        val posts = readAll().filter { it.id != post.id } + post
        save(posts)
    }

    fun removePost(postId: Int) {
        save(readAll().filterNot { it.id == postId })
    }

    /** Next free id for a locally-created post. */
    fun nextLocalId(): Int =
        maxOf(Post.LOCAL_ID_BASE + 1, (readAll().maxOfOrNull { it.id } ?: 0) + 1)

    private fun readAll(): List<Post> {
        val json = prefs.getString(KEY_POSTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val posts = mutableListOf<Post>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                posts.add(
                    Post(
                        userId = obj.optInt("userId"),
                        id = obj.optInt("id"),
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        createdAt = if (obj.has("createdAt")) obj.optLong("createdAt")
                        else System.currentTimeMillis(),
                        remoteId = if (obj.has("remoteId") && !obj.isNull("remoteId"))
                            obj.optInt("remoteId") else null,
                        tags = parseTags(obj.optJSONArray("tags")),
                        isPinned = obj.optBoolean("isPinned", false),
                        isBookmarked = obj.optBoolean("isBookmarked", false),
                        deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt"))
                            obj.optLong("deletedAt") else null
                    )
                )
            }
            posts
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTags(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val tags = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val tag = array.optString(i).trim()
            if (tag.isNotEmpty()) tags.add(tag)
        }
        return tags
    }

    private fun save(posts: List<Post>) {
        val array = JSONArray()
        for (post in posts) {
            array.put(
                JSONObject().apply {
                    put("userId", post.userId)
                    put("id", post.id)
                    put("title", post.title)
                    put("body", post.body)
                    put("createdAt", post.createdAt)
                    if (post.remoteId != null) put("remoteId", post.remoteId)
                    put("tags", JSONArray(post.tags))
                    put("isPinned", post.isPinned)
                    put("isBookmarked", post.isBookmarked)
                    if (post.deletedAt != null) put("deletedAt", post.deletedAt)
                }
            )
        }
        prefs.edit().putString(KEY_POSTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "miniblog_posts"
        private const val KEY_POSTS = "posts"

        /** Pinned posts first; within each group, newest first. */
        fun List<Post>.sortFeed(): List<Post> =
            sortedWith(
                compareByDescending<Post> { it.isPinned }
                    .thenByDescending { it.createdAt }
            )
    }
}
