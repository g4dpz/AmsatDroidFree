package uk.me.g4dpz.HamSatDroid.domain

import java.util.Locale

/**
 * Parsing and display of radio frequencies. The user enters MHz (for example "435.120"); it is stored as whole hertz.
 * Parsing is fixed-locale: a dot or a comma is accepted as the decimal separator and no grouping is assumed, so a
 * comma-decimal device still works and a value never changes meaning between locales. Display uses the device locale
 * for grouping only.
 */
object FrequencyText {

    /** Parses a frequency in MHz (dot or comma decimal) to whole hertz, or null if it is not a positive number. */
    fun megahertzToHz(text: String): Long? {
        val mhz = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        return Math.round(mhz * 1_000_000.0)
    }

    /** The frequency in MHz as plain text with up to six decimals (1 Hz resolution), fixed-locale, for editing. */
    fun hzToMegahertzText(hz: Long): String =
        String.format(Locale.ROOT, "%.6f", hz / 1_000_000.0).trimEnd('0').trimEnd('.')

    /** The frequency in Hz grouped for display in the device locale, for example "435,120,000". */
    fun hzGrouped(hz: Long): String = java.text.NumberFormat.getIntegerInstance().format(hz)
}
