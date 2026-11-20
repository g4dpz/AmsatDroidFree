// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CatnumListTest {
    @Test fun issIs25544() = assertEquals(25544, CatnumList.ISS)
    @Test fun parsesAndTrims() = assertEquals(setOf(25544, 7530), CatnumList.parse(" 25544 , 7530 "))
    @Test fun emptyTextIsAnEmptySet() = assertEquals(emptySet<Int>(), CatnumList.parse(""))
    @Test fun dropsJunkZeroNegativeAndRepeats() =
        assertEquals(setOf(7530, 44909), CatnumList.parse("7530,abc,,0,-5,7530,4.5,44909"))
    @Test fun keepsNumbersNoSourceHas() = assertEquals(setOf(99999999), CatnumList.parse("99999999"))
    @Test fun formatsSortedWithoutSpaces() = assertEquals("7530,25544", CatnumList.format(setOf(25544, 7530)))
    @Test fun emptySetFormatsAsEmptyText() = assertEquals("", CatnumList.format(emptySet()))
    @Test fun formatThenParseGivesTheSameSet() {
        val set = setOf(44909, 25544, 7530)
        assertEquals(set, CatnumList.parse(CatnumList.format(set)))
    }
}
