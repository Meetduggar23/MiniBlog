package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkClient
import com.example.miniblog.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository {

    suspend fun getPosts(): NetworkResult<List<Post>> =
        withContext(Dispatchers.IO) {
            when (val result = NetworkClient.get("/posts")) {
                is NetworkResult.Success ->
                    try {
                        NetworkResult.Success(JsonParser.parsePosts(result.data))
                    } catch (e: Exception) {
                        NetworkResult.Error("Could not read posts: ${e.message}")
                    }
                is NetworkResult.Error -> result
            }
        }

    suspend fun getComments(postId: Int): NetworkResult<List<Comment>> =
        withContext(Dispatchers.IO) {
            when (val result =
                NetworkClient.get("/posts/$postId/comments")) {
                is NetworkResult.Success ->
                    try {
                        NetworkResult.Success(
                            JsonParser.parseComments(result.data)
                        )
                    } catch (e: Exception) {
                        NetworkResult.Error(
                            "Could not read comments: ${e.message}"
                        )
                    }
                is NetworkResult.Error -> result
            }
        }

    suspend fun deletePost(postId: Int): NetworkResult<String> =
        withContext(Dispatchers.IO) {
            NetworkClient.delete("/posts/$postId")
        }

    suspend fun createPost(title: String, body: String): NetworkResult<String> =
        withContext(Dispatchers.IO) {
            val json = JsonParser.buildPostJson(title, body, 1)
            NetworkClient.post("/posts", json)
        }
}
