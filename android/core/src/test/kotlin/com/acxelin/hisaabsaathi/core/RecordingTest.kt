package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clips are sent without their silences, and never with the worker's name or numbers in them. */
class RecordingTest {
    private val rate = VoiceSamples.SAMPLE_RATE
    private fun quiet(ms: Int) = ShortArray(rate * ms / 1000)
    private fun voice(ms: Int) = ShortArray(rate * ms / 1000) { i -> (8000 * kotlin.math.sin(i * 0.12)).toInt().toShort() }

    @Test fun silencesAreCutOut() {
        // 1 s quiet, 1 s speech, 2 s pause, 0.5 s speech, 3 s quiet at the end (the stop).
        val clip = quiet(1000) + voice(1000) + quiet(2000) + voice(500) + quiet(3000)
        val trimmed = VoiceSamples.trimSilence(clip)
        val seconds = trimmed.size.toDouble() / rate
        // 1.5 s of speech, a 0.3 s pause, 0.15 s either side.
        assertTrue("was $seconds s", seconds in 1.9..2.2)
    }

    @Test fun anAllSilentClipIsNothing() {
        assertEquals(0, VoiceSamples.trimSilence(quiet(3000)).size)
    }

    @Test fun nameAndNumbersAreNeverKept() {
        assertTrue(Improve.sensitive("मेरा नंबर 98765 43210 है"))
        assertTrue(Improve.sensitive("mail me at ramesh@gmail.com"))
        assertTrue(Improve.sensitive("रमेश को 500 दिए", names = listOf("रमेश")))
        assertTrue(Improve.sensitive("Ramesh Behera ne 300 diye", names = listOf("Ramesh Behera")))
        assertFalse(Improve.sensitive("आज 600 रुपये कमाए", names = listOf("रमेश")))
    }
}
