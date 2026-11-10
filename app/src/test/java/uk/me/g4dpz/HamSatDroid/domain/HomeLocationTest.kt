// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLocationTest {
    @Test fun zeroZeroIsNotSet() = assertFalse(HomeLocation.isSet("0", "0"))
    @Test fun decimalZerosAreNotSet() = assertFalse(HomeLocation.isSet("0.0", "0.0"))
    @Test fun commaZerosAreNotSet() = assertFalse(HomeLocation.isSet("0,0", "0,0"))
    @Test fun negativeZeroIsNotSet() = assertFalse(HomeLocation.isSet("-0.0", "0"))
    @Test fun whitespaceAroundZerosIsNotSet() = assertFalse(HomeLocation.isSet(" 0 ", "0\n"))
    @Test fun blankIsNotSet() = assertFalse(HomeLocation.isSet("", ""))
    @Test fun nonNumericIsNotSet() = assertFalse(HomeLocation.isSet("north", "0"))
    @Test fun oneZeroOneNotIsSet() {
        assertTrue(HomeLocation.isSet("0", "-1.5"))
        assertTrue(HomeLocation.isSet("51.5", "0"))
    }
    @Test fun realLocationIsSet() = assertTrue(HomeLocation.isSet("51.5", "-0.1"))
    @Test fun commaDecimalsAreSet() = assertTrue(HomeLocation.isSet("51,5", "-0,1"))
}
