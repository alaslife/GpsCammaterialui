package com.alas.md3gpscam.data.repository

import com.alas.md3gpscam.data.database.PhotoDao
import com.alas.md3gpscam.data.database.PhotoEntity
import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()
    
    fun getRecentPhotos(limit: Int): Flow<List<PhotoEntity>> = photoDao.getRecentPhotos(limit)

    suspend fun getPhotoById(id: Long): PhotoEntity? = photoDao.getPhotoById(id)

    suspend fun insertPhoto(photo: PhotoEntity): Long = photoDao.insertPhoto(photo)

    suspend fun deletePhoto(photo: PhotoEntity) = photoDao.deletePhoto(photo)
}
