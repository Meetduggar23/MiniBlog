package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {

    fun parsePosts(jsonString: String): List<Post> {
        val posts = mutableListOf<Post>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            posts.add(
                Post(
                    userId = obj.optInt("userId"),
                    id = obj.optInt("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body")
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
}
