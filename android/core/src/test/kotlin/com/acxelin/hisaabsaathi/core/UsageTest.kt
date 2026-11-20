package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageTest {
    @Test fun weeksAndCounts() {
        assertEquals("2026-W47", Usage.week(LocalDate.of(2026, 11, 20)))
        assertEquals("2026-W53", Usage.week(LocalDate.of(2026, 12, 31))) // 2026 has 53 ISO weeks
        assertEquals("2027-W01", Usage.week(LocalDate.of(2027, 1, 4)))
        var s = Usage.add(emptyMap(), "2026-W47", Usage.Event.SAVED)
        s = Usage.add(s, "2026-W47", Usage.Event.SAVED, 2)
        s = Usage.add(s, "2026-W47", "a sentence the worker said") // never kept
        assertEquals(mapOf("2026-W47" to mapOf(Usage.Event.SAVED to 3)), Usage.read(Usage.write(s)))
        assertTrue(Usage.finished(s, LocalDate.of(2026, 11, 20), 2, "x").isEmpty())
        assertEquals(1, Usage.finished(s, LocalDate.of(2026, 11, 23), 2, "x").size)
        // At most KEEP_WEEKS weeks wait on the phone.
        var many = emptyMap<String, Map<String, Int>>()
        for (w in 1..20) many = Usage.add(many, "2026-W%02d".format(w), Usage.Event.DAYS)
        assertEquals(Usage.KEEP_WEEKS, many.size)
        assertEquals("2026-W20", many.keys.max())
    }

    @Test fun countsTravelInTheBatch() {
        val w = WeekCounts("2026-W46", mapOf(Usage.Event.SAVED to 4), 2, "0.9.6")
        val zip = Improve.batch(emptyList(), { null }, "0.9.6", 1, listOf(w))
        assertEquals(listOf(w), Improve.readCounts(zip))
        assertTrue(Improve.readBatch(zip).first.isEmpty())
    }
}
