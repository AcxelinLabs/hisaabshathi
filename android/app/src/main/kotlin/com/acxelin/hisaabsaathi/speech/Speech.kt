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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sqrt

private const val SAMPLE_RATE = 16_000

/** How one attempt to listen ended. Each case gets its own message, so a failure is never reported as "not understood". */
sealed interface Heard {
    data class Text(val text: String) : Heard
    /** The microphone worked but nobody spoke (or nothing recognisable was said). */
    data object Silence : Heard
    /** Something went wrong; [detail] is technical and shown only in Settings. */
    data class Failed(val kind: Kind, val detail: String) : Heard
    enum class Kind { MIC, ENGINE, NO_LANGUAGE }
}
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

    /** Frees the speech model (~200 MB); it reloads on the next listen. Called when memory runs low. */
    suspend fun release() = lock.withLock {
        recognizer?.release(); recognizer = null; loadedFor = null
    }

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
    ): Heard = withContext(Dispatchers.Default) {
        lock.withLock {
            stopRequested = false
            val pack = Catalog.stt(lang)
            val vadFile = packs.file(pack.files.first { it.path.endsWith("silero_vad_v5.onnx") })
            val vad = try { Vad(
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
            ) } catch (e: Throwable) {
                return@withLock Heard.Failed(Heard.Kind.ENGINE, "VAD: ${e.message ?: e.javaClass.simpleName}")
            }
            val samples = try {
                record(vad, maxSeconds, onLevel)
            } catch (e: Exception) {
                return@withLock Heard.Failed(Heard.Kind.MIC, e.message ?: e.javaClass.simpleName)
            } finally {
                vad.release()
            }
            onRecorded()
            if (samples.size < SAMPLE_RATE / 4) return@withLock Heard.Silence
            try {
                val rec = recognizerFor(lang)
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    rec.decode(stream)
                    rec.getResult(stream).text.trim().let { if (it.isEmpty()) Heard.Silence else Heard.Text(it) }
                } finally {
                    stream.release()
                }
            } catch (e: Throwable) {
                Heard.Failed(Heard.Kind.ENGINE, "STT: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun record(vad: Vad, maxSeconds: Int, onLevel: (Float) -> Unit): FloatArray {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Some phones refuse the speech-tuned source; fall back to the plain microphone.
        val recorder = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC).firstNotNullOfOrNull { source ->
            runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, WINDOW * 8))
            }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED || run { it.release(); false } }
        } ?: error("microphone could not start (minBuffer=$minBuf)")
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
                    if (n < 0) error("microphone read failed ($n)")
                    if (n == 0) continue
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

    suspend fun listen(lang: Lang, onRecorded: () -> Unit = {}): Heard = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext Heard.Failed(Heard.Kind.NO_LANGUAGE, "Android < 12")
        suspendCancellableCoroutine<Heard> { cont ->
            val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            fun finish(result: Heard) {
                sr.destroy()
                if (cont.isActive) cont.resume(result)
            }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    finish(if (text.isBlank()) Heard.Silence else Heard.Text(text))
                }
                override fun onError(error: Int) = finish(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Silence
                        SpeechRecognizer.ERROR_AUDIO -> Heard.Failed(Heard.Kind.MIC, "system recogniser error $error")
                        // 12 language not supported, 13 language unavailable, others: no usable offline model
                        else -> Heard.Failed(Heard.Kind.NO_LANGUAGE, "system recogniser error $error")
                    },
                )
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
 * Reads text aloud. Uses the app's own Indian-language voice when its pack is installed
 * (clearer, works with no internet, and covers Odia, which many phones lack), otherwise the
 * phone's text-to-speech engine. If neither can speak the language, [speak] returns false and
 * the app shows the text instead.
 */
class Speaker(context: Context, private val packs: com.acxelin.hisaabsaathi.models.PackManager) {
    private val app = context.applicationContext
    private var ready = false
    private val tts = TextToSpeech(app) { status -> ready = status == TextToSpeech.SUCCESS }
    private val voice = VoiceEngine(app)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private var job: kotlinx.coroutines.Job? = null
    private var player: android.media.MediaPlayer? = null

    /** "female" or "male", for the app's own voices. */
    var voiceName: String = "female"

    private fun systemCanSpeak(lang: Lang) = ready && tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE

    private fun voiceDir(lang: Lang): java.io.File? {
        val pack = com.acxelin.hisaabsaathi.models.Catalog.voice(lang) ?: return null
        return if (packs.isReady(pack) && voice.usable) packs.file(pack.files.first()).parentFile else null
    }

    fun canSpeak(lang: Lang): Boolean = voiceDir(lang) != null || systemCanSpeak(lang)

    fun speak(text: String, lang: Lang): Boolean {
        stop()
        val dir = voiceDir(lang)
        if (dir != null) {
            job = scope.launch {
                val wav = voice.synthesize(dir, text, lang, voiceName)
                if (wav != null) play(wav) else speakWithPhone(text, lang)
            }
            return true
        }
        return speakWithPhone(text, lang)
    }

    private fun speakWithPhone(text: String, lang: Lang): Boolean {
        if (!systemCanSpeak(lang)) return false
        tts.language = locale(lang)
        tts.setSpeechRate(0.9f)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hisaab") == TextToSpeech.SUCCESS
    }

    private fun play(file: java.io.File) {
        runCatching {
            player = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                setDataSource(file.path); setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare(); start()
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        player?.let { runCatching { it.stop(); it.release() } }; player = null
        if (ready) tts.stop()
    }

    fun shutdown() { stop(); tts.shutdown(); voice.close() }
}

/** Talks to [VoiceService] in its own process. Gives up (returns null) on any failure or delay. */
class VoiceEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private var service: android.os.Messenger? = null
    private var connection: android.content.ServiceConnection? = null
    private var count = 0

    /** False after the voice failed twice on this phone; the phone's own voice is used from then on. */
    val usable: Boolean get() = prefs.getInt("failures", 0) < 2
    val problem: String get() = prefs.getString("problem", "").orEmpty()

    private fun fail(why: String) {
        prefs.edit().putInt("failures", prefs.getInt("failures", 0) + 1).putString("problem", why).apply()
        close()
    }

    private suspend fun connect(): android.os.Messenger? {
        service?.let { return it }
        val ready = kotlinx.coroutines.CompletableDeferred<android.os.Messenger?>()
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                val m = binder?.let { android.os.Messenger(it) }; service = m; ready.complete(m)
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) { service = null }
            override fun onBindingDied(name: android.content.ComponentName?) { service = null }
        }
        if (!context.bindService(Intent(context, VoiceService::class.java), conn, Context.BIND_AUTO_CREATE)) return null
        connection = conn
        return kotlinx.coroutines.withTimeoutOrNull(8_000) { ready.await() }
    }

    suspend fun synthesize(dir: java.io.File, text: String, lang: Lang, voice: String): java.io.File? {
        val remote = connect() ?: return null.also { fail("voice service did not start") }
        val out = java.io.File(context.cacheDir, "voice/say_${count++ % 2}.wav")
        val done = kotlinx.coroutines.CompletableDeferred<Bundle>()
        val reply = android.os.Messenger(android.os.Handler(android.os.Looper.getMainLooper()) { m -> done.complete(m.data); true })
        val msg = android.os.Message.obtain(null, VoiceService.MSG_SPEAK).apply {
            data = Bundle().apply {
                putString(VoiceService.KEY_DIR, dir.path); putString(VoiceService.KEY_TEXT, text)
                putString(VoiceService.KEY_LANG, lang.code); putString(VoiceService.KEY_VOICE, voice)
                putString(VoiceService.KEY_OUT, out.path)
            }
            replyTo = reply
        }
        try { remote.send(msg) } catch (e: Exception) { return null.also { fail("voice process stopped") } }
        val data = kotlinx.coroutines.withTimeoutOrNull(30_000) { done.await() }
            ?: return null.also { fail(if (service == null) "voice process crashed" else "voice took too long") }
        data.getString(VoiceService.KEY_ERROR)?.let { return null.also { _ -> fail("voice error: $it") } }
        return out.takeIf { it.exists() && it.length() > 44 }
    }

    fun close() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null; service = null
    }
}
