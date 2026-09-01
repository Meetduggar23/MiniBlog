package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {

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
                obj.optInt("remoteId") else null
        )
    }
}
