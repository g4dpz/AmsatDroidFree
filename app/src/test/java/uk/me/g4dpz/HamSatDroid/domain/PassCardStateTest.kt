// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PassCardStateTest {
    private val aos = Instant.parse("2026-11-24T22:04:00Z")
    private val los = Instant.parse("2026-11-24T22:13:00Z")
    private val pass = PassSummary(1, "AO-91", aos, los, aos.plusSeconds(270), PoleCrossing.NONE, 200, 20, 25.0)

    private fun at(text: String) = PassCardStates.of(pass, Instant.parse(text))

    @Test fun greyMoreThanFiveMinutesBefore() = assertEquals(PassCardState.UPCOMING, at("2026-11-24T21:58:59Z"))
    @Test fun orangeFromFiveMinutesBefore() = assertEquals(PassCardState.SOON, at("2026-11-24T21:59:00Z"))
    @Test fun orangeJustBeforeAos() = assertEquals(PassCardState.SOON, at("2026-11-24T22:03:59Z"))
    @Test fun greenAtAos() = assertEquals(PassCardState.PASSING, at("2026-11-24T22:04:00Z"))
    @Test fun greenJustBeforeLos() = assertEquals(PassCardState.PASSING, at("2026-11-24T22:12:59Z"))
    @Test fun overAtLos() = assertEquals(PassCardState.OVER, at("2026-11-24T22:13:00Z"))
}
