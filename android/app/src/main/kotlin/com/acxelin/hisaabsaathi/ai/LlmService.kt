package com.acxelin.hisaabsaathi.ai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SessionConfig

/**
 * Runs the language model (LiteRT-LM) in its own process (":llm" in the manifest). If the
 * native runtime crashes or the phone runs out of memory while it works, only this process
 * dies: the app keeps running and carries on with its rules.
 *
 * Each sentence gets a fresh session with greedy decoding. The session applies the chat
 * template stored in the model file (Qwen3's, with thinking turned off), the same format
 * the model was fine-tuned on; tools/check_litertlm.py measures exactly this set-up.
 */
class LlmService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var engine: Engine? = null
    private var loadedPath: String? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("llm").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    private fun load(path: String): Engine {
        engine?.takeIf { loadedPath == path }?.let { return it }
        engine?.close()
        engine = null
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val e = Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(threads),
                maxNumTokens = 512,
                // Repacked weights are cached here so later loads are quick; Android may
                // clear it when storage runs low, and the model simply repacks next time.
                cacheDir = cacheDir.path,
            ),
        )
        e.initialize()
        engine = e
        loadedPath = path
        return e
    }

    private fun handle(msg: Message) {
        if (msg.what != MSG_GENERATE) return
        val data = msg.data
        val path = data.getString(KEY_PATH) ?: return
        val reply = Message.obtain(null, MSG_RESULT)
        reply.data = Bundle().apply {
            try {
                val session = load(path).createSession(
                    SessionConfig(SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)),
                )
                val text = session.use { it.generateContent(listOf(InputData.Text(data.getString(KEY_PROMPT).orEmpty()))) }
                putString(KEY_TEXT, text.substringBefore("<|im_end|>").trim())
            } catch (e: Throwable) {
                putString(KEY_ERROR, e.message ?: e.javaClass.simpleName)
            }
        }
        runCatching { msg.replyTo?.send(reply) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        runCatching { engine?.close() }
        thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val MSG_GENERATE = 1
        const val MSG_RESULT = 2
        const val KEY_PATH = "path"
        const val KEY_PROMPT = "prompt"
        const val KEY_TEXT = "text"
        const val KEY_ERROR = "error"
    }
}
