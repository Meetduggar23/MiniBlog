package com.example.miniblog.data

import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkClient
import com.example.miniblog.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository {

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

    /**
     * Publishes a post and returns the created post built from the backend
     * response (including the backend-assigned id when one is returned).
     * The caller assigns the device-local id before showing it in the feed.
     */
    suspend fun createPost(title: String, body: String): NetworkResult<Post> =
        withContext(Dispatchers.IO) {
            val json = JsonParser.buildPostJson(title, body, 1)
            when (val result = NetworkClient.post("/posts", json)) {
                is NetworkResult.Success ->
                    NetworkResult.Success(
                        Post(
                            userId = 1,
                            id = 0,
                            title = title,
                            body = body,
                            remoteId = JsonParser.parseCreatedPostId(result.data)
                        )
                    )
                is NetworkResult.Error -> result
            }
        }
}
