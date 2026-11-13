// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPolicyTest {

    @Test
    fun aFreshInstallSeesTheGuide() {
        assertTrue(SetupPolicy.shouldShowAtStart(locationSet = false, finished = false))
    }

    @Test
    fun aUserWhoSkippedTheGuideIsNotShownItAgain() {
        assertFalse(SetupPolicy.shouldShowAtStart(locationSet = false, finished = true))
    }

    @Test
    fun anExistingUserWithALocationNeverSeesIt() {
        // An upgrade: the location was set in an earlier version and the setting is new, so it reads false.
        assertFalse(SetupPolicy.shouldShowAtStart(locationSet = true, finished = false))
    }

    @Test
    fun aUserWhoFinishedTheGuideIsNotShownItAgain() {
        assertFalse(SetupPolicy.shouldShowAtStart(locationSet = true, finished = true))
    }
}
