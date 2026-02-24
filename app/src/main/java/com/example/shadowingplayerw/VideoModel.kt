package com.example.shadowingplayerw

data class VideoSegment(
    val id: Int,
    val name: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val thumbnailPath: String? = null
)
