package com.alas.md3gpscam.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationTracker(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    @Volatile private var cachedAddress = "Finding address…"
    @Volatile private var lastGeocodedLocation: Location? = null
    @Volatile private var lastGeocodeTime = 0L

    fun getLocationUpdates(intervalMs: Long = 1000): Flow<GpsLocation> = callbackFlow {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Coordinates should never wait for the comparatively slow Geocoder.
                    trySend(convertToGpsLocation(location, cachedAddress))

                    val previous = lastGeocodedLocation
                    val shouldRefreshAddress = previous == null ||
                        previous.distanceTo(location) > 75f ||
                        System.currentTimeMillis() - lastGeocodeTime > 30_000
                    if (shouldRefreshAddress) {
                        lastGeocodedLocation = location
                        lastGeocodeTime = System.currentTimeMillis()
                        launch(Dispatchers.IO) {
                            cachedAddress = getAddressFromLocation(location.latitude, location.longitude)
                            trySend(convertToGpsLocation(location, cachedAddress))
                        }
                    }
                }
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(500)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { trySend(convertToGpsLocation(it, cachedAddress)) }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }.conflate()

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GpsLocation = withContext(Dispatchers.IO) {
        try {
            val task = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            val location = task.await()
            if (location != null) {
                val address = getAddressFromLocation(location.latitude, location.longitude)
                cachedAddress = address
                return@withContext convertToGpsLocation(location, address)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val taskLast = fusedLocationClient.lastLocation
            val location = taskLast.await()
            if (location != null) {
                return@withContext convertToGpsLocation(location, cachedAddress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        GpsLocation(
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            speed = 0.0f,
            address = "No Location Available",
            time = System.currentTimeMillis()
        )
    }

    private fun convertToGpsLocation(location: Location, address: String): GpsLocation {
        return GpsLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            address = address,
            time = location.time,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        )
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())
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
                    return address.getAddressLine(0) ?: "Unknown Address"
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "Lat: %.4f, Long: %.4f".format(latitude, longitude)
    }

    // Custom suspend helper to wait for Play Services Tasks
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? = suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resume(null)
            }
        }
    }
}
