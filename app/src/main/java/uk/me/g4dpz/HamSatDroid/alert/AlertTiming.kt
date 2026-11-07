// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

/** The three moments in a pass the app reacts to. */
enum class AlertStage { LEAD, AOS, LOS }

/**
 * Works out when each stage of [alert] happens, given the lead time and the current time. Pure, so it is unit tested.
 *
 * The lead stage is `aos - lead`. A stage whose time is in the past is not scheduled again ([futureTriggers]), but
 * [stageNow] still reports the stage a pass is in right now, so an alert that fires late (Doze, or set while a pass is
 * already under way) shows the right notification immediately.
 */
class AlertTiming(private val alert: PassAlert, leadMinutes: Int, private val nowMillis: Long) {

    val leadMillis: Long = alert.aosMillis - leadMinutes * 60_000L
    val aosMillis: Long get() = alert.aosMillis
    val losMillis: Long get() = alert.losMillis

    /** The stages still ahead, with their trigger times, earliest first. */
    fun futureTriggers(): List<Pair<AlertStage, Long>> = buildList {
        if (leadMillis > nowMillis) add(AlertStage.LEAD to leadMillis)
        if (alert.aosMillis > nowMillis) add(AlertStage.AOS to alert.aosMillis)
        if (alert.losMillis > nowMillis) add(AlertStage.LOS to alert.losMillis)
    }

    /** The stage the pass is in at [nowMillis], or null once it has ended (so the alert can be cleared). */
    fun stageNow(): AlertStage? = when {
        nowMillis >= alert.losMillis -> null
        nowMillis >= alert.aosMillis -> AlertStage.AOS
        nowMillis >= leadMillis -> AlertStage.LEAD
        else -> AlertStage.LEAD // before the lead time nothing is shown, but if asked, LEAD is the first stage
    }

    /**
     * The stage whose trigger time has already passed while the pass has not yet ended, or null: nothing before the
     * lead time, LEAD from the lead time up to AOS, AOS from AOS up to LOS, nothing from LOS. Unlike [stageNow] it is
     * null before the lead time, so it says what should already be showing when an alert is set (or restored after
     * a reboot) part-way through its stages.
     */
    fun stageDue(): AlertStage? = when {
        nowMillis >= alert.losMillis -> null
        nowMillis >= alert.aosMillis -> AlertStage.AOS
        nowMillis >= leadMillis -> AlertStage.LEAD
        else -> null
    }

    /** Whether any stage is still to come, so scheduling is worthwhile. */
    fun hasFuture(): Boolean = alert.losMillis > nowMillis
}
