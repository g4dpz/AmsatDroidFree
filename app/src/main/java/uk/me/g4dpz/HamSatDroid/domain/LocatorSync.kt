// SPDX-License-Identifier: GPL-3.0-or-later
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

    /** What a character of a locator must be at each position: two letters A to R, two digits, two letters A to X, two digits. */
    enum class Expected { LETTER_A_TO_R, DIGIT, LETTER_A_TO_X, NOTHING }

    /**
     * Why the text typed so far cannot become a valid locator: the 1-based [position] of the first character that does
     * not fit, what that position needs, and the character [found] there.
     */
    data class TypingProblem(val position: Int, val expected: Expected, val found: Char)

    /**
     * Checks locator text while it is being typed. Null means the text can still become a valid locator (or already is
     * one), so a half-typed `IO82x` is fine; otherwise the first character that cannot be right, such as the zero in
     * `I082xl`. Case is ignored and spaces around the text are ignored, as when the locator is accepted. This is for
     * telling the user early; [editLocator] still decides what is accepted.
     */
    fun typingProblem(text: String): TypingProblem? {
        val typed = text.trim()
        for ((i, c) in typed.withIndex()) {
            val expected = when (i) {
                0, 1 -> Expected.LETTER_A_TO_R
                2, 3, 6, 7 -> Expected.DIGIT
                4, 5 -> Expected.LETTER_A_TO_X
                else -> Expected.NOTHING
            }
            val fits = when (expected) {
                Expected.LETTER_A_TO_R -> c in 'A'..'R' || c in 'a'..'r'
                Expected.LETTER_A_TO_X -> c in 'A'..'X' || c in 'a'..'x'
                Expected.DIGIT -> c in '0'..'9'
                Expected.NOTHING -> false
            }
            if (!fits) return TypingProblem(i + 1, expected, c)
        }
        return null
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
