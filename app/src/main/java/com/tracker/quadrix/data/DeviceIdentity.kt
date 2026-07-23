package com.tracker.quadrix.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface

/**
 * Identifies the device to the backend.
 *
 * ANDROID_ID is the identifier the backend keys on (sent as `device_id` at verify-otp and on
 * every location upload): no permission required, stable across reboots and app updates, unique
 * per device + app signing key, reset only by a factory reset. Note the signing-key scoping — a
 * debug build and a release build report different IDs.
 */
class DeviceIdentity(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    val deviceId: String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

    /** The user-visible device name (Settings › About), falling back to the model. */
    val deviceName: String =
        (Settings.Global.getString(appContext.contentResolver, "device_name")
            ?: Settings.Secure.getString(appContext.contentResolver, "bluetooth_name"))
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    /**
     * Human-readable dump of the hardware, OS and identifiers of this device, for logging/support.
     *
     * A note on MAC address and serial number: both are deliberately locked down on modern
     * Android. Since Android 6 the Wi-Fi MAC is randomised/withheld from ordinary apps, and since
     * Android 10 the hardware serial requires privileged (device-owner) access — so for a normal
     * app these read back as a placeholder or "unavailable". They are shown for completeness with
     * an honest fallback rather than a fake value.
     */
    val deviceInfo: String =
        """
        Manufacturer: ${Build.MANUFACTURER}
        Brand: ${Build.BRAND}
        Model: ${Build.MODEL}
        Device Name: $deviceName
        Device ID/Name: ${Build.DEVICE}
        Android ID: $deviceId
        Serial Number: ${readSerial()}
        MAC Address: ${readMacAddress()}
        Hardware/Board: ${Build.BOARD}
        Hardware Name: ${Build.HARDWARE}
        Product Name: ${Build.PRODUCT}
        Android OS Version: ${Build.VERSION.RELEASE}
        SDK API Level: ${Build.VERSION.SDK_INT}
        Build Fingerprint: ${Build.FINGERPRINT}
        """.trimIndent()

    /**
     * Reads the hardware serial where the platform still permits it. Ordinary apps are refused on
     * Android 8+ (needs READ_PHONE_STATE) and outright barred on Android 10+, so this usually
     * resolves to an honest "unavailable" rather than a real serial.
     */
    private fun readSerial(): String = try {
        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Build.getSerial()
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }
        serial?.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: "unavailable"
    } catch (_: SecurityException) {
        "unavailable (needs privileged access)"
    } catch (_: Exception) {
        "unavailable"
    }

    /**
     * Best-effort Wi-Fi (wlan0) MAC. Android 6+ randomises or hides this from ordinary apps, so it
     * commonly reads back as the placeholder 02:00:00:00:00:00 or is missing entirely.
     */
    private fun readMacAddress(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .firstOrNull { it.name.equals("wlan0", ignoreCase = true) }
            ?.hardwareAddress
            ?.joinToString(":") { "%02X".format(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "unavailable (restricted on Android 6+)"
    } catch (_: Exception) {
        "unavailable"
    }
}
