package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The minimum-wage data the app ships, and the open dataset it is built from. */
class WagePackTest {
    private val pack = Wages.parse(File("../app/src/main/assets/wage_pack_in.json").readText())

    @Test fun everyWorkStateHasItsOwnRates() {
        for (state in pack.regions - "Other") {
            val rates = Wages.forRegion(pack, state)
            assertTrue("$state uses central rates", rates.all { it.region == state })
        }
        assertTrue(pack.regions.size - 1 >= 10)
    }

    @Test fun everyRateSaysWhereItCameFrom() {
        for (r in pack.rates) {
            assertTrue("$r", r.source.isNotBlank() && r.effectiveFrom.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            assertTrue("$r", r.confidence in setOf("high", "medium", "low"))
            assertTrue("$r", r.dailyRupees in 300.0..1500.0)
            assertTrue("$r", r.validTo.isEmpty() || r.validTo > r.effectiveFrom)
        }
    }

    @Test fun matchesTheOpenDataset() {
        val rows = File("../../data/minimum-wages/minimum_wages_in.csv").readLines().drop(1).filter { it.isNotBlank() }
        assertEquals(rows.size, pack.rates.size)
    }

    @Test fun zonesUseTheLowestForTheCheck() {
        val ka = Wages.forRegion(pack, "Karnataka")
        val shown = Wages.lowestZone(ka)
        assertTrue(shown.all { it.zone.startsWith("Zone 3") })
        assertEquals(743.05, Wages.floor(shown)!!.dailyRupees, 0.001)
        assertEquals(899.09, Wages.topFor(ka, "Unskilled")!!.dailyRupees, 0.001)
        // A state without zones is shown as it is.
        assertEquals(Wages.forRegion(pack, "Odisha"), Wages.lowestZone(Wages.forRegion(pack, "Odisha")))
        assertEquals(478.0, Wages.floor(Wages.forRegion(pack, "Odisha"))!!.dailyRupees, 0.0)
    }
}
