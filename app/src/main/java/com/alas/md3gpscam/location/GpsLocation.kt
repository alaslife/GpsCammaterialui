package com.alas.md3gpscam.location

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val address: String,
    val time: Long,
    val bearing: Float = 0f,
    val accuracy: Float = 0f
)
