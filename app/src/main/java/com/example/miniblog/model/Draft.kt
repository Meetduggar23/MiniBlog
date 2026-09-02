package com.example.miniblog.model

/** A locally autosaved/saved draft of an unpublished post. */
data class Draft(
    val id: Int,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
