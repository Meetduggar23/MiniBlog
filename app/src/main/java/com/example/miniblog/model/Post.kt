package com.example.miniblog.model

data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: Int? = null
) {
    companion object {
        /**
         * Posts created on this device get ids starting from this base so they
         * can be told apart from ids that come from the remote service.
         */
        const val LOCAL_ID_BASE = 10000
    }
}
