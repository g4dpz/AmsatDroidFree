// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.PassHeader
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date, time and number formats for display, in the user's language and time zone. Stored values never use these.
 */
class DisplayFormats(val locale: Locale, val zone: ZoneId = ZoneId.systemDefault()) {
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zone)
    private val coordinate = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 }
    private val oneDecimal = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    private val integer = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val twoDecimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    private val groupedInteger = NumberFormat.getIntegerInstance(locale)

    /** A whole number, such as a degree of azimuth or a range in kilometres. */
    fun integer(value: Double): String = integer.format(value)

    /** A whole number with locale grouping, such as a frequency in hertz ("435,120,000"). */
    fun groupedInteger(value: Long): String = groupedInteger.format(value)

    /** A number with up to two decimals, such as a latitude. */
    fun twoDecimals(value: Double): String = twoDecimals.format(value)

    fun date(instant: Instant): String = date.format(instant)
    fun time(instant: Instant): String = time.format(instant)
    fun dateTime(instant: Instant): String = dateTime.format(instant)
    fun dateTime(millis: Long): String = dateTime(Instant.ofEpochMilli(millis))

    /** Latitude or longitude with up to four decimals. */
    fun coordinate(degrees: Double): String = coordinate.format(degrees)

    /** A number with exactly one decimal, such as an elevation or a duration. */
    fun oneDecimal(value: Double): String = oneDecimal.format(value)
}

/** The formats for the current language; recreated if the language changes. */
@Composable
fun rememberDisplayFormats(): DisplayFormats {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.ROOT else locales[0]
    return remember(locale) { DisplayFormats(locale) }
}

/** The text of the heading above the pass list. */
@Composable
fun PassHeader.text(formats: DisplayFormats): String = when (this) {
    is PassHeader.Predictions ->
        if (hoursAhead == 0) {
            stringResource(
                R.string.header_predictions_next_pass,
                satName, formats.coordinate(latitude), formats.coordinate(longitude), locator, formats.dateTime(startTime),
            )
        } else {
            pluralStringResource(
                R.plurals.header_predictions_hours, hoursAhead,
                satName, formats.coordinate(latitude), formats.coordinate(longitude), locator,
                formats.dateTime(startTime), hoursAhead,
            )
        }
    is PassHeader.NoPasses -> pluralStringResource(
        R.plurals.header_no_passes, searchedHours,
        satName, formats.coordinate(latitude), formats.coordinate(longitude), locator, searchedHours,
    )
    is PassHeader.Stationary ->
        if (position.isUp) {
            stringResource(
                R.string.header_stationary_up,
                satName, formats.integer(position.azimuthDeg), formats.integer(position.elevationDeg),
            )
        } else {
            stringResource(R.string.header_stationary_down, satName)
        }
    PassHeader.BadElements -> stringResource(R.string.header_bad_elements)
    PassHeader.UnknownSatellite -> stringResource(R.string.header_unknown_satellite)
    PassHeader.InvalidLocation -> stringResource(R.string.header_invalid_location)
    PassHeader.LocationNotSet -> stringResource(R.string.header_location_not_set)
    is PassHeader.Restored -> stringResource(R.string.header_restored, satName, formats.dateTime(computedAt))
    is PassHeader.Error -> stringResource(R.string.header_error, message)
}

/** The lines describing one pass, in the order they are shown. */
@Composable
fun PassSummary.lines(formats: DisplayFormats): List<String> = buildList {
    add(stringResource(R.string.pass_date, formats.date(start)))
    add(stringResource(R.string.pass_start_time, formats.time(start)))
    add(stringResource(R.string.pass_duration, formats.oneDecimal(duration.seconds / 60.0)))
    add(stringResource(R.string.pass_aos_azimuth, aosAzimuthDeg))
    add(stringResource(R.string.pass_max_elevation, formats.oneDecimal(maxElevationDeg)))
    add(stringResource(R.string.pass_los_azimuth, losAzimuthDeg))
}
