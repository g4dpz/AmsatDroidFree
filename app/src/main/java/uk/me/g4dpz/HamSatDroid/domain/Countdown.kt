package uk.me.g4dpz.HamSatDroid.domain

import java.time.Duration
import java.util.Locale

/** Formats a time span as a clock-style countdown: "4:05" under an hour, "1:04:05" from an hour. */
object Countdown {
    fun format(span: Duration): String {
        val total = span.seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
