package com.acxelin.hisaabsaathi.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sqrt

private const val SAMPLE_RATE = 16_000
private const val WINDOW = 512 // Silero VAD v5 requires exactly 512 samples at 16 kHz.

/**
 * Speech-to-text that runs entirely on the phone with the downloaded IndicConformer model.
 * Audio is held in memory only for the length of one utterance and is never written to disk.
 */
class OnDeviceStt(private val packs: PackManager) {
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var loadedFor: Lang? = null

    @Volatile private var stopRequested = false

    fun available(lang: Lang): Boolean = packs.isReady(Catalog.stt(lang))

    /** Ends the current recording early (the worker tapped the mic again). */
    fun stop() { stopRequested = true }

    /**
     * Records until the speaker pauses (or [maxSeconds]), then transcribes.
     * [onLevel] receives 0..1 loudness for the mic animation; [onRecorded] fires when
     * recording ends and recognition begins.
     */
    @SuppressLint("MissingPermission")
    suspend fun listen(
        lang: Lang,
        maxSeconds: Int = 20,
        onLevel: (Float) -> Unit = {},
        onRecorded: () -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        lock.withLock {
            stopRequested = false
            val pack = Catalog.stt(lang)
            val vadFile = packs.file(pack.files.first { it.path.endsWith("silero_vad_v5.onnx") })
            val vad = Vad(
                null,
                VadModelConfig().apply {
                    sileroVadModelConfig = SileroVadModelConfig().apply {
                        model = vadFile.path
                        threshold = 0.5f
                        minSilenceDuration = 0.5f
                        minSpeechDuration = 0.25f
                        windowSize = WINDOW
                        maxSpeechDuration = maxSeconds.toFloat()
                    }
                    sampleRate = SAMPLE_RATE
                    numThreads = 1
                },
            )
            val samples = try {
                record(vad, maxSeconds, onLevel)
            } finally {
                vad.release()
            }
            onRecorded()
            if (samples.size < SAMPLE_RATE / 4) return@withLock ""
            val rec = recognizerFor(lang)
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)
                rec.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun record(vad: Vad, maxSeconds: Int, onLevel: (Float) -> Unit): FloatArray {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, WINDOW * 8),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
        val all = FloatArray(maxSeconds * SAMPLE_RATE)
        var count = 0
        val pcm = ShortArray(WINDOW)
        val chunk = FloatArray(WINDOW)
        var heardSpeech = false
        var quietWindows = 0
        // Stop after ~1.3 s of silence once speech was heard, or 6 s if nobody spoke.
        val endSilence = (1.3f * SAMPLE_RATE / WINDOW).toInt()
        val noSpeechLimit = 6 * SAMPLE_RATE
        recorder.startRecording()
        try {
            while (!stopRequested && count + WINDOW <= all.size) {
                var got = 0
                while (got < WINDOW) {
                    val n = recorder.read(pcm, got, WINDOW - got)
                    if (n <= 0) return all.copyOf(count)
                    got += n
                }
                var energy = 0.0
                for (i in 0 until WINDOW) {
                    val v = pcm[i] / 32768f
                    chunk[i] = v
                    energy += v * v
                }
                System.arraycopy(chunk, 0, all, count, WINDOW)
                count += WINDOW
                onLevel((sqrt(energy / WINDOW) * 6).toFloat().coerceIn(0f, 1f))

                vad.acceptWaveform(chunk)
                if (vad.isSpeechDetected()) {
                    heardSpeech = true
                    quietWindows = 0
                } else if (heardSpeech) {
                    if (++quietWindows >= endSilence) break
                } else if (count > noSpeechLimit) {
                    return FloatArray(0)
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            onLevel(0f)
        }
        return if (heardSpeech) all.copyOf(count) else FloatArray(0)
    }

    private fun recognizerFor(lang: Lang): OfflineRecognizer {
        recognizer?.let { if (loadedFor == lang) return it else it.release() }
        val pack = Catalog.stt(lang)
        val model = packs.file(pack.files.first { it.path.endsWith("model.int8.onnx") })
        val tokens = packs.file(pack.files.first { it.path.endsWith("tokens.txt") })
        val config = OfflineRecognizerConfig().apply {
            modelConfig = OfflineModelConfig().apply {
                nemo = OfflineNemoEncDecCtcModelConfig(model.path)
                this.tokens = tokens.path
                numThreads = 2
            }
            decodingMethod = "greedy_search"
        }
        return OfflineRecognizer(null, config).also { recognizer = it; loadedFor = lang }
    }

    /** Frees ~300 MB when the app goes to the background or before the language model loads. */
    suspend fun unload() = lock.withLock {
        recognizer?.release()
        recognizer = null
        loadedFor = null
    }
}

/**
 * Fallback for before the voice pack is downloaded: Android's own recogniser, used ONLY in
 * its on-device mode (Android 12+), so audio still never leaves the phone.
 */
class SystemOnDeviceStt(private val context: Context) {
    fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    suspend fun listen(lang: Lang, onRecorded: () -> Unit = {}): String = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext ""
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            fun finish(text: String) {
                sr.destroy()
                if (cont.isActive) cont.resume(text)
            }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) =
                    finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                override fun onError(error: Int) = finish("")
                override fun onEndOfSpeech() = onRecorded()
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            sr.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale(lang).toLanguageTag()),
            )
            cont.invokeOnCancellation { sr.destroy() }
        }
    }
}

fun locale(lang: Lang): Locale = Locale(lang.code, "IN")

/**
 * Reads text aloud with the phone's own text-to-speech engine. If the phone has no voice
 * for the chosen language (common for Odia), [speak] returns false and the app shows the
 * text instead.
 */
class Speaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    fun canSpeak(lang: Lang): Boolean =
        ready && tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE

    fun speak(text: String, lang: Lang): Boolean {
        if (!canSpeak(lang)) return false
        tts.language = locale(lang)
        tts.setSpeechRate(0.9f)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hisaab") == TextToSpeech.SUCCESS
    }

    fun stop() { if (ready) tts.stop() }
    fun shutdown() = tts.shutdown()
}
