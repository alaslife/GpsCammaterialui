package com.alas.md3gpscam.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentPhotos(limit: Int): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos")
    suspend fun getPhotosList(): List<PhotoEntity>

    @Query("UPDATE photos SET address = :address WHERE id = :id")
    suspend fun updatePhotoAddress(id: Long, address: String): Int

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity): Int
}
