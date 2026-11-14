// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.OrbitalElements
import uk.me.g4dpz.satellite.TLE
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ContactPlannerCalculatorTest {
    private val uk = PlannerStation(52.4375, -2.0417)
    private val kansasCity = PlannerStation(39.5, -95.0)
    private val start = Instant.parse("2026-11-14T14:55:24Z")

    private fun tle(resource: String): TLE =
        checkNotNull(javaClass.getResourceAsStream("/$resource")) { "missing test resource $resource" }
            .use { TLE.importSat(it) }.first()

    private val qo100 = TLE.fromElements(
        OrbitalElements(
            name = "ES'HAIL 2", catnum = 43700, epochYear = 2026, epochDay = 308.11433122,
            meanMotion = 1.00272722, eccentricity = 0.00016, inclination = 0.0116, raan = 244.1212,
            argPerigee = 23.3346, meanAnomaly = 202.8356, elementSetNo = 999, revAtEpoch = 2908,
        )
    )

    @Test
    fun `all satellites in parallel give the same results as one at a time`() = runBlocking {
        val tles = listOf(tle("rs44_20261114.tle"), tle("iss_20261114.tle"), tle("ao7_20261114.tle"), qo100)
        val parallel = ContactPlannerCalculator().calculate(tles, uk, kansasCity, 0.0, start, 48).toList().sortedBy { it.catnum }
        val oneAtATime = tles.map { ContactPlanner.windows(it, uk, kansasCity, 0.0, start, 48) }.sortedBy { it.catnum }
        assertEquals(tles.size, parallel.size)
        assertEquals(oneAtATime, parallel)
    }

    @Test
    fun `every satellite gets an answer, also with a single thread`() = runBlocking {
        val tles = List(6) { tle("rs44_20261114.tle") }
        val single = ContactPlannerCalculator(Dispatchers.Default.limitedParallelism(1))
        assertEquals(6, single.calculate(tles, uk, kansasCity, 0.0, start, 24).toList().size)
    }

    @Test
    fun `cancelling while a satellite is being worked on stops it at the next checkpoint`() = runBlocking {
        val calls = AtomicInteger()
        lateinit var job: Job
        val calculator = ContactPlannerCalculator(Dispatchers.Default) {
            // the third checkpoint is reached part-way through the search; cancel the collector from there
            if (calls.incrementAndGet() == 3) job.cancel()
        }
        val results = ArrayList<PlannerItem>()
        job = launch(start = CoroutineStart.LAZY) {
            calculator.calculate(listOf(tle("rs44_20261114.tle")), uk, kansasCity, 0.0, start, 72).collect { results += it }
        }
        job.start()
        job.join()
        delay(300) // time for any work that did not stop to show itself
        assertEquals("the work stopped at the checkpoint after the cancel", 3, calls.get())
        assertTrue("no result is emitted after cancelling: $results", results.isEmpty())
    }

    @Test
    fun `satellites not yet started never start once the collector is cancelled`() = runBlocking {
        val calls = AtomicInteger()
        val single = Dispatchers.Default.limitedParallelism(1)
        val tles = List(20) { tle("rs44_20261114.tle") }
        ContactPlannerCalculator(single) { calls.incrementAndGet() }.calculate(tles, uk, kansasCity, 0.0, start, 72).toList()
        val everything = calls.get()
        calls.set(0)
        // take the first result and cancel: with one thread the other satellites are still waiting to start
        ContactPlannerCalculator(single) { calls.incrementAndGet() }.calculate(tles, uk, kansasCity, 0.0, start, 72).first()
        delay(300) // time for any work that did not stop to show itself
        assertTrue("a full run makes $everything checkpoint calls; after the first result only ${calls.get()} were made", calls.get() < everything / 2)
    }
}
