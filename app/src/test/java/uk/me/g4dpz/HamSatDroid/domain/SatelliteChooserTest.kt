// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.ui.SatelliteItem

class SatelliteChooserTest {
    private val ao7 = SatelliteItem(7530, "AO-07")
    private val iss = SatelliteItem(25544, "ISS (ZARYA)")
    private val so50 = SatelliteItem(27607, "SAUDISAT 1C (SO-50)")
    private val rs44 = SatelliteItem(44909, "RS-44 (DOSAAF-85)")
    private val all = listOf(ao7, iss, so50, rs44)

    @Test fun blankQueryShowsEverythingInOrder() = assertEquals(all, SatelliteChooser.shown(all, emptySet(), "  "))
    @Test fun searchMatchesNameIgnoringCase() = assertEquals(listOf(so50), SatelliteChooser.shown(all, emptySet(), "so-50"))
    @Test fun searchMatchesCatalogueNumber() = assertEquals(listOf(rs44), SatelliteChooser.shown(all, emptySet(), "44909"))
    @Test fun searchWithNoMatchShowsNothing() = assertEquals(emptyList<SatelliteItem>(), SatelliteChooser.shown(all, emptySet(), "zzz"))
    @Test fun initiallyChosenComeFirstEachGroupInOrder() =
        assertEquals(listOf(iss, rs44, ao7, so50), SatelliteChooser.shown(all, setOf(44909, 25544), ""))
    @Test fun tickingDoesNotReorderBecauseOnlyTheInitialSetCounts() {
        val before = SatelliteChooser.shown(all, setOf(25544), "")
        val draft = SatelliteChooser.toggle(setOf(25544), 7530)
        assertEquals(setOf(25544, 7530), draft)
        assertEquals(before, SatelliteChooser.shown(all, setOf(25544), ""))
    }
    @Test fun aRepeatedCatalogueNumberIsListedOnce() =
        assertEquals(listOf(ao7, iss), SatelliteChooser.shown(listOf(ao7, iss, ao7), emptySet(), ""))
<<<<<<< HEAD
    @Test fun beforeAListIsSavedTheSelectedSatelliteIsTickedToo() =
        assertEquals(setOf(25544, 44909), SatelliteChooser.opening(setOf(25544), chosen = false, selectedCatnum = 44909))
    @Test fun beforeAListIsSavedWithNoSelectionItIsJustTheDefault() =
        assertEquals(setOf(25544), SatelliteChooser.opening(setOf(25544), chosen = false, selectedCatnum = null))
    @Test fun aSavedListIsShownExactlyEvenWhenTheSelectedSatelliteIsNotInIt() =
        assertEquals(setOf(7530), SatelliteChooser.opening(setOf(7530), chosen = true, selectedCatnum = 44909))
    @Test fun aSavedEmptyListStaysEmpty() =
        assertEquals(emptySet<Int>(), SatelliteChooser.opening(emptySet(), chosen = true, selectedCatnum = 44909))
=======
    @Test fun aSecondNameForTheSameNumberIsStillSearchable() {
        val twin = SatelliteItem(7530, "OSCAR 7")
        assertEquals(listOf(twin), SatelliteChooser.shown(listOf(ao7, twin), emptySet(), "OSCAR"))
    }
>>>>>>> feat/choose-satellites-chooser
    @Test fun toggleUnticks() = assertEquals(setOf(1), SatelliteChooser.toggle(setOf(1, 2), 2))
    @Test fun selectAllAddsOnlyWhatIsShown() =
        assertEquals(setOf(99, 7530, 27607), SatelliteChooser.selectAll(setOf(99), listOf(ao7, so50)))
    @Test fun clearAllRemovesOnlyWhatIsShown() =
        assertEquals(setOf(44909), SatelliteChooser.clearAll(setOf(7530, 25544, 44909), listOf(ao7, iss), emptyList()))
    @Test fun clearAllAlsoRemovesShownAbsentNumbers() =
        assertEquals(setOf(7530), SatelliteChooser.clearAll(setOf(7530, 99999), emptyList(), listOf(99999)))
    @Test fun absentNumbersAreTheDraftMinusTheElements() =
        assertEquals(listOf(5, 99999), SatelliteChooser.notInElements(all, setOf(99999, 7530, 5), ""))
    @Test fun absentNumbersFollowTheSearch() =
        assertEquals(listOf(99999), SatelliteChooser.notInElements(all, setOf(99999, 5), "999"))
    @Test fun nothingAbsentWhenEveryChosenIsPresent() =
        assertTrue(SatelliteChooser.notInElements(all, setOf(7530), "").isEmpty())
}
