package com.alas.md3gpscam.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alas.md3gpscam.data.database.AppDatabase
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.data.preferences.SettingsRepository
import com.alas.md3gpscam.data.repository.PhotoRepository
import com.alas.md3gpscam.image.ImageProcessor
import com.alas.md3gpscam.location.GpsLocation
import com.alas.md3gpscam.location.LocationTracker
import com.alas.md3gpscam.location.CompassTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.work.Constraints
import java.io.File
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.alas.md3gpscam.data.database.GeocodingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val photoRepository = PhotoRepository(db.photoDao())
    val settingsRepository = SettingsRepository(application)
    private val locationTracker = LocationTracker(application)
    private val compassTracker = CompassTracker(application)

    // Current State
    val allPhotos = photoRepository.allPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentPhotos = photoRepository.getRecentPhotos(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Settings States
    val selectedTemplate = settingsRepository.selectedTemplate.stateIn(viewModelScope, SharingStarted.Lazily, "basic")
    val gridLinesEnabled = settingsRepository.gridLinesEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val soundEnabled = settingsRepository.soundEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val timerSeconds = settingsRepository.timerSeconds.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val imageRatio = settingsRepository.imageRatio.stateIn(viewModelScope, SharingStarted.Lazily, "4:3")
    val mapType = settingsRepository.mapType.stateIn(viewModelScope, SharingStarted.Lazily, "normal")
    val hapticFeedbackEnabled = settingsRepository.hapticFeedbackEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val speedUnit = settingsRepository.speedUnit.stateIn(viewModelScope, SharingStarted.Lazily, "kmh")
    val altitudeUnit = settingsRepository.altitudeUnit.stateIn(viewModelScope, SharingStarted.Lazily, "meters")
    val recordAudioInVideo = settingsRepository.recordAudioInVideo.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val themeMode = settingsRepository.themeMode.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val dynamicColorsEnabled = settingsRepository.dynamicColorsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val customNote = settingsRepository.customNote.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val photoQuality = settingsRepository.photoQuality.stateIn(viewModelScope, SharingStarted.Lazily, "medium")
    val videoQuality = settingsRepository.videoQuality.stateIn(viewModelScope, SharingStarted.Lazily, "1080p")
    val stampMapType = settingsRepository.stampMapType.stateIn(viewModelScope, SharingStarted.Lazily, "standard")
    val selectedPhotoForGallery = MutableStateFlow<PhotoEntity?>(null)

    private val _processingVideos = MutableStateFlow<Set<String>>(emptySet())
    val processingVideos = _processingVideos.asStateFlow()

    // Location State
    val currentLocation = locationTracker.getLocationUpdates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GpsLocation(0.0, 0.0, 0.0, 0.0f, "Determining location...", System.currentTimeMillis())
        )

    // Compass / Bearing State
    val deviceBearing = compassTracker.getBearingUpdates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )

    fun setTemplate(templateId: String) = viewModelScope.launch {
        settingsRepository.setSelectedTemplate(templateId)
    }

    fun setGridLines(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setGridLinesEnabled(enabled)
    }

    fun setSound(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSoundEnabled(enabled)
    }

    fun setTimer(seconds: Int) = viewModelScope.launch {
        settingsRepository.setTimerSeconds(seconds)
    }

    fun setRatio(ratio: String) = viewModelScope.launch {
        settingsRepository.setImageRatio(ratio)
    }

    fun setMapType(type: String) = viewModelScope.launch {
        settingsRepository.setMapType(type)
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHapticFeedbackEnabled(enabled)
    }

    fun setSpeedUnit(unit: String) = viewModelScope.launch {
        settingsRepository.setSpeedUnit(unit)
    }

    fun setAltitudeUnit(unit: String) = viewModelScope.launch {
        settingsRepository.setAltitudeUnit(unit)
    }

    fun setRecordAudioInVideo(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setRecordAudioInVideo(enabled)
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColorsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColorsEnabled(enabled)
    }

    fun setCustomNote(note: String) = viewModelScope.launch {
        settingsRepository.setCustomNote(note)
    }

    fun setPhotoQuality(quality: String) = viewModelScope.launch {
        settingsRepository.setPhotoQuality(quality)
    }

    fun setVideoQuality(quality: String) = viewModelScope.launch {
        settingsRepository.setVideoQuality(quality)
    }

    fun setStampMapType(type: String) = viewModelScope.launch {
        settingsRepository.setStampMapType(type)
    }

    fun setSelectedPhotoForGallery(photo: PhotoEntity?) {
        selectedPhotoForGallery.value = photo
    }

    // Formatter utilities
    fun getFormattedSpeed(speedMs: Float): String {
        val unit = speedUnit.value
        val converted = if (unit == "mph") speedMs * 2.23694f else speedMs * 3.6f
        val suffix = if (unit == "mph") "mph" else "km/h"
        return "%.1f %s".format(converted, suffix)
    }

    fun getFormattedAltitude(altMeters: Double): String {
        val unit = altitudeUnit.value
        val converted = if (unit == "feet") altMeters * 3.28084 else altMeters
        val suffix = if (unit == "feet") "ft" else "m"
        return "%.1f %s".format(converted, suffix)
    }

    fun getDirectionAbbreviation(bearing: Float): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = (((bearing + 22.5) / 45).toInt()) % 8
        return directions[index]
    }

    // Save Photo Flow: Stamping location & inserting record in Room
    fun saveCapturedPhoto(tempUri: Uri, bearing: Float, onSuccess: (PhotoEntity) -> Unit) {
        viewModelScope.launch {
            val freshLocation = locationTracker.getCurrentLocation()
            val note = customNote.value
            val template = selectedTemplate.value
            val sUnit = speedUnit.value
            val aUnit = altitudeUnit.value
            val ratio = imageRatio.value
            val pQuality = photoQuality.value
            val sMapType = stampMapType.value

            // Run image processing and DB insertion on a background thread
            val savedEntity: PhotoEntity? = withContext(Dispatchers.IO) {
                val location = if (freshLocation.latitude != 0.0 && freshLocation.longitude != 0.0) {
                    freshLocation
                } else {
                    currentLocation.value
                }

                val stampedUri = ImageProcessor.stampImage(
                    context = getApplication(),
                    inputUri = tempUri,
                    location = location,
                    templateId = template,
                    bearing = bearing,
                    speedUnit = sUnit,
                    altitudeUnit = aUnit,
                    customNote = if (note.isBlank()) null else note,
                    imageRatio = ratio,
                    photoQuality = pQuality,
                    stampMapType = sMapType
                )

                if (stampedUri != null) {
                    val photoEntity = PhotoEntity(
                        filePath = stampedUri.toString(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        speed = location.speed,
                        address = location.address,
                        timestamp = System.currentTimeMillis(),
                        templateId = template,
                        bearing = bearing,
                        isVideo = false,
                        customNote = if (note.isBlank()) null else note
                    )
                    val newId = photoRepository.insertPhoto(photoEntity)
                    photoEntity.copy(id = newId) // Return the entity from the withContext block
                } else {
                    null
                }
            }
            
            if (savedEntity != null) {
                // Enqueue worker and call success callback on the main thread
                val geocodingRequest = OneTimeWorkRequestBuilder<GeocodingWorker>()
                    .setInputData(workDataOf(GeocodingWorker.KEY_PHOTO_ID to savedEntity.id))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(getApplication()).enqueue(geocodingRequest)
                
                onSuccess(savedEntity)
            }
        }
    }

    // Save Video Flow: Insert video record with metadata in Room
    fun saveCapturedVideo(videoUri: Uri, bearing: Float, onSuccess: (PhotoEntity) -> Unit) {
        viewModelScope.launch {
            val freshLocation = locationTracker.getCurrentLocation()
            val note = customNote.value
            val template = selectedTemplate.value
            val sUnit = speedUnit.value
            val aUnit = altitudeUnit.value

            _processingVideos.update { it + videoUri.toString() }

            val savedEntity: PhotoEntity? = withContext(Dispatchers.IO) {
                val location = if (freshLocation.latitude != 0.0 && freshLocation.longitude != 0.0) {
                    freshLocation
                } else {
                    currentLocation.value
                }
                
                try {
                    val watermarkedFileUri = com.alas.md3gpscam.video.VideoWatermarkProcessor.applyWatermark(
                        context = getApplication(),
                        inputVideoUri = videoUri,
                        location = location,
                        templateId = template,
                        bearing = bearing,
                        speedUnit = sUnit,
                        altitudeUnit = aUnit,
                        customNote = if (note.isBlank()) null else note
                    )

                    if (watermarkedFileUri != videoUri) {
                        val path = watermarkedFileUri.path
                        if (path != null) {
                            val file = File(path)
                            if (file.exists() && file.length() > 0) {
                                getApplication<Application>().contentResolver.openOutputStream(videoUri, "wt")?.use { outputStream ->
                                    file.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                file.delete() // Clean up temp file
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val photoEntity = PhotoEntity(
                    filePath = videoUri.toString(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speed = location.speed,
                    address = location.address,
                    timestamp = System.currentTimeMillis(),
                    templateId = template,
                    bearing = bearing,
                    isVideo = true,
                    customNote = if (note.isBlank()) null else note
                )
                val newId = photoRepository.insertPhoto(photoEntity)
                photoEntity.copy(id = newId)
            }
            
            _processingVideos.update { it - videoUri.toString() }
            
            if (savedEntity != null) {
                val geocodingRequest = OneTimeWorkRequestBuilder<GeocodingWorker>()
                    .setInputData(workDataOf(GeocodingWorker.KEY_PHOTO_ID to savedEntity.id))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(getApplication()).enqueue(geocodingRequest)

                onSuccess(savedEntity)
            }
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(photo.filePath)
                getApplication<Application>().contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            photoRepository.deletePhoto(photo)
        }
    }
}
