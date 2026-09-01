package com.example.miniblog.data

import android.content.Context
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for user-created posts.
 *
 * Posts are stored as JSON in SharedPreferences and are always returned
 * newest-first (sorted by creation timestamp), so the feed order survives
 * process death and restarts.
 */
class LocalPostStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All saved posts, newest first. */
    fun getPosts(): List<Post> {
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
                            obj.optInt("remoteId") else null
                    )
                )
            }
            posts.sortNewestFirst()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Inserts or updates a post and keeps the stored order newest-first. */
    fun addPost(post: Post) {
        val posts = (getPosts().filter { it.id != post.id } + post).sortNewestFirst()
        save(posts)
    }

    fun removePost(postId: Int) {
        save(getPosts().filterNot { it.id == postId })
    }

    /** Next free id for a locally-created post. */
    fun nextLocalId(): Int =
        maxOf(Post.LOCAL_ID_BASE + 1, (getPosts().maxOfOrNull { it.id } ?: 0) + 1)

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
                }
            )
        }
        prefs.edit().putString(KEY_POSTS, array.toString()).apply()
    }

    private fun List<Post>.sortNewestFirst(): List<Post> =
        sortedByDescending { it.createdAt }

    companion object {
        private const val PREFS_NAME = "miniblog_posts"
        private const val KEY_POSTS = "posts"
    }
}
