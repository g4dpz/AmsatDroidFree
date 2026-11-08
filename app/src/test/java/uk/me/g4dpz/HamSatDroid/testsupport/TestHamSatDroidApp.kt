// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.testsupport

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp

/**
 * The application for Robolectric tests (`@Config(application = TestHamSatDroidApp::class)`).
 *
 * [HamSatDroidApp.onCreate] asks WorkManager for an instance to schedule the keps refresh. On a device WorkManager
 * is initialised by its `androidx.startup` content provider before the application is created; Robolectric creates
 * the application first, so the test instance is set up here before the real `onCreate` runs. Everything else is
 * the real application: the same settings, the same alert store (on Robolectric's temporary `filesDir`), the same
 * notification channel.
 */
class TestHamSatDroidApp : HamSatDroidApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
    }
}
