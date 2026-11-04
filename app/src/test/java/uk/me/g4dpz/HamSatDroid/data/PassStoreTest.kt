package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatPassTime
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.util.Date

class PassStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = PassStore(FileStore(File(tmp.root, "data")))

    private fun pass(start: Long, pole: String? = "north") =
        SatPassTime(Date(start), Date(start + 600_000), Date(start + 300_000), pole, 45, 310, 62.5)

    private fun sample() = StoredPasses(7530, "AO-07", 1_800_000_000_000L, 24, listOf(pass(1_800_000_100_000L), pass(1_800_006_000_000L, null)))

    private fun assertSame(a: SatPassTime, b: SatPassTime) {
        assertEquals(a.startTime, b.startTime)
        assertEquals(a.endTime, b.endTime)
        assertEquals(a.tca, b.tca)
        assertEquals(a.polePassed, b.polePassed)
        assertEquals(a.aosAzimuth, b.aosAzimuth)
        assertEquals(a.losAzimuth, b.losAzimuth)
        assertEquals(a.maxEl, b.maxEl, 0.0)
    }

    @Test
    fun roundTripKeepsEverything() {
        val s = store()
        val original = sample()
        s.save(original)
        val loaded = s.load()!!
        assertEquals(original.catnum, loaded.catnum)
        assertEquals(original.satName, loaded.satName)
        assertEquals(original.computedAtMillis, loaded.computedAtMillis)
        assertEquals(original.hoursAhead, loaded.hoursAhead)
        assertEquals(2, loaded.passes.size)
        original.passes.zip(loaded.passes).forEach { (a, b) -> assertSame(a, b) }
        assertNull(loaded.passes[1].polePassed)
    }

    @Test
    fun realPredictedPassesRoundTrip() {
        val tle = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }.first()
        val passes = PassPredictor(tle, GroundStationPosition(52.0, -1.0, 0.0)).getPasses(Date(1264723200000L), 24, true)
        assertTrue(passes.isNotEmpty())
        val s = store()
        s.save(StoredPasses(tle.catnum, tle.name, 1L, 24, passes))
        val loaded = s.load()!!.passes
        assertEquals(passes.size, loaded.size)
        passes.zip(loaded).forEach { (a, b) -> assertSame(a, b) }
    }

    @Test
    fun anEmptyListIsValid() {
        val s = store()
        s.save(StoredPasses(1, "X", 2L, 0, emptyList()))
        assertEquals(0, s.load()!!.passes.size)
    }

    @Test
    fun missingFileGivesNull() {
        assertNull(store().load())
    }

    @Test
    fun corruptOrWrongSchemaFilesGiveNull() {
        val files = FileStore(File(tmp.root, "data"))
        for (text in listOf("", "garbage", "[]", "{}", """{"schema":99,"catnum":1,"satName":"X","computedAtMillis":1,"hoursAhead":0,"passes":[]}""")) {
            files.writeTextAtomic(PassStore.FILE, text)
            assertNull("for: $text", PassStore(files).load())
        }
    }

    @Test
    fun badPassRecordsAreDroppedNotFatal() {
        val files = FileStore(File(tmp.root, "data"))
        files.writeTextAtomic(
            PassStore.FILE,
            """{"schema":1,"catnum":1,"satName":"X","computedAtMillis":1,"hoursAhead":0,"passes":[
               {"start":1000,"end":2000,"tca":1500,"pole":"none","aos":1,"los":2,"maxEl":3.5},
               {"start":1000,"end":2000},
               {"start":5000,"end":4000,"tca":4500,"pole":"none","aos":1,"los":2,"maxEl":3.5}]}"""
        )
        val loaded = PassStore(files).load()!!
        assertEquals(1, loaded.passes.size)
        assertEquals("none", loaded.passes[0].polePassed)
    }

    @Test
    fun clearRemovesTheFile() {
        val s = store()
        s.save(sample())
        s.clear()
        assertNull(s.load())
    }

    @Test
    fun overwriteReplacesThePreviousList() {
        val s = store()
        s.save(sample())
        s.save(StoredPasses(9, "NEW", 3L, 6, listOf(pass(1_900_000_000_000L))))
        val loaded = s.load()!!
        assertEquals("NEW", loaded.satName)
        assertEquals(1, loaded.passes.size)
    }
}
