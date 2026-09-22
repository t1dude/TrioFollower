package com.trionsandroid.app.data.device

import android.content.Context
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the phone's own battery level, for the Low phone battery alarm. */
@Singleton
class PhoneBatteryReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 0-100, or null if the system can't report it. */
    fun currentLevelPercent(): Int? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return capacity.takeIf { it in 0..100 }
    }
}
