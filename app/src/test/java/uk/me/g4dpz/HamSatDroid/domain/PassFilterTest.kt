// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PassFilterTest {
    private fun pass(startSecond: Long, maxEl: Double, catnum: Int = 7530) = PassSummary(
        catnum = catnum, satName = "AO-07",
        start = Instant.ofEpochSecond(startSecond), end = Instant.ofEpochSecond(startSecond + 600),
        tca = Instant.ofEpochSecond(startSecond + 300), pole = PoleCrossing.NONE,
        aosAzimuthDeg = 10, losAzimuthDeg = 200, maxElevationDeg = maxEl,
    )

    private val low = pass(1_000, 4.5)
    private val mid = pass(2_000, 10.0)
    private val high = pass(3_000, 62.3)
    private val all = listOf(low, mid, high)

    @Test
    fun aMinimumOfZeroListsEveryPass() {
        assertEquals(all, PassFilter.visible(all, 0, null))
        assertEquals(all, PassFilter.visible(all, -5, null))
    }

    @Test
    fun hidesPassesBelowTheMinimum() {
        assertEquals(listOf(mid, high), PassFilter.visible(all, 10, null))
        assertEquals(listOf(high), PassFilter.visible(all, 20, null))
        assertEquals(emptyList<PassSummary>(), PassFilter.visible(all, 70, null))
    }

    @Test
    fun aPassAtExactlyTheMinimumIsShown() {
        assertTrue(mid in PassFilter.visible(all, 10, null)) // 10.0 against 10
        assertFalse(low in PassFilter.visible(all, 5, null)) // 4.5 against 5
    }

    @Test
    fun theSelectedPassStaysEvenWhenItIsBelowTheMinimum() {
        assertEquals(listOf(low, high), PassFilter.visible(all, 20, low.id))
    }

    @Test
    fun anUnknownSelectedIdChangesNothing() {
        assertEquals(listOf(high), PassFilter.visible(all, 20, "no such pass"))
    }

    @Test
    fun nothingIsRemovedFromTheInputList() {
        val input = all.toList()
        PassFilter.visible(input, 30, null)
        assertEquals(all, input)
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList<PassSummary>(), PassFilter.visible(emptyList(), 30, "x"))
    }

    @Test
    fun saysWhichPassesAreOnlyShownBecauseTheyAreSelected() {
        assertTrue(PassFilter.isBelowMinimum(low, 10))
        assertFalse(PassFilter.isBelowMinimum(mid, 10))
        assertFalse(PassFilter.isBelowMinimum(low, 0))
    }
}
