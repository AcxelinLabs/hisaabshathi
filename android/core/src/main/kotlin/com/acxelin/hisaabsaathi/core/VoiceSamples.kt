package com.acxelin.hisaabsaathi.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sound as the app hears it: 16 kHz mono 16-bit WAV, the format of its own speech models,
 * and a check of how a recording sounds. Used for the voice clips a worker agrees to share
 * to improve the app (see Improve.kt) and by the test tools.
 */
object VoiceSamples {
    const val SAMPLE_RATE = 16_000

    /** How a recording sounds. */
    enum class Quality { GOOD, QUIET, LOUD, SILENT, SHORT }

    /**
     * [Quality] of a recording: SHORT under 0.4 s; SILENT when no 20 ms frame rises above a
     * whisper; QUIET when less than a quarter second is clearly voiced; LOUD when more than
     * 0.1% of samples hit the top (distortion). Thresholds suit a phone held about 20 cm away.
     */
    fun quality(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): Quality {
        if (pcm.size < sampleRate * 4 / 10) return Quality.SHORT
        val frame = sampleRate / 50
        var voiced = 0; var loudest = 0.0; var clipped = 0
        var i = 0
        while (i + frame <= pcm.size) {
            var e = 0.0
            for (k in i until i + frame) {
                val v = pcm[k].toInt()
                if (v >= 32_000 || v <= -32_000) clipped++
                val x = v / 32768.0; e += x * x
            }
            val rms = kotlin.math.sqrt(e / frame)
            if (rms > loudest) loudest = rms
            if (rms > 0.015) voiced++
            i += frame
        }
        return when {
            loudest < 0.006 -> Quality.SILENT
            voiced * 20 < 250 -> Quality.QUIET
            clipped * 1000 > pcm.size -> Quality.LOUD
            else -> Quality.GOOD
        }
    }

    /** The number in an id: "hi_017" is 17, "hi_1000" is 1000. */
    fun number(id: String): Int = id.substringAfterLast('_').toIntOrNull() ?: 0

    /** 16-bit samples from the speech engine's floats (-1..1). */
    fun pcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }

    /** A WAV file of [pcm] samples. */
    fun wav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return ByteArrayOutputStream(44 + data.size).apply { write(header); write(data) }.toByteArray()
    }
}
