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
 * The Indian English voice (Indic Tiny TTS English) through the app's own player: the app's
 * English answers come out as speech of a sensible length, with amounts said as words. Needs the
 * downloaded voice; point EN_TTS_DIR at the folder holding config.json and onnx/ (skipped otherwise).
 * Writes the clips to build/en-voice/ so a person (or a speech recogniser) can check them.
 */
class EnglishVoiceTest {
    private val dir = File(System.getenv("EN_TTS_DIR") ?: "/nonexistent")

    @Test fun speaksTheAppsEnglishAnswers() {
        assumeTrue(File(dir, "config.json").exists())
        val s = Strings(Lang.EN)
        val lines = listOf(
            s.welcome,
            s.sayYesNoPrompt,
            "Work, 120 pieces at 5 rupees, 600 rupees. Advance, 300 rupees.",
            "This week you earned 600 rupees.",
            "1,500 rupees is still due to you.",
        )
        val out = File("build/en-voice").apply { mkdirs() }
        TinyTts(dir, threads = 2).use { tts ->
            assertEquals("this week you earned six hundred rupees.", tts.normalize(lines[3], Lang.EN).lowercase())
            for ((i, line) in lines.withIndex()) for (voice in listOf("female", "male")) {
                val pcm = tts.synthesize(line, Lang.EN, voice)
                val seconds = pcm.size / tts.sampleRate.toDouble()
                val rms = sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size)
                val words = tts.normalize(line, Lang.EN).split(' ').size
                println("EN $voice ${"%.1f".format(seconds)} s, rms ${"%.3f".format(rms)}: ${tts.normalize(line, Lang.EN)}")
                assertTrue("too short or too long for $words words: $seconds s", seconds in words * 0.2..words * 0.9)
                assertTrue("silent", rms > 0.01)
                File(out, "$i-$voice.wav").writeBytes(wav(pcm, tts.sampleRate))
            }
        }
    }

    private fun wav(pcm: FloatArray, rate: Int): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in pcm) data.putShort((f.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size * 2)
        return h.array() + data.array()
    }
}
