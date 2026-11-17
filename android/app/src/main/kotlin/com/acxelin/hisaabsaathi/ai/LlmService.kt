package com.acxelin.hisaabsaathi.ai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * Runs the language model in its own process (":llm" in the manifest). If the native
 * model runtime crashes or the phone runs out of memory while it works, only this process
 * dies: the app keeps running and carries on with its rules.
 */
class LlmService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var llm: LlmInference? = null
    private var loadedPath: String? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("llm").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    private fun handle(msg: Message) {
        if (msg.what != MSG_GENERATE) return
        val data = msg.data
        val path = data.getString(KEY_PATH) ?: return
        val reply = Message.obtain(null, MSG_RESULT)
        reply.data = Bundle().apply {
            try {
                if (llm == null || loadedPath != path) {
                    llm?.close()
                    llm = LlmInference.createFromOptions(
                        this@LlmService,
                        LlmInference.LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(768).build(),
                    )
                    loadedPath = path
                }
                putString(KEY_TEXT, llm!!.generateResponse(data.getString(KEY_PROMPT).orEmpty()))
            } catch (e: Throwable) {
                putString(KEY_ERROR, e.message ?: e.javaClass.simpleName)
            }
        }
        runCatching { msg.replyTo?.send(reply) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        llm?.close()
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
