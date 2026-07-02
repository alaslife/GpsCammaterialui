package com.alas.md3gpscam.data.database

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class GeocodingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PHOTO_ID = "key_photo_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val photoId = inputData.getLong(KEY_PHOTO_ID, -1)
        if (photoId == -1L) {
            return@withContext Result.failure()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val photoDao = database.photoDao()

        try {
            val photo = photoDao.getPhotoById(photoId)
                ?: return@withContext Result.failure()

            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            val address = getAddress(geocoder, photo.latitude, photo.longitude)

            if (address != null && !address.startsWith("Lat:")) {
                photoDao.updatePhotoAddress(photo.id, address)
                Result.success(workDataOf("address" to address))
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun getAddress(geocoder: Geocoder, latitude: Double, longitude: Double): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val addressParts = mutableListOf<String>()
                    for (i in 0..address.maxAddressLineIndex) {
                        addressParts.add(address.getAddressLine(i))
                    }
                    return addressParts.joinToString(", ")
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    return address.getAddressLine(0)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}
