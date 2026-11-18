package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionsTest {
    private val understood = listOf(DraftEntry(EntryType.WORK, 500, hours = 6.0, pieces = 120), DraftEntry(EntryType.ADVANCE, 50_000))
    private val fixed = listOf(DraftEntry(EntryType.WORK, 60_000, hours = 6.0, pieces = 120, ratePaise = 500), DraftEntry(EntryType.ADVANCE, 50_000))

    @Test fun keptUnchangedIsNotACorrection() {
        assertNull(Corrections.corrected(1, Lang.KN, "voice", "heard", understood, understood))
    }

    @Test fun editedEntryIsRecordedWithBothVersions() {
        val c = Corrections.corrected(1, Lang.KN, "voice", "ನೂರಿಪ್ಪತ್ತು ಪೀಸ್ ಪೀಸ್ದ ಐದು ರೂಪಾಯಿ", understood, fixed)!!
        assertEquals("WORK 5 h6 p120; ADVANCE 500", c.understood)
        assertEquals("WORK 600 h6 p120 r5; ADVANCE 500", c.kept)
        assertEquals("corrected", c.outcome)
        assertEquals("kn", c.lang)
    }

    @Test fun saidWrongHasNoKeptVersion() {
        val c = Corrections.rejected(1, Lang.TE, "voice", "heard", understood)!!
        assertNull(c.kept)
        assertEquals("rejected", c.outcome)
    }

    @Test fun scannedBillsAreNotRecorded() {
        assertNull(Corrections.corrected(1, Lang.HI, "scan", "bill", understood, fixed))
        assertNull(Corrections.rejected(1, Lang.HI, "scan", "bill", understood))
    }

    @Test fun sharedFileIsOneJsonObjectPerLineOldestFirst() {
        val a = Corrections.rejected(2, Lang.TA, "voice", "இரண்டு", understood)!!
        val b = Corrections.corrected(1, Lang.TA, "typed", "x", understood, fixed)!!
        val lines = Corrections.toJsonLines(listOf(a, b)).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("{\"createdAtMs\":1,"))
        assertTrue(lines[1], "\"kept\":null" in lines[1] && "இரண்டு" in lines[1])
    }
}
