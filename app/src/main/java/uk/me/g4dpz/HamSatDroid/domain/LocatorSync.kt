package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.data.LocationText
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The rules that keep the home latitude, longitude and IARU (Maidenhead) locator consistent. They are the rules of
 * the original settings screen, without any Android dependency:
 *  - a new latitude must be within -90..90 and a new longitude within -180..180;
 *  - a new locator must be a valid locator;
 *  - a valid change updates the other two values to match;
 *  - an invalid value is rejected and that field is reset (to 0, or to JJ00aa for the locator), leaving the others.
 *
 * Improvements over the original: a comma is accepted as the decimal separator, numbers are always written with a
 * dot and at most five decimals regardless of the device language, and a locator is accepted only if the whole text
 * is a locator (the original accepted a locator anywhere inside other text).
 */
object LocatorSync {

    const val ZERO = "0"
    const val DEFAULT_LOCATOR = "JJ00aa"

    private val LOCATOR = Regex("^([A-Ra-r]{2})([0-9]{2})([A-Xa-x]{2})([0-9]{2})?$")

    enum class Field { LATITUDE, LONGITUDE, LOCATOR }

    /** The three stored values. Latitude and longitude are decimal degrees as text. */
    data class Location(val latitude: String, val longitude: String, val locator: String)

    sealed interface Result {
        /** The edit was valid; [location] holds all three values, updated to agree. */
        data class Accepted(val location: Location) : Result

        /** The edit was not valid. [location] is the previous state with [field] reset. */
        data class Rejected(val field: Field, val location: Location) : Result
    }

    fun editLatitude(text: String, current: Location): Result {
        val lat = LocationText.toDegrees(text)
        if (lat == null || lat < -90.0 || lat > 90.0) {
            return Result.Rejected(Field.LATITUDE, current.copy(latitude = ZERO))
        }
        val lon = LocationText.toDegrees(current.longitude) ?: 0.0
        return Result.Accepted(Location(format(lat), current.longitude, IaruLocator(lat, lon).toMaidenhead()))
    }

    fun editLongitude(text: String, current: Location): Result {
        val lon = LocationText.toDegrees(text)
        if (lon == null || lon < -180.0 || lon > 180.0) {
            return Result.Rejected(Field.LONGITUDE, current.copy(longitude = ZERO))
        }
        val lat = LocationText.toDegrees(current.latitude) ?: 0.0
        return Result.Accepted(Location(current.latitude, format(lon), IaruLocator(lat, lon).toMaidenhead()))
    }

    fun editLocator(text: String, current: Location): Result {
        val match = LOCATOR.matchEntire(text.trim())
            ?: return Result.Rejected(Field.LOCATOR, current.copy(locator = DEFAULT_LOCATOR))
        val (field, square, sub) = match.destructured
        val canonical = field.uppercase() + square + sub.lowercase()
        val locator = IaruLocator(canonical)
        return Result.Accepted(
            Location(format(locator.latitude.toDegrees()), format(locator.longitude.toDegrees()), canonical)
        )
    }

    /** All three values for a position found by GPS or network location. */
    fun fromCoordinates(latitude: Double, longitude: Double): Location =
        Location(format(latitude), format(longitude), IaruLocator(latitude, longitude).toMaidenhead())

    /** Plain decimal text with a dot, at most five decimals, no trailing zeros, never scientific notation. */
    private fun format(degrees: Double): String =
        BigDecimal.valueOf(degrees).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
