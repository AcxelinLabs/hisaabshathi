package com.acxelin.hisaabsaathi.speech

import ai.onnxruntime.OrtEnvironment
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.TinyTts
import java.io.File
import java.io.RandomAccessFile

/**
 * Speaks with the Indic Tiny TTS voices in a separate process (":voice" in the manifest), so
 * a native crash or memory pressure there can never close the app; it then falls back to the
 * phone's own voice. Writes the audio as a WAV file in the app's private cache.
 */
class VoiceService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var tts: TinyTts? = null
    private var loadedDir: String? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("voice").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    private fun handle(msg: Message) {
        if (msg.what != MSG_SPEAK) return
        val d = msg.data
        val reply = Message.obtain(null, MSG_DONE)
        reply.data = Bundle().apply {
            try {
                val dir = d.getString(KEY_DIR)!!
                if (tts == null || loadedDir != dir) {
                    tts?.close()
                    // Never send anything anywhere: switch the runtime's telemetry off before use.
                    runCatching { OrtEnvironment.getEnvironment().setTelemetry(false) }
                    tts = TinyTts(File(dir), threads = 2); loadedDir = dir
                }
                val t = tts!!
                val wav = t.synthesize(d.getString(KEY_TEXT).orEmpty(), Lang.fromCode(d.getString(KEY_LANG)), d.getString(KEY_VOICE) ?: "female")
                val out = File(d.getString(KEY_OUT)!!)
                writeWav(out, wav, t.sampleRate)
                putString(KEY_OUT, out.path)
            } catch (e: Throwable) {
                putString(KEY_ERROR, e.message ?: e.javaClass.simpleName)
            }
        }
        runCatching { msg.replyTo?.send(reply) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        tts?.close(); thread.quitSafely(); super.onDestroy()
    }

    companion object {
        const val MSG_SPEAK = 1
        const val MSG_DONE = 2
        const val KEY_DIR = "dir"; const val KEY_TEXT = "text"; const val KEY_LANG = "lang"
        const val KEY_VOICE = "voice"; const val KEY_OUT = "out"; const val KEY_ERROR = "error"

        /** 16-bit mono PCM WAV. */
        fun writeWav(file: File, samples: FloatArray, rate: Int) {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { f ->
                f.setLength(0)
                val data = samples.size * 2
                fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
                fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
                f.write("RIFF".toByteArray()); f.write(le32(36 + data)); f.write("WAVE".toByteArray())
                f.write("fmt ".toByteArray()); f.write(le32(16)); f.write(le16(1)); f.write(le16(1))
                f.write(le32(rate)); f.write(le32(rate * 2)); f.write(le16(2)); f.write(le16(16))
                f.write("data".toByteArray()); f.write(le32(data))
                val buf = ByteArray(data)
                for (i in samples.indices) {
                    val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
                    buf[2 * i] = v.toByte(); buf[2 * i + 1] = (v shr 8).toByte()
                }
                f.write(buf)
            }
        }
    }
}
