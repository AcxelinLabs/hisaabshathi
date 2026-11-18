package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * The Kotlin voice player must produce the same audio as the tested Python reference
 * (tools/tts/tiny_tts_ref.py) when given the same noise. Needs a downloaded voice and a
 * reference sample (tools/tts/make_reference.py); skipped when they are not present.
 * Point TINY_TTS_DIR at the folder holding m_telugu/ and ref/.
 */
class TinyTtsTest {
    private val root = File(System.getenv("TINY_TTS_DIR") ?: "/nonexistent")

    private fun floats(f: File): FloatArray {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(b.remaining()).also { b.get(it) }
    }

    @Test fun matchesTheReferenceAudio() {
        assumeTrue(File(root, "m_telugu/config.json").exists() && File(root, "ref/te.noise").exists())
        val noise = floats(File(root, "ref/te.noise")); val want = floats(File(root, "ref/te.wav.f32"))
        val text = File(root, "ref/te.txt").readText()
        TinyTts(File(root, "m_telugu"), threads = 2).use { tts ->
            assertEquals("ఈ వారం మీరు ఆరు వందల రూపాయలు సంపాదించారు.", tts.normalize(text, Lang.TE))
            var used = 0
            val got = tts.synthesize(text, Lang.TE, "female") { n -> noise.copyOfRange(used, used + n).also { used += n } }
            assertEquals(want.size, got.size)
            val err = sqrt(got.indices.sumOf { ((got[it] - want[it]) * (got[it] - want[it])).toDouble() } / got.size)
            val rms = sqrt(want.sumOf { (it * it).toDouble() } / want.size)
            println("TinyTts: ${got.size} samples, rms error ${"%.2e".format(err)} vs signal rms ${"%.3f".format(rms)}")
            assertTrue("audio differs from reference: rms error $err", err < rms * 0.01)
        }
    }
}
