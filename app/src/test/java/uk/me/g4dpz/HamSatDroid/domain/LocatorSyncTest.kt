// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync.Field
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync.Location
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync.Result

class LocatorSyncTest {

    private val london = Location("51.52083", "-0.125", "IO91wm")

    private fun accepted(r: Result) = (r as Result.Accepted).location
    private fun rejected(r: Result) = r as Result.Rejected

    // ---- latitude ----

    @Test
    fun aValidLatitudeUpdatesTheLocator() {
        val loc = accepted(LocatorSync.editLatitude("40.7", london))
        assertEquals("40.7", loc.latitude)
        assertEquals("-0.125", loc.longitude) // untouched
        assertEquals("IN90", loc.locator.take(4)) // 40.7N, 0.125W: field I N, squares 9 0
    }

    @Test
    fun latitudeBoundsAreInclusive() {
        for (ok in listOf("90", "-90", "0", "89.99999", "-89.99999")) {
            assertTrue("latitude $ok", LocatorSync.editLatitude(ok, london) is Result.Accepted)
        }
        for (bad in listOf("90.00001", "-90.00001", "91", "-180", "1000")) {
            assertTrue("latitude $bad", LocatorSync.editLatitude(bad, london) is Result.Rejected)
        }
    }

    @Test
    fun anInvalidLatitudeIsResetToZeroAndTheRestIsKept() {
        for (bad in listOf("abc", "", "  ", "NaN", "Infinity", "1e999", "--5", "5..5")) {
            val r = rejected(LocatorSync.editLatitude(bad, london))
            assertEquals("for '$bad'", Field.LATITUDE, r.field)
            assertEquals("0", r.location.latitude)
            assertEquals(london.longitude, r.location.longitude)
            assertEquals(london.locator, r.location.locator)
        }
    }

    @Test
    fun aCommaIsAcceptedAsTheDecimalSeparatorAndStoredWithADot() {
        assertEquals("51.5", accepted(LocatorSync.editLatitude("51,5", london)).latitude)
        assertEquals("-0.1", accepted(LocatorSync.editLongitude("-0,1", london)).longitude)
    }

    // ---- longitude ----

    @Test
    fun aValidLongitudeUpdatesTheLocator() {
        val loc = accepted(LocatorSync.editLongitude("-74", london))
        assertEquals("-74", loc.longitude)
        assertEquals("51.52083", loc.latitude)
        assertEquals("FO", loc.locator.take(2)) // 74W is longitude band F, 51.5N is latitude band O
    }

    @Test
    fun longitudeBoundsAreInclusive() {
        for (ok in listOf("180", "-180", "0")) assertTrue(LocatorSync.editLongitude(ok, london) is Result.Accepted)
        for (bad in listOf("180.00001", "-181", "360")) assertTrue(LocatorSync.editLongitude(bad, london) is Result.Rejected)
    }

    @Test
    fun anInvalidLongitudeIsResetToZero() {
        val r = rejected(LocatorSync.editLongitude("east", london))
        assertEquals(Field.LONGITUDE, r.field)
        assertEquals("0", r.location.longitude)
        assertEquals(london.latitude, r.location.latitude)
        assertEquals(london.locator, r.location.locator)
    }

    // ---- locator ----

    @Test
    fun aValidLocatorGivesTheCentreOfItsSquare() {
        val loc = accepted(LocatorSync.editLocator("IO91wm", Location("0", "0", "JJ00aa")))
        assertEquals("IO91wm", loc.locator)
        assertEquals("51.52083", loc.latitude)
        assertEquals("-0.125", loc.longitude)
    }

    @Test
    fun aLocatorIsAcceptedInAnyCaseAndStoredInTheStandardCase() {
        for (typed in listOf("io91wm", "IO91WM", "iO91Wm", "  IO91wm  ")) {
            assertEquals("for '$typed'", "IO91wm", accepted(LocatorSync.editLocator(typed, london)).locator)
        }
    }

    @Test
    fun aLocatorWithTheExtraDigitPairIsAcceptedAndShortened() {
        assertEquals("IO91wm", accepted(LocatorSync.editLocator("IO91wm47", london)).locator)
    }

    @Test
    fun locatorRangesAreEnforced() {
        // first pair A..R, last pair A..X
        for (ok in listOf("AA00aa", "RR99xx", "JJ00AA", "ra09xa")) {
            assertTrue("locator $ok", LocatorSync.editLocator(ok, london) is Result.Accepted)
        }
        for (bad in listOf("SA00aa", "AS00aa", "AA00ya", "AA00ay", "AA0aaa", "AAAAaa", "IO91", "IO91w", "IO91wmx", "", "IO 91wm", "xxIO91wmxx")) {
            val r = LocatorSync.editLocator(bad, london)
            assertTrue("locator '$bad' should be rejected", r is Result.Rejected)
        }
    }

    @Test
    fun anInvalidLocatorIsResetToTheDefaultAndTheRestIsKept() {
        val r = rejected(LocatorSync.editLocator("nonsense", london))
        assertEquals(Field.LOCATOR, r.field)
        assertEquals("JJ00aa", r.location.locator)
        assertEquals(london.latitude, r.location.latitude)
        assertEquals(london.longitude, r.location.longitude)
    }

    @Test
    fun theSouthWestAndNorthEastCornersWork() {
        val sw = accepted(LocatorSync.editLocator("AA00aa", london))
        assertTrue(sw.latitude.toDouble() < -89.9 && sw.longitude.toDouble() < -179.9)
        val ne = accepted(LocatorSync.editLocator("RR99xx", london))
        assertTrue(ne.latitude.toDouble() > 89.9 && ne.longitude.toDouble() > 179.9)
    }

    // ---- coordinates from the device ----

    @Test
    fun coordinatesFromTheDeviceGiveALocator() {
        val loc = LocatorSync.fromCoordinates(51.5208333, -0.125)
        assertEquals("51.52083", loc.latitude)
        assertEquals("-0.125", loc.longitude)
        assertEquals("IO91wm", loc.locator)
    }

    @Test
    fun numbersAreNeverScientificOrLocaleDependent() {
        val loc = LocatorSync.fromCoordinates(0.000001, -0.00001)
        assertEquals("0", loc.latitude)
        assertEquals("-0.00001", loc.longitude)
        val tiny = accepted(LocatorSync.editLatitude("1e-3", london))
        assertEquals("0.001", tiny.latitude)
    }

    @Test
    fun theWholeCycleIsStable() {
        // locator -> coordinates -> locator gives the same locator
        val first = accepted(LocatorSync.editLocator("JN58td", london))
        val again = LocatorSync.fromCoordinates(first.latitude.toDouble(), first.longitude.toDouble())
        assertEquals("JN58td", again.locator)
        // and editing latitude to its own value changes nothing
        val same = accepted(LocatorSync.editLatitude(first.latitude, first))
        assertEquals(first, same)
    }

    // ---- checking a locator while it is being typed ----

    @Test
    fun everyPrefixOfAValidLocatorIsFineWhileItIsTyped() {
        for (locator in listOf("IO82xl", "io82xl", "IO82XL", "IO82xl44", "AA00aa", "RR99xx", "JJ00aa")) {
            for (n in 0..locator.length) {
                assertNull("'${locator.take(n)}'", LocatorSync.typingProblem(locator.take(n)))
            }
        }
    }

    @Test
    fun aZeroTypedForTheLetterOIsCaughtAtTheSecondCharacter() {
        val problem = LocatorSync.typingProblem("I082xl")!!
        assertEquals(2, problem.position)
        assertEquals(LocatorSync.Expected.LETTER_A_TO_R, problem.expected)
        assertEquals('0', problem.found)
        // and as soon as it is typed, before the rest
        assertEquals(2, LocatorSync.typingProblem("I0")!!.position)
    }

    @Test
    fun eachPositionSaysWhatItNeeds() {
        fun at(text: String) = LocatorSync.typingProblem(text)!!
        assertEquals(LocatorSync.TypingProblem(1, LocatorSync.Expected.LETTER_A_TO_R, 'S'), at("S"))
        assertEquals(LocatorSync.TypingProblem(1, LocatorSync.Expected.LETTER_A_TO_R, '1'), at("1"))
        assertEquals(LocatorSync.TypingProblem(3, LocatorSync.Expected.DIGIT, 'O'), at("IOO"))
        assertEquals(LocatorSync.TypingProblem(4, LocatorSync.Expected.DIGIT, 'x'), at("IO8x"))
        assertEquals(LocatorSync.TypingProblem(5, LocatorSync.Expected.LETTER_A_TO_X, '1'), at("IO821"))
        assertEquals(LocatorSync.TypingProblem(6, LocatorSync.Expected.LETTER_A_TO_X, 'y'), at("IO82xy"))
        assertEquals(LocatorSync.TypingProblem(7, LocatorSync.Expected.DIGIT, 'a'), at("IO82xla"))
        assertEquals(LocatorSync.TypingProblem(8, LocatorSync.Expected.DIGIT, '-'), at("IO82xl4-"))
        assertEquals(LocatorSync.TypingProblem(9, LocatorSync.Expected.NOTHING, '1'), at("IO82xl441"))
    }

    @Test
    fun spacesAroundTheTextAreIgnoredButASpaceInsideIsAProblem() {
        assertNull(LocatorSync.typingProblem("  IO82xl  "))
        assertNull(LocatorSync.typingProblem(" "))
        val inside = LocatorSync.typingProblem("IO 82")!!
        assertEquals(3, inside.position)
        assertEquals(' ', inside.found)
    }

    @Test
    fun textThatIsAcceptedWhenFinishedHasNoTypingProblemAndTextWithOneIsRejected() {
        for (text in listOf("IO82xl", "io82xl", " IO82xl44 ", "AA00aa", "RR99xx")) {
            assertNull(text, LocatorSync.typingProblem(text))
            assertTrue(text, LocatorSync.editLocator(text, london) is Result.Accepted)
        }
        for (text in listOf("I082xl", "IO82xy", "SS00aa", "IO82xl441", "IO 82xl")) {
            assertNotNull(text, LocatorSync.typingProblem(text))
            assertTrue(text, LocatorSync.editLocator(text, london) is Result.Rejected)
        }
    }
}
