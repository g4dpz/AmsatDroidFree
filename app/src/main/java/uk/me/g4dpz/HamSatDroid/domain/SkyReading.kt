package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.SatPos
import kotlin.math.PI

/** Where a satellite is at one moment, as seen from the home location, in the units screens show. */
data class SkyReading(
    val azEl: AzEl,
    val latitudeDeg: Double,
    /** Longitude of the point below the satellite, -180 to 180 (east positive). */
    val longitudeDeg: Double,
    val rangeKm: Double,
    /** Rate of change of range in km/s (positive receding), for the Doppler correction. */
    val rangeRateKmS: Double,
) {
    val elevationDeg: Double get() = Math.toDegrees(azEl.elevation)
    val azimuthDeg: Double get() = Math.toDegrees(azEl.azimuth)

    companion object {
        fun from(pos: SatPos): SkyReading {
            var lon = Math.toDegrees(pos.longitude)
            // The library gives longitude as 0..2 pi; show it as -180..180.
            if (pos.longitude > PI) lon -= 360.0
            return SkyReading(AzEl(pos.azimuth, pos.elevation), Math.toDegrees(pos.latitude), lon, pos.range, pos.rangeRate)
        }
    }
}
