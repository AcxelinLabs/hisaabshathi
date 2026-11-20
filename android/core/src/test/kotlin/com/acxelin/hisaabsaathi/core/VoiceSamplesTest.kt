package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceSamplesTest {
    @Test fun wavHeaderDescribes16kMono16Bit() {
        val wav = VoiceSamples.wav(ShortArray(16_000) { (it % 100).toShort() })
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4)); assertEquals("data", String(wav, 36, 4))
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16_000, b.getInt(24)) // sample rate
        assertEquals(16, b.getShort(34).toInt()) // bits
        assertEquals(32_000, b.getInt(40)) // data bytes
        assertEquals(44 + 32_000, wav.size)
    }

    @Test fun floatsBecome16BitAndClip() {
        assertEquals(listOf<Short>(0, 16383, 32767, -32767), VoiceSamples.pcm16(floatArrayOf(0f, 0.5f, 1.5f, -1f)).toList())
    }

    @Test fun idsSortByNumber() {
        assertEquals(1000, VoiceSamples.number("hi_1000")); assertEquals(17, VoiceSamples.number("hi_017"))
    }
}

class RecordingQualityTest {
    private fun tone(seconds: Double, amplitude: Double) =
        ShortArray((16_000 * seconds).toInt()) { (amplitude * 32767 * kotlin.math.sin(it * 0.07)).toInt().toShort() }

    @Test fun normalSpeechLevelIsGood() = assertEquals(VoiceSamples.Quality.GOOD, VoiceSamples.quality(tone(1.5, 0.2)))
    @Test fun nothingHeard() = assertEquals(VoiceSamples.Quality.SILENT, VoiceSamples.quality(tone(1.5, 0.002)))
    @Test fun tooQuiet() = assertEquals(VoiceSamples.Quality.QUIET, VoiceSamples.quality(tone(1.5, 0.012)))
    @Test fun tooShort() = assertEquals(VoiceSamples.Quality.SHORT, VoiceSamples.quality(tone(0.3, 0.2)))

    @Test fun distortedIsLoud() {
        val pcm = tone(1.5, 1.6).map { it.toInt().coerceIn(-32768, 32767).toShort() }.toShortArray() // clipped sine
        val clipped = ShortArray(pcm.size) { (if (it % 200 == 0) 32767 else pcm[it].toInt() / 2).toShort() }
        assertEquals(VoiceSamples.Quality.LOUD, VoiceSamples.quality(clipped))
    }

    @Test fun briefWordInSilenceIsQuiet() {
        val pcm = ShortArray(32_000) // 2 s of silence with 0.1 s of speech
        for (i in 8_000 until 9_600) pcm[i] = (6000 * kotlin.math.sin(i * 0.07)).toInt().toShort()
        assertEquals(VoiceSamples.Quality.QUIET, VoiceSamples.quality(pcm))
    }
}

