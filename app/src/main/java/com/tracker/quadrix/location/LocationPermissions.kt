package com.tracker.quadrix.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The permission state tracking depends on, reduced to the two things that actually gate it.
 *
 * [background] is the important one: "Allow all the time". Without it Android delivers location
 * only while the app is visible, so the whole point — collecting in the background — does not
 * work. The app treats anything short of [allGranted] as not ready and blocks on it.
 */
data class LocationPermissionStatus(
    val foreground: Boolean,
    val background: Boolean,
) {
    val allGranted: Boolean get() = foreground && background

    companion object {
        fun read(context: Context): LocationPermissionStatus {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val foreground = fine || coarse

            // Below Android 10 there is no separate background permission — foreground grant
            // already covers background delivery.
            val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                foreground
            }

            return LocationPermissionStatus(foreground = foreground, background = background)
        }
    }
}
