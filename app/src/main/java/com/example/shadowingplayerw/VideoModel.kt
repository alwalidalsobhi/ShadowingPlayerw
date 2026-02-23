package com.example.shadowingplayerw

// الموديل المسؤول عن بيانات مقاطع الفيديو
data class VideoSegment(
    val id: Int,
    val name: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val thumbnailPath: String? = null // الحقل الجديد المسبب للمشكلة
)
