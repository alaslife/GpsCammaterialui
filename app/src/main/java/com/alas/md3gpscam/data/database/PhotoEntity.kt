package com.alas.md3gpscam.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val address: String,
    val timestamp: Long,
    val templateId: String = "basic",
    val bearing: Float = 0f,
    val isVideo: Boolean = false,
    val customNote: String? = null
)
