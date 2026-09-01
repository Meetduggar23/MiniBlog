package com.example.miniblog.model

data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)
