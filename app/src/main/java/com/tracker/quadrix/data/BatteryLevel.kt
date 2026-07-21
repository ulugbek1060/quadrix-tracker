package com.tracker.quadrix.data

import android.content.Context
import android.os.BatteryManager

/**
 * Battery percentage sent with each fix — useful on the server side for telling "the device
 * stopped reporting" apart from "the device went flat".
 */
object BatteryLevel {

    fun read(context: Context): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }.getOrNull()
}
