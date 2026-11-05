// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Hosts [NextPassWidget]. It starts the periodic refresh when the first widget is added and cancels it when the last
 * one is removed, so no work runs while no widget is placed.
 */
class NextPassWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget get() = NextPassWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler(context).start()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler(context).stop()
    }
}
