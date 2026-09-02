package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {

    // --- JSON PARSING: JSONArray + JSONObject (demonstrates assignment req.) ---

    /**
     * Parses a JSON array of posts fetched from the remote API.
     * Demonstrates: JSONArray iteration + JSONObject field extraction.
     */
    fun parsePosts(jsonString: String): List<Post> {
        val posts = mutableListOf<Post>()
        val array = JSONArray(jsonString) // JSONArray: the full posts response
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i) // JSONObject: each individual post
            posts.add(
                Post(
                    userId = obj.optInt("userId"),
                    id = obj.optInt("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return posts
    }

    fun parseComments(jsonString: String): List<Comment> {
        val comments = mutableListOf<Comment>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            comments.add(
                Comment(
                    postId = obj.optInt("postId"),
                    id = obj.optInt("id"),
                    name = obj.optString("name"),
                    email = obj.optString("email"),
                    body = obj.optString("body")
                )
            )
        }
        return comments
    }

    fun buildPostJson(title: String, body: String, userId: Int): String {
        val obj = JSONObject()
        obj.put("title", title)
        obj.put("body", body)
        obj.put("userId", userId)
        return obj.toString()
    }

    /**
     * Extracts the id the backend assigned to a newly created post.
     * Returns null when the response carries no usable id.
     */
    fun parseCreatedPostId(jsonString: String): Int? = try {
        val obj = JSONObject(jsonString)
        if (obj.has("id")) obj.getInt("id") else null
    } catch (e: Exception) {
        null
    }

    fun postToJson(post: Post): String =
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
        }.toString()

    fun parsePost(jsonString: String): Post {
        val obj = JSONObject(jsonString)
        return Post(
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
    }

    private fun parseTags(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val tags = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val tag = array.optString(i).trim()
            if (tag.isNotEmpty()) tags.add(tag)
        }
        return tags
    }
}
