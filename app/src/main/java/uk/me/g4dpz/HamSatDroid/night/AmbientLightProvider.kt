// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.night

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Ambient light readings in lux from the device's light sensor. The sensor is registered only while [lux] is being
 * collected, so the caller controls when it runs (only while the app is visible and automatic mode is on).
 */
class AmbientLightProvider(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** Whether the device has a light sensor; without one the automatic option is not offered. */
    val available: Boolean get() = sensor != null

    /** The readings; empty if there is no light sensor. */
    fun lux(): Flow<Float> = callbackFlow {
        val light = sensor
        if (light == null || sensorManager == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()
}
