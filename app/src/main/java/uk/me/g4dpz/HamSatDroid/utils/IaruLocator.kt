package uk.me.g4dpz.HamSatDroid.utils

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class IaruLocator {

    var latitude: Latitude
    var longitude: Longitude

    constructor() {
        latitude = Latitude()
        longitude = Longitude()
    }

    constructor(latitudeIn: Latitude, longitudeIn: Longitude) {
        latitude = latitudeIn
        longitude = longitudeIn
    }

    constructor(latitudeIn: Double, longitudeIn: Double) {
        latitude = Latitude.fromDegrees(latitudeIn)
        longitude = Longitude.fromDegrees(longitudeIn)
    }

    /** @param maidenhead used to construct location from a maidenhead locator string */
    constructor(maidenhead: String) {
        latitude = extractLat(maidenhead)
        longitude = extractLon(maidenhead)
    }

    override fun equals(other: Any?): Boolean = other is IaruLocator && other.hashCode() == hashCode()

    override fun hashCode(): Int {
        var hash = 1
        hash = hash * 17 + latitude.hashCode()
        hash = hash * 31 + longitude.hashCode()
        return hash
    }

    /** @return maidenhead locator string */
    fun toMaidenhead(): String = toMaidenhead(latitude.toDegrees(), longitude.toDegrees())

    fun getDistanceMi(loc2: IaruLocator): Double = getDistanceMi(this, loc2)

    fun getDistanceKm(loc2: IaruLocator): Double = getDistanceKm(this, loc2)

    fun getDistanceNm(loc2: IaruLocator): Double = getDistanceNm(this, loc2)

    /** @return bearing in degrees to the destination */
    fun getBearing(loc2: IaruLocator): Double = getBearing(this, loc2)

    companion object {
        /** Average earth radius in kilometers, IUGG definition. */
        private const val AVG_EARTH_RADIUS_KM = 6371.009

        /** Average earth radius in statute miles, IUGG definition. */
        private const val AVG_EARTH_RADIUS_SM = 3958.761

        /** Average earth radius in nautical miles, IUGG definition. */
        private const val AVG_EARTH_RADIUS_NM = 3440.069

        @JvmStatic
        fun toMaidenhead(latitudeIn: Double, longitudeIn: Double): String {
            val lon = (longitudeIn + 180) / 2
            val lonFirst = ('A'.code + (lon / 10).toInt()).toChar()
            val lonSecond = ('0'.code + (lon % 10).toInt()).toChar()
            val lonThird = ('A'.code + ((lon % 1) * 24).toInt()).toChar()
            val lat = latitudeIn + 90
            val latFirst = ('A'.code + (lat / 10).toInt()).toChar()
            val latSecond = ('0'.code + (lat % 10).toInt()).toChar()
            val latThird = ('A'.code + ((lat % 1) * 24).toInt()).toChar()
            return buildString {
                append(lonFirst)
                append(latFirst)
                append(lonSecond)
                append(latSecond)
                append(lonThird.lowercaseChar())
                append(latThird.lowercaseChar())
            }
        }

        @JvmStatic
        fun extractLat(maidenheadIn: String): Latitude {
            val m = maidenheadIn.uppercase()
            val latitude = -90 + 10 * (m[1] - 'A') + (m[3] - '0') + 2.5 / 60 * (m[5] - 'A') + 2.5 / 60 / 2
            return Latitude.fromDegrees(latitude)
        }

        @JvmStatic
        fun extractLon(maidenheadIn: String): Longitude {
            val m = maidenheadIn.uppercase()
            val longitude = -180 + 20 * (m[0] - 'A') + 2 * (m[2] - '0') + 5.0 / 60 * (m[4] - 'A') + 5.0 / 60 / 2
            return Longitude.fromDegrees(longitude)
        }

        @JvmStatic
        fun getDistanceMi(loc1: IaruLocator, loc2: IaruLocator): Double = getDistance(loc1, loc2, AVG_EARTH_RADIUS_SM)

        private fun getDistanceKm(loc1: IaruLocator, loc2: IaruLocator): Double = getDistance(loc1, loc2, AVG_EARTH_RADIUS_KM)

        private fun getDistanceNm(loc1: IaruLocator, loc2: IaruLocator): Double = getDistance(loc1, loc2, AVG_EARTH_RADIUS_NM)

        /** @return great circle distance between the two locations, in the same units as [radius] */
        private fun getDistance(loc1: IaruLocator, loc2: IaruLocator, radius: Double): Double {
            if (loc1 == loc2) return 0.0
            val lat1 = loc1.latitude.getRadians()
            val lat2 = loc2.latitude.getRadians()
            return acos(
                sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(loc2.longitude.getRadians() - loc1.longitude.getRadians())
            ) * radius
        }

        @JvmStatic
        fun getBearing(loc1: IaruLocator, loc2: IaruLocator): Double {
            if (loc1 == loc2) return Double.NaN
            val dLon = loc2.longitude.getRadians() - loc1.longitude.getRadians()
            val lat1 = loc1.latitude.getRadians()
            val lat2 = loc2.latitude.getRadians()
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return (Angle.radiansToDegrees(atan2(y, x)) + 360) % 360
        }
    }
}
