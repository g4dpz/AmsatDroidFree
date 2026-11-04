package uk.me.g4dpz.HamSatDroid.aim

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/**
 * One compass reading: the world-from-device rotation matrix (row-major 3x3), the heading of the device's top edge in
 * true-north degrees, the sensor accuracy, and which sensor it came from.
 */
data class Orientation(
    val rotationMatrix: FloatArray,
    val headingDeg: Double,
    val accuracy: Int,
    val source: CompassSource,
) {
    /** Whether the accuracy is low enough to suggest calibrating. */
    val needsCalibration: Boolean get() = accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
        accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
}

enum class CompassSource { ROTATION_VECTOR, GEOMAGNETIC_ROTATION_VECTOR, ACCEL_MAG, NONE }

/**
 * Heading readings from the device's sensors, as a [Flow] that registers the sensors only while it is collected.
 *
 * The source is chosen in order of quality: the rotation-vector sensor (uses the gyroscope when present), then the
 * geomagnetic rotation-vector sensor (no gyroscope, lower accuracy), then accelerometer plus magnetometer combined
 * by hand. The Galaxy Tab A7 Lite / A11 has no gyroscope, so it falls to one of the latter two.
 *
 * Each reading is corrected to true north with the magnetic declination at the observer, mapped for the current
 * display rotation, and smoothed (more for the noisier sources). The raw matrix is kept so the aiming aid can take a
 * pointing direction from the same reading.
 */
class CompassProvider(context: Context) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)

    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticRotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val source: CompassSource = when {
        rotationVector != null -> CompassSource.ROTATION_VECTOR
        geomagneticRotationVector != null -> CompassSource.GEOMAGNETIC_ROTATION_VECTOR
        accelerometer != null && magnetometer != null -> CompassSource.ACCEL_MAG
        else -> CompassSource.NONE
    }

    /** Whether the device can give a heading at all; the compass toggle is hidden when false. */
    val isAvailable: Boolean get() = source != CompassSource.NONE

    /**
     * Readings for the observer at [latitudeDeg]/[longitudeDeg] (for the declination). [displayRotation] is
     * `display.rotation` (Surface.ROTATION_*). Emits nothing if no source is available.
     */
    fun orientations(latitudeDeg: Double, longitudeDeg: Double, displayRotation: Int): Flow<Orientation> = callbackFlow {
        val manager = sensorManager
        if (manager == null || source == CompassSource.NONE) {
            close()
            return@callbackFlow
        }

        val declination = GeomagneticField(
            latitudeDeg.toFloat(), longitudeDeg.toFloat(), 0f, System.currentTimeMillis(),
        ).declination.toDouble()

        val alpha = if (source == CompassSource.ROTATION_VECTOR) 0.35 else 0.15 // noisier sources smoothed more
        var smoothed = Double.NaN

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveMagnetic = false
        val rotation = FloatArray(9)
        val mapped = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

        fun emitFrom(matrix: FloatArray) {
            val worldFromDevice = remapForDisplay(matrix, displayRotation, mapped)
            SensorManager.getOrientation(worldFromDevice, orientationAngles)
            val magneticHeading = Math.toDegrees(orientationAngles[0].toDouble())
            val trueHeading = HeadingMath.trueHeading(magneticHeading, declination)
            smoothed = if (smoothed.isNaN()) trueHeading else HeadingMath.smoothHeading(smoothed, trueHeading, alpha)
            trySend(Orientation(worldFromDevice.copyOf(), smoothed, accuracy, source))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        accuracy = event.accuracy
                        emitFrom(rotation)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity); haveGravity = true
                        if (haveMagnetic) tryAccelMag()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic); haveMagnetic = true
                        accuracy = event.accuracy
                        if (haveGravity) tryAccelMag()
                    }
                }
            }

            private fun tryAccelMag() {
                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) emitFrom(rotation)
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        val rate = SensorManager.SENSOR_DELAY_UI
        when (source) {
            CompassSource.ROTATION_VECTOR -> manager.registerListener(listener, rotationVector, rate)
            CompassSource.GEOMAGNETIC_ROTATION_VECTOR -> manager.registerListener(listener, geomagneticRotationVector, rate)
            CompassSource.ACCEL_MAG -> {
                manager.registerListener(listener, accelerometer, rate)
                manager.registerListener(listener, magnetometer, rate)
            }
            CompassSource.NONE -> Unit
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun remapForDisplay(matrix: FloatArray, displayRotation: Int, out: FloatArray): FloatArray {
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        return if (SensorManager.remapCoordinateSystem(matrix, axisX, axisY, out)) out else matrix
    }

    private fun lowPass(input: FloatArray, output: FloatArray) {
        val a = 0.2f
        for (i in 0..2) output[i] = output[i] + a * (input[i] - output[i])
    }
}
