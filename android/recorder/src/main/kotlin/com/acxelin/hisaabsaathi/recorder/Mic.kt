package com.acxelin.hisaabsaathi.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.acxelin.hisaabsaathi.core.VoiceSamples
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** Records one answer: [start] begins, [stop] returns the samples (16 kHz mono). */
interface Mic {
    fun start(onLevel: (Float) -> Unit)
    fun stop(): ShortArray
}

/**
 * The phone's microphone, set up exactly like HisaabSaathi listens (speech-tuned source,
 * 16 kHz mono 16-bit), so recordings sound to the speech models as they will in the app.
 * Stops by itself after [maxSeconds].
 */
class PhoneMic(private val maxSeconds: Int = 30) : Mic {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var samples = ShortArray(0)
    private var count = 0
    @Volatile private var error: Throwable? = null

    @SuppressLint("MissingPermission")
    override fun start(onLevel: (Float) -> Unit) {
        val rate = VoiceSamples.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Some phones refuse the speech-tuned source; fall back to the plain microphone.
        val rec = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC).firstNotNullOfOrNull { src ->
            runCatching { AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096)) }
                .getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED || run { it.release(); false } }
        } ?: error("The microphone could not start. Close other apps that use it and try again.")
        samples = ShortArray(maxSeconds * rate); count = 0; error = null; running = true
        rec.startRecording()
        worker = thread(name = "mic") {
            val buf = ShortArray(1024)
            try {
                while (running && count < samples.size) {
                    val n = rec.read(buf, 0, minOf(buf.size, samples.size - count))
                    if (n < 0) error("microphone read failed ($n)")
                    if (n == 0) { Thread.sleep(5); continue }
                    System.arraycopy(buf, 0, samples, count, n); count += n
                    var e = 0.0
                    for (i in 0 until n) { val v = buf[i] / 32768.0; e += v * v }
                    onLevel((sqrt(e / n) * 6).toFloat().coerceIn(0f, 1f))
                }
            } catch (t: Throwable) {
                error = t
            } finally {
                runCatching { rec.stop() }; rec.release(); onLevel(0f)
            }
        }
    }

    override fun stop(): ShortArray {
        running = false
        worker?.join(2000); worker = null
        error?.let { throw it }
        return samples.copyOf(count)
    }
}
