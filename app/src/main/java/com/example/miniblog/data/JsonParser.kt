package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {

    // --- JSON PARSING: JSONArray + JSONObject (demonstrates assignment req.) ---

    /**
     * Parses the JSON response of posts fetched from the remote API.
     * DummyJSON wraps the list in a top-level "posts" array:
     * { "posts": [ { ... }, ... ], "total": ..., "skip": 0, "limit": <n> }
     * Demonstrates: JSONObject field access + JSONArray iteration.
     */
    fun parsePosts(jsonString: String): List<Post> {
        val posts = mutableListOf<Post>()
        // Extract the wrapped "posts" JSONArray from the response object.
        val array = JSONObject(jsonString).getJSONArray("posts")
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

    /**
     * Parses the comments response. DummyJSON wraps them in a top-level
     * "comments" array: { "comments": [ { ... }, ... ] }. Each comment carries a
     * nested "user" object, so the commenter's name and handle are read from it.
     */
    fun parseComments(jsonString: String): List<Comment> {
        val comments = mutableListOf<Comment>()
        val array = JSONObject(jsonString).getJSONArray("comments")
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val user = obj.optJSONObject("user")
            comments.add(
                Comment(
                    postId = obj.optInt("postId"),
                    id = obj.optInt("id"),
                    name = user?.optString("fullName")
                        ?.takeIf { it.isNotBlank() }
                        ?: user?.optString("username") ?: "",
                    email = user?.optString("username")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "@$it" } ?: "",
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
