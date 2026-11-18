package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class DetectedLocation(
    val area: String,
    val city: String
)

object LocationHelper {

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun detectAreaAndCity(context: Context): Result<DetectedLocation> = withContext(Dispatchers.IO) {
        if (!hasCoarseLocationPermission(context)) {
            return@withContext Result.failure(IllegalStateException("Location permission not granted."))
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext Result.failure(IllegalStateException("Location service unavailable."))

        val location = getBestLastKnownLocation(locationManager)
            ?: return@withContext Result.failure(
                IllegalStateException("Could not determine current location. Please enter your area & city manually.")
            )

        if (!Geocoder.isPresent()) {
            return@withContext Result.failure(
                IllegalStateException("Geocoder unavailable on this device. Please type your area & city.")
            )
        }

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = fetchAddress(geocoder, location.latitude, location.longitude)
            ?: return@withContext Result.failure(
                IllegalStateException("Could not resolve area name from location. Please enter manually.")
            )

        val area = listOfNotNull(
            address.subLocality,
            address.thoroughfare,
            address.featureName?.takeIf { it != address.locality }
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val city = listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        if (area.isBlank() && city.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Location found, but area/city details were unavailable. Enter manually.")
            )
        }

        Result.success(
            DetectedLocation(
                area = area.ifBlank { city },
                city = city.ifBlank { area }
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            val enabled = runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) continue
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    private suspend fun fetchAddress(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): Address? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            runCatching {
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }.getOrNull()
        }
    }
}
