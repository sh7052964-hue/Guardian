package com.guardian360.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long
)

class LocationTracker(private val context: Context) {

    fun hasLocationPermission(): Boolean = runCatching {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fine || coarse
    }.getOrDefault(false)

    fun isLocationEnabled(): Boolean = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentOrLastLocation(): Result<CapturedLocation> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted. Allow Location access first."))
        }
        return try {
            val fusedLocation = runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val current = runCatching {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                }.getOrNull()
                current ?: runCatching { client.lastLocation.await() }.getOrNull()
            }.getOrNull()

            val platformLocation = fusedLocation ?: runCatching {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                providers.mapNotNull { provider ->
                    runCatching { lm?.getLastKnownLocation(provider) }.getOrNull()
                }.maxByOrNull { it.time }
            }.getOrNull()

            if (platformLocation == null) {
                val msg = if (!isLocationEnabled()) {
                    "Device GPS / Location services are turned off. Enable Location in quick settings and retry."
                } else {
                    "Location fix not available yet. Move outdoors or retry in a few seconds."
                }
                return Result.failure(IllegalStateException(msg))
            }

            Result.success(
                CapturedLocation(
                    latitude = platformLocation.latitude,
                    longitude = platformLocation.longitude,
                    accuracyMeters = platformLocation.accuracy,
                    capturedAtEpochMs = platformLocation.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Location permission denied by OS.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Never display stale location (> 5 min old) as live.
         */
        fun isLive(capturedAtEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
            return (nowEpochMs - capturedAtEpochMs) <= 5 * 60_000L
        }

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
            return 2.0 * r * atan2(sqrt(a), sqrt(1.0 - a))
        }
    }
}
